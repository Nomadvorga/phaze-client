package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.FakeFps;

/**
 * Swaps the value returned by {@link MinecraftClient#getCurrentFps()}
 * AND the underlying {@code currentFps} field for the
 * {@link FakeFps}-rolled value when the module is enabled.
 *
 * <h3>Why both the accessor and the field</h3>
 * Most consumers in 1.21.4 read through {@code getCurrentFps()}
 * (vanilla F3 debug overlay, our FPS HUD, third-party mods that
 * use the public API). The HEAD-inject below covers all of those.
 *
 * <p>However, Sodium's in-game HUD and a handful of other
 * performance mods access the package-private {@code currentFps}
 * field directly via an accessor mixin / reflection, bypassing the
 * getter entirely. To make Fake FPS show up there too, we shadow
 * the field as {@link Mutable} and overwrite it from the head of
 * the same getter call. Because {@code getCurrentFps} is invoked
 * every frame by anyone who needs the value, this guarantees the
 * field is replaced with a fake reading at least once per frame -
 * sufficient for any consumer that re-reads each frame, and a
 * negligible no-op for any consumer that doesn't.
 *
 * <h3>Cancellable inject vs ModifyReturnValue</h3>
 * Plain {@code @Inject(cancellable=true)} keeps the dependency on
 * vanilla mixin only - we don't need MixinExtras here because the
 * accessor takes no arguments and the original return value is
 * irrelevant once we decide to override.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientFakeFpsMixin {

    @Shadow
    @Mutable
    private static int currentFps;

    @Inject(method = "getCurrentFps", at = @At("HEAD"), cancellable = true)
    private void phaze$fakeFps(CallbackInfoReturnable<Integer> cir) {
        FakeFps module = FakeFps.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        int fake = module.getFakeFps();
        // Mirror the fake value into the package-private field so
        // direct readers (Sodium HUD accessor, third-party perf
        // mods) see the same number we report through the getter.
        // Re-set every call rather than once per tick so consumers
        // that read multiple times per frame can never observe a
        // stale real value between vanilla's once-per-second update
        // and the next FakeFps sample window.
        currentFps = fake;
        cir.setReturnValue(fake);
    }
}
