package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aspect Ratio override. Forces the world projection matrix to use a
 * custom width/height ratio instead of the actual window's, so the user
 * can stretch / squeeze the field of view independently of monitor
 * shape (cinematic 21:9 on a 16:9 panel, "old Minecraft" 4:3 look,
 * arbitrary squish via the manual factor, etc.).
 *
 * <p>Two operating modes selected by {@link #usePreset}:
 * <ul>
 *   <li>{@code usePreset = true} - {@link #preset} picks a named ratio
 *       ({@code 16:9}, {@code 5:4}, {@code 4:3}, {@code 21:9}). The
 *       preset slider is hidden when this mode is off.</li>
 *   <li>{@code usePreset = false} - {@link #factor} is the raw aspect
 *       ratio applied directly. {@code 1.0} = perfect square, values
 *       above stretch horizontally, below squeeze. The manual slider
 *       is hidden when preset mode is on.</li>
 * </ul>
 *
 * <p>The projection-matrix override itself lives in
 * {@link vorga.phazeclient.mixins.GameRendererMixin} which
 * calls {@link #getRatio()} once per projection-matrix recomputation.
 */
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

    /**
     * Resolves the currently-configured aspect ratio. When
     * {@link #usePreset} is on, decodes the {@link #preset} string into
     * a width/height ratio; otherwise returns the raw {@link #factor}
     * slider value. Unknown or invalid preset values fall back to
     * {@code 16:9}, so an edited or old config can never blank the world.
     */
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
            // A malformed value from a manually edited config uses 16:9.
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
