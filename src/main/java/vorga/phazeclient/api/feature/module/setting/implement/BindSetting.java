package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class BindSetting extends Setting {
    private int key = GLFW.GLFW_KEY_UNKNOWN;
    private Integer defaultKey;
    private int type = 1;

    public BindSetting(String name, String description) {
        super(name, description);
    }

    public BindSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public BindSetting setKey(int key) {
        if (defaultKey == null) {
            defaultKey = key;
        }
        this.key = key;
        notifyChange();
        return this;
    }

    @Override
    public boolean isModified() {
        if (defaultKey == null) {
            return false;
        }
        return this.key != defaultKey;
    }

    @Override
    public void reset() {
        if (defaultKey != null) {
            this.key = defaultKey;
        }
    }
}