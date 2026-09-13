package vorga.phazeclient.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.NoRender;

@Mixin(net.minecraft.client.render.WeatherRendering.class)
public class WeatherRenderingNoRenderMixin {

    @Inject(method = "renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelRain(net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
                                   net.minecraft.util.math.Vec3d cameraPos,
                                   net.minecraft.client.render.state.WeatherRenderState weatherState,
                                   CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && phaze$rainHidden(mod)) {
            ci.cancel();
        }
    }

    @Inject(method = "buildPrecipitationPieces(Lnet/minecraft/world/World;IFLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelRainStateBuild(net.minecraft.world.World world,
                                             int precision,
                                             float tickDelta,
                                             net.minecraft.util.math.Vec3d cameraPos,
                                             net.minecraft.client.render.state.WeatherRenderState weatherState,
                                             CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && phaze$rainHidden(mod)) {
            ci.cancel();
        }
    }

    private static boolean phaze$rainHidden(NoRender mod) {
        return mod.particles.isValue() || mod.rain.isValue();
    }
}
