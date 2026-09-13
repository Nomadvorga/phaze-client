package vorga.phazeclient.base.util.color;

import vorga.phazeclient.implement.menu.MenuPalette;

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
