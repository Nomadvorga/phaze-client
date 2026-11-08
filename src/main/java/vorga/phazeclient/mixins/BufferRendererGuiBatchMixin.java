package vorga.phazeclient.mixins;

import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;

/**
 * Deferred menu rectangles and MSDF quads were submitted before any foreign draw (vanilla
 * widgets, items, entities), so they must reach the GPU before it.
 */
@Mixin(BufferRenderer.class)
public abstract class BufferRendererGuiBatchMixin {
    @Inject(method = "drawWithGlobalProgram", at = @At("HEAD"))
    private static void phaze$flushBeforeGlobalDraw(BuiltBuffer buffer, CallbackInfo ci) {
        GuiDrawStats.bufferDraws++;
        BatchedRectangle.flushForForeignDraw();
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private static void phaze$flushBeforeDraw(BuiltBuffer buffer, CallbackInfo ci) {
        GuiDrawStats.bufferDraws++;
        BatchedRectangle.flushForForeignDraw();
    }
}
