package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.render.GlintTextureClock;
import vorga.phazeclient.implement.features.modules.other.GlintColorizer;

@Mixin(RenderPhase.class)
public abstract class GlintColorizerMixin {

    @Unique
    private static final GlintTextureClock phaze$glintTextureClock = new GlintTextureClock();

    // Both GLINT_TEXTURING (items) and ENTITY_GLINT_TEXTURING (armor) use this
    // clock. Vanilla still applies options.getGlintSpeed() * 8 and its matrix.
    @ModifyExpressionValue(
            method = "setupGlintTexturing(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMeasuringTimeMs()J"),
            require = 1,
            allow = 1
    )
    private static long phaze$scaleGlintTextureTime(long original) {
        // One shared clock for every glint pass, exclusively owned by rendering.
        RenderSystem.assertOnRenderThread();
        GlintColorizer module = GlintColorizer.getInstance();
        boolean enabled = module != null && module.isEnabled();
        return phaze$glintTextureClock.sample(original, enabled, enabled ? module.speed.getValue() : 1.0);
    }

    @Inject(method = "startDrawing", at = @At("TAIL"))
    private void phaze$applyCustomGlint(CallbackInfo ci) {
        if (!((Object) this instanceof RenderLayer layer)
                || !layer.toString().toLowerCase(java.util.Locale.ROOT).contains("glint")) {
            return;
        }

        GlintColorizer module = GlintColorizer.getInstance();
        if (module == null || !module.isEnabled()) return;

        int color = module.getActiveGlintColor();
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                ((color >>> 24) & 0xFF) / 255.0f
        );
    }

    @Inject(method = "endDrawing", at = @At("HEAD"))
    private void phaze$resetCustomGlint(CallbackInfo ci) {
        if (!((Object) this instanceof RenderLayer layer)
                || !layer.toString().toLowerCase(java.util.Locale.ROOT).contains("glint")) {
            return;
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
