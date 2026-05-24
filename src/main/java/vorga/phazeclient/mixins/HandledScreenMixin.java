package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.type.ContainerComponent;
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
        if (button != 0 || !Screen.hasShiftDown()) {
            return;
        }

        phaze$shiftDragActive = true;
        phaze$transferredSlots.clear();
        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot != null) {
            phaze$transferredSlots.add(slot.id);
        }
        phaze$lastTransferAt = System.currentTimeMillis();
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
        ItemScroller mod = ItemScroller.getInstance();
        if (mod == null || !mod.isEnabled() || !ServerUtil.isItemScrollerSupported()) {
            return;
        }
        if (button != 0 || !Screen.hasShiftDown()) {
            return;
        }

        if (!phaze$shiftDragActive) {
            phaze$shiftDragActive = true;
            phaze$transferredSlots.clear();
            phaze$lastTransferAt = 0L;
        }

        long now = System.currentTimeMillis();
        if (now - phaze$lastTransferAt < mod.getDelayMs()) {
            return;
        }

        double startX = mouseX - deltaX;
        double startY = mouseY - deltaY;
        double dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        int steps = Math.max(1, (int) Math.ceil(dist / 4.0));

        boolean transferred = false;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double sampleX = startX + deltaX * t;
            double sampleY = startY + deltaY * t;
            Slot slot = getSlotAt(sampleX, sampleY);
            if (slot == null || !slot.hasStack()) {
                continue;
            }
            if (phaze$transferredSlots.contains(slot.id)) {
                continue;
            }
            phaze$transferredSlots.add(slot.id);
            onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
            transferred = true;
        }

        if (transferred) {
            phaze$lastTransferAt = now;
        }
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

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$drawHealingHighlights(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HealingHelper module = HealingHelper.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        phaze$paintInventoryFills(context, module::colorForStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$drawHandledItemHighlights(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ItemHighlighter module = ItemHighlighter.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        phaze$paintInventoryFills(context, module::colorForStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$drawMaceIndicatorHighlights(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MaceIndicator module = MaceIndicator.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        phaze$paintInventoryFills(context, module::colorForStack);
    }

    /**
     * Walks the trailing 36 slots of the current handler (player
     * inventory + hotbar) and fills every slot whose colour function
     * returns a non-zero alpha.
     */
    @Unique
    private void phaze$paintInventoryFills(DrawContext context,
                                           java.util.function.ToIntFunction<ItemStack> colorFn) {
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        ScreenHandler handler = self.getScreenHandler();
        if (handler == null) {
            return;
        }

        int totalSlots = handler.slots.size();
        int playerInvStart = totalSlots - 36;
        if (playerInvStart < 0) {
            return;
        }

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            int color = colorFn.applyAsInt(stack);
            if ((color & 0xFF000000) == 0) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            context.fill(sx, sy, sx + 16, sy + 16, color);
        }

        context.draw();
    }
}
