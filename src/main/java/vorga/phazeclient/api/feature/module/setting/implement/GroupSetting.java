package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class GroupSetting extends Setting {
    private boolean value;
    private Boolean defaultValue;
    private boolean checkbox = true;
    private List<Setting> subSettings = new ArrayList<>();
    private boolean colorPickerStyleWindow = false;
    private float popupWidth = 148.0F;
    private float popupMaxHeight = 190.0F;

    public GroupSetting(String name, String description) {
        super(name, description);
    }

    public GroupSetting(String name, String description, boolean checkbox) {
        super(name, description);
        this.checkbox = checkbox;
    }

    public GroupSetting settings(Setting... setting) {
        subSettings.addAll(Arrays.asList(setting));
        return this;
    }

    public GroupSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public GroupSetting colorPickerStyleWindow(boolean colorPickerStyleWindow) {
        this.colorPickerStyleWindow = colorPickerStyleWindow;
        return this;
    }

    public GroupSetting popupWidth(float popupWidth) {
        this.popupWidth = popupWidth;
        return this;
    }

    public GroupSetting popupMaxHeight(float popupMaxHeight) {
        this.popupMaxHeight = popupMaxHeight;
        return this;
    }

    @Override
    public String getName() {
        return getNameKey();
    }

    @Override
    public String getDescription() {
        String descriptionKey = getDescriptionKey();
        if (descriptionKey == null || descriptionKey.isEmpty()) {
            return "";
        }
        return descriptionKey;
    }

    @Override
    public String getLocalizedName() {
        return super.getName();
    }

    @Override
    public String getLocalizedDescription() {
        return super.getDescription();
    }

    public GroupSetting setValue(boolean value) {
        if (defaultValue == null) {
            defaultValue = value;
        }
        this.value = value;
        notifyChange();
        return this;
    }

    @Override
    public boolean isModified() {
        if (defaultValue != null && this.value != defaultValue) {
            return true;
        }

        for (Setting subSetting : subSettings) {
            if (subSetting.isModified()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void reset() {
        if (defaultValue != null) {
            // setValue handles notifyChange + the dirty-flag plumbing.
            setValue(defaultValue);
        }

        for (Setting subSetting : subSettings) {
            subSetting.reset();
        }
    }
}
