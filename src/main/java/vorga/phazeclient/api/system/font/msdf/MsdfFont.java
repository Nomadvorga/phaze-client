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

    /**
     * Per-(text, size) cache for {@link #getWidth(String, float)}. The
     * legacy implementation walked every character + kerning lookup
     * for every call, which the menu does ~40-60 times per frame just
     * to center / position constant labels (category chip names,
     * "OPTIONS", "ENABLED" / "DISABLED", module display names, bind
     * names, etc.). Most of those strings never change for the
     * lifetime of the menu, so caching turns a per-frame O(n*m) walk
     * into an O(1) HashMap lookup. Bounded eviction: cleared when it
     * grows past 1024 entries to keep dynamic strings (search
     * autocomplete fragments, scrolling text) from leaking.
     */
    private record WidthKey(String text, float size) {}
    private final Map<WidthKey, Float> widthCache = new HashMap<>(64);

    /**
     * Latches whether {@link #applyGlyphs} has already pinned the
     * atlas's GL_TEXTURE_MIN/MAG_FILTER to LINEAR/LINEAR. Vanilla's
     * {@code AbstractTexture.setFilter(true, true)} performs two
     * {@code glTexParameteri} calls which Sodium / Iris hook for
     * tracking - cheap individually, but called per-glyph-batch by
     * the menu they add up. Filter modes never change for an MSDF
     * atlas after the first paint, so we only set them once.
     */
    private boolean filterApplied = false;

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
        if (!filterApplied) {
            texture.setFilter(true, true);
            filterApplied = true;
        }

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
        // Empty strings are common (placeholder labels, "" for no
        // bind name). Short-circuit before hashmap lookup.
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

        // Cap cache to avoid unbounded growth from streaming inputs
        // (search-autocomplete drafts, ticker text, etc.). Wholesale
        // wipe is fine here because the cost of repopulating the most
        // common ~50 menu labels is microscopic relative to a single
        // saved frame's worth of avoided getWidth walks.
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
