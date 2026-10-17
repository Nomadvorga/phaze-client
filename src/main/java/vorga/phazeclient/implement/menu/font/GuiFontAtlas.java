package vorga.phazeclient.implement.menu.font;

import com.google.gson.Gson;
import vorga.phazeclient.api.system.font.msdf.FontData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** CPU-only atlas metrics, loaded once; measuring a label never creates a GL texture/glyph map. */
final class GuiFontAtlas {
    private final Map<Integer, Float> advances = new HashMap<>();
    private final Map<Long, Float> kernings = new HashMap<>();
    final float lineHeight;

    private GuiFontAtlas(String name) {
        String path = "/assets/minecraft/msdf/" + name + ".json";
        try (InputStream stream = GuiFontAtlas.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing GUI atlas: " + path);
            FontData data = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), FontData.class);
            lineHeight = data.metrics().lineHeight();
            for (FontData.GlyphData glyph : data.glyphs()) advances.put(glyph.unicode(), glyph.advance());
            if (data.kernings() != null) {
                for (FontData.KerningData kerning : data.kernings()) {
                    kernings.put(pair(kerning.leftChar(), kerning.rightChar()), kerning.advance());
                }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read GUI atlas: " + path, exception);
        }
    }

    static GuiFontAtlas get(boolean bold) {
        return bold ? Bold.INSTANCE : Medium.INSTANCE;
    }

    boolean contains(int codepoint) {
        return advances.containsKey(codepoint);
    }

    int supported(int codepoint) {
        return contains(codepoint) ? codepoint : '?';
    }

    float advance(int codepoint) {
        return advances.getOrDefault(codepoint, 0.0F);
    }

    float kerning(int left, int right) {
        return kernings.getOrDefault(pair(left, right), 0.0F);
    }

    private static long pair(int left, int right) {
        return ((long) left << 32) | (right & 0xFFFFFFFFL);
    }

    private static final class Bold {
        private static final GuiFontAtlas INSTANCE = new GuiFontAtlas("bold");
    }

    private static final class Medium {
        private static final GuiFontAtlas INSTANCE = new GuiFontAtlas("medium");
    }
}
