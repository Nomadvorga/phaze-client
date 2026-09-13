package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

public final class NoFluid extends Module {
    private static final NoFluid INSTANCE = new NoFluid();

    public static final String MODE_WATER = "Water";
    public static final String MODE_LAVA = "Lava";
    public static final String MODE_BOTH = "Both";

    public final SectionSetting generalSection = new SectionSetting("General");

    public final SelectSetting mode = new SelectSetting(
            "Mode",
            "Which fluid's fog and overlay to remove"
    ).value(MODE_WATER, MODE_LAVA, MODE_BOTH).selected(MODE_BOTH);

    public static NoFluid getInstance() {
        return INSTANCE;
    }

    private NoFluid() {
        super("no_fluid", "No Fluid", ModuleCategory.UTILITIES);
        mode.setFullWidth(true);
        setup(generalSection, mode);
    }

    public boolean shouldHideWater() {
        if (!isEnabled()) return false;
        String selected = mode.getSelected();
        return MODE_WATER.equals(selected) || MODE_BOTH.equals(selected);
    }

    public boolean shouldHideLava() {
        if (!isEnabled()) return false;
        String selected = mode.getSelected();
        return MODE_LAVA.equals(selected) || MODE_BOTH.equals(selected);
    }

    @Override
    public String getDescription() {
        return "Removes the fog and screen overlay applied while the camera is submerged in water or lava";
    }

    @Override
    public String getIcon() {
        return "no_fluid.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
