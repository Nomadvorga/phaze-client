package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class MsdfFont {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final AbstractTexture texture;
    private final FontData.AtlasData atlas;
    private final Map<Integer, MsdfGlyph> glyphs;
    private final Map<Integer, Map<Integer, Float>> kernings;

    private MsdfFont(AbstractTexture texture, FontData.AtlasData atlas, Map<Integer, MsdfGlyph> glyphs, Map<Integer, Map<Integer, Float>> kernings) {
        this.texture = texture;
        this.atlas = atlas;
        this.glyphs = glyphs;
        this.kernings = kernings;
    }

    public FontData.AtlasData getAtlas() {
        return atlas;
    }

    public int getTextureId() {
        return texture.getGlId();
    }

    public void applyGlyphs(Matrix4f matrix, VertexConsumer consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color) {
        texture.setFilter(true, true);

        int previousChar = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MsdfGlyph glyph = glyphs.get((int) c);
            if (glyph == null) {
                continue;
            }

            Map<Integer, Float> kerning = kernings.get(previousChar);
            if (kerning != null) {
                x += kerning.getOrDefault((int) c, 0.0F) * size;
            }

            x += glyph.apply(matrix, consumer, size, x, y, z, color) + thickness + spacing;
            previousChar = c;
        }
    }

    public float getWidth(String text, float size) {
        int previousChar = -1;
        float width = 0.0F;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MsdfGlyph glyph = glyphs.get((int) c);
            if (glyph == null) {
                continue;
            }

            Map<Integer, Float> kerning = kernings.get(previousChar);
            if (kerning != null) {
                width += kerning.getOrDefault((int) c, 0.0F) * size;
            }

            width += glyph.getWidth(size);
            previousChar = c;
        }

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

            RenderSystem.recordRenderCall(() -> texture.setFilter(true, false));

            float atlasWidth = data.atlas().width();
            float atlasHeight = data.atlas().height();
            Map<Integer, MsdfGlyph> glyphs = data.glyphs().stream()
                    .collect(Collectors.toMap(FontData.GlyphData::unicode, glyphData -> new MsdfGlyph(glyphData, atlasWidth, atlasHeight)));

            Map<Integer, Map<Integer, Float>> kernings = new HashMap<>();
            data.kernings().forEach(kerning -> {
                Map<Integer, Float> map = kernings.computeIfAbsent(kerning.leftChar(), ignored -> new HashMap<>());
                map.put(kerning.rightChar(), kerning.advance());
            });

            return new MsdfFont(texture, data.atlas(), glyphs, kernings);
        }
    }
}
