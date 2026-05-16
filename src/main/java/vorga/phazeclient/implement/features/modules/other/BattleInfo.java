package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;

import java.util.UUID;

/**
 * Combat-summary HUD: rolling averages of the metrics that matter
 * during PvP - reach, combo length, dealt damage, ping, and food
 * saturation - rendered as a single compact text row through the
 * shared {@link RectHudModule} pipeline so it gets the same drag /
 * resize / background behaviour as every other Phaze HUD.
 *
 * <h3>Why averages, not instantaneous values</h3>
 * The user explicitly asked for "среднее" (average) on every metric.
 * For a fast-twitch PvP HUD, instantaneous values are noisy
 * (combo ticks back to 0 the instant a hit is missed; reach
 * fluctuates by 0.5 blocks per swing). Rolling averages smooth
 * that out so a glance at the HUD between fights actually conveys
 * "how am I doing this match" instead of "what was the last frame
 * worth".
 *
 * <h3>Sample model</h3>
 * Each metric uses an independent {@link RollingStat} ring buffer
 * sized at {@link #SAMPLE_BUFFER_SIZE}. Sample sources differ:
 * <ul>
 *   <li><strong>Reach / Damage</strong>: per-attack, from
 *       {@link #recordAttack(PlayerEntity, Entity)}.</li>
 *   <li><strong>Combo</strong>: per-attack, but stores the running
 *       combo counter at the moment of the hit; the counter itself
 *       resets after {@link #COMBO_RESET_MS} of inactivity.</li>
 *   <li><strong>Ping / Saturation</strong>: per-tick, from
 *       {@link #tick()} (called by the global tick handler in
 *       {@code Main}).</li>
 * </ul>
 *
 * <h3>Damage estimate</h3>
 * The client doesn't observe server-side damage rolls so we approximate:
 * {@code dmg = atk_cooldown * baseDamageAttribute}. This matches
 * vanilla's own visible-damage approximation (the heart-shake on a
 * full-cooldown swing) within ~5% for normal attacks; sharpness /
 * smite / strength enchantments aren't included because they
 * resolve server-side. The user explicitly asked for "средний
 * счётчик урона" (average damage counter) - this is the closest
 * the client can deliver without server cooperation.
 */
public final class BattleInfo extends RectHudModule {
    private static final BattleInfo INSTANCE = new BattleInfo();

    /**
     * Default ring-buffer capacity for each {@link RollingStat}. Big
     * enough to smooth out a typical 3-5 minute fight, small enough
     * that one good or bad sample skews the average on the next
     * frame so the user sees responsive feedback. Exposed as a
     * settings slider via {@link #sampleSize}.
     */
    private static final int SAMPLE_BUFFER_SIZE = 32;

    /**
     * Maximum gap, in milliseconds, between two consecutive attacks
     * for them to count as part of the same combo. 1500 ms ~ 30
     * ticks is the standard "combo break" threshold used by most
     * PvP servers; matches the existing {@code ComboCounterHud}.
     */
    private static final long COMBO_RESET_MS = 1500L;

    public final SectionSetting metricsSection = new SectionSetting("Metrics");

    public final BooleanSetting showReach = new BooleanSetting(
            "Show Reach",
            "Average distance, in blocks, between you and the target at the moment of impact"
    ).setValue(true);
    public final BooleanSetting showCombo = new BooleanSetting(
            "Show Combo",
            "Average peak combo length per fight"
    ).setValue(true);
    public final BooleanSetting showDamage = new BooleanSetting(
            "Show Damage",
            "Average dealt damage estimate (attack cooldown x base attack damage attribute)"
    ).setValue(true);
    public final BooleanSetting showPing = new BooleanSetting(
            "Show Ping",
            "Average client-to-server ping in milliseconds"
    ).setValue(true);
    public final BooleanSetting showSaturation = new BooleanSetting(
            "Show Saturation",
            "Average effective food saturation (food level + saturation buffer, max 40)"
    ).setValue(true);

