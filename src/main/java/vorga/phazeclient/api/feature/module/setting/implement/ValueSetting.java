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
    /** Magnet target and reach; radius 0 disables it. See {@link #snapTo}. */
    private float snapTarget;
    private float snapRadius;
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

    /**
     * Makes the slider magnetic around one value, the way a design
     * tool snaps to a guide.
     *
     * <p>Scale sliders are the motivating case: getting a HUD back to
     * exactly 1.0 by hand means chasing a single pixel of travel, and
     * landing on 0.98 instead looks subtly wrong forever. With a snap
     * of {@code snapTo(1.0F, 0.08F)} the thumb latches onto 1.0 as
     * soon as the drag comes within 0.08 of it and stays there until
     * the pointer pulls clearly past - so "roughly the middle" is
     * enough to hit the default exactly.
     *
     * @param target value to latch onto
     * @param radius how far either side of it the pull reaches, in
     *               the setting's own units
     */
    public ValueSetting snapTo(float target, float radius) {
        this.snapTarget = target;
        this.snapRadius = Math.abs(radius);
        return this;
    }

    /** Convenience: the usual "snap to the default" case. */
    public ValueSetting snapToDefault(float radius) {
        return snapTo(defaultValue != null ? defaultValue : value, radius);
    }

    /**
     * Applies the magnet to a raw, drag-derived value. Returns the
     * input unchanged when no snap is configured or the value is
     * outside the magnet's reach.
     */
    public float applySnap(float raw) {
        if (snapRadius <= 0.0F) {
            return raw;
        }
        return Math.abs(raw - snapTarget) <= snapRadius ? snapTarget : raw;
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