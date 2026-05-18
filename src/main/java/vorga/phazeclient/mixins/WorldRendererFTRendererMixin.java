package vorga.phazeclient.mixins;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.FTHelperRenderer;
import vorga.phazeclient.implement.features.modules.other.PredictionsRenderer;

import java.util.List;

/**
 * World-render entrypoint for FT Helper, Snowball Tracker, and
 * Predictions. Hooks the TAIL of {@link WorldRenderer#renderEntities}
 * - same hook point used by {@link WorldRendererHitRangeMixin} - so
 * the matrix stack is rooted at the camera and every entity has
 * already had its position lerped for the partial-tick frame.
 *
 * <h3>Why one mixin, three modules</h3>
 * All three features paint into the same world-render pass, share
 * the same camera offset, and have no per-pixel ordering
 * requirements between each other. Bundling them keeps the mixin
 * count in {@code phaze.mixins.json} small and avoids three
 * back-to-back {@code renderEntities} TAIL injects when one is
 * enough.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererFTRendererMixin {

    @Inject(method = "renderEntities", at = @At(value = "TAIL"))
    private void phaze$drawFTLayers(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            Camera camera,
            RenderTickCounter tickCounter,
            List<Entity> entities,
            CallbackInfo ci
    ) {
        // Force-flush the entity vertex batch before we draw on top.
        // {@code WorldRenderer.renderEntities} buffers every entity's
        // geometry into the VertexConsumerProvider.Immediate but
        // doesn't necessarily call draw() before TAIL fires - some
        // layers stay queued and get flushed by the GameRenderer
        // post-pass. Without this manual draw, our trajectory lines
        // and impact sphere submit into GL ahead of the still-pending
        // entity layers, so when those layers finally rasterise they
        // land on TOP of our marker - exactly the "entity covers the
        // sphere" symptom the user reported. Calling immediate.draw()
        // here writes the entity layers out NOW, so anything we
        // submit afterwards is guaranteed to paint over them in the
        // GL pipeline.
        immediate.draw();

        Vec3d cameraPos = camera.getPos();
        // FT helper visualisers (trapka cylinder, dezo circle, etc.).
        FTHelperRenderer.renderHighlight(matrices, cameraPos, tickCounter);
        // Snowball-tracker per-entity feet circles.
        FTHelperRenderer.renderSnowballs(matrices, cameraPos);
        // Held-hand and own-projectile trajectory polylines.
        PredictionsRenderer.render(matrices, cameraPos, tickCounter);
    }
}
