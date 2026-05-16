package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player totem-pop tracker.
 *
 * <h3>What it does</h3>
 * Listens for vanilla's {@code USE_TOTEM_OF_UNDYING} entity status
 * (broadcast by the server through {@code EntityStatusS2CPacket}
 * with status id 35 - the same packet that drives the
 * "totem fly-up" animation client-side). Every time a player within
 * {@link #radius} blocks of the local player triggers it,
 * {@link #recordTotemUse(LivingEntity)} bumps that player's
 * personal counter, optionally announces it in chat, and exposes
 * the counter to the nametag-decoration mixin so other players see
 * a {@code | -N} suffix next to their name reflecting how many
 * totems they've burned through this session.
 *
 * <h3>Why a separate radius gate</h3>
 * The vanilla {@code EntityStatusS2CPacket} doesn't carry distance
 * information - the server broadcasts it to every client subscribed
 * to the entity, which is "everyone in tracking range" (~256 blocks
 * by default). Pop notifications from the entire server would spam
 * chat unusably; the {@link #radius} setting filters down to a
 * locally-relevant bubble around the local player so the user only
 * sees what's happening in their actual fight.
 *
 * <h3>Threading</h3>
 * The packet handler runs on the network thread; counter mutations
 * therefore have to be safe for concurrent access from network +
 * render threads. {@link ConcurrentHashMap} provides per-bucket
 * atomicity for the increment-or-create idiom we use, and the read
 * path (nametag mixin) is fine with eventual consistency - one
 * frame's stale read is invisible to the user. Last-use timestamps
 * use {@link System#currentTimeMillis()} so the cleanup tick can
 * compare them against the user-set {@link #resetCooldownSeconds}
 * window.
 *
 * <h3>Why no expiration of counts on player leave</h3>
 * The counter persists as long as the module is active because the
 * user explicitly asked for "кто и сколько потерял тотемов" - i.e.
 * a session-cumulative record. Clearing on leave would lose data
 * the user might want to reference. The {@code Reset Cooldown}
 * setting governs PASSIVE expiration after extended inactivity,
 * not on-leave clearing.
 */
public final class TotemTracker extends Module {
    private static final TotemTracker INSTANCE = new TotemTracker();

    /**
     * Vanilla's {@code EntityStatuses.USE_TOTEM_OF_UNDYING} numeric
     * value. Hard-coded as a byte literal rather than imported so the
     * mixin descriptor doesn't need an additional vanilla class
     * reference and so the module compiles cleanly even if Mojang
     * relocates the constant in a future version (the wire-protocol
     * value 35 is far more stable than the class location).
     */
    public static final byte STATUS_USE_TOTEM = 35;

    public final SectionSetting generalSection = new SectionSetting("General");

    /**
     * Maximum distance, in blocks, from the local player at which a
     * totem pop will be counted. The default of 32 covers a typical
     * PvP arena without dragging in irrelevant pops from the other
     * end of the map. Range cap of 256 matches vanilla's default
     * entity tracking range - going beyond that wouldn't see anything
     * because the packet wouldn't reach the client.
     */
    public final ValueSetting radius = new ValueSetting(
            "Radius",
            "Maximum distance from you in blocks within which a totem pop is counted"
    ).range(8, 256).setValue(32);

    /**
     * Whether to print a chat line every time a tracked player pops
     * a totem. Default ON because the user explicitly requested the
     * chat notification ("в чате пишется"); kept as a toggle for
     * users who'd rather rely on the nametag suffix alone in busy
     * fights.
     */
    public final BooleanSetting chatNotify = new BooleanSetting(
            "Chat Notify",
            "Print a chat message when a tracked player pops a totem"
    ).setValue(true);

    /**
     * Whether to append a {@code | -N} suffix to tracked players'
     * nametags showing their cumulative pop count this session.
     * Default ON; turn off if you want a cleaner overhead view.
     */
    public final BooleanSetting nametagSuffix = new BooleanSetting(
            "Nametag Suffix",
            "Append \" | -N\" to tracked players' nametags showing how many totems they've lost"
    ).setValue(true);

    /**
     * Seconds of inactivity (no fresh totem pops from a given player)
     * after which their counter resets to zero. Set to {@code 0} to
     * never auto-reset (counter persists for the whole session).
     */
    public final ValueSetting resetCooldownSeconds = new ValueSetting(
            "Reset Cooldown",
            "Seconds of inactivity after which a player's totem-loss counter resets to 0 (0 = never)"
    ).range(0, 600).setValue(0);

    /**
     * Per-name running totem-pop counter. Keyed by the player's
     * unwrapped display-name string ({@code PlayerEntity.getName()
     * .getString()}) rather than by UUID for one specific reason:
     * the {@code EntityRenderer.renderLabelIfPresent} method we have
     * to hook for the nametag suffix only receives an
     * {@code EntityRenderState} (with no UUID surface) and a
     * decorated {@link Text}. The text's plain string at least
     * <em>contains</em> the player's name even when the server has
     * prepended a rank prefix, so a substring lookup against this
     * keyed map works without us having to reach back through the
     * dispatcher to recover the source entity.
     *
     * <p>Concurrent map because writes come from the network thread
     * and reads from the render thread.
     */
    private final Map<String, Integer> losses = new ConcurrentHashMap<>();

    /**
     * Per-name timestamp of the player's most recent totem pop, in
     * wall-clock millis. Used by {@link #pruneStaleEntries()} to
     * drop counters that haven't been touched in
     * {@link #resetCooldownSeconds}.
     */
    private final Map<String, Long> lastUseMs = new ConcurrentHashMap<>();

    public static TotemTracker getInstance() {
        return INSTANCE;
    }

    private TotemTracker() {
        super("totem_tracker", "Totem Tracker", ModuleCategory.UTILITIES);
        radius.setFullWidth(true);
        chatNotify.setFullWidth(true);
        nametagSuffix.setFullWidth(true);
        resetCooldownSeconds.setFullWidth(true);
        setup(generalSection, radius, chatNotify, nametagSuffix, resetCooldownSeconds);
    }

    /**
     * Hook called by {@code LivingEntityTotemTrackerMixin} every time
     * vanilla dispatches the {@code USE_TOTEM_OF_UNDYING} status to
     * a {@link LivingEntity}.
     *
     * <p>Filters down to {@link PlayerEntity} (so non-player totem
     * usage on Vindicators / Evokers / Iron Golems doesn't pollute
     * the counter) and to entities within the configured
     * {@link #radius} of the local player. Both filters are
     * deliberately cheap so the network-thread call returns quickly.
     *
     * <p>Runs on the network thread - writes go through the
     * concurrent map's atomic compute methods.
     */
    public void recordTotemUse(LivingEntity entity) {
        if (!isEnabled() || entity == null) {
            return;
        }
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        // Exclude self - the user already sees their own totem
        // pop animation; counting it would add noise without value.
        if (player == client.player) {
            return;
        }
        double maxDist = radius.getValue();
        double distSq = player.squaredDistanceTo(client.player);
        if (distSq > maxDist * maxDist) {
            return;
        }

        String name = player.getName().getString();
        if (name == null || name.isEmpty()) {
            return;
        }
        int newCount = losses.merge(name, 1, Integer::sum);
        lastUseMs.put(name, System.currentTimeMillis());

        if (chatNotify.isValue()) {
            // Compose a coloured chat row: gold {prefix}, white name,
            // gray descriptor, red {-N}. Goes through the local
            // chat-message path so it doesn't leak to the server.
            MutableText line = Text.literal("[Totem] ").formatted(Formatting.GOLD)
                    .append(Text.literal(name).formatted(Formatting.WHITE))
                    .append(Text.literal(" lost a totem ").formatted(Formatting.GRAY))
                    .append(Text.literal("(-" + newCount + ")").formatted(Formatting.RED));
            client.execute(() -> {
                if (client.inGameHud != null) {
                    client.inGameHud.getChatHud().addMessage(line);
                }
            });
        }
    }

    /**
     * Returns the current totem-loss count for the given player name,
     * or {@code 0} if we've never seen them lose a totem (also the
     * default state for non-tracked players).
     *
     * <p>Called by {@code EntityRendererTotemNametagMixin} on the
     * render thread; no synchronisation required because
     * {@link ConcurrentHashMap#getOrDefault} is atomic.
     */
    public int getLossCount(String name) {
        if (name == null) {
            return 0;
        }
        return losses.getOrDefault(name, 0);
    }

    /**
     * Substring-match variant of {@link #getLossCount(String)} used
     * by the nametag mixin: scans the tracker's keys looking for any
     * tracked name that occurs as a substring of the rendered
     * nametag text. This handles the common server case of prefix
     * decoration (e.g. {@code "§6[VIP]§r Steve"} - the displayed
     * text contains the {@code Steve} key the tracker stored when
     * the {@code USE_TOTEM_OF_UNDYING} packet arrived).
     *
     * <p>Returns the count for the FIRST matching key. With the
     * radius gate applied at record time, the live keyset rarely
     * exceeds a handful of entries, so the linear scan is cheap.
     * Returns {@code 0} when no key matches, which the caller
     * treats as "don't decorate this nametag".
     */
    public int getLossCountFromText(String displayed) {
        if (displayed == null || displayed.isEmpty() || losses.isEmpty()) {
            return 0;
        }
        for (Map.Entry<String, Integer> e : losses.entrySet()) {
            String key = e.getKey();
            if (key.isEmpty()) continue;
            if (displayed.contains(key)) {
                return e.getValue();
            }
        }
        return 0;
    }

    /**
     * Convenience: combined "is this enabled AND should the nametag
     * be decorated for this player" check. Used by the nametag
     * mixin to short-circuit the suffix append when the toggle is
     * off or no count exists - avoids building the {@link
     * MutableText} copy on the hot render path.
     */
    public boolean shouldDecorateNametag(String name) {
        return isEnabled() && nametagSuffix.isValue() && getLossCount(name) > 0;
    }

    /**
     * Periodic GC of stale counter entries. Called from the client
     * tick handler in {@code Main}. When {@link #resetCooldownSeconds}
     * is non-zero, any entry whose last update is older than the
     * window is dropped from both maps so the next nametag query
     * returns the fresh "0" sentinel.
     *
     * <p>The cleanup pass is O(n) over the loss map, which is
     * fine because n is bounded by "players you've fought this
     * session" - typically &lt;100. Running this once per second
     * (the user-facing tick) is plenty.
     */
    public void pruneStaleEntries() {
        if (!isEnabled()) {
            return;
        }
        int cooldown = resetCooldownSeconds.getInt();
        if (cooldown <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = cooldown * 1000L;
        Iterator<Map.Entry<String, Long>> it = lastUseMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() >= windowMs) {
                losses.remove(e.getKey());
                it.remove();
            }
        }
    }

    /**
     * Snapshot of the current state, used for the diagnostic /reset
     * subcommand if/when one is wired up. Read-only - returns a
     * defensive copy so callers can't mutate the live map.
     */
    public Map<String, Integer> snapshot() {
        return new HashMap<>(losses);
    }

    /**
     * Manual reset hook (intended for future "/totemtracker reset"
     * style command). Wipes both maps atomically.
     */
    public void resetAll() {
        losses.clear();
        lastUseMs.clear();
    }

    @Override
    public String getDescription() {
        return "Tracks who pops totems near you - prints to chat and adds \" | -N\" to their nametag";
    }

    @Override
    public String getIcon() {
        return "totem_tracker.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
