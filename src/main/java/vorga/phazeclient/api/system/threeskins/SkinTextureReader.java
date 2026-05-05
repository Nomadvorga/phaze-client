package vorga.phazeclient.api.system.threeskins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Optional;

public class SkinTextureReader {

    public static NativeImage readSkin(Identifier skinLocation) {
        if (skinLocation == null) return null;

        MinecraftClient client = MinecraftClient.getInstance();

        // First, try to get from resource manager (for local skins)
        try {
            Optional<net.minecraft.resource.Resource> resource = client.getResourceManager().getResource(skinLocation);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().getInputStream()) {
                    return NativeImage.read(stream);
                }
            }
        } catch (Exception e) {
            // Not in resources, continue
        }

        // Second, try to get from texture manager using reflection (for downloaded skins)
        try {
            AbstractTexture texture = client.getTextureManager().getTexture(skinLocation);
            if (texture != null) {
                // Try to find NativeImage field in the texture class
                for (Field field : texture.getClass().getDeclaredFields()) {
                    if (field.getType() == NativeImage.class) {
                        field.setAccessible(true);
                        NativeImage image = (NativeImage) field.get(texture);
                        if (image != null) {
                            return image;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failed to access texture
        }

        return null;
    }
}
