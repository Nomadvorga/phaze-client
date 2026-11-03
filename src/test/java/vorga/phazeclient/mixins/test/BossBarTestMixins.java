package vorga.phazeclient.mixins.test;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Mouse;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.hud.BossBarProbe;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class BossBarTestMixins {
    private BossBarTestMixins() {}

    @Mixin(BossBarHud.class)
    public interface Bars {
        @Accessor("bossBars") Map<UUID, ClientBossBar> phazeTest$getBars();
    }

    @Mixin(Mouse.class)
    public interface MouseState {
        @Accessor("activeButton") int phazeTest$getActiveButton();
    }

    @Mixin(value = BossBarHud.class, priority = 900)
    public abstract static class ForeignHooks {
        @Shadow @Final private static Identifier[] PROGRESS_TEXTURES;

        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        private void foreignRender(DrawContext context, CallbackInfo ci) {
            BossBarProbe.foreignRender();
            if (BossBarProbe.cancelRender) ci.cancel();
        }

        @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
                at = @At("HEAD"), cancellable = true)
        private void foreignProgress(DrawContext context, int x, int y, BossBar bar, int width,
                                     Identifier[] textures, Identifier[] notches, CallbackInfo ci) {
            boolean progress = textures == PROGRESS_TEXTURES;
            BossBarProbe.foreignPass(progress);
            if (progress && BossBarProbe.cancelProgress) ci.cancel();
        }

        @ModifyArg(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIIIIII)V", ordinal = 0), index = 1)
        private Identifier foreignTexture(Identifier texture) {
            return BossBarProbe.replaceProgress && texture.getPath().endsWith("_progress")
                    ? BossBarProbe.FOREIGN_TEXTURE : texture;
        }
    }

    @Mixin(BossBarHud.class)
    public abstract static class Renders {
        @WrapMethod(method = "render")
        private void observeRender(DrawContext context, Operation<Void> original) {
            long started = BossBarProbe.enterRender();
            try {
                original.call(context);
            } finally {
                BossBarProbe.exitRender(started);
            }
        }
    }

    @Mixin(DrawContext.class)
    public abstract static class Draws {
        @WrapMethod(method = "drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIIIIII)V")
        private void observeSprite(Function<Identifier, RenderLayer> layer, Identifier texture,
                                   int textureWidth, int textureHeight, int u, int v,
                                   int x, int y, int width, int height, Operation<Void> original) {
            Identifier previous = BossBarProbe.sprite;
            if (BossBarProbe.recording) BossBarProbe.sprite = texture;
            try {
                original.call(layer, texture, textureWidth, textureHeight, u, v, x, y, width, height);
            } finally {
                BossBarProbe.sprite = previous;
            }
        }

        @Inject(method = "drawTexturedQuad(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIIFFFFI)V", at = @At("RETURN"))
        private void observeQuad(Function<Identifier, RenderLayer> layer, Identifier atlas,
                                 int x1, int x2, int y1, int y2, float u1, float u2, float v1, float v2,
                                 int color, CallbackInfo ci) {
            BossBarProbe.quad((DrawContext) (Object) this, x1, x2, y1, y2, color);
        }

        @Inject(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I", at = @At("HEAD"))
        private void observeName(TextRenderer renderer, Text name, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
            BossBarProbe.text(name);
        }
    }

    @Mixin(TextRenderer.class)
    public abstract static class Widths {
        @Inject(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"))
        private void observeWidth(StringVisitable text, CallbackInfoReturnable<Integer> cir) { BossBarProbe.width(text); }
    }

    @Mixin(value = ExordiumAnimationBridge.class, remap = false)
    public abstract static class Buffers {
        @Inject(method = "afterBufferRender", at = @At("RETURN"))
        private static void observeCapture(Object instance, CallbackInfo ci) { BossBarProbe.buffer(instance); }

        @Inject(method = "deferBufferedComponent", at = @At("RETURN"))
        private static void observeCache(Object buffer, CallbackInfoReturnable<Boolean> cir) {
            BossBarProbe.cached(buffer, cir.getReturnValueZ());
        }
    }

    @Mixin(NativeImage.class)
    public abstract static class Images {
        @Inject(method = "<init>(Lnet/minecraft/client/texture/NativeImage$Format;IIZ)V", at = @At("RETURN"))
        private void image(CallbackInfo ci) { if (BossBarProbe.insideRender()) BossBarProbe.images++; }

        @Inject(method = "uploadInternal(IIIIIIIZ)V", at = @At("HEAD"))
        private void upload(CallbackInfo ci) { if (BossBarProbe.insideRender()) BossBarProbe.uploads++; }
    }

    @Mixin(TextureManager.class)
    public abstract static class Textures {
        @Inject(method = "registerTexture", at = @At("HEAD"))
        private void register(CallbackInfo ci) { if (BossBarProbe.insideRender()) BossBarProbe.registrations++; }
    }
}
