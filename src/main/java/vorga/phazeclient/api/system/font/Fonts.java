package vorga.phazeclient.api.system.font;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.awt.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Fonts {

    public static FontRenderer create(float size, String name) {
        float derivedSize = size / 2f;
        for (String path : List.of(
                "assets/minecraft/fonts/" + name + ".otf",
                "assets/minecraft/fonts/" + name + ".ttf",
                "assets/minecraft/font/" + name + ".otf",
                "assets/minecraft/font/" + name + ".ttf"
        )) {
            try (InputStream inputStream = Fonts.class.getClassLoader().getResourceAsStream(path)) {
                if (inputStream == null) {
                    continue;
                }

                Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream)
                        .deriveFont(Font.PLAIN, derivedSize);
                return new FontRenderer(font, derivedSize);
            } catch (Throwable ignored) {
            }
        }

        return new FontRenderer(createFallbackFont(name, derivedSize), derivedSize);
    }

    private static Font createFallbackFont(String name, float size) {
        String family = switch (name) {
            case "jetbrains_mono", "jetbrains_mono_bold" -> Font.MONOSPACED;
            case "inter", "inter_bold", "sfpromedium", "sfprosemibold" -> Font.SANS_SERIF;
            default -> Font.DIALOG;
        };
        int style = name.contains("bold") || "sfprosemibold".equals(name) ? Font.BOLD : Font.PLAIN;
        return new Font(family, style, Math.max(1, Math.round(size)));
    }

    private static final Map<FontKey, FontRenderer> fontCache = new HashMap<>();

    public static void init() {
        for (Type type : Type.values()) {
            for (int size = 4; size <= 32; size++) {
                fontCache.put(new FontKey(size, type), create(size, type.getType()));
            }
        }
    }

    public static FontRenderer getSize(int size) {
        return getSize(size, Type.INTER_BOLD);
    }

    public static FontRenderer getSize(int size, Type type) {
        return fontCache.computeIfAbsent(new FontKey(size, type), k -> create(size, type.getType()));
    }

    @Getter
    @RequiredArgsConstructor
    public enum Type {
        SF_DEFAULT("sfpromedium"),
        SF_BOLD("sfprosemibold"),
        INTER_DEFAULT("inter"),
        INTER_BOLD("inter_bold"),
        ICO("ico"),
        JET_DEFAULT("jetbrains_mono"),
        JET_BOLD("jetbrains_mono_bold");

        private final String type;
    }

    private record FontKey(int size, Type type) {
    }
}
