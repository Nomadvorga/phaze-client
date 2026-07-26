package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class ButtonSetting extends Setting {
    private Runnable runnable;
    private String buttonName;

    public ButtonSetting(String name, String description) {
        super(name, description);
    }

    public ButtonSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void reset() {
    }
}