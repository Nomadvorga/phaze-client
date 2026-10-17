package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.menu.loading.BootstrapLoadingRenderer;

import java.util.Optional;
import java.util.function.Consumer;

/** Keeps vanilla reload ownership, but draws exclusively with our bootstrap shaders. */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$renderLoadingScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();
        long now = Util.getMeasuringTimeMs();
        if (reloadCompleteTime == -1L && reload.isComplete()) {
            // Mark first so a callback can never be repeated by the next frame.
            reloadCompleteTime = now;
            Optional<Throwable> failure = Optional.empty();
            try {
                reload.throwException();
            } catch (Throwable throwable) {
                failure = Optional.of(throwable);
            }
            exceptionHandler.accept(failure);
            // Failure recovery can install a different reload overlay. Do not
            // draw over it or later clear it on behalf of this completed one.
            if (client.getOverlay() != (Object) this) return;
            if (client.currentScreen != null) {
                client.currentScreen.init(client, context.getScaledWindowWidth(), context.getScaledWindowHeight());
            }
        }

        float alpha = 1.0F;
        if (reloadCompleteTime != -1L) {
            float elapsed = (now - reloadCompleteTime) / 1000.0F;
            if (client.currentScreen != null) {
                client.currentScreen.render(context, 0, 0, delta);
            }
            // A short full-opacity hold shows the completed bar before fading.
            alpha = 1.0F - MathHelper.clamp((elapsed - 0.2F) / 0.4F, 0.0F, 1.0F);
            if (elapsed >= 0.6F) {
                if (client.getOverlay() == (Object) this) client.setOverlay(null);
                return;
            }
            progress = 1.0F;
        } else {
            float target = MathHelper.clamp(reload.getProgress(), 0.0F, 1.0F);
            progress = Math.max(progress, MathHelper.lerp(0.15F, progress, target));
        }

        // No ResourceManager / Minecraft ShaderLoader / vanilla text or fills.
        // Bundled atlases and our own programs are available even at progress 0.
        context.draw();
        BootstrapLoadingRenderer.render(client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), progress, alpha);
    }
}
