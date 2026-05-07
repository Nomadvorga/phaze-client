package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.EnumMap;
import java.util.Map;

public final class ArmorNotifier extends Module {
    private static final ArmorNotifier INSTANCE = new ArmorNotifier();
    private static final long ALERT_COOLDOWN_MS = 4000L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting threshold = new ValueSetting(
            "Threshold (%)",
            "Play an alert sound when any armor piece drops below this durability percentage"
    ).range(1, 50).setValue(5);

    private final Map<EquipmentSlot, Boolean> wasBelow = new EnumMap<>(EquipmentSlot.class);
    private long lastAlertMs = 0L;

    private ArmorNotifier() {
        super("armor_notifier", "Armor Notifier", ModuleCategory.UTILITIES);
        threshold.setFullWidth(true);
        setup(generalSection, threshold);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public static ArmorNotifier getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Plays a sound when any armor piece drops below a durability threshold";
    }

    @Override
    public void deactivate() {
        wasBelow.clear();
    }

    private void tick(MinecraftClient mc) {
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }

        float pct = threshold.getValue() / 100.0F;
        boolean newlyBelow = false;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // Only check actual armor slots; skip MAINHAND/OFFHAND/etc.
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = mc.player.getEquippedStack(slot);
            boolean below = isBelowThreshold(stack, pct);
            boolean previouslyBelow = wasBelow.getOrDefault(slot, false);
            if (below && !previouslyBelow) {
                newlyBelow = true;
            }
            wasBelow.put(slot, below);
        }

        if (newlyBelow) {
            long now = System.currentTimeMillis();
            if (now - lastAlertMs >= ALERT_COOLDOWN_MS) {
                playPing(mc);
                lastAlertMs = now;
            }
        }
    }

    /**
     * True iff the stack is a damageable armor item whose remaining
     * durability fraction is strictly below {@code thresholdFraction}. Empty
     * or unbreakable items return false so they never trigger an alert.
     */
    private boolean isBelowThreshold(ItemStack stack, float thresholdFraction) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        int remaining = max - stack.getDamage();
        float fraction = (float) remaining / (float) max;
        return fraction <= thresholdFraction;
    }

    private void playPing(MinecraftClient mc) {
        SoundManager soundManager = mc.getSoundManager();
        if (soundManager == null) {
            return;
        }
        SoundEvent sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        soundManager.play(PositionedSoundInstance.master(sound, 1.6F, 0.9F));
    }
}
