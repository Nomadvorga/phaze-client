package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.api.system.colorcorrection.WorldColorRenderHelper;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherWorldColorMixin {

    @Inject(
            method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At("HEAD")
    )
    private <E extends Entity> void phaze$pushEntityWorldColor(
            E entity,
            double x,
            double y,
            double z,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            EntityRenderer<? super E, ?> renderer,
            CallbackInfo ci
    ) {
        WorldColorCorrectionController.Target target =
                entity instanceof PlayerEntity
                        ? WorldColorCorrectionController.Target.PLAYERS
                        : WorldColorCorrectionController.Target.ENTITIES;
        WorldColorCorrectionController.push(target);
        WorldColorRenderHelper.beginEntityShaderAlpha(target);
    }

    @Inject(
            method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At("RETURN")
    )
    private <E extends Entity> void phaze$popEntityWorldColor(
            E entity,
            double x,
            double y,
            double z,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            EntityRenderer<? super E, ?> renderer,
            CallbackInfo ci
    ) {
        WorldColorRenderHelper.endEntityShaderAlpha();
        WorldColorCorrectionController.pop();
    }
}
