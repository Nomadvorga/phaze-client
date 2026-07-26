package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;
import vorga.phazeclient.api.system.nametag.NametagBlurStorage;
import vorga.phazeclient.api.system.render.Render3DUtil;
import vorga.phazeclient.implement.features.modules.other.HitboxCustomizer;
import vorga.phazeclient.util.render.PhazeRenderLayers;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void startNametagBatching(CallbackInfo ci) {
        // Start batching at the beginning of entity rendering
        NametagBlurStorage.setEnabled(true);
        NametagBlurStorage.clear();
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitbox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;FFFF)V"
            )
    )
    private <E extends Entity> void phaze$redirectHitboxRender(
            MatrixStack matrices,
            VertexConsumer vertices,
            Entity entity,
            float tickDelta,
            float red,
            float green,
            float blue,
            E renderedEntity,
            double x,
            double y,
            double z,
            float renderTickDelta,
            MatrixStack renderMatrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            EntityRenderer<? super E, ?> renderer
    ) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (!module.isEnabled()) {
            phaze$invokeVanillaRenderHitbox(matrices, vertices, entity, tickDelta, red, green, blue);
            return;
        }

        VertexConsumerProvider.Immediate immediate = phaze$getImmediate(vertexConsumers);

        int colorInt = module.getHitboxColor();
        if (module.redInReach.isValue() && phaze$isCrosshairTarget(entity)) {
            colorInt = module.reachColor.getColor();
        }

        float a = ((colorInt >>> 24) & 0xFF) / 255.0f;
        float r = ((colorInt >>> 16) & 0xFF) / 255.0f;
        float g = ((colorInt >>> 8) & 0xFF) / 255.0f;
        float b = (colorInt & 0xFF) / 255.0f;

        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

        if (immediate != null) {
            immediate.draw();
        }

        float thickness = Math.max(1.0f, module.outlineThickness.getValue());
        if (module.isFillEnabled()) {
            RenderLayer fillLayer = PhazeRenderLayers.getHitboxFill();
            VertexConsumer fillConsumer = vertexConsumers.getBuffer(fillLayer);
            Render3DUtil.vertexBoxFill(matrices, fillConsumer, box, colorInt, module.getFillOpacity());
            if (immediate != null) {
                immediate.draw(fillLayer);
            }
        }

        RenderLayer lineLayer = thickness > 1.0f
                ? PhazeRenderLayers.getThickLines(thickness)
                : RenderLayer.getLines();
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(lineLayer);
        VertexRendering.drawBox(matrices, lineConsumer, box, r, g, b, a);

        if (module.showLookLine.isValue()) {
            VertexRendering.drawVector(
                    matrices,
                    lineConsumer,
                    new Vector3f(0.0f, entity.getStandingEyeHeight(), 0.0f),
                    entity.getRotationVec(tickDelta).multiply(2.0),
                    -16776961
            );
        }
        if (immediate != null) {
            immediate.draw(lineLayer);
        }
    }

    private static VertexConsumerProvider.Immediate phaze$getImmediate(VertexConsumerProvider provider) {
        if (provider instanceof VertexConsumerProvider.Immediate immediate) {
            return immediate;
        }
        if (provider instanceof OutlineVertexConsumerProvider outline) {
            return ((OutlineVertexConsumerProviderAccessor) outline).phaze$getParent();
        }
        return null;
    }

    private static boolean phaze$isCrosshairTarget(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;
        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            return ehr.getEntity() == entity;
        }
        return false;
    }

    @Invoker("renderHitbox")
    private static void phaze$invokeVanillaRenderHitbox(
            MatrixStack matrices,
            VertexConsumer vertices,
            Entity entity,
            float tickDelta,
            float red,
            float green,
            float blue
    ) {
        throw new AssertionError();
    }
}
