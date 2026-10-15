package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.GlintColorizer;

@Mixin(RenderPhase.class)
public abstract class GlintColorizerMixin {

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
