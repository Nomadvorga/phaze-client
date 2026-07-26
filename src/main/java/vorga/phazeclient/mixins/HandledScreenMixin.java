package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.base.util.ServerUtil;
import vorga.phazeclient.implement.features.modules.other.HealingHelper;
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;
import vorga.phazeclient.implement.features.modules.other.ItemScroller;
import vorga.phazeclient.implement.features.modules.other.MaceIndicator;
import vorga.phazeclient.implement.features.modules.other.ShulkerPreview;

import java.util.HashSet;
import java.util.Set;

/**
 * Consolidated mixin for {@link HandledScreen}, merging the previous
 * sibling mixins (ItemScroller, ShulkerPreview, MaceIndicator,
 * ItemHighlighter, HealingHelper).
 * Each original injector is preserved with a unique {@code phaze$}
 * method name; shadow fields and unique state are merged at the top.
 *
 * <p>The trailing-36-slots iteration is shared between three modules
 * (HealingHelper, ItemHighlighter, MaceIndicator) so we extract a
 * helper {@link #phaze$paintInventoryFills} that all three call into
 * with their own per-stack colour function.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    // ---------------------------------------------------------------
    // Shared shadows
    // ---------------------------------------------------------------

    @Shadow protected int x;
    @Shadow protected int y;

    @Shadow @Nullable protected Slot focusedSlot;

    @Shadow @Nullable protected abstract Slot getSlotAt(double x, double y);

    @Shadow protected abstract void onMouseClick(@Nullable Slot slot, int slotId, int button, SlotActionType actionType);

    @Unique
    private static void phaze$resetGuiRenderState() {
    }

    // ---------------------------------------------------------------
    // ItemScroller unique state
    // ---------------------------------------------------------------

    @Unique private final Set<Integer> phaze$transferredSlots = new HashSet<>();
    @Unique private boolean phaze$shiftDragActive = false;
    @Unique private long phaze$lastTransferAt = 0L;
    // ---------------------------------------------------------------
    // ItemScroller: shift-drag through inventory slots
    // ---------------------------------------------------------------

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void phaze$onMouseClickedHead(double mouseX, double mouseY, int button,
                                          CallbackInfoReturnable<Boolean> cir) {
        ItemScroller mod = ItemScroller.getInstance();
        if (mod == null || !mod.isEnabled() || !ServerUtil.isItemScrollerSupported()) {
            phaze$shiftDragActive = false;
            return;
        }
        if ((Object) this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) {
            phaze$shiftDragActive = false;
            phaze$transferredSlots.clear();
            return;
        }
        if (button != 0 || !Screen.hasShiftDown()) {
            return;
        }

        phaze$shiftDragActive = true;
        phaze$transferredSlots.clear();
        // Let the very first drag frame transfer the slot under the
        // cursor immediately. Pre-marking it here makes short drags in
        // creative inventory miss every slot before the debounce elapses.
        phaze$lastTransferAt = 0L;
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void phaze$onMouseReleasedHead(double mouseX, double mouseY, int button,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) {
            phaze$shiftDragActive = false;
            phaze$transferredSlots.clear();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void phaze$onMouseDraggedHead(double mouseX, double mouseY, int button,
                                          double deltaX, double deltaY,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!phaze$shiftDragActive) {
            phaze$shiftDragActive = true;
            phaze$transferredSlots.clear();
            phaze$lastTransferAt = 0L;
        }

        double startX = mouseX - deltaX;
        double startY = mouseY - deltaY;
        double dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        int steps = Math.max(1, (int) Math.ceil(dist / 4.0));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double sampleX = startX + deltaX * t;
            double sampleY = startY + deltaY * t;
            if (phaze$tryShiftDragTransfer(sampleX, sampleY)) {
                return;
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$processShiftDragWhileHovering(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        phaze$tryShiftDragTransfer(mouseX, mouseY);
    }

    // ---------------------------------------------------------------
    // ShulkerPreview: replace vanilla tooltip with a 9x3 grid
    // ---------------------------------------------------------------

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressShulkerVanillaTooltip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerPreview module = ShulkerPreview.getInstance();
        if (module == null || !module.isEnabled() || !module.shouldShow()) {
            return;
        }
        if (focusedSlot == null) {
            return;
        }
        ItemStack stack = focusedSlot.getStack();
        ContainerComponent container = module.extractContainer(stack);
        if (container == null) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawShulkerPreview(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ShulkerPreview module = ShulkerPreview.getInstance();
        if (module == null || !module.isEnabled() || !module.shouldShow()) {
            return;
        }
        if (focusedSlot == null) {
            return;
        }
        ItemStack stack = focusedSlot.getStack();
        ContainerComponent container = module.extractContainer(stack);
        if (container == null) {
            return;
        }
        module.renderPreview(context, mouseX, mouseY, container, stack);
    }

    // ---------------------------------------------------------------
    // HealingHelper / ItemHighlighter / MaceIndicator: trailing 36 fills
    // ---------------------------------------------------------------

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$resetStateAtRenderHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        phaze$resetGuiRenderState();
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void phaze$resetStateBeforeSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        phaze$resetGuiRenderState();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$drawUtilityHighlights(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HealingHelper healing = HealingHelper.getInstance();
        ItemHighlighter highlighter = ItemHighlighter.getInstance();
        MaceIndicator mace = MaceIndicator.getInstance();
        boolean healingEnabled = healing != null && healing.isEnabled();
        boolean highlighterEnabled = highlighter != null && highlighter.isEnabled();
        boolean maceEnabled = mace != null && mace.isEnabled();
        if (!healingEnabled && !highlighterEnabled && !maceEnabled) {
            phaze$resetGuiRenderState();
            return;
        }
        if (healingEnabled) healing.beginRenderPass();
        if (highlighterEnabled) highlighter.beginRenderPass();
        phaze$paintInventoryUtilityFills(
                context,
                healingEnabled ? healing : null,
                highlighterEnabled ? highlighter : null,
                maceEnabled ? mace : null
        );
        phaze$resetGuiRenderState();
    }

    /**
     * Walks the local player's storage + hotbar slots and fills every
     * slot whose colour function returns a non-zero alpha. Falls back
     * to the legacy trailing-36 heuristic on handlers that don't
     * expose the expected player inventory wiring.
     */
    @Unique
    private void phaze$paintInventoryUtilityFills(
            DrawContext context,
            HealingHelper healing,
            ItemHighlighter highlighter,
            MaceIndicator mace
    ) {
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        ScreenHandler handler = self.getScreenHandler();
        if (handler == null) {
            return;
        }


        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory playerInventory = client != null && client.player != null ? client.player.getInventory() : null;
        boolean paintedPlayerSlots = false;
        if (playerInventory != null) {
            for (Slot slot : handler.slots) {
                if (!phaze$isMainPlayerSlot(slot, playerInventory)) {
                    continue;
                }
                phaze$paintUtilitySlot(context, slot, healing, highlighter, mace);
                paintedPlayerSlots = true;
            }
        }

        if (!paintedPlayerSlots) {
            int totalSlots = handler.slots.size();
            int playerInvStart = totalSlots - 36;
            if (playerInvStart >= 0) {
                for (int i = playerInvStart; i < totalSlots; i++) {
                    phaze$paintUtilitySlot(context, handler.slots.get(i), healing, highlighter, mace);
                }
            }
        }

        phaze$resetGuiRenderState();
    }

    @Unique
    private boolean phaze$isMainPlayerSlot(Slot slot, PlayerInventory playerInventory) {
        return slot != null
                && slot.inventory == playerInventory
                && slot.getIndex() >= 0
                && slot.getIndex() < 36;
    }

    @Unique
    private void phaze$paintUtilitySlot(
            DrawContext context,
            Slot slot,
            HealingHelper healing,
            ItemHighlighter highlighter,
            MaceIndicator mace
    ) {
        ItemStack stack = slot.getStack();
        int sx = x + slot.x;
        int sy = y + slot.y;
        if (healing != null) {
            phaze$fillUtilitySlot(context, sx, sy, healing.colorForPreparedStack(stack));
        }
        if (highlighter != null) {
            phaze$fillUtilitySlot(context, sx, sy, highlighter.colorForPreparedStack(stack));
        }
        if (mace != null) {
            phaze$fillUtilitySlot(context, sx, sy, mace.colorForStack(stack));
        }
    }

    @Unique
    private static void phaze$fillUtilitySlot(DrawContext context, int x, int y, int color) {
        if ((color & 0xFF000000) != 0) {
            context.fill(x, y, x + 16, y + 16, color);
        }
    }

    @Unique
    private boolean phaze$tryShiftDragTransfer(double mouseX, double mouseY) {
        ItemScroller mod = ItemScroller.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (mod == null || !mod.isEnabled() || !ServerUtil.isItemScrollerSupported() || client == null || client.getWindow() == null) {
            phaze$shiftDragActive = false;
            return false;
        }
        if ((Object) this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) {
            phaze$shiftDragActive = false;
            phaze$transferredSlots.clear();
            return false;
        }

        boolean leftPressed = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!phaze$shiftDragActive || !leftPressed || !Screen.hasShiftDown()) {
            if (!leftPressed) {
                phaze$shiftDragActive = false;
                phaze$transferredSlots.clear();
            }
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - phaze$lastTransferAt < mod.getDelayMs()) {
            return false;
        }

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) {
            return false;
        }

        int transferSlotId = slot.id;
        if (phaze$transferredSlots.contains(transferSlotId)) {
            return false;
        }

        phaze$transferredSlots.add(transferSlotId);
        if (!phaze$quickMoveSlot(slot)) {
            phaze$transferredSlots.remove(transferSlotId);
            return false;
        }

        phaze$lastTransferAt = now;
        return true;
    }

    @Unique
    private boolean phaze$quickMoveSlot(Slot slot) {
        if (slot == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
        return true;
    }
}
