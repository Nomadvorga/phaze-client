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
        NativeImage nativeImage = this.texture.getImage();
        HitColor module = HitColor.getInstance();
        boolean useCustomHitColor = module.isEnabled() && module.customHitcolor.isValue();

        for(int i = 0; i < 16; ++i) {
            for(int j = 0; j < 16; ++j) {
                if (i < 8) {
                    int argb;
                    if (useCustomHitColor) {
                        int hitColor = module.getHitColor();
                        int red = (hitColor >> 16) & 0xFF;
                        int green = (hitColor >> 8) & 0xFF;
                        int blue = hitColor & 0xFF;
                        int alpha = (hitColor >> 24) & 0xFF;
                        argb = getColorInt(red, green, blue, alpha);
                    } else {
                        // Vanilla hurt overlay color from OverlayTexture ctor.
                        argb = -1291911168;
                    }
                    try {
                        // Use obfuscated method name for 1.21.4
                        Method setColorMethod = NativeImage.class.getDeclaredMethod("method_61941", int.class, int.class, int.class);
                        setColorMethod.setAccessible(true);
                        setColorMethod.invoke(nativeImage, j, i, argb);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        RenderSystem.activeTexture(33985);
        this.texture.bindTexture();
        try {
            Method uploadMethod = NativeImage.class.getDeclaredMethod("method_22619", int.class, int.class, int.class, int.class, int.class, int.class, int.class, boolean.class);
            uploadMethod.setAccessible(true);
            uploadMethod.invoke(nativeImage, 0, 0, 0, 0, 0, nativeImage.getWidth(), nativeImage.getHeight(), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        RenderSystem.activeTexture(33984);
    }
}
