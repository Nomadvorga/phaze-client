package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class WaveCapes extends Module {
    private static final WaveCapes INSTANCE = new WaveCapes();

    public final SelectSetting mode = new SelectSetting("Physics Mode", "Cape cloth physics model")
            .value("Smooth Waves", "Physics 3D").selected("Smooth Waves");

    public final ValueSetting waveSpeed = new ValueSetting("Wave Speed", "Speed of cloth wave ripple")
            .range(0.5f, 3.0f).step(0.1f).setValue(1.2f);
    public final ValueSetting waveStrength = new ValueSetting("Wave Strength", "Amplitude of cape waving curves")
            .range(0.2f, 2.5f).step(0.1f).setValue(1.0f);
    public final ValueSetting gravity = new ValueSetting("Gravity", "Simulation gravity pull")
            .range(5.0f, 50.0f).step(1.0f).setValue(25.0f);
    public final ValueSetting heightMultiplier = new ValueSetting("Height Drag", "Vertical movement drag multiplier")
            .range(1.0f, 10.0f).step(0.5f).setValue(6.0f);
    public final ValueSetting strafeMultiplier = new ValueSetting("Strafe Drag", "Horizontal strafing swing multiplier")
            .range(0.5f, 5.0f).step(0.5f).setValue(2.0f);
    public final BooleanSetting windEffect = new BooleanSetting("Wind Ripple", "Travelling wave ripples along the cloth")
            .setValue(true);

    private WaveCapes() {
        super("wave_capes", "Wave Capes", ModuleCategory.OTHER);
        setup(mode, waveSpeed, waveStrength, gravity, heightMultiplier, strafeMultiplier, windEffect);
    }

    public static WaveCapes getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "1:1 Smooth continuous 16-segment cloth physics for capes (WaveyCapes model)";
    }

    @Override
    public String getIcon() {
        return "elytrautility.png";
    }
}
