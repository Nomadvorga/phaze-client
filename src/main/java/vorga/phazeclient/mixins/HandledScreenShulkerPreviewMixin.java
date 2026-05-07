package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ShulkerPreview;

/**
 * Two-part shulker preview hook:
 *   1. {@code drawMouseoverTooltip} HEAD - if the focused slot has a shulker
 *      and the preview is active, cancel the call so vanilla never draws its
 *      multi-line "Container / Bottle o' Enchanting (64x) / and 22 more..."
 *      list. We supply our own grid instead.
 *   2. {@code render} TAIL - draw our 9x3 grid after vanilla has finished
 *      every other layer (slots, dragged item, foreground), at high Z so it
 *      can't be overlapped by anything that batched at lower Z.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenShulkerPreviewMixin {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

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
        // Vanilla tooltip is suppressed entirely; the @Inject below renders
        // the contents grid in its place.
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
        module.renderPreview(context, mouseX, mouseY, container);
    }
}
