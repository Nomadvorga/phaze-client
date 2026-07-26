package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
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

import java.lang.reflect.Method;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixin implements OverlayReloadListener {
    @Shadow
    @Final
    private NativeImageBackedTexture texture;

    /** Vanilla hurt overlay color, as written by the OverlayTexture ctor. */
    @Unique
    private static final int phaze$VANILLA_HURT_ARGB = -1291911168;

    // Reflection handles for the obfuscated NativeImage members on 1.21.4.
    //
    // These are resolved exactly once. Previously getDeclaredMethod +
    // setAccessible ran inside the pixel loop, i.e. 128 times per setColor()
    // call - and setColor() is reachable from the entity render path, so on
    // a busy server that was hundreds of thousands of reflective lookups per
    // second. getDeclaredMethod is a linear scan over the class's declared
    // methods plus a defensive array copy; it is not free.
    @Unique
    private static Method phaze$setColorMethod;
    @Unique
    private static Method phaze$uploadMethod;
    @Unique
    private static boolean phaze$reflectionResolved;
    @Unique
    private static boolean phaze$reflectionFailed;

    /**
     * ARGB currently written into the texture, or {@link Integer#MIN_VALUE}
     * when nothing has been written yet.
     *
     * <p>The overlay only ever holds one flat color, so a repeated
     * {@code setColor()} with an unchanged color is pure waste. Caching it
     * turns the redundant calls into a single int compare instead of 128
     * reflective invokes plus a full 16x16 texture upload (which also forces
     * a driver round-trip mid-frame).
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

    @Unique
    private static void phaze$resolveReflection() {
        if (phaze$reflectionResolved) {
            return;
        }
        phaze$reflectionResolved = true;
        try {
            // Obfuscated method names for 1.21.4.
            phaze$setColorMethod = NativeImage.class.getDeclaredMethod(
                    "method_61941", int.class, int.class, int.class);
            phaze$setColorMethod.setAccessible(true);
            phaze$uploadMethod = NativeImage.class.getDeclaredMethod(
                    "method_22619", int.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, boolean.class);
            phaze$uploadMethod.setAccessible(true);
        } catch (Throwable t) {
            // Log once, then stay out of the way and let the vanilla overlay
            // stand. The old code threw inside the pixel loop and called
            // printStackTrace(), which on a mapping change meant 128 stack
            // traces per entity per frame - a hard freeze plus a log flood.
            phaze$reflectionFailed = true;
            phaze$setColorMethod = null;
            phaze$uploadMethod = null;
            System.err.println("[Phaze] HitColor overlay unavailable, using vanilla hurt color: " + t);
        }
    }

    public void setColor() {
        phaze$resolveReflection();
        if (phaze$reflectionFailed) {
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
            // skipped everything with i >= 8).
            for (int i = 0; i < 8; ++i) {
                for (int j = 0; j < 16; ++j) {
                    phaze$setColorMethod.invoke(nativeImage, j, i, argb);
                }
            }

            RenderSystem.activeTexture(33985);
            this.texture.bindTexture();
            phaze$uploadMethod.invoke(nativeImage, 0, 0, 0, 0, 0,
                    nativeImage.getWidth(), nativeImage.getHeight(), false);
            RenderSystem.activeTexture(33984);
        } catch (Throwable t) {
            phaze$reflectionFailed = true;
            System.err.println("[Phaze] HitColor overlay disabled after upload failure: " + t);
            return;
        }

        this.phaze$appliedArgb = argb;
    }
}
