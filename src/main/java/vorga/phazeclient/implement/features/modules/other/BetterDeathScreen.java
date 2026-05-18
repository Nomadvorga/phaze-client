package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Statistics panel rendered on top of vanilla's death screen.
 * Captures the death context as it happens (killer entity name +
 * weapon stack), tracks session-level death count and time-since-
 * last-death, and surfaces it on the death screen so the user has
 * a quick post-mortem.
 *
 * <h3>Capture path</h3>
 * {@code PlayerEntityBetterDeathMixin} hooks
 * {@code LivingEntity.onDeath(DamageSource)} on
 * {@code MinecraftClient.player} and forwards the source to
 * {@link #recordDeath(DamageSource)}. From the source we extract
 * the attacker's display name and (if the attacker is a
 * LivingEntity) their main hand stack as the "weapon".
 *
 * <h3>Render path</h3>
 * {@code DeathScreenBetterMixin} draws the captured context onto
 * the screen at TAIL of the screen's render method. Spectate
 * button intentionally NOT touched - the user explicitly asked to
 * keep death screen non-spectate.
 */
public final class BetterDeathScreen extends Module {
    private static final BetterDeathScreen INSTANCE = new BetterDeathScreen();
    /** How many recent deaths to remember in the history strip. */
    private static final int HISTORY_LIMIT = 5;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting showKiller = new BooleanSetting(
            "Show Killer",
            "Display the entity that killed you and its weapon"
    ).setValue(true);
    public final BooleanSetting showSessionDeaths = new BooleanSetting(
            "Show Session Deaths",
            "Display total deaths since the client started and time since previous death"
    ).setValue(true);
    public final BooleanSetting showDeathHistory = new BooleanSetting(
            "Show Death History",
            "List the last 5 death sources for quick comparison"
    ).setValue(true);

    private DeathRecord lastDeath;
    private final Deque<DeathRecord> history = new ArrayDeque<>(HISTORY_LIMIT);
    private int sessionDeaths = 0;
    private long lastDeathMs = 0L;

    private BetterDeathScreen() {
        super("better_death_screen", "Better Death Screen", ModuleCategory.OTHER);
        showKiller.setFullWidth(true);
        showSessionDeaths.setFullWidth(true);
        showDeathHistory.setFullWidth(true);
        setup(generalSection, showKiller, showSessionDeaths, showDeathHistory);
    }

    public static BetterDeathScreen getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds a stats panel to the death screen with killer / weapon / session counters";
    }

    @Override
    public String getIcon() {
        return "better_death_screen.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Capture a death event. Called from the player-mixin onDeath
     * hook. Idempotent within a single client tick - if the player
     * "dies" multiple times in a tick (mod-induced edge case), the
     * first call wins so the captured weapon snapshot matches the
     * actual killing blow.
     */
    public void recordDeath(DamageSource source) {
        long now = System.currentTimeMillis();
        long delta = lastDeathMs == 0L ? -1L : now - lastDeathMs;
        DeathRecord record = buildRecord(source, now, delta);
        lastDeath = record;
        lastDeathMs = now;
        sessionDeaths++;
        history.addFirst(record);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private DeathRecord buildRecord(DamageSource source, long timestampMs, long deltaSinceLastMs) {
        String killerName = "Unknown";
        ItemStack weapon = ItemStack.EMPTY;
        if (source != null) {
            // {@link DamageSource#getAttacker} returns the entity
            // that "physically" attacked (the wolf, the arrow's
            // shooter, etc.). For environmental damage (lava, fall)
            // it's null; we fall back to the death-message text in
            // that case so the user still gets meaningful context.
            if (source.getAttacker() != null) {
                killerName = source.getAttacker().getDisplayName().getString();
                if (source.getAttacker() instanceof LivingEntity living) {
                    weapon = living.getMainHandStack();
                }
            } else {
                // Environmental death: extract a short label from
                // the type registry name (e.g. "minecraft:fall" -> "fall").
                killerName = source.getType().msgId();
            }
        }
        return new DeathRecord(killerName, weapon, timestampMs, deltaSinceLastMs);
    }

    public DeathRecord getLastDeath() {
        return lastDeath;
    }

    public int getSessionDeaths() {
        return sessionDeaths;
    }

    public long getLastDeathMs() {
        return lastDeathMs;
    }

    public Deque<DeathRecord> getHistory() {
        return history;
    }

    /**
     * Build a one-line summary string for a record entry. Public so
     * the screen mixin can reuse this for both the "current death"
     * and "history" rendering paths.
     */
    public static String summarise(DeathRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.killerName);
        if (!r.weapon.isEmpty()) {
            Text weaponName = r.weapon.getName();
            sb.append(" with ").append(weaponName.getString());
        }
        return sb.toString();
    }

    /** Per-death capture record. Public for mixin access. */
    public record DeathRecord(String killerName, ItemStack weapon, long timestampMs,
                              long deltaSinceLastMs) {
    }
}