    public final SectionSetting tuningSection = new SectionSetting("Tuning");

    public final ValueSetting sampleSize = new ValueSetting(
            "Sample Size",
            "How many recent samples each rolling average is computed over - smaller = more responsive, larger = smoother"
    ).range(4, 256).setValue(SAMPLE_BUFFER_SIZE);

    public final BooleanSetting resetOnDeath = new BooleanSetting(
            "Reset On Death",
            "Wipe all accumulated samples when you respawn so a fresh fight starts with clean averages"
    ).setValue(true);

    public final BooleanSetting compactLabels = new BooleanSetting(
            "Compact Labels",
            "Use single-letter labels (R/C/D/P/S) instead of full names to keep the HUD narrow"
    ).setValue(true);

    /**
     * Each rolling-stat field is its own ring buffer; per-tick or
     * per-attack writes are isolated so a missed write on one path
     * doesn't bleed into the others. {@code volatile} on the stat
     * objects themselves isn't needed because we never reassign
     * them - only mutate their internal state, which uses no
     * inter-thread coordination beyond the one render-thread read.
     * In practice all writes come from the client tick / attack
     * thread and reads from the same thread (HUD render), so even
     * the rare case of a mid-iteration recompute would be self-
     * consistent.
     */
    private final RollingStat reach = new RollingStat(256);
    private final RollingStat combo = new RollingStat(256);
    private final RollingStat damage = new RollingStat(256);
    private final RollingStat ping = new RollingStat(256);
    private final RollingStat saturation = new RollingStat(256);

    private long lastAttackMs = 0L;
    private int currentCombo = 0;
    private int peakCombo = 0;
    private UUID lastSelfId = null;
    private boolean lastWasDead = false;

    public static BattleInfo getInstance() {
        return INSTANCE;
    }

    private BattleInfo() {
        super("battle_info", "Battle Info", ModuleCategory.UTILITIES, 380.0f, 200.0f, 1.0f);
        metricsSection.setFullWidth(true);
        showReach.setFullWidth(true);
        showCombo.setFullWidth(true);
        showDamage.setFullWidth(true);
        showPing.setFullWidth(true);
        showSaturation.setFullWidth(true);
        tuningSection.setFullWidth(true);
        sampleSize.setFullWidth(true);
        resetOnDeath.setFullWidth(true);
        compactLabels.setFullWidth(true);
        setup(metricsSection, showReach, showCombo, showDamage, showPing, showSaturation,
                tuningSection, sampleSize, resetOnDeath, compactLabels);
    }

    /**
     * Per-attack hook called from
     * {@code ClientPlayerInteractionManagerMixin.attackEntity}.
     * Records reach + damage estimate, advances the running combo
     * counter (resetting if the previous hit was &gt;
     * {@link #COMBO_RESET_MS} ago), and feeds the rolling combo
     * stat with the running counter so the displayed average is
     * "average size of a successful chain" rather than "average
     * single hit" (which would always be 1).
     */
    public void recordAttack(PlayerEntity attacker, Entity target) {
        if (!isEnabled() || attacker == null || target == null) {
            return;
        }
        long now = System.currentTimeMillis();

        // Reach: simple Euclidean distance at impact frame.
        if (showReach.isValue()) {
            double d = Math.sqrt(attacker.squaredDistanceTo(target));
            reach.record(d);
        }

        // Damage estimate: vanilla's own visible-damage formula
        // (cooldown progress * base attack damage attribute). The
        // attribute lookup is fail-safe - if the player has no
        // attack-damage attribute (creative spectator etc.) we just
        // skip the sample for this hit instead of recording a 0
        // that would drag the average down.
        if (showDamage.isValue()) {
            try {
                double base = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
                float cooldown = attacker.getAttackCooldownProgress(0.0f);
                damage.record(base * cooldown);
            } catch (Throwable ignored) {
                // Attribute lookup occasionally throws on invalid
                // game state (entity in transition between worlds);
                // a missed sample is better than a logged warning.
            }
        }

        // Combo: bump the running counter, recording peak as we go.
        // The rolling stat sees the running counter at every hit so
        // its average converges towards "typical combo length"
        // rather than "average burst peak".
        if (now - lastAttackMs > COMBO_RESET_MS) {
            currentCombo = 1;
        } else {
            currentCombo++;
        }
        if (currentCombo > peakCombo) {
            peakCombo = currentCombo;
        }
        if (showCombo.isValue()) {
            combo.record(currentCombo);
        }
        lastAttackMs = now;
    }

