package vorga.phazeclient.base.util.color;

public class SoftLightPalette extends ColorPalette {

    @Override
    public int mainGuiColor() {
        return 0xFFFAF9F5;
    }

    @Override
    public int guiRectColor() {
        return 0xFFFFFFFF;
    }

    @Override
    public int guiRectColor2() {
        return 0xFFF0EEE6;
    }

    @Override
    public int rectColor() {
        return 0xFFDAD9D4;
    }

    @Override
    public int rectDarkerColor() {
        return 0xFFDAD9D4;
    }

    @Override
    public int textColor() {
        return 0xFF30302F;
    }

    @Override
    public int descriptionColor() {
        return 0xFF615F57;
    }

    @Override
    public int outlineColor() {
        return 0xFFDAD9D4;
    }

    @Override
    public int friendColor() {
        return 0xFF22C55E;
    }

    @Override
    public String getName() {
        return "Soft Light";
    }

    @Override
    public boolean isDark() {
        return false;
    }
}
