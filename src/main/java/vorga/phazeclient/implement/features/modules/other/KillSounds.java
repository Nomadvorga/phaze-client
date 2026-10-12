package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class KillSounds extends Module {
    private static final KillSounds INSTANCE = new KillSounds();
    private static boolean registered = false;

    public final SelectSetting soundType = new SelectSetting("Sound", "Kill sound effect")
            .value("Laser", "Ding", "Destroy", "Hit", "Pop", "Crunch", "Pling", "Bell", "Orb XP", "Level Up", "Anvil")
            .selected("Laser");
    public final ValueSetting volume = new ValueSetting("Volume", "Sound volume").range(10, 100).step(5).setValue(85);
    public final ValueSetting pitch = new ValueSetting("Pitch", "Sound pitch").range(0.5f, 2.0f).step(0.1f).setValue(1.0f);
    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", "Only play when eliminating players").setValue(true);

    private LivingEntity targetEntity = null;
    private long lastAttackTime = 0;

    private KillSounds() {
        super("kill_sounds", "Kill Sounds", ModuleCategory.OTHER);
        setup(soundType, volume, pitch, playersOnly);
        registerEvents();
    }

    public static KillSounds getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Plays crisp open-source and retro sound effects on eliminating opponents";
    }

    @Override
    public String getIcon() {
        return "chat_helper.png";
    }

    private synchronized void registerEvents() {
        if (registered) return;
        registered = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && isEnabled() && entity instanceof LivingEntity living) {
                targetEntity = living;
                lastAttackTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled() || targetEntity == null) return;

            long now = System.currentTimeMillis();
            if (now - lastAttackTime > 2500) {
                targetEntity = null;
                return;
            }

            if (targetEntity.isDead() || targetEntity.getHealth() <= 0.0f) {
                if (!playersOnly.isValue() || targetEntity instanceof PlayerEntity) {
                    playKillSound(client);
                }
                targetEntity = null;
            }
        });
    }

    private void playKillSound(MinecraftClient client) {
        SoundEvent sound;
        switch (soundType.getSelected()) {
            case "Laser" -> sound = PhazeSounds.KILL_LASER;
            case "Ding" -> sound = PhazeSounds.KILL_DING;
            case "Destroy" -> sound = PhazeSounds.KILL_DESTROY;
            case "Hit" -> sound = PhazeSounds.KILL_HIT;
            case "Pop" -> sound = PhazeSounds.KILL_POP;
            case "Crunch" -> sound = PhazeSounds.KILL_CRUNCH;
            case "Bell" -> sound = SoundEvents.BLOCK_BELL_USE;
            case "Orb XP" -> sound = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "Level Up" -> sound = SoundEvents.ENTITY_PLAYER_LEVELUP;
            case "Anvil" -> sound = SoundEvents.BLOCK_ANVIL_LAND;
            default -> sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        }

        float vol = volume.getValue() / 100.0f;
        float p = pitch.getValue();
        client.getSoundManager().play(PositionedSoundInstance.master(sound, p, vol));
    }
}
