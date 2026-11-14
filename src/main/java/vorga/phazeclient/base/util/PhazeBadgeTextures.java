package vorga.phazeclient.base.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.io.InputStream;

/**
 * Badge textures made to measure: each is the 64×64 art reduced to exactly the number of
 * screen pixels it is drawn at, built once per size and drawn 1:1 so it stays sharp.
 */
final class PhazeBadgeTextures {
    private static final Identifier[] SOURCES = {
            Identifier.of("phaze", "textures/gui/phaze_user_badge_hd.png"),
            Identifier.of("phaze", "textures/gui/phaze_code_badge_hd.png")
    };
    private static final String[] NAMES = {"user", "code"};
    private static final int MAX_PIXELS = 256;

    private static final BadgeRaster.Square[] squares = new BadgeRaster.Square[SOURCES.length];
    private static final boolean[] unavailable = new boolean[SOURCES.length];
    private static final Identifier[][] built = new Identifier[SOURCES.length][MAX_PIXELS + 1];
    /** Leans towards the sharper of the two levels being blended, so mid distances stay crisp. */
    private static final float WORLD_LOD_BIAS = -0.5F;
    private static final Identifier[] world = new Identifier[SOURCES.length];

    private PhazeBadgeTextures() {
    }

    /** Width of the cropped art in pixels, or 0 when it could not be loaded. */
    static int sourceSize(boolean codeBadge) {
        BadgeRaster.Square square = square(codeBadge ? 1 : 0);
        return square == null ? 0 : square.size();
    }

    /** A texture exactly {@code pixels} wide, or null when the art is unavailable. */
    static Identifier texture(boolean codeBadge, int pixels) {
        int kind = codeBadge ? 1 : 0;
        BadgeRaster.Square square = square(kind);
        if (square == null || pixels < 1 || pixels > MAX_PIXELS) {
            return null;
        }
        Identifier id = built[kind][pixels];
        if (id != null) {
            return id;
        }
        int size = Math.min(pixels, square.size());
        int[] argb = BadgeRaster.downsample(square, size);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setColorArgb(x, y, argb[y * size + x]);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        texture.setFilter(false, false);
        id = Identifier.of("phaze", "dynamic/badge_" + NAMES[kind] + "_" + size);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        built[kind][pixels] = id;
        return id;
    }

    /**
     * The nametag badge. Closer than the art's own size its pixels are drawn as sharp blocks like
     * the font's; further away it is one texture with every smaller size down to a pixel, blended
     * by the GPU as the distance changes, so it never jumps between versions.
     */
    static Identifier worldTexture(boolean codeBadge) {
        int kind = codeBadge ? 1 : 0;
        if (world[kind] != null) {
            return world[kind];
        }
        BadgeRaster.Square square = square(kind);
        if (square == null) {
            return null;
        }
        Identifier id = Identifier.of("phaze", "dynamic/badge_" + NAMES[kind] + "_world");
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new MipTexture(BadgeRaster.mipChain(square), square.size()));
        world[kind] = id;
        return id;
    }

    private static BadgeRaster.Square square(int kind) {
        if (squares[kind] != null || unavailable[kind]) {
            return squares[kind];
        }
        try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(SOURCES[kind]);
             NativeImage image = NativeImage.read(NativeImage.Format.RGBA, stream)) {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    argb[y * width + x] = image.getColorArgb(x, y);
                }
            }
            BadgeRaster.Square square = BadgeRaster.cropSquare(argb, width, height);
            if (square.size() == 0) {
                unavailable[kind] = true;
                return null;
            }
            squares[kind] = square;
            return square;
        } catch (Exception error) {
            unavailable[kind] = true;
            return null;
        }
    }

    /**
     * Nearest when enlarged, trilinear when reduced. Render layers reset a texture's filter on
     * every draw, so this one keeps its own and ignores those calls.
     */
    private static final class MipTexture extends AbstractTexture {
        MipTexture(int[][] levels, int size) {
            int id = getGlId();
            TextureUtil.prepareImage(NativeImage.InternalFormat.RGBA, id, levels.length - 1, size, size);
            for (int level = 0; level < levels.length; level++) {
                int levelSize = size >> level;
                NativeImage image = new NativeImage(NativeImage.Format.RGBA, levelSize, levelSize, false);
                int[] pixels = levels[level];
                for (int y = 0; y < levelSize; y++) {
                    for (int x = 0; x < levelSize; x++) {
                        image.setColorArgb(x, y, pixels[y * levelSize + x]);
                    }
                }
                bindTexture();
                image.upload(level, 0, 0, true);
            }
            bindTexture();
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, WORLD_LOD_BIAS);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        @Override
        public void setFilter(boolean bilinear, boolean mipmap) {
        }

        @Override
        public void setFilter(TriState bilinear, boolean mipmap) {
        }

        @Override
        public void setClamp(boolean clamp) {
        }
    }
}
