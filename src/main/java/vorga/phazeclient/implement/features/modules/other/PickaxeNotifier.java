package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.PhazeAnnouncements;

public final class PickaxeNotifier extends Module {
    private static final PickaxeNotifier INSTANCE = new PickaxeNotifier();
    private static final int MIN_HOTBAR_SLOT = 1;
    private static final int MAX_HOTBAR_SLOT = 9;

    public final SectionSetting generalSection = new SectionSetting("General");

    public final ValueSetting durabilityThreshold = new ValueSetting(
            "Durability Threshold",
            "Warn when remaining durability drops below this many uses"
    ).range(1, 2031).step(1).setValue(50);

    public final BooleanSetting autoSwitch = new BooleanSetting(
            "Auto Switch",
            "Automatically switch to the configured hotbar slot when the pickaxe falls under the threshold"
    ).setValue(false);
    public final BooleanSetting playSound = new BooleanSetting(
            "Play Sound",
            "Play a warning sound together with the low durability message"
    ).setValue(true);

    public final ValueSetting swapSlot = new ValueSetting(
            "Swap Slot",
            "Hotbar slot (1-9) to switch to when Auto Switch fires"
    ).range(1, 9).step(1).setValue(2)
            .visible(() -> autoSwitch.isValue());

    private String warnedSignature;

    private PickaxeNotifier() {
        super("pickaxe_notifier", "Pickaxe Notifier", ModuleCategory.UTILITIES);
        durabilityThreshold.setFullWidth(true);
        autoSwitch.setFullWidth(true);
        playSound.setFullWidth(true);
        swapSlot.setFullWidth(true);
        setup(generalSection, durabilityThreshold, autoSwitch, playSound, swapSlot);
    }

    public static PickaxeNotifier getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Warns when the held pickaxe is low on durability and can auto-swap to a safe hotbar slot";
    }

    @Override
    public String getIcon() {
        return "pickaxe_notifier.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public void onAttackBlock() {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        ItemStack stack = mc.player.getMainHandStack();
        if (!isTrackedPickaxe(stack)) {
            warnedSignature = null;
            return;
        }
        int remaining = stack.getMaxDamage() - stack.getDamage();
        if (remaining >= durabilityThreshold.getInt()) {
            warnedSignature = null;
            return;
        }

        String currentSignature = mc.player.getInventory().getSelectedSlot() + ":" + System.identityHashCode(stack);
        if (currentSignature.equals(warnedSignature)) {
            return;
        }
        warnedSignature = currentSignature;

        boolean switched = autoSwitch.isValue() && switchToConfiguredSlot(mc);
        sendLowDurabilityMessage(mc, remaining, switched);
    }

    private boolean isTrackedPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isDamageable() && stack.isIn(ItemTags.PICKAXES);
    }

    private boolean switchToConfiguredSlot(MinecraftClient mc) {

        int targetSlot = clampHotbarSlot(swapSlot.getInt()) - 1;
        if (mc.player.getInventory().getSelectedSlot() == targetSlot) {
            return false;
        }
        mc.player.getInventory().setSelectedSlot(targetSlot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
        }
        return true;
    }

    private void sendLowDurabilityMessage(MinecraftClient mc, int remaining, boolean switched) {
        if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            return;
        }
        MutableText line = Text.literal("Pickaxe durability low ").formatted(Formatting.RED)
                .append(Text.literal("(" + remaining + " left)").formatted(Formatting.GOLD));
        if (switched) {
            line.append(Text.literal(" -> slot " + clampHotbarSlot(swapSlot.getInt())).formatted(Formatting.GRAY));
        }
        mc.inGameHud.getChatHud().addMessage(PhazeAnnouncements.systemMessage(line));
        if (playSound.isValue() && mc.getSoundManager() != null) {

            mc.getSoundManager().play(PositionedSoundInstance.ui(
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.25F, 1.0F
            ));
        }
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(MIN_HOTBAR_SLOT, Math.min(MAX_HOTBAR_SLOT, slot));
    }

    @Override
    public void deactivate() {
        super.deactivate();
        warnedSignature = null;
    }
}
