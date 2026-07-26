package vorga.phazeclient.base.util.color;

import vorga.phazeclient.implement.menu.MenuPalette;

/**
 * HUD-facing {@link ColorPalette} derived directly from a menu
 * {@link MenuPalette}. Keeps the HUD and menu visually consistent:
 * whichever theme the user picks in the menu dropdown becomes the
 * single source of truth for HUD surface / text / accent colors too.
 *
 * <p>The menu record stores its panel colors with built-in translucency
 * (alpha around {@code 0x5C-0xB2}) because the menu composites them over
 * the game's blurred backdrop. HUD elements usually render onto the
 * game world without any such backdrop, so here we strip the alpha to
 * produce solid fills via {@link #opaque(int)}. The opaque versions
 * still carry the theme's subtle surface/accent blend, just without
 * the see-through factor that would bleed the game through.
 *
 * <p>{@link #isDark()} is inferred from the theme's {@code textPrimary}
 * brightness - dark themes use light text, light themes use dark text,
 * so a bright text color is a reliable dark-theme signal.
 */
public final class ThemeColorPalette extends ColorPalette {

    private final MenuPalette menu;

    public ThemeColorPalette(MenuPalette menu) {
        this.menu = menu;
    }

    private static int opaque(int argb) {
        return argb | 0xFF000000;
    }

    @Override
    public int mainGuiColor() {
        return opaque(menu.panelBg());
    }

    @Override
    public int guiRectColor() {
        return opaque(menu.panelChip());
    }

    @Override
    public int guiRectColor2() {
        return opaque(menu.panelContent());
    }

    @Override
    public int rectColor() {
        return opaque(menu.panelRow());
    }

    @Override
    public int rectDarkerColor() {
        return opaque(menu.panelBgSoft());
    }

    @Override
    public int textColor() {
        return menu.textPrimary();
    }

    @Override
    public int descriptionColor() {
        return menu.textMuted();
    }

    @Override
    public int outlineColor() {
        return opaque(menu.borderLight());
    }

    @Override
    public int friendColor() {
        return menu.accentGreen();
    }

    @Override
    public String getName() {
        return menu.name();
    }

    @Override
    public boolean isDark() {
        int rgb = menu.textPrimary();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int brightness = (r + g + b) / 3;
        return brightness > 128;
    }
}
