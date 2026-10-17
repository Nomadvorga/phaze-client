package vorga.phazeclient.implement.menu.font;

import com.google.gson.Gson;
import vorga.phazeclient.api.system.font.msdf.FontData;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** CPU-only regression checks: run main with main/test classes, resources and Gson on the classpath. */
public final class GuiFontChecks {
    public static void main(String[] args) throws Exception {
        reportedLabelsAndCyrillic();
        rendererAdvanceParity();
        formattingAndAlpha();
        multilineAndEmpty();
        unsupportedCharacters();
        gradients();
        menuFontBoundary();
        System.out.println("GUI MSDF font checks PASSED");
    }

    private static void reportedLabelsAndCyrillic() {
        for (boolean bold : new boolean[]{false, true}) {
            for (String label : new String[]{"HUD text color", "Show cosmetics on other players",
                    "Цвет текста HUD", "Показывать косметику других игроков", "ЁёЙй"}) {
                GuiTextLayout layout = GuiTextLayout.create(label, 7, bold);
                check(layout.width > 0, "label has measurable width");
                check(layout.runs.size() == 1, "plain labels are one atlas submission");
                check(layout.runs.getFirst().text().equals(label), "label has no missing or substituted glyphs");
                for (int cp : label.codePoints().toArray()) {
                    check(GuiFontAtlas.get(bold).contains(cp), "bundled atlas supports label character " + cp);
                }
                near(GuiTextLayout.create(label, 14, bold).width, layout.width * 2, "font scale remains linear");
            }
        }
    }

