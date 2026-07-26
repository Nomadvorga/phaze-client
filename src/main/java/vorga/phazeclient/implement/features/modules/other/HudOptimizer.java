package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;

public final class HudOptimizer extends Module {
    private static final HudOptimizer INSTANCE = new HudOptimizer();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting refreshRate = new ValueSetting("Refresh Rate", "How many times per second the cached HUD frame is regenerated. Lower = more performance, higher = smoother animations.")
            .range(10, 120)
            .setValue(30)
            .onChange(value -> BatchedHudBuffer.INSTANCE.setTargetFps(value.intValue()));

    private HudOptimizer() {
        super("hudoptimizer", "HUD Optimizer", ModuleCategory.HUD);
        refreshRate.setFullWidth(true);
        setup(generalSection, refreshRate);
        BatchedHudBuffer.INSTANCE.setTargetFps((int) refreshRate.getValue());
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
    public void deactivate() {
        BatchedHudBuffer.INSTANCE.invalidate();
    }
}
