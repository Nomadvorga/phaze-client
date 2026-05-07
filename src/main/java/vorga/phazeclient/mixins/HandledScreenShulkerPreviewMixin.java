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
 * After vanilla finishes drawing the slot tooltip, ask the Shulker Preview
 * module whether it wants to render its 9x3 contents grid for the currently
 * focused shulker box stack.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenShulkerPreviewMixin {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

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
