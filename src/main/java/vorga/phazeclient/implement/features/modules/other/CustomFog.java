package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.features.modules.client.Theme;

public final class CustomFog extends Module {
    private static final CustomFog INSTANCE = new CustomFog();

    public final SectionSetting distanceSection = new SectionSetting("Distance");
    public final ValueSetting distance = new ValueSetting(
            "Distance",
            "Distance in blocks at which the fog reaches full opacity"
    ).range(2.0F, 512.0F).step(1.0F).setValue(96.0F);
    public final ValueSetting density = new ValueSetting(
            "Density",
            "How much of the distance range is fogged. 0 = thin band at the far plane, 1 = solid wall starting at the camera"
    ).range(0.0F, 1.0F).step(0.01F).setValue(0.5F);

    public final SectionSetting colorSection = new SectionSetting("Color");
    public final SelectSetting colorMode = new SelectSetting(
            "Color Mode",
            "Custom uses the picker below, Theme follows the active GUI palette"
    ).value("Custom", "Theme").selected("Custom");
    public final ColorSetting color = new ColorSetting(
            "Fog Color",
            "Custom fog colour. Ignored when Color Mode is set to Theme"
    ).setColor(0xFF6E83C7).noAlpha().popupRow();

    public final SectionSetting scopeSection = new SectionSetting("Scope");
    public final BooleanSetting affectSky = new BooleanSetting(
            "Affect Sky Fog",
            "Apply the override to the sky-dome fog pass too. Disable to keep the horizon clear and only fog terrain."
    ).setValue(true);

    private CustomFog() {
        super("custom_fog", "Custom Fog", ModuleCategory.OTHER);

        distance.setFullWidth(true);
        density.setFullWidth(true);
        colorMode.setFullWidth(true);
        color.setFullWidth(true);
        affectSky.setFullWidth(true);

        color.visible(() -> "Custom".equalsIgnoreCase(colorMode.getSelected()));

        setup(
                distanceSection, distance, density,
                colorSection, colorMode, color,
                scopeSection, affectSky
        );
    }

    public static CustomFog getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Custom fog with adjustable distance, density and color";
    }

    @Override
    public String getIcon() {
        return "custom_fog.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getDistance() {
        return distance.getValue();
    }

    public float getDensity() {
        return density.getValue();
    }

    public boolean isAffectSky() {
        return affectSky.isValue();
    }

    public int getResolvedRgb() {
        if ("Theme".equalsIgnoreCase(colorMode.getSelected())) {
            return Theme.getInstance().getCurrentMenuPalette().chipActive() & 0x00FFFFFF;
        }
        return color.getColor() & 0x00FFFFFF;
    }
}
