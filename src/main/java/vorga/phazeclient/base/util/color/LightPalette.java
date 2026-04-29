package vorga.phazeclient.base.util.color;

public class LightPalette extends ColorPalette {

    @Override
    public int mainGuiColor() {
        return 0xFFF5F5F7;
    }

    @Override
    public int guiRectColor() {
        return 0xFFFFFFFF;
    }

    @Override
    public int guiRectColor2() {
        return 0xFFE8E8EA;
    }

    @Override
    public int rectColor() {
        return 0xFFC7C7CC;
    }

    @Override
    public int rectDarkerColor() {
        return 0xFFC7C7CC;
    }

    @Override
    public int textColor() {
        return 0xFF1D1D1F;
    }

    @Override
    public int descriptionColor() {
        return 0xFF86868B;
    }

    @Override
    public int outlineColor() {
        return 0xFFD1D1D6;
    }

    @Override
    public int friendColor() {
        return 0xFF22C55E;
    }

    @Override
    public String getName() {
        return "Light";
    }

    @Override
    public boolean isDark() {
        return false;
    }
}
