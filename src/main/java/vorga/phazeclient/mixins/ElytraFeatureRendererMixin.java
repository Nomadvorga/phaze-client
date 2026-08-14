package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
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
import vorga.phazeclient.implement.cosmetics.PreviewMarker;

/** Hides the elytra mesh when it would clip through a back cosmetic. */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$hideElytraBehindCosmetic(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
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
            boolean preview = playerState instanceof PreviewMarker marker
                    && marker.phaze$previewSelection() != null;
            if (cosmetics.isEquipped() || preview || CosmeticsRenderer.isRenderingPreview()) ci.cancel();
            return;
        }
        Entity entity = client.world.getEntityById(playerState.id);
        if (entity instanceof PlayerEntity player
                && CosmeticsSyncService.getInstance().selectionFor(player.getUuid()) != null) {
            ci.cancel();
        }
    }
}
