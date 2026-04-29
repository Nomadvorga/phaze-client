package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetting extends Setting {
    private float value, min, max;
    private boolean integer;
    private Float defaultValue;

    public ValueSetting(String name, String description) {
        super(name, description);
    }

    public ValueSetting range(float min, float max) {
        this.min = min;
        this.max = max;
        return this;
    }

    public ValueSetting range(int min, int max) {
        this.min = min;
        this.max = max;
        this.integer = true;
        return this;
    }

    public int getInt() {
        return (int) value;
    }

    public ValueSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public ValueSetting setValue(float value) {
        if (defaultValue == null) {
            defaultValue = value;
        }
        this.value = value;
        notifyChange();
        return this;
    }

    @Override
    public boolean isModified() {
        if (defaultValue == null) {
            return false;
        }
        return this.value != defaultValue;
    }

    @Override
    public void reset() {
        if (defaultValue != null) {
            this.value = defaultValue;
        }
    }
}