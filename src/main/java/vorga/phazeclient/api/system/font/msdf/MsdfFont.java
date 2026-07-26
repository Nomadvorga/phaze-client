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

    private MsdfFont(AbstractTexture texture, FontData.AtlasData atlas, Int2ObjectMap<MsdfGlyph> glyphs, Int2ObjectMap<Int2FloatMap> kernings) {
        this.texture = texture;
        this.atlas = atlas;
        this.glyphs = glyphs;
        this.kernings = kernings;
    }

    public FontData.AtlasData getAtlas() {
        return atlas;
    }

    // 1.21.11: getTextureId() removed. AbstractTexture.getGlId() is gone -
    // the raw GL name now lives behind GlTexture, i.e. the OpenGL backend
    // only, and nothing in Phaze binds this atlas imperatively any more.
    // MsdfRenderer samples it through getTextureView() below, so the int
    // handle had no remaining caller.

    /**
     * Texture view for the atlas, for binding as a shader sampler.
     *
     * <p>1.21.11 dropped imperative texture binding; a sampler is bound on
     * the render pass from a {@code GpuTextureView}, which
     * {@code AbstractTexture} exposes directly.
     */
    public com.mojang.blaze3d.textures.GpuTextureView getTextureView() {
        return texture.getGlTextureView();
    }

    /**
     * @param paramsElement vertex attribute carrying (range, thickness,
     *                      smoothness) - the 1.21.11 stand-in for the loose
     *                      uniforms the MSDF shader used to read. Takes a
     *                      BufferBuilder rather than a VertexConsumer
     *                      because writing a custom attribute needs
     *                      {@code beginElement}.
     */
    // 1.21.11: the per-batch texture.setFilter(true, true) that used to sit
    // at the top of this method is gone. Filtering is a sampler property
    // now, not texture state, so it is set once in Builder.build() and the
    // `filterApplied` latch that guarded the repeated glTexParameteri calls
    // has no purpose any more.
    public void applyGlyphs(Matrix4f matrix, net.minecraft.client.render.BufferBuilder consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color,
                            com.mojang.blaze3d.vertex.VertexFormatElement paramsElement, float range, float smoothness) {
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

            x += glyph.apply(matrix, consumer, size, x, y, z, color, paramsElement, range, thickness, smoothness) + thickness + spacing;
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

            // 1.21.11: setFilter(bilinear, mipmap) and recordRenderCall are
            // both gone. Filtering is now a sampler property, so the old
            // setFilter(true, false) becomes a cached sampler assignment.
            // SamplerCache.get(FilterMode) is (CLAMP_TO_EDGE, CLAMP_TO_EDGE,
            // LINEAR min, LINEAR mag, no mipmap) - verified from bytecode -
            // which is exactly what an MSDF atlas needs: bilinear taps, no
            // mip chain, and clamping so edge glyphs cannot bleed across the
            // atlas seam. Samplers are owned by the cache and shared, so this
            // neither leaks nor needs closing. Assigned inline rather than
            // deferred: builders run lazily from MsdfFonts during rendering,
            // i.e. already on the render thread. The off-thread branch is a
            // no-op rather than a crash because this assignment is only a
            // default for other bind paths - MsdfRenderer's own draw goes
            // through GpuDraw, which binds SamplerCache.get(LINEAR) on the
            // render pass itself and never consults texture.sampler.
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
