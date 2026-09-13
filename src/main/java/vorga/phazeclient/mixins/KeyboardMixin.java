package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
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
import vorga.phazeclient.implement.features.modules.other.FastSwap;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.Zoom;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyAucHelper(long window, int action, KeyInput input, CallbackInfo ci) {
        AucHelper.getInstance().onBindPressed(input.key(), action);
    }

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void phaze$onKeyAutoSwap(long window, int action, KeyInput input, CallbackInfo ci) {
        if (!AutoSwap.getInstance().isEnabled() || !AutoSwap.getInstance().canActivateInGame()) {
            return;
        }
        int key = input.key();
        int bindKey = AutoSwap.getInstance().keybind.getKey();
        if (bindKey == key && bindKey != GLFW.GLFW_KEY_UNKNOWN && action == GLFW.GLFW_PRESS) {
            AutoSwap.getInstance().activateDirectSwap();
            ci.cancel();
        }
    }

    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyBinds(long window, int action, KeyInput input, CallbackInfo ci) {
        Binds.getInstance().onKey(input.key(), action);
    }

    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyFastSwap(long window, int action, KeyInput input, CallbackInfo ci) {
        FastSwap.getInstance().onKey(input.key(), action);
    }

    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyElytraUtility(long window, int action, KeyInput input, CallbackInfo ci) {
        ElytraUtility module = ElytraUtility.getInstance();
        if (module == null) {
            return;
        }
        module.onBindStateChanged(input.key(), action);
    }

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void phaze$onKeyZoom(long window, int action, KeyInput input, CallbackInfo ci) {
        int key = input.key();

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

    @Inject(
            method = "processF3(Lnet/minecraft/client/input/KeyInput;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;reload()V",
                    shift = At.Shift.AFTER
            )
    )
    private void phaze$chunkAnimatorOnF3A(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        ChunkAnimator.getInstance().onF3AReload();
    }
}
