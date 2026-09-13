package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

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

    public float getSmoothness() {
        return smoothness.getValue();
    }
}