    private static void rendererAdvanceParity() throws Exception {
        // Independently simulate MsdfFont.applyGlyphs using the real atlas metadata, including kerning.
        for (boolean bold : new boolean[]{false, true}) {
            String name = bold ? "bold" : "medium";
            try (var input = GuiFontChecks.class.getResourceAsStream("/assets/minecraft/msdf/" + name + ".json")) {
                check(input != null, "atlas metadata exists");
                FontData data = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), FontData.class);
                for (float size : new float[]{5.5F, 6.5F, 7, 8}) {
                    String text = "AV 123 Цвет HUD";
                    GuiTextLayout layout = GuiTextLayout.create(text, size, bold);
                    float pen = 0;
                    int previous = -1;
                    for (int i = 0; i < text.length(); i++) {
                        int cp = text.charAt(i);
                        for (FontData.KerningData pair : data.kernings()) {
                            if (pair.leftChar() == previous && pair.rightChar() == cp) pen += pair.advance() * size;
                        }
                        near(layout.glyphs.get(i).x(), pen, "glyph origin agrees with renderer");
                        float advance = 0;
                        for (FontData.GlyphData glyph : data.glyphs()) {
                            if (glyph.unicode() == cp) advance = glyph.advance();
                        }
                        pen += (advance + 0.025F) * size;
                        previous = cp;
                    }
                    near(layout.width, pen - 0.025F * size, "clip width includes rendered spacing without trailing advance");
                    GuiTextLayout formatted = GuiTextLayout.create("A§cV§r 123 Цвет HUD", size, bold);
                    near(formatted.width, layout.width, "color boundaries do not disturb kerning or width");
                    for (int i = 0; i < layout.glyphs.size(); i++) {
                        near(formatted.glyphs.get(i).x(), layout.glyphs.get(i).x(), "formatted glyph placement stays identical");
                    }
                }
            }
        }
    }

    private static void formattingAndAlpha() {
        GuiTextLayout plain = GuiTextLayout.create("ABC", 7, false);
        GuiTextLayout colored = GuiTextLayout.create("A§cB§rC", 7, false);
        near(colored.width, plain.width, "format controls have zero width");
        check(colored.runs.size() == 3, "colored text splits only at style boundaries");
        check(colored.glyphs.get(1).rgb() == 0xFF5555, "Minecraft color code");
        check(colored.glyphs.get(2).rgb() == -1, "reset restores caller color");
        check(GuiTextLayout.color(0x35112233, 0xFF5555) == 0x35FF5555, "format colors preserve caller fade alpha");
        check(GuiTextLayout.color(0x00112233, 0xFFFFFF) == 0x00FFFFFF, "transparent formatted text stays transparent");
        near(GuiTextLayout.create("A⏏16711680⏏B§rC", 7, false).width, plain.width, "decimal RGB markers are not glyphs");
        check(GuiTextLayout.create("⏏16711680⏏A", 7, false).glyphs.getFirst().rgb() == 0xFF0000, "decimal RGB color");
        check(GuiTextLayout.create("§x§1§2§a§b§0§fA", 7, false).glyphs.getFirst().rgb() == 0x12AB0F, "hex RGB color");
        GuiTextLayout weights = GuiTextLayout.create("A§lB§rC", 7, false);
        check(!weights.glyphs.get(0).bold() && weights.glyphs.get(1).bold() && !weights.glyphs.get(2).bold(), "bold and reset choose correct atlas");
        check(GuiTextLayout.create("A§k§n§o§mB", 7, false).glyphs.size() == 2, "legacy nonprinting style controls stay nonprinting");
        near(GuiTextLayout.create("AB§", 7, false).width, GuiTextLayout.create("AB", 7, false).width, "dangling format marker is harmless");
        check(GuiTextLayout.create("⏏bad⏏A", 7, false).glyphs.getFirst().rgb() == -1, "invalid decimal marker is harmless");
    }

    private static void multilineAndEmpty() {
        GuiTextLayout first = GuiTextLayout.create("Wide label", 7, true);
        GuiTextLayout lines = GuiTextLayout.create("Wide label\nx\n", 7, true);
        near(lines.width, first.width, "multiline width is longest line");
        near(lines.glyphs.getLast().x(), 0, "newline resets pen X");
        near(lines.height, 7 + 2 * Math.max(GuiFontAtlas.get(false).lineHeight, GuiFontAtlas.get(true).lineHeight) * 7, "trailing empty line has height");
        near(GuiTextLayout.create("", 7, true).width, 0, "empty label width");
        near(GuiTextLayout.create("", 7, true).height, 7, "empty label retains stable line box");
        near(GuiTextLayout.create("A\r\nB", 7, true).height, GuiTextLayout.create("A\nB", 7, true).height, "CRLF is one newline");
        near(GuiTextLayout.create("A\tB", 7, true).width, GuiTextLayout.create("A    B", 7, true).width, "tabs have matching draw and measurement");
    }

    private static void unsupportedCharacters() {
        GuiTextLayout fallback = GuiTextLayout.create("A\uD83D\uDE00B", 7, false);
        check(fallback.glyphs.size() == 3, "unsupported supplementary character becomes one replacement glyph");
        near(fallback.width, GuiTextLayout.create("A?B", 7, false).width, "replacement width matches drawing");
    }

    private static void gradients() {
        check(GuiTextLayout.mix(0x00102030, 0xFFABCDEF, 0) == 0x00102030, "gradient start");
        check(GuiTextLayout.mix(0x00102030, 0xFFABCDEF, 1) == 0xFFABCDEF, "gradient end");
        check(GuiTextLayout.mix(0, 0xFFFFFFFF, 0.5F) == 0x80808080, "gradient includes alpha");
        check(GuiTextLayout.create("X", 7, true).glyphs.size() == 1, "single-glyph gradient does not need a zero denominator");
    }

    private static void menuFontBoundary() throws Exception {
        Path menu = Path.of("src/main/java/vorga/phazeclient/implement/menu");
        Pattern legacy = Pattern.compile("(?<![\\w])Fonts\\.(getSize|create)\\s*\\(|new\\s+FontRenderer\\s*\\(");
        try (var paths = Files.walk(menu)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                check(!legacy.matcher(Files.readString(path)).find(), "custom GUI must not request legacy glyph fonts: " + path);
            }
        }
        String picker = Files.readString(menu.resolve("ItemPickerScreen.java"));
        check(picker.contains("Math.round(slotY + 1.0F), \"\")"), "vanilla item overlay must suppress bitmap counts");
        check(picker.contains("countFont.drawString"), "item counts use the GUI adapter");
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
