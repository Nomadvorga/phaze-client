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
public class ValueSetting extends Setting {
    private float value, min, max;
    private boolean integer;
    private Float defaultValue;
    private float step = 0.1f;
    private Consumer<Float> onChangeCallback;

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

    public ValueSetting step(float step) {
        this.step = step;
        return this;
    }

    public int getInt() {
        return (int) value;
    }

    public ValueSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public ValueSetting onChange(Consumer<Float> callback) {
        this.onChangeCallback = callback;
        return this;
    }

    public ValueSetting setValue(float value) {
        if (defaultValue == null) {
            defaultValue = value;
        }
        boolean changed = this.value != value;
        this.value = value;
        notifyChange();
        if (changed && onChangeCallback != null) {
            onChangeCallback.accept(value);
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
            // Route through setValue() so notifyChange fires (auto-save
            // markDirty hook) and the onChange callback runs - the
            // previous "this.value = defaultValue" direct assignment
            // bypassed both, which meant clicking the reset icon
            // visually moved the slider but never persisted to disk
            // (so the value reverted on the next session) and never
            // recomputed dependent renderer state (HitRange's cached
            // circle angles, for example - the slider thumb snapped
            // back but the rendered circle stayed at the pre-reset
            // radius until the user dragged the slider).
            setValue(defaultValue);
        }
    }
}