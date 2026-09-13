package vorga.phazeclient.implement.menu;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

final class PhazeCubeMapTexture extends AbstractTexture {

    private static final int[] FACE_TO_PANORAMA = {1, 3, 5, 4, 0, 2};

    PhazeCubeMapTexture(Identifier id, NativeImage[] facesByPanoramaIndex) {

        int size = commonFaceSize(facesByPanoramaIndex);

        GpuDevice device = RenderSystem.getDevice();

        this.glTexture = device.createTexture(
                id::toString,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_CUBEMAP_COMPATIBLE,
                TextureFormat.RGBA8,
                size,
                size,
                6,
                1
        );
        this.glTextureView = device.createTextureView(this.glTexture);

        this.sampler = RenderSystem.getSamplerCache()
                .get(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);

        try {
            CommandEncoder encoder = device.createCommandEncoder();
            for (int face = 0; face < 6; face++) {
                NativeImage source = facesByPanoramaIndex[FACE_TO_PANORAMA[face]];

                NativeImage squared = source.getWidth() == size && source.getHeight() == size
                        ? null
                        : centerCrop(source, size);
                NativeImage cropped;
                try {
                    cropped = flipVertically(squared != null ? squared : source, size);
                } finally {

                    if (squared != null) {
                        squared.close();
                    }
                }
                try {

                    encoder.writeToTexture(
                            this.glTexture,
                            cropped != null ? cropped : source,
                            0,
                            face,
                            0,
                            0,
                            size,
                            size,
                            0,
                            0
                    );
                } finally {
                    if (cropped != null) {
                        cropped.close();
                    }
                }
            }
        } catch (Throwable t) {

            close();
            throw t;
        }
    }

    private static int commonFaceSize(NativeImage[] facesByPanoramaIndex) {
        if (facesByPanoramaIndex == null || facesByPanoramaIndex.length != 6) {
            throw new IllegalArgumentException("panorama cube map needs exactly 6 faces");
        }
        int size = Integer.MAX_VALUE;
        for (NativeImage face : facesByPanoramaIndex) {
            if (face == null) {
                throw new IllegalArgumentException("panorama cube map face is missing");
            }
            size = Math.min(size, Math.min(face.getWidth(), face.getHeight()));
        }
        if (size < 1) {
            throw new IllegalArgumentException("panorama cube map faces are empty");
        }
        return size;
    }

    private static NativeImage centerCrop(NativeImage source, int size) {
        int offsetX = Math.max(0, (source.getWidth() - size) / 2);
        int offsetY = Math.max(0, (source.getHeight() - size) / 2);
        NativeImage square = new NativeImage(source.getFormat(), size, size, false);
        source.copyRect(square, offsetX, offsetY, 0, 0, size, size, false, false);
        return square;
    }

    private static NativeImage flipVertically(NativeImage source, int size) {
        NativeImage flipped = new NativeImage(source.getFormat(), size, size, false);
        source.copyRect(flipped, 0, 0, 0, 0, size, size, false, true);
        return flipped;
    }

}
