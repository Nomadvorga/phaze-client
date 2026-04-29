package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class BooleanSetting extends Setting {
    private boolean value;
    private Boolean defaultValue;
    private int key = GLFW.GLFW_KEY_UNKNOWN;
    private int type = 1;
    private Consumer<Boolean> onChangeCallback;

    public BooleanSetting(String name, String description) {
        super(name, description);
    }

    public BooleanSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public BooleanSetting onChange(Consumer<Boolean> callback) {
        this.onChangeCallback = callback;
        return this;
    }

    public BooleanSetting setValue(boolean value) {
        if (defaultValue == null) {
            defaultValue = value;
        }
        boolean changed = this.value != value;
        this.value = value;
        if (changed) {
            notifyChange();
            if (onChangeCallback != null) {
                onChangeCallback.accept(value);
            }
        }
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
            setValue(defaultValue);
        }
    }
}