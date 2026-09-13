package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class Crosshair extends Module {
    private static final Crosshair INSTANCE = new Crosshair();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting showInThirdPerson = new BooleanSetting(
            "Show In Third Person",
            "Keep the vanilla crosshair visible while in F5 / third-person view"
    ).setValue(true);

    private Crosshair() {
        super("crosshair", "Crosshair", ModuleCategory.OTHER);
        showInThirdPerson.setFullWidth(true);
        setup(generalSection, showInThirdPerson);
    }

    public static Crosshair getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Keep the vanilla crosshair visible in third-person (F5) view";
    }

    @Override
    public String getIcon() {
        return "crosshair.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