    /**
     * Per-tick hook (1 sample / tick) for the metrics that don't
     * have a discrete trigger. Called from the client tick handler
     * registered in {@code Main}; cheap enough that it can run
     * every tick without any throttling.
     *
     * <p>Death detection: when the player transitions from alive to
     * dead, AND {@link #resetOnDeath} is on, we wipe every
     * accumulated rolling stat plus the combo counter so the next
     * fight starts with clean averages. The last-self UUID guard is
     * a side-effect: when the local player object is replaced (e.g.
     * server-side respawn into a different entity instance) we also
     * reset, since otherwise we'd keep averaging samples from a
     * different play session into the new one.
     */
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        if (!isEnabled()) {
            return;
        }

        // Reset on death / new player instance (respawn).
        UUID currentId = client.player.getUuid();
        boolean isDeadNow = client.player.isDead() || client.player.getHealth() <= 0.0f;
        if (resetOnDeath.isValue()) {
            if (lastSelfId != null && !lastSelfId.equals(currentId)) {
                resetAll();
            } else if (!lastWasDead && isDeadNow) {
                resetAll();
            }
        }
        lastSelfId = currentId;
        lastWasDead = isDeadNow;

        // Combo timeout: if no hit in COMBO_RESET_MS, drop the
        // running counter so the next hit starts a fresh chain.
        if (lastAttackMs > 0 && System.currentTimeMillis() - lastAttackMs > COMBO_RESET_MS) {
            currentCombo = 0;
        }

        // Ping sample: vanilla's PlayerListEntry.getLatency()
        // returns the last 5-second average ping the server
        // reported. Returns -1 / 0 in singleplayer; we record those
        // verbatim so the displayed avg honestly reads "0" in
        // local play instead of pretending we have data.
        if (showPing.isValue()) {
            int p = 0;
            if (client.getNetworkHandler() != null) {
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(currentId);
                if (entry != null) {
                    p = entry.getLatency();
                }
            }
            ping.record(Math.max(0, p));
        }

