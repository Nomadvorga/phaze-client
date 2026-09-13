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

public final class AutoGG extends Module {
    private static final AutoGG INSTANCE = new AutoGG();

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

    private final Map<PlayerEntity, Long> recentlyAttacked = new ConcurrentHashMap<>();

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
        return 28.35F;
    }

    @Override
    public float getIconOffsetY() {
        return -2.0F;
    }

    @Override
    public void deactivate() {
        recentlyAttacked.clear();
        trackedEntities.clear();
        hasPending = false;
        pendingSendAt = 0L;
    }

    public void recordAttack(PlayerEntity target) {
        if (!isEnabled() || target == null) {
            return;
        }
        recentlyAttacked.put(target, System.currentTimeMillis());
    }

    private void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();

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

        recentlyAttacked.entrySet().removeIf(entry -> now - entry.getValue() > ATTACK_TIMEOUT_MS);

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

                continue;
            }

            data.previousHealth = currentHealth;
            data.wasAlive = player.isAlive();
        }

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

    private static class EntityHealthData {
        float previousHealth;
        boolean wasAlive;

        EntityHealthData(float currentHealth) {
            this.previousHealth = currentHealth;
            this.wasAlive = true;
        }
    }
}
