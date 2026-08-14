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

/**
 * Cube map built by Phaze from six in-memory {@link NativeImage} faces.
 *
 * <p>1.21.11: CubeMapRenderer no longer binds the six panorama faces itself,
 * one per Identifier - it samples ONE cube-map texture looked up under the base
 * id. Vanilla builds that texture with {@code CubemapTexture}, a
 * ReloadableTexture whose faces can only come out of the ResourceManager, so
 * zip-loaded panoramas (which publish dynamic textures, not resources) have no
 * vanilla path at all. This class is the replacement: it allocates the same
 * GpuTexture vanilla would - USAGE_CUBEMAP_COMPATIBLE, 6 layers, 1 mip level -
 * and fills it with one writeToTexture per face layer.</p>
 *
 * <p>Deliberately extends AbstractTexture and NOT ReloadableTexture:
 * TextureManager.reload() only reloads ReloadableTexture entries, so a resource
 * reload leaves this one alone instead of trying (and failing) to re-read it
 * from the ResourceManager. The flip side is that Phaze owns its lifetime -
 * see MenuPanoramaRegistry.CustomPanoramaDescriptor.close().</p>
 */
final class PhazeCubeMapTexture extends AbstractTexture {
    /**
     * 1.21.11: vanilla's {@code CubemapTexture.TEXTURE_SUFFIXES} is
     * {@code {_1, _3, _5, _4, _0, _2}} and cube layer {@code i} is filled from
     * suffix {@code i}, i.e. the GL face order (+X, -X, +Y, -Y, +Z, -Z) is NOT
     * the panorama file order. Uploading panorama_0..5 straight into layers 0..5
     * compiles, runs, and renders a scrambled sky - so the remap lives here.
     *
     * <p>This is vanilla's mapping verbatim. An earlier attempt to correct a
     * 180 degree offset by swapping the horizontal pairs here was wrong: it
     * mirrors each wall's CONTENT as well as reordering the faces, so the walls
     * stayed backwards while the floor - which a mirror does not visibly change
     * - started looking right. The offset is a rotation, not a mirroring, so it
     * belongs on the view yaw in MenuPanoramaRenderer, where it turns the whole
     * cube as one piece.
     */
    private static final int[] FACE_TO_PANORAMA = {1, 3, 5, 4, 0, 2};

    /**
     * @param id                   texture id the cube map is registered under (used as the GPU debug label)
     * @param facesByPanoramaIndex six faces indexed by PANORAMA FILE NUMBER (panorama_0 .. panorama_5);
     *                             the images stay owned by the caller and are never closed here
     * @throws IllegalArgumentException if the faces are missing or unusable - callers degrade to skipping the draw
     */
    PhazeCubeMapTexture(Identifier id, NativeImage[] facesByPanoramaIndex) {
        // Validate before allocating anything: GlBackend.createTexture rejects a
        // cube map whose width != height, and a half-built texture would leak.
        int size = commonFaceSize(facesByPanoramaIndex);

        GpuDevice device = RenderSystem.getDevice();
        // Same allocation vanilla's CubemapTexture.load() performs (usage 21).
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
        // A sampler is mandatory: RenderPass.bindTexture silently DROPS the
        // Sampler0 uniform when handed null, which shows up as a blank draw with
        // no exception. REPEAT + LINEAR is what ReloadableTexture.reload() ends
        // up with for CubemapTexture (clamp=false, blur=true, no mipmaps).
        this.sampler = RenderSystem.getSamplerCache()
                .get(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);

        try {
            CommandEncoder encoder = device.createCommandEncoder();
            for (int face = 0; face < 6; face++) {
                NativeImage source = facesByPanoramaIndex[FACE_TO_PANORAMA[face]];
                // Non-square user zips would be rejected by the allocation above,
                // so they get centre-cropped onto a COPY - the originals belong to
                // the registry's NativeImageBackedTextures and must survive.
                //
                // The copy is also where the vertical flip happens. CubeMapRenderer
                // starts its matrix with rotationX(PI) - a 180 degree flip about X
                // that vanilla's own stacked-image upload is authored against. Six
                // separate panorama_N images are not, so without mirroring each one
                // the whole cube renders upside down. Reordering faces cannot fix
                // that: it moves images between faces but never flips their content,
                // which is why the two face-order attempts both failed.
                NativeImage squared = source.getWidth() == size && source.getHeight() == size
                        ? null
                        : centerCrop(source, size);
                NativeImage cropped;
                try {
                    cropped = flipVertically(squared != null ? squared : source, size);
                } finally {
                    // The crop is only an intermediate; the flip already copied
                    // out of it, so it must not outlive this statement.
                    if (squared != null) {
                        squared.close();
                    }
                }
                try {
                    // The 4th argument is the destination layer, i.e. the cube face:
                    // GlCommandEncoder picks GlConst.CUBEMAP_TARGETS[layer % 6] when
                    // the texture carries USAGE_CUBEMAP_COMPATIBLE.
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
            // Never leave a half-uploaded GpuTexture behind on the way out.
            close();
            throw t;
        }
    }

    /**
     * Largest square every face can supply. Also the validation gate: cube maps
     * must be square and all six layers share one size.
     */
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

    /**
     * Vertically mirrored copy of a square face.
     *
     * <p>Always returns a NEW image, so the caller can close it unconditionally
     * and the registry-owned originals are never touched. {@code copyRect}'s
     * last two flags are mirrorX / mirrorY, so this costs one blit rather than
     * a manual row loop.
     */
    private static NativeImage flipVertically(NativeImage source, int size) {
        NativeImage flipped = new NativeImage(source.getFormat(), size, size, false);
        source.copyRect(flipped, 0, 0, 0, 0, size, size, false, true);
        return flipped;
    }

    // No close() override on purpose: AbstractTexture.close() already frees the
    // GpuTexture and its view, must not touch the registry-owned NativeImages,
    // and must not touch the sampler (SamplerCache owns and shares those).
}