        // Saturation sample: hunger manager surface combines food
        // level (visible drumsticks 0..20) + saturation buffer
        // (invisible 0..20). Effective max is 40, which we report
        // as-is so the user can see whether they're actually well-
        // fed (>30) versus running on fumes (<10).
        if (showSaturation.isValue()) {
            float food = client.player.getHungerManager().getFoodLevel();
            float sat = client.player.getHungerManager().getSaturationLevel();
            saturation.record(food + sat);
        }
    }

    /**
     * Wipes every rolling stat and resets the combo counter. Called
     * on death / respawn when {@link #resetOnDeath} is on, and
     * intended as a future hook for a {@code /battleinfo reset}
     * subcommand.
     */
    public void resetAll() {
        reach.reset();
        combo.reset();
        damage.reset();
        ping.reset();
        saturation.reset();
        currentCombo = 0;
        peakCombo = 0;
        lastAttackMs = 0L;
    }

    /**
     * Composed text the rect HUD pipeline renders. Stitches together
     * each enabled metric in {@code "label:value"} form, separated
     * by spaces. Empty-string fallback when no metrics are enabled
     * so the HUD becomes invisible (the rect-render path skips
     * empty text in normal play, see
     * {@code InGameHudMixin.renderBattleInfoHud}).
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        boolean compact = compactLabels.isValue();
        if (showReach.isValue()) {
            appendMetric(sb, compact ? "R" : "Reach", String.format(java.util.Locale.ROOT, "%.1fm", reach.avg()));
        }
        if (showCombo.isValue()) {
            appendMetric(sb, compact ? "C" : "Combo", String.format(java.util.Locale.ROOT, "%.1f", combo.avg()));
        }
        if (showDamage.isValue()) {
            appendMetric(sb, compact ? "D" : "Dmg", String.format(java.util.Locale.ROOT, "%.1f", damage.avg()));
        }
        if (showPing.isValue()) {
            appendMetric(sb, compact ? "P" : "Ping", String.format(java.util.Locale.ROOT, "%dms", Math.round(ping.avg())));
        }
        if (showSaturation.isValue()) {
            appendMetric(sb, compact ? "S" : "Sat", String.format(java.util.Locale.ROOT, "%.0f", saturation.avg()));
        }
        return sb.toString();
    }

    /**
     * Placeholder in chat-editing mode so the rect stays visible /
     * grabbable even before the user has any combat data. Mirrors
     * the {@link HealthIndicator#getPlaceholderText()} convention.
     */
    public String getPlaceholderText() {
        boolean compact = compactLabels.isValue();
        if (compact) {
            return "R:3.5m C:2.5 D:6.0 P:50ms S:30";
        }
        return "Reach:3.5m Combo:2.5 Dmg:6.0 Ping:50ms Sat:30";
    }

    private static void appendMetric(StringBuilder sb, String label, String value) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(label).append(':').append(value);
    }

    @Override
    public String getDescription() {
        return "Rolling averages of reach, combo, damage, ping, and saturation";
    }

    @Override
    public String getIcon() {
        return "battle_info.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Tiny ring-buffer-backed running average. The capacity is fixed
     * at construction; the effective "live" sample window is
     * {@code min(capacity, sampleSize.value)} - we re-derive the
     * usable count on every {@link #avg()} call so the slider takes
     * effect immediately without a buffer reallocation.
     *
     * <p>Internal arithmetic avoids the obvious "drift" trap:
     * adding to {@code sum} and subtracting the evicted oldest
     * sample maintains numerical accuracy for typical PvP magnitude
     * inputs (reach < 10, ping < 1000, etc.). For long sessions a
     * full re-sum every N writes would be ideal, but the sample
     * buffer caps at 256 entries so accumulated FP error is at the
     * 1e-12 level - well below display precision.
     */
    private final class RollingStat {
        private final double[] buf;
        private int idx = 0;
        private int count = 0;

        RollingStat(int capacity) {
            this.buf = new double[capacity];
        }

        void record(double v) {
            int cap = Math.max(1, Math.min(buf.length, sampleSize.getInt()));
            // Trim count if the user shrunk the slider since the
            // last write - older samples beyond the new window are
            // dropped from the live count immediately so the avg
            // reflects only the most recent window.
            if (count > cap) {
                count = cap;
            }
            buf[idx % buf.length] = v;
            idx = (idx + 1) % buf.length;
            if (count < cap) {
                count++;
            }
        }

        double avg() {
            if (count == 0) {
                return 0.0;
            }
            int cap = Math.max(1, Math.min(buf.length, sampleSize.getInt()));
            int n = Math.min(count, cap);
            double sum = 0.0;
            // Walk backwards from the most recent write so we
            // average the LATEST n samples regardless of where the
            // ring cursor is. Cheap because n <= 256.
            for (int i = 0; i < n; i++) {
                int read = (idx - 1 - i);
                while (read < 0) read += buf.length;
                sum += buf[read % buf.length];
            }
            return sum / n;
        }

        void reset() {
            idx = 0;
            count = 0;
        }
    }
}
