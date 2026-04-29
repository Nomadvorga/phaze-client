package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class TextSetting extends Setting {
    private String text;
    private String defaultText;
    private int min, max;

    public TextSetting(String name, String description) {
        super(name, description);
    }

    public TextSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public TextSetting setText(String text) {
        if (defaultText == null) {
            defaultText = text;
        }
        this.text = text;
        notifyChange();
        return this;
    }

    @Override
    public boolean isModified() {
        if (defaultText == null) {
            return false;
        }
        if (text == null) {
            return defaultText != null;
        }
        return !text.equals(defaultText);
    }

    @Override
    public void reset() {
        if (defaultText != null) {
            this.text = defaultText;
        }
    }
}