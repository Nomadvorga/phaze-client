package vorga.phazeclient.implement.menu.font;

import java.util.ArrayList;
import java.util.List;

/** Shared layout for drawing, hit testing and clipping, independent of Minecraft/GL. */
final class GuiTextLayout {
    // MsdfRenderer's default thickness adds .05 * .5 * size to every glyph advance.
    static final float GLYPH_SPACING = 0.025F;
    private static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    record Glyph(String text, float x, float y, int rgb, boolean bold) {
    }

    record Run(String text, float x, float y, int rgb, boolean bold) {
    }

    final List<Glyph> glyphs;
    final List<Run> runs;
    final float width;
    final float height;

    private GuiTextLayout(List<Glyph> glyphs, List<Run> runs, float width, float height) {
        this.glyphs = List.copyOf(glyphs);
        this.runs = List.copyOf(runs);
        this.width = width;
        this.height = height;
    }

    static GuiTextLayout create(String text, float size, boolean defaultBold) {
        List<Glyph> glyphs = new ArrayList<>();
        float x = 0, y = 0, width = 0;
        float lineHeight = Math.max(GuiFontAtlas.get(false).lineHeight, GuiFontAtlas.get(true).lineHeight) * size;
        int previous = -1, rgb = -1;
        boolean bold = defaultBold, previousBold = bold;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                if (i >= text.length()) break;
                char code = Character.toLowerCase(text.charAt(i++));
                int colorIndex = "0123456789abcdef".indexOf(code);
                if (colorIndex >= 0) {
                    rgb = COLORS[colorIndex];
                    bold = defaultBold;
                } else if (code == 'r') {
                    rgb = -1;
                    bold = defaultBold;
                } else if (code == 'l') {
                    bold = true;
                } else if (code == 'x' && i + 12 <= text.length()) {
                    int hex = 0;
                    boolean valid = true;
                    for (int digit = 0; digit < 6; digit++) {
                        int offset = i + digit * 2;
                        int value = Character.digit(text.charAt(offset + 1), 16);
                        if (text.charAt(offset) != '§' || value < 0) { valid = false; break; }
                        hex = (hex << 4) | value;
                    }
                    if (valid) { rgb = hex; bold = defaultBold; i += 12; }
                }
                // Other legacy style controls are nonprinting (as in FontRenderer).
                continue;
            }
            if (cp == '⏏') {
                int end = text.indexOf('⏏', i);
                if (end < 0) break;
                try { rgb = Integer.parseInt(text.substring(i, end)) & 0xFFFFFF; }
                catch (NumberFormatException ignored) { /* Malformed markers retain the previous color. */ }
                i = end + 1;
                continue;
            }
            if (cp == '\r') continue;
            if (cp == '\n' || cp == 0x2028 || cp == 0x2029) {
                width = Math.max(width, previous < 0 ? 0 : x - GLYPH_SPACING * size);
                x = 0;
                y += lineHeight;
                previous = -1;
                continue;
            }
            int repetitions = cp == '\t' ? 4 : 1;
            GuiFontAtlas atlas = GuiFontAtlas.get(bold);
            cp = atlas.supported(cp == '\t' ? ' ' : cp);
            for (int repeat = 0; repeat < repetitions; repeat++) {
                if (previous >= 0 && previousBold == bold) x += atlas.kerning(previous, cp) * size;
                glyphs.add(new Glyph(Character.toString(cp), x, y, rgb, bold));
                x += (atlas.advance(cp) + GLYPH_SPACING) * size;
                previous = cp;
                previousBold = bold;
            }
        }
        width = Math.max(width, previous < 0 ? 0 : x - GLYPH_SPACING * size);
        List<Run> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Glyph start = null;
        for (Glyph glyph : glyphs) {
            if (start != null && (glyph.rgb != start.rgb || glyph.bold != start.bold || glyph.y != start.y)) {
                runs.add(new Run(run.toString(), start.x, start.y, start.rgb, start.bold));
                run.setLength(0);
                start = null;
            }
            if (start == null) start = glyph;
            run.append(glyph.text);
        }
        if (start != null) runs.add(new Run(run.toString(), start.x, start.y, start.rgb, start.bold));
        return new GuiTextLayout(glyphs, runs, width, y + size);
    }

    static int color(int base, int rgb) {
        return rgb < 0 ? base : (base & 0xFF000000) | rgb;
    }

    static int mix(int start, int end, float t) {
        t = Math.max(0, Math.min(1, t));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (start >>> shift) & 255;
            int b = (end >>> shift) & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }
}
