package vorga.phazeclient.mixins.sodiumextra;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.FakeFps;

@Mixin(targets = "me.flashyreese.mods.sodiumextra.client.FrameCounter")
public class SodiumExtraFrameCounterMixin {

    @Unique
    private static int phaze$fakeFpsOrMinusOne() {
        FakeFps module = FakeFps.getInstance();
        if (module == null || !module.isEnabled()) {
            return -1;
        }
        return module.getFakeFps();
    }

    @Inject(method = "getSmoothFps()I", at = @At("HEAD"), cancellable = true)
    private void phaze$fakeSmoothFps(CallbackInfoReturnable<Integer> cir) {
        int fake = phaze$fakeFpsOrMinusOne();
        if (fake >= 0) {
            cir.setReturnValue(fake);
        }
    }

    @Inject(method = "getAverageFps()I", at = @At("HEAD"), cancellable = true)
    private void phaze$fakeAverageFps(CallbackInfoReturnable<Integer> cir) {
        int fake = phaze$fakeFpsOrMinusOne();
        if (fake >= 0) {
            cir.setReturnValue(fake);
        }
    }

    @Inject(method = "getOnePercentLowFps()I", at = @At("HEAD"), cancellable = true)
    private void phaze$fakeOnePercentLow(CallbackInfoReturnable<Integer> cir) {
        int fake = phaze$fakeFpsOrMinusOne();
        if (fake >= 0) {
            cir.setReturnValue(fake);
        }
    }

    @Inject(method = "getPointOnePercentLowFps()I", at = @At("HEAD"), cancellable = true)
    private void phaze$fakePointOnePercentLow(CallbackInfoReturnable<Integer> cir) {
        int fake = phaze$fakeFpsOrMinusOne();
        if (fake >= 0) {
            cir.setReturnValue(fake);
        }
    }
}
