package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Cinematic-style camera smoothing. Filters the per-tick mouse
 * delta through an exponential-decay low-pass before vanilla feeds
 * it to {@code ClientPlayerEntity.changeLookDirection}, so quick
 * mouse flicks become smooth glides instead of instant snaps.
 *
 * <h3>Why exponential decay vs vanilla cinematic toggle</h3>
 * Vanilla's {@code Smooth Camera} option uses a simple per-frame
 * average over a fixed-size sample window, which produces a hard
 * "rubber-band" feel when the user stops moving the mouse - the
 * window's tail keeps replaying old samples for ~5 frames after
 * release. Our path uses a one-pole IIR with user-tunable
 * {@code smoothness} parameter so the response curves
 * monotonically toward the target without oscillation, and the
 * smoothness directly controls how quickly the filter catches up
 * (0 = instant / disabled, 1 = no movement ever).
 *
 * <h3>Mixin coupling</h3>
 * The actual mouse-delta substitution happens in
 * {@code MouseSmoothCameraMixin.@ModifyArg} on
 * {@code Mouse.updateMouse}'s {@code changeLookDirection(D, D)} call.
 * That mixin reads {@link #smoothness} and {@link #isEnabled()},
 * accumulates the smoothed delta in a static field, and returns
 * the smoothed value to vanilla.
 */
public final class SmoothCamera extends Module {
    private static final SmoothCamera INSTANCE = new SmoothCamera();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting smoothness = new ValueSetting(
            "Smoothness",
            "Higher = more cinematic glide, lower = closer to raw mouse input"
    ).range(0.05f, 0.95f).step(0.01f).setValue(0.50f);

    private SmoothCamera() {
        super("smooth_camera", "Smooth Camera", ModuleCategory.OTHER);
        smoothness.setFullWidth(true);
        setup(generalSection, smoothness);
    }

    public static SmoothCamera getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Cinematic-style camera smoothing for mouse movement";
    }

    @Override
    public String getIcon() {
        return "smooth_camera.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Per-pole filter coefficient. {@code smoothness=0.50} maps to
     * 0.50, meaning the smoothed value moves halfway to the target
     * each frame - a comfortable middle ground. Higher values
     * approach the IIR's unstable boundary (smoothness=1 freezes
     * input completely), so the slider caps at 0.95.
     */
    public float getSmoothness() {
        return smoothness.getValue();
    }
}
