package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AspectRatio extends Module {
    private static final Pattern PRESET_RATIO = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*:\\s*(\\d+(?:\\.\\d+)?)$");
    private static final float FALLBACK_RATIO = 16.0F / 9.0F;
    private static final AspectRatio INSTANCE = new AspectRatio();

    public final vorga.phazeclient.api.feature.module.setting.implement.SectionSetting generalSection =
            new vorga.phazeclient.api.feature.module.setting.implement.SectionSetting("General");

    public final BooleanSetting usePreset = new BooleanSetting(
            "Use Preset",
            "Pick from a list of common aspect ratios instead of a free-form factor"
    ).setValue(false);

    public final SelectSetting preset = new SelectSetting(
            "Aspect Preset",
            "Choose a standard, classic, or ultrawide aspect ratio"
    ).value(
            "4:3",
            "5:4",
            "1:1",
            "3:2",
            "16:10",
            "16:9",
            "21:9",
            "32:9"
    ).selected("16:9");

    public final ValueSetting factor = new ValueSetting(
            "Aspect Factor",
            "Manual aspect ratio; 1.0 = square, >1 stretches horizontally, <1 squeezes."
    ).range(0.5F, 4.0F).step(0.01F).setValue(1.0F);

    private AspectRatio() {
        super("aspect_ratio", "Aspect Ratio", ModuleCategory.OTHER);

        usePreset.setFullWidth(true);
        preset.setFullWidth(true);
        preset.setVisible(usePreset::isValue);
        factor.setFullWidth(true);
        factor.setVisible(() -> !usePreset.isValue());

        setup(generalSection, usePreset, preset, factor);
    }

    public static AspectRatio getInstance() {
        return INSTANCE;
    }

    public float getRatio() {
        if (usePreset.isValue()) {
            return parsePresetRatio(preset.getSelected());
        }
        return factor.getValue();
    }

    private static float parsePresetRatio(String preset) {
        if (preset == null) {
            return FALLBACK_RATIO;
        }

        Matcher matcher = PRESET_RATIO.matcher(preset);
        if (!matcher.matches()) {
            return FALLBACK_RATIO;
        }

        try {
            float width = Float.parseFloat(matcher.group(1));
            float height = Float.parseFloat(matcher.group(2));
            if (width > 0.0F && height > 0.0F) {
                return width / height;
            }
        } catch (NumberFormatException ignored) {

        }
        return FALLBACK_RATIO;
    }

    @Override
    public String getDescription() {
        return "Override the world projection's aspect ratio with a preset or manual factor";
    }

    @Override
    public String getIcon() {
        return "aspect_ratio.png";
    }

    @Override
    public float getIconSize() {
        return 27.4F;
    }

    @Override
    public float getIconOffsetY() {
        return -2.0F;
    }
}
