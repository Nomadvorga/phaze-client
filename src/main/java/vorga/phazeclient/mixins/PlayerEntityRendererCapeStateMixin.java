package vorga.phazeclient.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsState;

/**
 * Installs the selected Phaze cape into the vanilla player render state.
 * Doing this at state creation makes every later cape render path see the
 * custom texture, including shader/modded feature pipelines.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererCapeStateMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void phaze$installCapeState(
            AbstractClientPlayerEntity player,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        String selection = CosmeticsRenderer.capeSelectionFor(player);
        if (!state.capeVisible
                || !CosmeticsState.isCape(selection)
                || state.skinTextures == null) return;
        Identifier texture = CosmeticsRenderer.capeTexture(selection);
        if (texture == null) return;

        SkinTextures original = state.skinTextures;
        state.skinTextures = new SkinTextures(
                original.texture(),
                original.textureUrl(),
                texture,
                original.elytraTexture(),
                original.model(),
                original.secure()
        );
    }
}
