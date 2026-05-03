package vorga.phazeclient.implement.hitcolor;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import vorga.phazeclient.implement.features.modules.other.HitColor;

import java.lang.reflect.Method;

public class HitColorOverlayTexture {
    private static NativeImageBackedTexture customOverlayTexture;
    private static int textureId;

    public static void init() {
        if (customOverlayTexture == null) {
            customOverlayTexture = new NativeImageBackedTexture(16, 16, false);
            try {
                Method getIdMethod = NativeImageBackedTexture.class.getDeclaredMethod("getId");
                getIdMethod.setAccessible(true);
                textureId = (Integer) getIdMethod.invoke(customOverlayTexture);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        setColor();
    }

    private static void setColor() {
        if (customOverlayTexture == null) return;

        NativeImage nativeImage = customOverlayTexture.getImage();

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (i < 8) {
                    int hitColor = HitColor.getInstance().getHitColor();
                    int red = (hitColor >> 16) & 0xFF;
                    int green = (hitColor >> 8) & 0xFF;
                    int blue = hitColor & 0xFF;
                    int alpha = (hitColor >> 24) & 0xFF;
                    try {
                        // Use obfuscated method name for 1.21.4
                        Method setColorMethod = NativeImage.class.getDeclaredMethod("method_61941", int.class, int.class, int.class);
                        setColorMethod.setAccessible(true);
                        setColorMethod.invoke(nativeImage, j, i, ((255 - alpha) << 24 | red << 16 | green << 8 | blue));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        RenderSystem.activeTexture(33985);
        customOverlayTexture.bindTexture();
        nativeImage.upload(0, 0, 0, 0, 0, nativeImage.getWidth(), nativeImage.getHeight(), false);
        RenderSystem.activeTexture(33984);
    }

    public static void setupOverlayColor() {
        init();
        RenderSystem.setupOverlayColor(textureId, 16);
    }

    public static void teardownOverlayColor() {
        RenderSystem.teardownOverlayColor();
    }
}
