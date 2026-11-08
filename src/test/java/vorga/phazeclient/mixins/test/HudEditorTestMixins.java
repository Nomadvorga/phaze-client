package vorga.phazeclient.mixins.test;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudCapture;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.implement.features.modules.hud.HudEditorProbe;
import vorga.phazeclient.implement.hud.PaperDollRenderer;

public final class HudEditorTestMixins {
    private HudEditorTestMixins() {}

    @Mixin(value = BatchedHudBuffer.class, remap = false)
    public abstract static class Batch {
        @Inject(method = "openCapture", at = @At("RETURN"))
        private void capture(CallbackInfoReturnable<HudCapture> cir) {
            if (cir.getReturnValue() != null) HudEditorProbe.capture(false);
        }
    }

    @Mixin(value = HudBuffer.class, remap = false)
    public abstract static class Child {
        @Inject(method = "openCapture", at = @At("RETURN"))
        private void capture(CallbackInfoReturnable<HudCapture> cir) {
            if (cir.getReturnValue() != null) HudEditorProbe.capture(true);
        }
    }

    @Mixin(DrawContext.class)
    public abstract static class Items {
        @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;II)V", at = @At("HEAD"))
        private void item(CallbackInfo ci) {
            if (HudEditorProbe.recording && HudBuffer.activeCaptureTarget >= 0) HudEditorProbe.cachedItems++;
        }
    }

    @Mixin(EntityRenderDispatcher.class)
    public abstract static class Models {
        @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
                at = @At("HEAD"))
        private void model(CallbackInfo ci) {
            if (PaperDollRenderer.isDrawing()) HudEditorProbe.model();
        }
    }

    @Mixin(value = Blur.class, remap = false)
    public abstract static class Masks {
        @Inject(method = {"renderCached", "renderCachedBatch"}, at = @At("HEAD"))
        private void mask(CallbackInfo ci) {
            if (HudEditorProbe.recording && HudBuffer.activeCaptureTarget >= 0) HudEditorProbe.blurMasks++;
        }
    }

    @Mixin(ChatHud.class)
    public abstract static class Chat {
        @Inject(method = "render", at = @At("HEAD"))
        private void chat(CallbackInfo ci) {
            if (!HudEditorProbe.recording) return;
            HudEditorProbe.chatRenders++;
            if (HudBuffer.activeCaptureTarget >= 0) HudEditorProbe.capturedChat++;
        }
    }
}
