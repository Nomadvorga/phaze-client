package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.hitcolor.OverlayReloadListener;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixin implements OverlayReloadListener {
    @Shadow
    @Final
    private NativeImageBackedTexture texture;

    @Unique
    private static final int phaze$VANILLA_HURT_ARGB = -1291911168;

    @Unique
    private static boolean phaze$overlayDisabled;

    @Unique
    private int phaze$appliedArgb = Integer.MIN_VALUE;

    @Inject(
        method = {"<init>"},
        at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo ci) {
        this.setColor();
        OverlayReloadListener.registerOverlay(this);
    }

    @Unique
    private static int getColorInt(int red, int green, int blue, int alpha) {
        alpha = 255 - alpha;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public void setColor() {
        if (phaze$overlayDisabled) {
            return;
        }

        NativeImage nativeImage = this.texture.getImage();
        if (nativeImage == null) {
            return;
        }

        HitColor module = HitColor.getInstance();
        int argb;
        if (module.isEnabled() && module.customHitcolor.isValue()) {
            int hitColor = module.getHitColor();
            int red = (hitColor >> 16) & 0xFF;
            int green = (hitColor >> 8) & 0xFF;
            int blue = hitColor & 0xFF;
            int alpha = (hitColor >> 24) & 0xFF;
            argb = getColorInt(red, green, blue, alpha);
        } else {
            argb = phaze$VANILLA_HURT_ARGB;
        }

        if (argb == this.phaze$appliedArgb) {
            return;
        }

        try {

            for (int i = 0; i < 8; ++i) {
                for (int j = 0; j < 16; ++j) {
                    nativeImage.setColorArgb(j, i, argb);
                }
            }

            this.texture.upload();
        } catch (Throwable t) {
            phaze$overlayDisabled = true;
            System.err.println("[Phaze] HitColor overlay disabled after upload failure: " + t);
            return;
        }

        this.phaze$appliedArgb = argb;
    }
}
