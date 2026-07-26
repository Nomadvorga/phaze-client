package vorga.phazeclient.base.util.color;

public class DarkPalette extends ColorPalette {

    @Override
    public int mainGuiColor() {
        return 0xFF1A1A20;
    }

    @Override
    public int guiRectColor() {
        return 0xFF1E1E24;
    }

    @Override
    public int guiRectColor2() {
        return 0xFF22222A;
    }

    @Override
    public int rectColor() {
        return 0xFF1C1C22;
    }

    @Override
    public int rectDarkerColor() {
        return 0xFF181820;
    }

    @Override
    public int textColor() {
        return 0xFFD4D4D8;
    }

    @Override
    public int descriptionColor() {
        return 0xFF878894;
    }

    @Override
    public int outlineColor() {
        return 0xFF3A3A42;
    }

    @Override
    public int friendColor() {
        return 0xFF4ADE80;
    }

    @Override
    public String getName() {
        return "Dark";
    }

    @Override
    public boolean isDark() {
        return true;
    }
}
