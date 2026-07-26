package vorga.phazeclient.base.util.color;

public class CustomPalette extends ColorPalette {

    private final int mainGui;
    private final int guiRect;
    private final int guiRect2;
    private final int rect;
    private final int rectDarker;
    private final int text;
    private final int description;

    public CustomPalette(int mainGui, int guiRect, int guiRect2, int rect, int rectDarker, int text, int description) {
        this.mainGui = mainGui;
        this.guiRect = guiRect;
        this.guiRect2 = guiRect2;
        this.rect = rect;
        this.rectDarker = rectDarker;
        this.text = text;
        this.description = description;
    }

    @Override
    public int mainGuiColor() {
        return mainGui;
    }

    @Override
    public int guiRectColor() {
        return guiRect;
    }

    @Override
    public int guiRectColor2() {
        return guiRect2;
    }

    @Override
    public int rectColor() {
        return rect;
    }

    @Override
    public int rectDarkerColor() {
        return rectDarker;
    }

    @Override
    public int textColor() {
        return text;
    }

    @Override
    public int descriptionColor() {
        return description;
    }

    @Override
    public int outlineColor() {
        return isDark() ? 0xFF3A3A42 : 0xFFCCCCCC;
    }

    @Override
    public int friendColor() {
        return isDark() ? 0xFF4ADE80 : 0xFF22C55E;
    }

    @Override
    public String getName() {
        return "Custom";
    }

    @Override
    public boolean isDark() {
        int r = (mainGui >> 16) & 0xFF;
        int g = (mainGui >> 8) & 0xFF;
        int b = mainGui & 0xFF;
        int brightness = (r + g + b) / 3;
        return brightness < 128;
    }
}
