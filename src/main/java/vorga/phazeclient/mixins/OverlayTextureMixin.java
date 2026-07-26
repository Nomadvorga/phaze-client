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

    /** Vanilla hurt overlay color, as written by the OverlayTexture ctor. */
    @Unique
    private static final int phaze$VANILLA_HURT_ARGB = -1291911168;

    // 1.21.11: the reflective lookups this class used to do are gone.
    //
    // On 1.21.4 the pixel write and the texture upload were only reachable
    // through obfuscated NativeImage members (method_61941 / method_22619),
    // so the old code resolved them with getDeclaredMethod + setAccessible.
    // 1.21.11 exposes both as ordinary public API:
    //   NativeImage#setColorArgb(x, y, argb)   - same (x, y, ARGB) contract
    //                                            the vanilla ctor uses
    //   NativeImageBackedTexture#upload()      - uploads the whole image via
    //                                            the GpuDevice command encoder
    // so the reflection scaffolding, the RenderSystem.activeTexture() texture
    // unit juggling and the explicit bindTexture() are all deleted. Binding is
    // no longer a thing: upload() writes through CommandEncoder#writeToTexture
    // against the texture's own GpuTexture handle.

    /** Set once if a write/upload throws, so a broken state is not retried every frame. */
    @Unique
    private static boolean phaze$overlayDisabled;

    /**
     * ARGB currently written into the texture, or {@link Integer#MIN_VALUE}
     * when nothing has been written yet.
     *
     * <p>The overlay only ever holds one flat color, so a repeated
     * {@code setColor()} with an unchanged color is pure waste. Caching it
     * turns the redundant calls into a single int compare instead of 128
     * pixel writes plus a full 16x16 texture upload (which also forces a
     * driver round-trip mid-frame).
     */
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

        // Nothing changed since the last upload - skip the whole rebuild.
        // This is what makes the call from the entity render path cheap.
        if (argb == this.phaze$appliedArgb) {
            return;
        }

        try {
            // Rows 0..7 are the "hurt" half of the overlay; rows 8..15 stay
            // untouched, exactly as before (the old loop ran i in 0..15 and
            // skipped everything with i >= 8). Argument order matches the
            // vanilla ctor: setColorArgb(x, y, argb).
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
