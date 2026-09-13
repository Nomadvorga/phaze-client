package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class Bright extends Module {
    private static final Bright INSTANCE = new Bright();

    public final ValueSetting brightness = new ValueSetting(
            "Brightness",
            "Lightmap gamma override; 1.0 = full bright, 0.0 = vanilla feel."
    ).range(0.0F, 1.0F).setValue(1.0F);

    private Bright() {
        super("full_bright", "Full Bright", ModuleCategory.OTHER);
        brightness.setFullWidth(true);
        setup(brightness);
    }

    public static Bright getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Forces maximum lightmap brightness so caves and night render at near-day levels";
    }

    @Override
    public String getIcon() {
        return "bright.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
