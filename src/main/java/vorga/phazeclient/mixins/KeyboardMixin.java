package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import net.minecraft.client.render.WorldRenderer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.AucHelper;
import vorga.phazeclient.implement.features.modules.other.AutoSwap;
import vorga.phazeclient.implement.features.modules.other.Binds;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;
import vorga.phazeclient.implement.features.modules.other.ElytraUtility;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.Zoom;

/**
 * Consolidated {@link Keyboard} mixin. Replaces the per-feature
 * {@code Keyboard*Mixin} files (AucHelper / AutoSwap / Binds /
 * ChunkAnimatorReset / ElytraUtility / Zoom). Each feature lives in
 * its own injector method below; the file consolidation is a pure
 * source-organisation change because Mixin processor merges every
 * mixin targeting the same class into one transformation pass
 * regardless of how many source files contributed - so runtime
 * behaviour is identical.
 *
 * <h3>Why grouped, not interleaved</h3>
 * Each {@code @Inject} stays its own method (instead of one
 * super-handler that dispatches to every module) for two reasons:
 * <ul>
 *   <li>Two of the original mixins use {@code cancellable = true}
 *       to swallow the key event when their bind matches (AutoSwap,
 *       Zoom). Sharing a {@code CallbackInfo} between modules would
 *       let one cancellation block another module from observing
 *       the same key tick - bad for binds that need to coexist
 *       (e.g. Zoom + Binds on different keys).</li>
 *   <li>{@code ChunkAnimatorReset} hooks {@code processF3(I)Z} -
 *       a different vanilla method - and uses
 *       {@code CallbackInfoReturnable<Boolean>}, which is
 *       structurally incompatible with the other handlers' {@code
 *       CallbackInfo}. Different methods can't share an
 *       injector.</li>
 * </ul>
 *
 * <h3>Order of HEAD injectors</h3>
 * Mixin doesn't guarantee deterministic ordering between sibling
 * HEAD injectors on the same target. None of these handlers depend
 * on the others' state - each module reads its own bind, mutates
 * its own state - so the undefined order is fine. The only
 * cross-module interaction is "did one of them cancel?", and
 * that's a one-way signal: if AutoSwap or Zoom cancels, vanilla
 * doesn't run the rest of {@code onKey} but the other HEAD
 * injectors have already fired.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    /** AucHelper: PRESS forwards to the module's {@code /ah search}
     *  trigger. Not cancellable - the action is a chat-side-effect,
     *  not a keymap replacement. */
    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyAucHelper(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        AucHelper.getInstance().onBindPressed(key, action);
    }

    /** AutoSwap: PRESS on the configured bind triggers a direct
     *  swap and CANCELS the vanilla key dispatch so the bind key
     *  doesn't double-fire its normal binding. */
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void phaze$onKeyAutoSwap(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!AutoSwap.getInstance().isEnabled()) {
            return;
        }
        int bindKey = AutoSwap.getInstance().keybind.getKey();
        if (bindKey == key && bindKey != GLFW.GLFW_KEY_UNKNOWN && action == GLFW.GLFW_PRESS) {
            AutoSwap.getInstance().activateDirectSwap();
            ci.cancel();
        }
    }

    /** Binds: forwards every key tick to the canned-message
     *  dispatcher in {@link Binds#onKey}. Not cancellable - sending
     *  a chat message is a side effect, the user's other binds on
     *  the same key (rare, but allowed) should still fire. */
    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyBinds(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Binds.getInstance().onKey(key, action);
    }

    /** ElytraUtility: forwards every key edge to the module's bind
     *  state machine. */
    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyElytraUtility(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        ElytraUtility module = ElytraUtility.getInstance();
        if (module == null) {
            return;
        }
        module.onBindStateChanged(key, action);
    }

    /**
     * Zoom + FreeLook + ChangeHand. Cancellable because Zoom
     * swallows the vanilla key dispatch when its bind matches
     * (otherwise the bound key would also trigger whatever vanilla
     * keybind shares it).
     */
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void phaze$onKeyZoom(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook != null && freeLook.isEnabled()) {
            freeLook.onBindStateChanged(key, action);
        }

        ChangeHand changeHand = ChangeHand.getInstance();
        if (changeHand != null && changeHand.isEnabled()) {
            changeHand.onBindStateChanged(key, action);
        }

        int bindKey = Zoom.getInstance().keybind.getKey();
        if (bindKey == key && bindKey != GLFW.GLFW_KEY_UNKNOWN) {
            // Block zoom activation while any GUI is open. Without
            // this guard the user could press the bind inside the
            // chat, our own menu, the inventory, or even on Esc
            // and re-enter the world already zoomed in - which the
            // user explicitly didn't want. RELEASE is also gated
            // so a HOLD-mode bind that started in the world but
            // gets released while a GUI is open doesn't toggle
            // zoom off either (matches the screen-as-release
            // semantics already implemented in {@link
            // vorga.phazeclient.mixins.ScreenOpenMixin}).
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean inGui = mc != null && mc.currentScreen != null;
            if (action == GLFW.GLFW_PRESS) {
                if (Zoom.getInstance().isEnabled() && !inGui) {
                    if (Zoom.getInstance().isHold()) {
                        Zoom.setZoomActive(true);
                    } else {
                        Zoom.setZoomActive(!Zoom.isZoomActive());
                    }
                }
                ci.cancel();
            } else if (action == GLFW.GLFW_RELEASE) {
                if (Zoom.getInstance().isEnabled() && Zoom.getInstance().isHold() && !inGui) {
                    Zoom.setZoomActive(false);
                }
                ci.cancel();
            }
        }
    }

    /**
     * ChunkAnimator F3+A reset hook. Different target method
     * ({@code processF3(I)Z}) and different return signal
     * ({@code CallbackInfoReturnable<Boolean>}), so it shares the
     * file but not the injector with the {@code onKey} handlers
     * above. Mixin merges all of them into the same transformed
     * Keyboard class regardless.
     */
    @Inject(
            method = "processF3(I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;reload()V"
            )
    )
    private void phaze$chunkAnimatorOnF3A(int key, CallbackInfoReturnable<Boolean> cir) {
        ChunkAnimator.getInstance().onF3AReload();
    }
}
