package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;

public final class HudOptimizer extends Module {
    private static final HudOptimizer INSTANCE = new HudOptimizer();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting refreshRate = new ValueSetting("Refresh Rate", "How many times per second the HUD content (text and backgrounds) is regenerated. Lower = more performance, higher = smoother updates.")
            .range(10, 120)
            .setValue(30)
            .onChange(value -> BatchedHudBuffer.INSTANCE.setTargetFps(value.intValue()));

    public final ValueSetting blurRefreshRate = new ValueSetting("Refresh Rate With Background Blur", "Refresh rate of the blurred HUD background while blur is enabled. The regular Refresh Rate keeps governing the text.")
            .range(10, 360)
            .setValue(60);

    private HudOptimizer() {
        super("hudoptimizer", "HUD Optimizer", ModuleCategory.HUD);
        refreshRate.setFullWidth(true);
        blurRefreshRate.setFullWidth(true);
        setup(generalSection, refreshRate, blurRefreshRate);
        BatchedHudBuffer.INSTANCE.setTargetFps((int) refreshRate.getValue());
    }

    public static HudOptimizer getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isDefaultStateOn() {
        return true;
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
    public void deactivate() {
        BatchedHudBuffer.INSTANCE.invalidate();
    }
}
