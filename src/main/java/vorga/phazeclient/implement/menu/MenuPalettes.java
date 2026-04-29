package vorga.phazeclient.implement.menu;

public final class MenuPalettes {
    public static final MenuPalette LUNAR_BLUE = preset(
            "Lunar Blue",
            0xFF24211E,
            0xFF5B8CFF,
            0xFF4A8A23,
            0xFF2FB36E,
            0xFFE9E6E2,
            0xFF9A958E
    );

    public static final MenuPalette MOCHA_GOLD = preset(
            "Mocha Gold",
            0xFF2A231D,
            0xFFD9A55A,
            0xFF7BB35E,
            0xFF52BE7B,
            0xFFF0E7DB,
            0xFFA89A88
    );

    public static final MenuPalette ROSE_QUARTZ = preset(
            "Rose Quartz",
            0xFF241E22,
            0xFFD779B8,
            0xFF7FB7FF,
            0xFF4DBF95,
            0xFFF0E3EA,
            0xFFA7949F
    );

    public static final MenuPalette EMERALD_FROST = preset(
            "Emerald Frost",
            0xFF1D2321,
            0xFF53C8A7,
            0xFF86C95C,
            0xFF37B982,
            0xFFE6F0EC,
            0xFF94A39D
    );

    public static final MenuPalette ARCTIC_MINT = preset(
            "Arctic Mint",
            0xFF1C2326,
            0xFF73C9FF,
            0xFF7FD6B8,
            0xFF4BC38F,
            0xFFE3EEF2,
            0xFF92A1A8
    );

    public static final MenuPalette CRIMSON_SILK = preset(
            "Crimson Silk",
            0xFF241C1D,
            0xFFD96874,
            0xFFB990E8,
            0xFF4DB783,
            0xFFF0E4E4,
            0xFFA69292
    );

    public static final MenuPalette SOLAR_EMBER = preset(
            "Solar Ember",
            0xFF2A1F1A,
            0xFFE58A4F,
            0xFFE0B35A,
            0xFF62C37D,
            0xFFF3E8DD,
            0xFFB29B89
    );

    public static final MenuPalette MIDNIGHT_BLOOM = preset(
            "Midnight Bloom",
            0xFF1C1B27,
            0xFF7C8CFF,
            0xFFC27BFF,
            0xFF4DC39A,
            0xFFE7E6F3,
            0xFF9894AC
    );

    public static final MenuPalette DESERT_MIRAGE = preset(
            "Desert Mirage",
            0xFF2B241D,
            0xFFC79A63,
            0xFF6CA7D9,
            0xFF58B98A,
            0xFFF1E7DA,
            0xFFAC9C88
    );

    public static final MenuPalette SAPPHIRE_STEEL = preset(
            "Sapphire Steel",
            0xFF1D2228,
            0xFF5E93E8,
            0xFF7AA6FF,
            0xFF45B88A,
            0xFFE5EBF2,
            0xFF92A0AD
    );

    public static final MenuPalette VELVET_PLUM = preset(
            "Velvet Plum",
            0xFF241D26,
            0xFFB67BE3,
            0xFFE38DB7,
            0xFF52BC8C,
            0xFFF0E6F1,
            0xFFA596A7
    );

    public static final MenuPalette FROSTED_PEACH = preset(
            "Frosted Peach",
            0xFF292220,
            0xFFE7A889,
            0xFF9CB8FF,
            0xFF60BE96,
            0xFFF4EAE2,
            0xFFB2A094
    );

    public static final MenuPalette MOSS_SMOKE = preset(
            "Moss Smoke",
            0xFF20221E,
            0xFF7FA36A,
            0xFF99B678,
            0xFF57BA84,
            0xFFE8ECE5,
            0xFF98A191
    );

    public static final MenuPalette POLAR_NIGHT = preset(
            "Polar Night",
            0xFF1A2026,
            0xFF74AEFF,
            0xFF93C9D8,
            0xFF4FC299,
            0xFFE4ECF2,
            0xFF90A1AC
    );

