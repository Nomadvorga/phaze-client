package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Getter
public class SelectSetting extends Setting {
    private String selected;
    private List<String> list;
    private Consumer<String> onChangeCallback;
    private String defaultSelected;

    public SelectSetting(String name, String description) {
        super(name, description);
    }

    public SelectSetting value(String... values) {
        List<String> list = Arrays.asList(values);

        selected = list.getFirst();
        this.list = list;

        return this;
    }

    public SelectSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public SelectSetting selected(String string) {
        if (defaultSelected == null) {
            defaultSelected = string;
        }
        this.selected = string;
        return this;
    }

    public boolean isSelected(String name) {
        return selected.equalsIgnoreCase(name);
    }

    public SelectSetting onChange(Consumer<String> callback) {
        this.onChangeCallback = callback;
        return this;
    }

    public void setSelected(String newSelected) {
        boolean changed = selected == null || !selected.equals(newSelected);

        this.selected = newSelected;

        if (changed) {
            notifyChange();
            if (onChangeCallback != null) {
                onChangeCallback.accept(newSelected);
            }
        }
    }

    public String getSelectedLocalized() {
        return selected;
    }

    public List<String> getListLocalized() {
        return list;
    }

    @Override
    public boolean isModified() {
        if (defaultSelected == null) {
            return false;
        }
        if (selected == null) {
            return defaultSelected != null;
        }
        return !selected.equals(defaultSelected);
    }

    @Override
    public void reset() {
        if (defaultSelected != null) {
            setSelected(defaultSelected);
        }
    }

}
