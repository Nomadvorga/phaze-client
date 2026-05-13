package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Re-triggers {@link ChunkAnimator}'s per-section animation on
 * the F3+A debug keybind by clearing {@code firstSeenMs}.
 *
 * <p>Hooks the exact {@code INVOKE} site inside
 * {@code Keyboard#processF3(I)} where vanilla calls
 * {@link WorldRenderer#reload()}. That call site is reachable
 * <em>only</em> when the user presses F3+A - {@code processF3}
 * dispatches on the key code and dispatches to the chunk-rebuild
 * branch exclusively for {@code GLFW_KEY_A}. Iris cannot reach
 * this injector even though it also calls {@code reload()} for
 * pipeline rebuilds; Iris triggers {@code reload()} from its own
 * pipeline manager, never from {@code Keyboard#processF3}, so the
 * injector simply doesn't fire there.
 *
 * <p>This replaces the older {@code WorldRendererChunkAnimatorResetMixin}
 * which hooked {@code WorldRenderer#reload()} directly. The old
 * approach caught every caller including Iris's pipeline rebuilds
 * (fired up to ~10 times per shader pack apply / settings change),
 * which visibly restarted in-flight animations - the symptom the
 * user observed as "chunk plays, snaps back to start, plays
 * again, ~10 times". Scoping the trigger to {@code processF3}'s
 * specific invocation keeps F3+A's "re-animate everything"
 * behaviour while staying silent under Iris.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardChunkAnimatorResetMixin {

    @Inject(
            method = "processF3(I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;reload()V"
            )
    )
    private void phaze$chunkAnimatorOnF3A(int key, CallbackInfoReturnable<Boolean> cir) {
        ChunkAnimator.getInstance().resetTracker();
    }
}
