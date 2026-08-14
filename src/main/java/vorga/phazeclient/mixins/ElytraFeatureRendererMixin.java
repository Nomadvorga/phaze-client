package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsState;
import vorga.phazeclient.implement.cosmetics.CosmeticsSyncService;

/**
 * Prevents the vanilla elytra mesh from clipping through an equipped
 * client-side back cosmetic. The item and its gameplay remain untouched.
 */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$hideElytraBehindCosmetic(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            BipedEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        CosmeticsState cosmetics = CosmeticsState.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(state instanceof PlayerEntityRenderState playerState)
                || client.player == null || client.world == null
                || !cosmetics.isHideElytra()) return;
        if (playerState.id == client.player.getId()) {
            if (cosmetics.isEquipped() || CosmeticsRenderer.isRenderingPreview()) {
                ci.cancel();
            }
            return;
        }
        Entity entity = client.world.getEntityById(playerState.id);
        if (entity instanceof PlayerEntity player
                && CosmeticsSyncService.getInstance()
                .selectionFor(player.getUuid()) != null) {
            ci.cancel();
        }
    }
}
