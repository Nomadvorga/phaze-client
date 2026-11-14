package vorga.phazeclient.api.system.font.msdf;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class MsdfFont {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final AbstractTexture texture;
    private final FontData.AtlasData atlas;
    /**
     * Int-keyed so per-character lookups don't box.
     *
     * <p>These were {@code Map<Integer, ...>}, so every glyph lookup and
     * every kerning lookup autoboxed its key, and the kerning default
     * ({@code 0.0F}) allocated a {@code Float} as well - {@code Float} has
     * no valueOf cache. That was up to three allocations per character per
     * frame on the menu's text path.
     */
    private final Int2ObjectMap<MsdfGlyph> glyphs;
    private final Int2ObjectMap<Int2FloatMap> kernings;
    /** Latin and Cyrillic (code points below {@link #DIRECT_LIMIT}) skip the hash lookups. */
    private static final int DIRECT_LIMIT = 0x500;
    private final MsdfGlyph[] directGlyphs = new MsdfGlyph[DIRECT_LIMIT];
    private final Int2FloatMap[] directKernings = new Int2FloatMap[DIRECT_LIMIT];

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
     * atlas's GL_TEXTURE_MIN/MAG_FILTER to linear filtering without
     * mipmaps. Mipmapped MSDF atlases become noticeably soft when the
     * menu is opened at a non-native GUI scale; resource reloads hide the
     * problem until the first menu render sets the old mipmapped mode.
     * Filter modes never change for an MSDF atlas after the first paint,
     * so we only set them once.
     */
    private boolean filterApplied = false;

    /** Highest quad top and lowest quad bottom of any glyph, in em above the baseline. */
    private final float planeTop;
    private final float planeBottom;

    private MsdfFont(AbstractTexture texture, FontData.AtlasData atlas, Int2ObjectMap<MsdfGlyph> glyphs,
                     Int2ObjectMap<Int2FloatMap> kernings, float planeTop, float planeBottom) {
        this.texture = texture;
        this.atlas = atlas;
        this.glyphs = glyphs;
        this.kernings = kernings;
        this.planeTop = planeTop;
        this.planeBottom = planeBottom;
        for (int c = 0; c < DIRECT_LIMIT; c++) {
            directGlyphs[c] = glyphs.get(c);
            directKernings[c] = kernings.get(c);
        }
    }

    private MsdfGlyph glyph(int c) {
        MsdfGlyph glyph = c >= 0 && c < DIRECT_LIMIT ? directGlyphs[c] : null;
        return glyph != null ? glyph : glyphs.get(c);
    }

    private Int2FloatMap kerning(int previousChar) {
        return previousChar >= 0 && previousChar < DIRECT_LIMIT ? directKernings[previousChar] : kernings.get(previousChar);
    }

    float planeTop() {
        return planeTop;
    }

    float planeBottom() {
        return planeBottom;
    }

    public FontData.AtlasData getAtlas() {
        return atlas;
    }

    public int getTextureId() {
        return texture.getGlId();
    }

    /** Resource reloads keep this wrapper but recreate/reset the GL atlas. */
    public void onResourcesReloaded() {
        filterApplied = false;
    }

    public void applyGlyphs(Matrix4f matrix, VertexConsumer consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color) {
        applyGlyphs(matrix, consumer, null, text, size, thickness, spacing, x, y, z, color);
    }

    /** Appends one quad per visible glyph to a {@link GuiBatch} run. */
    public void applyGlyphs(Matrix4f matrix, GuiBatch.Run run, String text, float size, float thickness, float spacing, float x, float y, float z, int color) {
        applyGlyphs(matrix, null, run, text, size, thickness, spacing, x, y, z, color);
    }

    private void applyGlyphs(Matrix4f matrix, VertexConsumer consumer, GuiBatch.Run run, String text, float size,
                             float thickness, float spacing, float x, float y, float z, int color) {
        if (!filterApplied) {
            texture.setFilter(true, false);
            filterApplied = true;
        }

        int previousChar = -1;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) {
                continue;
            }

            Int2FloatMap kerning = kerning(previousChar);
            if (kerning != null) {
                x += kerning.getOrDefault(c, 0.0F) * size;
            }

            float advance = run != null
                    ? glyph.emit(matrix, run, size, x, y, z, color)
                    : glyph.apply(matrix, consumer, size, x, y, z, color);
            x += advance + thickness + spacing;
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

        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) {
                continue;
            }

            Int2FloatMap kerning = kerning(previousChar);
            if (kerning != null) {
                width += kerning.getOrDefault(c, 0.0F) * size;
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

    /** Width of a rendered plain run, including inter-glyph thickness spacing but not a trailing gap. */
    public float getRenderedWidth(String text, float size, float thickness) {
        if (text == null || text.isEmpty()) return 0;
        int count = (int) text.codePoints().filter(glyphs::containsKey).count();
        return MsdfTextMetrics.renderedWidth(getWidth(text, size), count, size, thickness);
    }

    public float getRenderedWidth(String text, float size) {
        return getRenderedWidth(text, size, MsdfTextMetrics.DEFAULT_THICKNESS);
    }

    /** Offset from the x given to {@link MsdfRenderer#renderText} to the first visible glyph edge. */
    public float getInkLeft(String text, float size) {
        if (text == null || text.isEmpty()) return 0.0F;
        return glyphPadding() * size - MsdfTextMetrics.PEN_OFFSET_X;
    }

    /** Offset from the x given to {@link MsdfRenderer#renderText} to the last visible glyph edge. */
    public float getInkRight(String text, float size) {
        if (text == null || text.isEmpty()) return 0.0F;
        float spacing = MsdfTextMetrics.DEFAULT_GLYPH_SPACING * size;
        float pen = 0.0F;
        float lastPen = 0.0F;
        MsdfGlyph last = null;
        int previousChar = -1;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) continue;
            Int2FloatMap kerning = kerning(previousChar);
            if (kerning != null) pen += kerning.getOrDefault(c, 0.0F) * size;
            lastPen = pen;
            last = glyph;
            pen += glyph.getWidth(size) + spacing;
            previousChar = c;
        }
        if (last == null) return 0.0F;
        // Quads are placed at the pen without the plane-left bearing, so ink spans pen + padding .. pen + planeWidth - padding.
        return lastPen + (last.getPlaneWidth() - glyphPadding()) * size - MsdfTextMetrics.PEN_OFFSET_X;
    }

    public float getInkWidth(String text, float size) {
        return getInkRight(text, size) - getInkLeft(text, size);
    }

    /** Distance-field padding around every glyph in em; plane bounds are snapped outward by half a texel. */
    private float glyphPadding() {
        return atlas.size() > 0.0F ? (atlas.range() * 0.5F + 0.5F) / atlas.size() : 0.0F;
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

            float atlasWidth = data.atlas().width();
            float atlasHeight = data.atlas().height();
            Int2ObjectMap<MsdfGlyph> glyphs = new Int2ObjectOpenHashMap<>();
            float planeTop = 0.0F;
            float planeBottom = 0.0F;
            for (var glyphData : data.glyphs()) {
                glyphs.put(glyphData.unicode(), new MsdfGlyph(glyphData, atlasWidth, atlasHeight));
                if (glyphData.planeBounds() != null) {
                    planeTop = Math.max(planeTop, glyphData.planeBounds().top());
                    planeBottom = Math.min(planeBottom, glyphData.planeBounds().bottom());
                }
            }

            addMiddleDot(data, glyphs);

            Int2ObjectMap<Int2FloatMap> kernings = new Int2ObjectOpenHashMap<>();
            for (var kerning : data.kernings()) {
                Int2FloatMap map = kernings.get(kerning.leftChar());
                if (map == null) {
                    map = new Int2FloatOpenHashMap();
                    kernings.put(kerning.leftChar(), map);
                }
                map.put(kerning.rightChar(), kerning.advance());
            }

            return new MsdfFont(texture, data.atlas(), glyphs, kernings, planeTop, planeBottom);
        }

        /**
         * The UI separates fields with " · ", which these atlases do not contain: without this it
         * renders as a plain double space. The period is lifted to the middle of the x-height.
         */
        private static void addMiddleDot(FontData data, Int2ObjectMap<MsdfGlyph> glyphs) {
            if (glyphs.containsKey(0xB7) || !glyphs.containsKey('.')) return;
            FontData.BoundsData period = null;
            FontData.BoundsData x = null;
            for (var glyphData : data.glyphs()) {
                if (glyphData.unicode() == '.') period = glyphData.planeBounds();
                else if (glyphData.unicode() == 'x') x = glyphData.planeBounds();
            }
            if (period == null || x == null) return;
            float raise = (x.top() + x.bottom()) / 2.0F - (period.top() + period.bottom()) / 2.0F;
            glyphs.put(0xB7, glyphs.get('.').raised(raise));
        }
    }
}
