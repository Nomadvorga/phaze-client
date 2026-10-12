package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class WaveCapes extends Module {
    private static final WaveCapes INSTANCE = new WaveCapes();

    public final ValueSetting waveSpeed = new ValueSetting("Wave Speed", "Speed of cloth wave ripple")
            .range(0.5f, 3.0f).step(0.1f).setValue(1.2f);
    public final ValueSetting waveStrength = new ValueSetting("Strength", "Amplitude of cape waving curves")
            .range(0.2f, 2.5f).step(0.1f).setValue(1.0f);
    public final ValueSetting gravity = new ValueSetting("Gravity", "Downward cloth curvature factor")
            .range(0.5f, 2.5f).step(0.1f).setValue(1.0f);
    public final ValueSetting movementScale = new ValueSetting("Tilt Angle", "Base backward tilt when moving")
            .range(0.5f, 2.0f).step(0.1f).setValue(1.2f);
    public final BooleanSetting windEffect = new BooleanSetting("Wind Effect", "Natural subtle swaying when standing still")
            .setValue(true);

    private WaveCapes() {
        super("wave_capes", "Wave Capes", ModuleCategory.OTHER);
        setup(waveSpeed, waveStrength, gravity, movementScale, windEffect);
    }

    public static WaveCapes getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Dynamic fluid cloth physics simulation for player capes (16 segments)";
    }

    @Override
    public String getIcon() {
        return "elytrautility.png";
    }
}
