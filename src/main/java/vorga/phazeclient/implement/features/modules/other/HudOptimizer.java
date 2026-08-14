package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.shape.implement.Blur;

public final class HudOptimizer extends Module {
    private static final HudOptimizer INSTANCE = new HudOptimizer();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting refreshRate = new ValueSetting("Refresh Rate", "How many times per second the cached HUD frame is regenerated. Lower = more performance, higher = smoother animations.")
            .range(10, 120)
            .setValue(30)
            .onChange(value -> BatchedHudBuffer.INSTANCE.setTargetFps(value.intValue()));
    public final ValueSetting blurBackgroundFps = new ValueSetting("Blur Background FPS", "How many times per second the world behind blurred HUDs is refreshed.")
            .range(10, 360)
            .step(1)
            .setValue(120)
            .onChange(value -> Blur.INSTANCE.setHudBackgroundFps(value.intValue()));
    private HudOptimizer() {
        super("hudoptimizer", "HUD Optimizer", ModuleCategory.HUD);
        refreshRate.setFullWidth(true);
        blurBackgroundFps.setFullWidth(true);
        setup(generalSection, refreshRate, blurBackgroundFps);
        BatchedHudBuffer.INSTANCE.setTargetFps((int) refreshRate.getValue());
        Blur.INSTANCE.setHudBackgroundFps(blurBackgroundFps.getInt());
    }

    public static HudOptimizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Batches all 2D HUD elements into a single FBO and refreshes at a throttled rate";
    }

    @Override
    public String getIcon() {
        return "hudoptimizer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void deactivate() {
        BatchedHudBuffer.INSTANCE.invalidate();
    }
}
