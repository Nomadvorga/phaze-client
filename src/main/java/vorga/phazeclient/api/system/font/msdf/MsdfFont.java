package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public final class MsdfFont {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final AbstractTexture texture;
    private final FontData.AtlasData atlas;

    private final Int2ObjectMap<MsdfGlyph> glyphs;
    private final Int2ObjectMap<Int2FloatMap> kernings;

    private record WidthKey(String text, float size) {}
    private final Map<WidthKey, Float> widthCache = new HashMap<>(64);

    private MsdfFont(AbstractTexture texture, FontData.AtlasData atlas, Int2ObjectMap<MsdfGlyph> glyphs, Int2ObjectMap<Int2FloatMap> kernings) {
        this.texture = texture;
        this.atlas = atlas;
        this.glyphs = glyphs;
        this.kernings = kernings;
    }

    public FontData.AtlasData getAtlas() {
        return atlas;
    }

    public com.mojang.blaze3d.textures.GpuTextureView getTextureView() {
        return texture.getGlTextureView();
    }

    public void applyGlyphs(Matrix4f matrix, net.minecraft.client.render.BufferBuilder consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color,
                            com.mojang.blaze3d.vertex.VertexFormatElement paramsElement, float range, float shaderThickness, float smoothness) {
        int previousChar = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MsdfGlyph glyph = glyphs.get(c);
            if (glyph == null) {
                continue;
            }

            Int2FloatMap kerning = kernings.get(previousChar);
            if (kerning != null) {
                x += kerning.getOrDefault(c, 0.0F) * size;
            }

            x += glyph.apply(matrix, consumer, size, x, y, z, color, paramsElement, range, shaderThickness, smoothness) + thickness + spacing;
            previousChar = c;
        }
    }

    public float getWidth(String text, float size) {

        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        WidthKey key = new WidthKey(text, size);
        Float cached = widthCache.get(key);
        if (cached != null) {
            return cached;
        }

        int previousChar = -1;
        float width = 0.0F;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MsdfGlyph glyph = glyphs.get(c);
            if (glyph == null) {
                continue;
            }

            Int2FloatMap kerning = kernings.get(previousChar);
            if (kerning != null) {
                width += kerning.getOrDefault(c, 0.0F) * size;
            }

            width += glyph.getWidth(size);
            previousChar = c;
        }

        if (widthCache.size() >= 1024) {
            widthCache.clear();
        }
        widthCache.put(key, width);
        return width;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Identifier dataIdentifier;
        private Identifier atlasIdentifier;

        public Builder data(String fileName) {
            this.dataIdentifier = Identifier.of("minecraft", "msdf/" + fileName + ".json");
            return this;
        }

        public Builder atlas(String fileName) {
            this.atlasIdentifier = Identifier.of("minecraft", "msdf/" + fileName + ".png");
            return this;
        }

        public MsdfFont build() {
            FontData data = ResourceProvider.fromJsonToInstance(dataIdentifier, FontData.class);
            AbstractTexture texture = MC.getTextureManager().getTexture(atlasIdentifier);

            if (RenderSystem.isOnRenderThread()) {
                texture.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            }

            float atlasWidth = data.atlas().width();
            float atlasHeight = data.atlas().height();
            Int2ObjectMap<MsdfGlyph> glyphs = new Int2ObjectOpenHashMap<>();
            for (var glyphData : data.glyphs()) {
                glyphs.put(glyphData.unicode(), new MsdfGlyph(glyphData, atlasWidth, atlasHeight));
            }

            Int2ObjectMap<Int2FloatMap> kernings = new Int2ObjectOpenHashMap<>();
            for (var kerning : data.kernings()) {
                Int2FloatMap map = kernings.get(kerning.leftChar());
                if (map == null) {
                    map = new Int2FloatOpenHashMap();
                    kernings.put(kerning.leftChar(), map);
                }
                map.put(kerning.rightChar(), kerning.advance());
            }

            return new MsdfFont(texture, data.atlas(), glyphs, kernings);
        }
    }
}
