package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.system.motionblur.Shader;

public final class MotionBlur extends Module {
    private static final MotionBlur INSTANCE = new MotionBlur();

    public final SectionSetting mainSection = new SectionSetting("General");
    public final ValueSetting strength = new ValueSetting("Strength", "Motion blur strength").range(-2.0f, 2.0f).setValue(-0.8f);
    public final BooleanSetting useRRC = new BooleanSetting("Refresh Rate Scaling", "Scale strength based on refresh rate").setValue(true);
    public final ValueSetting quality = new ValueSetting("Quality", "Quality level (0=Low, 1=Medium, 2=High, 3=Ultra)").range(0, 3).setValue(2);
    public final ValueSetting handDepthThreshold = new ValueSetting("Hand Depth Threshold", "Hand depth threshold").range(0.0f, 1.0f).setValue(0.56f);

    public final Shader shader;

    private MotionBlur() {
        super("motion_blur", "Motion Blur", ModuleCategory.OTHER, true, false);
        
        strength.setFullWidth(true);
        useRRC.setFullWidth(true);
        quality.setFullWidth(true);
        handDepthThreshold.setFullWidth(true);
        
        setup(mainSection, strength, useRRC, quality, handDepthThreshold);
        
        shader = new Shader(this);
    }

    public static MotionBlur getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Motion blur effect";
    }

    @Override
    public String getIcon() {
        return "motion_blur.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getStrength() {
        return strength.getValue();
    }

    public boolean isUseRRC() {
        return useRRC.isValue();
    }

    public int getQuality() {
        return quality.getInt();
    }

    public float getHandDepthThreshold() {
        return handDepthThreshold.getValue();
    }

    public void setStrength(float value) {
        strength.setValue(value);
    }

    public void updateBlurStrength(float value) {
        strength.setValue(value);
    }
}
