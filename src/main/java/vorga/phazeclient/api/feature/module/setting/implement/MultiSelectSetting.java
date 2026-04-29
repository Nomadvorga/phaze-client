package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Getter
@Setter
public class MultiSelectSetting extends Setting {
    private List<String> list, selected = new ArrayList<>();
    private List<String> defaultSelected;

    public MultiSelectSetting(String name, String description) {
        super(name, description);
    }

    public MultiSelectSetting value(String... settings) {
        list = Arrays.asList(settings);
        return this;
    }

    public MultiSelectSetting selected(String... settings) {
        if (defaultSelected == null) {
            defaultSelected = new ArrayList<>(Arrays.asList(settings));
        }
        selected = new ArrayList<>(Arrays.asList(settings));
        notifyChange();
        return this;
    }

    public MultiSelectSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public boolean isSelected(String name) {
        return selected.contains(name);
    }

    public List<String> getListLocalized() {
        return list;
    }

    public List<String> getSelectedLocalized() {
        return selected;
    }

    @Override
    public boolean isModified() {
        if (defaultSelected == null) {
            return false;
        }
        if (selected == null) {
            return defaultSelected != null;
        }
        if (selected.size() != defaultSelected.size()) {
            return true;
        }
        return !selected.containsAll(defaultSelected) || !defaultSelected.containsAll(selected);
    }

    @Override
    public void reset() {
        if (defaultSelected != null) {
            selected = new ArrayList<>(defaultSelected);
        }
    }
}
