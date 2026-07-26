package vorga.phazeclient.base.util.color;

public class SoftDarkPalette extends ColorPalette {

    @Override
    public int mainGuiColor() {
        return 0xFF262624;
    }

    @Override
    public int guiRectColor() {
        return 0xFF30302E;
    }

    @Override
    public int guiRectColor2() {
        return 0xFF1F1E1D;
    }

    @Override
    public int rectColor() {
        return 0xFF41413E;
    }

    @Override
    public int rectDarkerColor() {
        return 0xFF41413E;
    }

    @Override
    public int textColor() {
        return 0xFF98968E;
    }

    @Override
    public int descriptionColor() {
        return 0xFF98968E;
    }

    @Override
    public int outlineColor() {
        return 0xFF41413E;
    }

    @Override
    public int friendColor() {
        return 0xFF4ADE80;
    }

    @Override
    public String getName() {
        return "Soft Dark";
    }

    @Override
    public boolean isDark() {
        return true;
    }
}