    private MenuPalettes() {
    }

    public static MenuPalette byName(String name) {
        if (name == null) {
            return LUNAR_BLUE;
        }

        return switch (name) {
            case "Mocha Gold" -> MOCHA_GOLD;
            case "Rose Quartz" -> ROSE_QUARTZ;
            case "Emerald Frost" -> EMERALD_FROST;
            case "Arctic Mint" -> ARCTIC_MINT;
            case "Crimson Silk" -> CRIMSON_SILK;
            case "Solar Ember" -> SOLAR_EMBER;
            case "Midnight Bloom" -> MIDNIGHT_BLOOM;
            case "Desert Mirage" -> DESERT_MIRAGE;
            case "Sapphire Steel" -> SAPPHIRE_STEEL;
            case "Velvet Plum" -> VELVET_PLUM;
            case "Frosted Peach" -> FROSTED_PEACH;
            case "Moss Smoke" -> MOSS_SMOKE;
            case "Polar Night" -> POLAR_NIGHT;
            default -> LUNAR_BLUE;
        };
    }

    public static MenuPalette custom(int surface, int accent, int secondaryAccent, int successAccent, int textPrimary, int textMuted) {
        return preset("Custom", opaque(surface), opaque(accent), opaque(secondaryAccent), opaque(successAccent), opaque(textPrimary), opaque(textMuted));
    }

    private static MenuPalette preset(String name, int surface, int accent, int secondaryAccent, int successAccent, int textPrimary, int textMuted) {
        int deepSurface = blend(surface, 0xFF000000, 0.22F);
        int raisedSurface = blend(surface, 0xFFFFFFFF, 0.035F);
        int header = blend(surface, accent, 0.07F);
        int chipSurface = blend(surface, 0xFFFFFFFF, 0.05F);
        int rowSurface = blend(surface, 0xFFFFFFFF, 0.03F);
        int activeRow = blend(surface, accent, 0.16F);
        int contentSurface = blend(deepSurface, accent, 0.025F);
        int cardSurface = blend(surface, 0xFFFFFFFF, 0.04F);
        int optionSurface = blend(surface, 0xFFFFFFFF, 0.11F);
        int disabledSurface = blend(surface, textMuted, 0.18F);
        int border = withAlpha(blend(surface, textMuted, 0.34F), 0x80);
        int borderLight = withAlpha(blend(surface, textPrimary, 0.42F), 0xAA);

        return new MenuPalette(
                name,
                withAlpha(blend(surface, accent, 0.04F), 0x9A),
                withAlpha(deepSurface, 0x7C),
                withAlpha(header, 0x78),
                withAlpha(blend(surface, accent, 0.035F), 0x76),
                withAlpha(contentSurface, 0x5C),
                withAlpha(chipSurface, 0xA1),
                withAlpha(rowSurface, 0xA5),
                withAlpha(activeRow, 0xB2),
                withAlpha(cardSurface, 0xAF),
                withAlpha(raisedSurface, 0xA5),
                withAlpha(optionSurface, 0xA8),
                withAlpha(disabledSurface, 0x98),
                border,
                borderLight,
                opaque(textPrimary),
                opaque(textMuted),
                opaque(accent),
                opaque(secondaryAccent),
                opaque(successAccent)
        );
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int blend(int from, int to, float amount) {
        amount = Math.max(0.0F, Math.min(1.0F, amount));

        int start = opaque(from);
        int end = opaque(to);

        int af = (start >>> 24) & 0xFF;
        int rf = (start >>> 16) & 0xFF;
        int gf = (start >>> 8) & 0xFF;
        int bf = start & 0xFF;

        int at = (end >>> 24) & 0xFF;
        int rt = (end >>> 16) & 0xFF;
        int gt = (end >>> 8) & 0xFF;
        int bt = end & 0xFF;

        int a = (int) (af + (at - af) * amount);
        int r = (int) (rf + (rt - rf) * amount);
        int g = (int) (gf + (gt - gf) * amount);
        int b = (int) (bf + (bt - bf) * amount);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }
}
