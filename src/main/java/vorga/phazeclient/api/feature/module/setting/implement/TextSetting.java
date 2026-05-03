package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Getter
@Setter
@Accessors(chain = true)
public class TextSetting extends Setting {
    private String text;
    private String defaultText;
    private int min = 0;
    private int max = Integer.MAX_VALUE;
    private Consumer<String> onChangeCallback;

    public TextSetting(String name, String description) {
        super(name, description);
    }

    public TextSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public TextSetting onChange(Consumer<String> callback) {
        this.onChangeCallback = callback;
        return this;
    }

    public TextSetting setText(String text) {
        if (defaultText == null) {
            defaultText = text;
        }
        boolean changed = this.text == null ? text != null : !this.text.equals(text);
        this.text = text;
        notifyChange();
        if (changed && onChangeCallback != null) {
            onChangeCallback.accept(text);
        }
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