package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hard guard against leaked render state from custom blur/overlay passes.
 * Ensures every item icon render starts from vanilla-safe GL state so GUI
 * item sprites cannot appear dark/tinted.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextItemRenderStateMixin {

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"))
    private void phaze$resetStateBeforeDrawItem(ItemStack stack, int x, int y, CallbackInfo ci) {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

