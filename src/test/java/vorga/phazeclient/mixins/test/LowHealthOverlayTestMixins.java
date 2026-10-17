package vorga.phazeclient.mixins.test;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.LowHealthOverlayProbe;
import vorga.phazeclient.implement.features.modules.other.LowHealthWarning;

import java.util.function.Function;

/** Test-mod-only observers. No cancellation, redirects, or replacement rendering. */
public final class LowHealthOverlayTestMixins {
    private LowHealthOverlayTestMixins() {}

    @Mixin(GameRenderer.class)
    public abstract static class Frames {
        @Inject(method = "render", at = @At("HEAD"))
        private void begin(CallbackInfo ci) { LowHealthOverlayProbe.beginFrame(); }

        @Inject(method = "render", at = @At("RETURN"))
        private void end(CallbackInfo ci) { LowHealthOverlayProbe.endFrame(); }
    }

    @Mixin(DrawContext.class)
    public abstract static class Draws {
        @Inject(method = "draw()V", at = @At("TAIL"))
        private void flushed(CallbackInfo ci) {
            LowHealthOverlayProbe.flushed((DrawContext) (Object) this);
        }

        @Inject(method = "drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", at = @At("HEAD"))
        private void mask(Function<Identifier, RenderLayer> layer, Identifier texture, int x, int y,
                          float u, float v, int width, int height, int regionWidth, int regionHeight,
                          int textureWidth, int textureHeight, int color, CallbackInfo ci) {
            LowHealthOverlayProbe.mask((DrawContext) (Object) this, layer, texture, x, y, width, height, color);
        }
    }

    @Mixin(value = LowHealthWarning.class, remap = false)
    public abstract static class Warning {
        @Inject(method = "render", at = @At("HEAD"))
        private void begin(DrawContext context, CallbackInfoReturnable<Boolean> cir) {
            LowHealthOverlayProbe.beginWarning(context);
        }

        @Inject(method = "render", at = @At("RETURN"))
        private void end(DrawContext context, CallbackInfoReturnable<Boolean> cir) {
            LowHealthOverlayProbe.endWarning(cir.getReturnValue());
        }
    }

    @Mixin(InGameHud.class)
    public abstract static class Hud {
        @Inject(method = "render", at = @At("TAIL"))
        private void hud(CallbackInfo ci) { LowHealthOverlayProbe.hud(); }

        // Observe the populated-objective overload, not the empty sidebar dispatcher.
        @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("TAIL"))
        private void scoreboard(CallbackInfo ci) { LowHealthOverlayProbe.scoreboard(); }
    }

    @Mixin(DebugHud.class)
    public abstract static class Debug {
        @Inject(method = "render", at = @At("TAIL"))
        private void debug(CallbackInfo ci) { LowHealthOverlayProbe.debug(); }
    }

    @Mixin(Screen.class)
    public abstract static class Screens {
        @Inject(method = "renderWithTooltip", at = @At("TAIL"))
        private void screen(CallbackInfo ci) { LowHealthOverlayProbe.screen((Screen) (Object) this); }
    }

    @Mixin(NativeImage.class)
    public abstract static class Images {
        @Inject(method = "<init>(Lnet/minecraft/client/texture/NativeImage$Format;IIZ)V", at = @At("RETURN"))
        private void allocation(CallbackInfo ci) { LowHealthOverlayProbe.allocatedImage(); }

        @Inject(method = "uploadInternal", at = @At("HEAD"))
        private void upload(CallbackInfo ci) { LowHealthOverlayProbe.uploadedImage(); }
    }

    @Mixin(Framebuffer.class)
    public abstract static class Framebuffers {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void allocation(CallbackInfo ci) { LowHealthOverlayProbe.framebuffer(); }

        @Inject(method = {"initFbo", "beginWrite"}, at = @At("HEAD"))
        private void use(CallbackInfo ci) { LowHealthOverlayProbe.framebuffer(); }
    }

    @Mixin(TextureManager.class)
    public abstract static class Textures {
        @Inject(method = "registerTexture(Lnet/minecraft/util/Identifier;Lnet/minecraft/client/texture/AbstractTexture;)V", at = @At("HEAD"))
        private void register(Identifier id, net.minecraft.client.texture.AbstractTexture texture, CallbackInfo ci) {
            LowHealthOverlayProbe.registered(id);
        }
    }
}
