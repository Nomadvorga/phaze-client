package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.nametag.NametagBlurStorage;
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

    /**
     * Replace vanilla hitbox rendering with custom-colored box.
     * Inspired by HitboxPlus by PingIsFun (MIT).
     * Signature must match: renderHitbox(MatrixStack, VertexConsumer, Entity, float tickDelta, float red, float green, float blue)
     */
    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void phaze$replaceHitbox(MatrixStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green, float blue, CallbackInfo ci) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        int colorInt = module.getHitboxColor();
        if (module.redInReach.isValue() && phaze$isCrosshairTarget(entity)) {
            colorInt = module.reachColor.getColor();
        }

        float a = ((colorInt >>> 24) & 0xFF) / 255.0f;
        float r = ((colorInt >>> 16) & 0xFF) / 255.0f;
        float g = ((colorInt >>> 8) & 0xFF) / 255.0f;
        float b = (colorInt & 0xFF) / 255.0f;

        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

        float thickness = Math.max(1.0f, module.outlineThickness.getValue());
        VertexConsumer target = vertices;
        if (thickness > 1.0f) {
            VertexConsumerProvider provider = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            target = provider.getBuffer(PhazeRenderLayers.getThickLines(thickness));
        }
        VertexRendering.drawBox(matrices, target, box, r, g, b, a);

        if (module.showLookLine.isValue()) {
            VertexRendering.drawVector(
                    matrices,
                    target,
                    new Vector3f(0.0f, entity.getStandingEyeHeight(), 0.0f),
                    entity.getRotationVec(tickDelta).multiply(2.0),
                    -16776961
            );
        }

        ci.cancel();
    }

    private static boolean phaze$isCrosshairTarget(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;
        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            return ehr.getEntity() == entity;
        }
        return false;
    }
}
