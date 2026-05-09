package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto GG. Sends {@code gg} or {@code GG} in chat after the local player
 * kills another player. The send is deferred by a small delay so it
 * doesn't look bot-instant.
 *
 * <p>Detection runs entirely in the client tick loop and combines a
 * "recently attacked" stamp with a per-player health snapshot:
 *
 * <ul>
 *   <li><b>Live transition path</b>: every visible player is sampled
 *       each tick. A flip from {@code wasAlive=true} to
 *       {@code !isAlive()} <em>or</em> a {@code prevHealth>0 -> curr<=0}
 *       step counts as a kill if we attacked them within the
 *       {@link #ATTACK_TIMEOUT_MS} window. Covers servers that sync
 *       opponent health honestly (vanilla, most PvP servers, etc).</li>
 *   <li><b>Despawn path</b>: any entry in {@link #trackedEntities}
 *       whose entity has been removed from the world (or reports
 *       {@code !isAlive()} but isn't iterable in
 *       {@code mc.world.getPlayers()} anymore) counts as a kill if it
 *       was attacked within the same window. Covers servers that
 *       remove the dead player instantly without ever pushing a zero
 *       health value to the client - the original limitation that the
 *       previous {@code <= 2.0F} gate failed at.</li>
 * </ul>
 *
 * <p>{@code recentlyAttacked} is keyed by the {@link PlayerEntity}
 * reference - identity equality is fine because the entity object
 * survives across the 5-second window even when the world tracker
 * unhooks it. The map is cleaned at the top of every tick so an
 * escaped target's later death by a third party can't be misattributed.
 */
public final class AutoGG extends Module {
    private static final AutoGG INSTANCE = new AutoGG();

    /** Window during which an attack still counts toward kill credit. */
    private static final long ATTACK_TIMEOUT_MS = 5000L;

    public final SectionSetting generalSection = new SectionSetting("General");

    public final SelectSetting message = new SelectSetting(
            "Message",
            "Phrase to send in chat after a kill"
    ).value("gg", "GG").selected("GG");

    public final ValueSetting delayMs = new ValueSetting(
            "Delay (ms)",
            "Delay before the message is sent after a kill is detected"
    ).range(0, 3000).step(50).setValue(500);

    /**
     * Entities the local player has hit recently. Reference-keyed: a
     * given PlayerEntity instance identifies one spawned-in player and
     * survives the brief window between hit and death. Cleared every
     * tick of records older than {@link #ATTACK_TIMEOUT_MS}.
     */
    private final Map<PlayerEntity, Long> recentlyAttacked = new ConcurrentHashMap<>();

    /**
     * Per-player health history. Drives all three detection paths above.
     * Keyed by the same PlayerEntity reference for parity with
     * {@link #recentlyAttacked}.
     */
    private final Map<PlayerEntity, EntityHealthData> trackedEntities = new ConcurrentHashMap<>();

    private long pendingSendAt = 0L;
    private boolean hasPending = false;

    private AutoGG() {
        super("auto_gg", "Auto GG", ModuleCategory.OTHER);
        message.setFullWidth(true);
        delayMs.setFullWidth(true);
        setup(generalSection, message, delayMs);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public static AutoGG getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Sends 'gg' or 'GG' in chat automatically after killing another player";
    }

    @Override
    public String getIcon() {
        return "auto_gg.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public void deactivate() {
        recentlyAttacked.clear();
        trackedEntities.clear();
        hasPending = false;
        pendingSendAt = 0L;
    }

    /**
     * Stamp the given victim as recently attacked by the local player.
     * Called from
     * {@link vorga.phazeclient.mixins.ClientPlayerInteractionManagerMixin#phaze$recordReach}
     * for every successful entity-attack the client initiates against
     * a non-self {@link PlayerEntity}.
     */
    public void recordAttack(PlayerEntity target) {
        if (!isEnabled() || target == null) {
            return;
        }
        recentlyAttacked.put(target, System.currentTimeMillis());
    }

    private void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();

        // Drain any pending send first so the same tick that schedules a
        // GG can immediately fire the previous queued one (if its delay
        // window happens to elapse on the same tick boundary).
        if (hasPending) {
            if (!isEnabled()) {
                hasPending = false;
                pendingSendAt = 0L;
            } else if (mc != null && mc.player != null && mc.getNetworkHandler() != null
                    && now >= pendingSendAt) {
                String phrase = message.getSelected();
                if (phrase == null || phrase.isEmpty()) {
                    phrase = "GG";
                }
                mc.getNetworkHandler().sendChatMessage(phrase);
                hasPending = false;
                pendingSendAt = 0L;
            }
        }

        if (!isEnabled() || mc == null || mc.player == null || mc.world == null) {
            return;
        }

        // Drop stale attack records so a target who escaped doesn't get
        // their later death (by someone else) credited to us.
        recentlyAttacked.entrySet().removeIf(entry -> now - entry.getValue() > ATTACK_TIMEOUT_MS);

        // Live transition path: snapshot health for every visible
        // player and look for an alive->dead flip we caused.
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || mc.player.equals(player)) {
                continue;
            }

            float currentHealth = player.getHealth();
            EntityHealthData data = trackedEntities.computeIfAbsent(player,
                    k -> new EntityHealthData(currentHealth));

            boolean weAttacked = recentlyAttacked.containsKey(player);
            boolean killTransition = (data.wasAlive && !player.isAlive())
                    || (data.previousHealth > 0.0F && currentHealth <= 0.0F);

            if (killTransition && weAttacked) {
                onKillDetected(player);
                // onKillDetected drops the entry from trackedEntities;
                // skip the snapshot update so a stale tick doesn't
                // re-create it.
                continue;
            }

            data.previousHealth = currentHealth;
            data.wasAlive = player.isAlive();
        }

        // Despawn path: any tracked player whose entity is gone or no
        // longer alive counts as a kill if we attacked them within the
        // 5-second window. No HP-threshold gate - servers that despawn
        // the dead body instantly never send a zero-health update, so
        // requiring previousHealth<=2.0 (the previous behavior) silently
        // dropped those kills. The attack window is short enough that
        // out-of-render-distance despawn is essentially impossible in
        // active PvP.
        Iterator<Map.Entry<PlayerEntity, EntityHealthData>> iter = trackedEntities.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<PlayerEntity, EntityHealthData> entry = iter.next();
            PlayerEntity ent = entry.getKey();
            if (ent.isRemoved() || !ent.isAlive()) {
                if (recentlyAttacked.containsKey(ent)) {
                    onKillDetected(ent);
                }
                iter.remove();
            }
        }
    }

    private void onKillDetected(PlayerEntity victim) {
        recentlyAttacked.remove(victim);
        trackedEntities.remove(victim);
        if (!hasPending) {
            pendingSendAt = System.currentTimeMillis() + Math.max(0L, (long) delayMs.getValue());
            hasPending = true;
        }
    }

    /**
     * Per-player health history snapshot. Drives the live alive->dead
     * transition check in {@link #tick}.
     */
    private static class EntityHealthData {
        float previousHealth;
        boolean wasAlive;

        EntityHealthData(float currentHealth) {
            this.previousHealth = currentHealth;
            this.wasAlive = true;
        }
    }
}
