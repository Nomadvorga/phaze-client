package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.NoRender;

@Mixin(ClientWorld.class)
public class ClientWorldBreakParticlesMixin {

    @Inject(method = "addBlockBreakParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelBreakShards(BlockPos pos, BlockState state, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled()
                && (mod.breakBlockParticles.isValue() || mod.particles.isValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnBlockBreakingParticle(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelDigShards(BlockPos pos, Direction direction, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled()
                && (mod.breakBlockParticles.isValue() || mod.particles.isValue())) {
            ci.cancel();
        }
    }
}
