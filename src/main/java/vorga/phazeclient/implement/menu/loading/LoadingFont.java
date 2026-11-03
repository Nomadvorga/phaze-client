package vorga.phazeclient.implement.menu.loading;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL32C.*;

/** The two fixed word meshes in em units, backed by independently owned atlas textures. */
final class LoadingFont {
    record Glyph(float x, float y, float width, float height, float u, float v, float uw, float vh) { }

    final Glyph[] glyphs;
    final float left;
    final float top;
    final float width;
    final float height;
    final float range;
    final int atlasWidth;
    final int atlasHeight;
    private int texture;

    private LoadingFont(JsonObject data, String word) {
        JsonObject atlas = data.getAsJsonObject("atlas");
        atlasWidth = atlas.get("width").getAsInt();
        atlasHeight = atlas.get("height").getAsInt();
        range = atlas.get("distanceRange").getAsFloat();
        String type = atlas.get("type").getAsString();
        if ((!type.equals("mtsdf") && !type.equals("msdf")) || atlasWidth <= 0 || atlasHeight <= 0
                || !Float.isFinite(range) || range <= 0
                || !atlas.get("yOrigin").getAsString().equals("bottom")) {
            throw new IllegalArgumentException("Expected a bottom-origin MSDF/MTSDF atlas with a positive range");
        }
        Map<Integer, JsonObject> byCodepoint = new HashMap<>();
        for (var entry : data.getAsJsonArray("glyphs")) {
            JsonObject glyph = entry.getAsJsonObject();
            byCodepoint.put(glyph.get("unicode").getAsInt(), glyph);
        }
        Map<Long, Float> kerning = new HashMap<>();
        JsonArray pairs = data.getAsJsonArray("kerning");
        if (pairs != null) {
            for (var entry : pairs) {
                JsonObject pair = entry.getAsJsonObject();
                kerning.put(pairKey(pair.get("unicode1").getAsInt(), pair.get("unicode2").getAsInt()),
                        pair.get("advance").getAsFloat());
            }
        }
        glyphs = new Glyph[word.length()];
        float pen = 0, minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < word.length(); i++) {
            int codepoint = word.charAt(i);
            JsonObject glyph = byCodepoint.get(codepoint);
            if (glyph == null || !glyph.has("planeBounds") || !glyph.has("atlasBounds")) {
                throw new IllegalArgumentException("Missing drawable glyph '" + word.charAt(i) + "'");
            }
            if (i > 0) pen += kerning.getOrDefault(pairKey(word.charAt(i - 1), codepoint), 0.0F);
            JsonObject plane = glyph.getAsJsonObject("planeBounds");
            JsonObject uv = glyph.getAsJsonObject("atlasBounds");
            float x = pen + value(plane, "left");
            float y = -value(plane, "top");
            float w = value(plane, "right") - value(plane, "left");
            float h = value(plane, "top") - value(plane, "bottom");
            // NativeImage rows are top-down; JSON atlas coordinates are bottom-up (as in MsdfGlyph).
            glyphs[i] = new Glyph(x, y, w, h, value(uv, "left") / atlasWidth,
                    1.0F - value(uv, "top") / atlasHeight,
                    (value(uv, "right") - value(uv, "left")) / atlasWidth,
                    (value(uv, "top") - value(uv, "bottom")) / atlasHeight);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + w);
            maxY = Math.max(maxY, y + h);
            pen += value(glyph, "advance");
        }
        left = minX;
        top = minY;
        width = maxX - minX;
        height = maxY - minY;
    }

    static LoadingFont load(String name, String word) throws IOException {
        String base = "/assets/minecraft/msdf/" + name;
        LoadingFont font;
        try (InputStream stream = BootstrapLoadingRenderer.resource(base + ".json");
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            font = new LoadingFont(JsonParser.parseReader(reader).getAsJsonObject(), word);
        } catch (RuntimeException | IOException exception) {
            throw new IOException("Cannot read bootstrap MSDF metadata " + base + ".json", exception);
        }
        try (InputStream stream = BootstrapLoadingRenderer.resource(base + ".png");
             NativeImage image = NativeImage.read(NativeImage.Format.RGBA, stream)) {
            if (image.getWidth() != font.atlasWidth || image.getHeight() != font.atlasHeight) {
                throw new IOException("Atlas dimensions disagree with JSON: " + base);
            }
            ByteBuffer pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(font.atlasWidth, font.atlasHeight), 4));
            try {
                // Do not use NativeImage.upload(): it changes Minecraft's cached GL state.
                for (int y = 0; y < font.atlasHeight; y++) {
                    for (int x = 0; x < font.atlasWidth; x++) {
                        int argb = image.getColorArgb(x, y);
                        pixels.put((byte) (argb >>> 16)).put((byte) (argb >>> 8))
                                .put((byte) argb).put((byte) (argb >>> 24));
                    }
                }
                pixels.flip();
                font.texture = glGenTextures();
                if (font.texture == 0) throw new IOException("Cannot allocate bootstrap atlas " + base);
                // Keep Minecraft's texture-unit cache in sync. The bootstrap
                // atlas lives on a reserved unit, away from Minecraft's
                // primary block/item sampler on unit 0.
                RenderSystem.activeTexture(LoadingGlState.ATLAS_TEXTURE_UNIT);
                RenderSystem.bindTexture(font.texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                // Linear RGBA8 is essential: sRGB decoding would corrupt the distance field.
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, font.atlasWidth, font.atlasHeight,
                        0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                if (glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) != font.atlasWidth) {
                    throw new IOException("OpenGL did not allocate bootstrap atlas " + base);
                }
            } finally {
                MemoryUtil.memFree(pixels);
            }
            return font;
        } catch (RuntimeException | IOException | Error exception) {
            font.close();
            throw new IOException("Cannot upload bootstrap MSDF atlas " + base + ".png", exception);
        }
    }

    int texture() {
        return texture;
    }

    void close() {
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
    }

    private static float value(JsonObject object, String name) {
        return object.get(name).getAsFloat();
    }

    private static long pairKey(int first, int second) {
        return ((long) first << 32) | (second & 0xFFFFFFFFL);
    }
}
