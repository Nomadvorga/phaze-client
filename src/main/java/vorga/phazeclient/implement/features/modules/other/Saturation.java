package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

public final class Saturation extends Module {
    private static final Saturation INSTANCE = new Saturation();

    public static Saturation getInstance() {
        return INSTANCE;
    }

    public final SelectSetting style = new SelectSetting("Style", "Saturation bar style")
            .value("Yellow Bar", "Second Hunger Bar")
            .selected("Yellow Bar");

    private Saturation() {
        super("saturation", "Saturation", ModuleCategory.UTILITIES);
        setup(style);
    }

    @Override
    public String getDescription() {
        return "Shows saturation bar above hunger bar";
    }

    @Override
    public String getIcon() {
        return "saturation.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }
}
