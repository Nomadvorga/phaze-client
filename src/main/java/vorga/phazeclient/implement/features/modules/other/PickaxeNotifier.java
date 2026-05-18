package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Warns the player when their currently held pickaxe drops below a
 * configurable durability threshold and (optionally) auto-swaps to
 * a pre-set hotbar slot to avoid breaking the tool mid-swing.
 *
 * <h3>How it triggers</h3>
 * On every {@code attackBlock} (forwarded from
 * {@link vorga.phazeclient.mixins.ClientPlayerInteractionManagerMixin})
 * we inspect the held stack:
 * <ol>
 *   <li>Stack must be a {@link PickaxeItem} (any tier - wooden up
 *       to netherite). Non-pickaxes silently reset the warning
 *       cooldown so the next pickaxe pickup gets a fresh warning.</li>
 *   <li>Remaining durability ({@code maxDamage - damage}) must be
 *       below the user's threshold.</li>
 * </ol>
 * When both pass we fire one chat warning per "stack identity" so
 * the user isn't spammed every left-click; the
 * {@link #warnedSignature} latch keys off both the selected slot
 * and the stack's identity hash so swapping back and forth correctly
 * re-warns when the stack actually changes.
 *
 * <h3>Auto-swap path</h3>
 * When {@link #autoSwitch} is on we point the player's selected
 * hotbar slot at {@link #swapSlot} (1-based, 1..9 mirroring the
 * vanilla number-key labels) and send an
 * {@link UpdateSelectedSlotC2SPacket} so the server agrees on the
 * swap. If the player is already on that slot we skip both the
 * client-side flip and the network round-trip.
 *
 * <h3>Logic credits</h3>
 * Adapted from
 * {@code winvi.moscow.soupbetter.modules.PickaxeNotificationsModule};
 * the Phaze port replaces the upstream config plumbing with module
 * settings and routes the chat output through the local chat hud
 * instead of the upstream's {@code QuickLogger}.
 */
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
        swapSlot.setFullWidth(true);
        setup(generalSection, durabilityThreshold, autoSwitch, swapSlot);
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

    /**
     * Hook from {@code ClientPlayerInteractionManagerMixin}'s
     * attack-block path. Decides whether to warn / swap and emits
     * the chat line at most once per matching pickaxe stack.
     */
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

        String currentSignature = mc.player.getInventory().selectedSlot + ":" + System.identityHashCode(stack);
        if (currentSignature.equals(warnedSignature)) {
            return;
        }
        warnedSignature = currentSignature;

        boolean switched = autoSwitch.isValue() && switchToConfiguredSlot(mc);
        sendLowDurabilityMessage(mc, remaining, switched);
    }

    private boolean isTrackedPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isDamageable() && stack.getItem() instanceof PickaxeItem;
    }

    private boolean switchToConfiguredSlot(MinecraftClient mc) {
        int targetSlot = clampHotbarSlot(swapSlot.getInt()) - 1;
        if (mc.player.getInventory().selectedSlot == targetSlot) {
            return false;
        }
        mc.player.getInventory().selectedSlot = targetSlot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
        }
        return true;
    }

    private void sendLowDurabilityMessage(MinecraftClient mc, int remaining, boolean switched) {
        if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            return;
        }
        MutableText line = Text.literal("[Phaze] ").formatted(Formatting.GOLD)
                .append(Text.literal("Pickaxe durability low ").formatted(Formatting.RED))
                .append(Text.literal("(" + remaining + " left)").formatted(Formatting.GOLD));
        if (switched) {
            line.append(Text.literal(" -> slot " + clampHotbarSlot(swapSlot.getInt())).formatted(Formatting.GRAY));
        }
        mc.inGameHud.getChatHud().addMessage(line);
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
