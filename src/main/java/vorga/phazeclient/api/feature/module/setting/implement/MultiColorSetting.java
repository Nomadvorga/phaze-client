package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
@Setter
public class MultiColorSetting extends Setting {
    private final List<ColorSetting> colors;
    private int selectedColorIndex = 0;

    public MultiColorSetting(String name, String description) {
        super(name, description);
        this.colors = new ArrayList<>();
    }

    public MultiColorSetting colors(String... colorNames) {
        colors.clear();
        for (int i = 0; i < colorNames.length; i++) {
            colors.add(new ColorSetting(colorNames[i], "Color " + (i + 1) + " for " + getName()));
        }
        return this;
    }

    public MultiColorSetting defaultColors(int... defaultColors) {
        for (int i = 0; i < Math.min(colors.size(), defaultColors.length); i++) {
            colors.get(i).setColor(defaultColors[i]);
        }
        return this;
    }

    public MultiColorSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        for (ColorSetting color : colors) {
            color.setVisible(visible);
        }
        return this;
    }

    public ColorSetting getColor1() {
        return !colors.isEmpty() ? colors.getFirst() : null;
    }

    public ColorSetting getColor2() {
        return colors.size() > 1 ? colors.get(1) : null;
    }

    public ColorSetting getColor3() {
        return colors.size() > 2 ? colors.get(2) : null;
    }

    public ColorSetting getColor(int index) {
        if (index >= 0 && index < colors.size()) {
            return colors.get(index);
        }
        return null;
    }

    public int[] getColorValues() {
        return colors.stream().mapToInt(ColorSetting::getColor).toArray();
    }

    public int getColorCount() {
        return colors.size();
    }

    public List<ColorSetting> getAllColors() {
        return new ArrayList<>(colors);
    }

    @Override
    public boolean isModified() {
        for (ColorSetting color : colors) {
            if (color.isModified()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset() {
        for (ColorSetting color : colors) {
            color.reset();
        }
    }
}