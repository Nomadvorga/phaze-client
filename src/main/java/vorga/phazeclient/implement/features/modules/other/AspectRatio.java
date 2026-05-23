package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

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
 * <p>Logic mirrors the upstream {@code padej.soup.other.AspectRatio}
 * port. The projection-matrix override itself lives in
 * {@link vorga.phazeclient.mixins.GameRendererAspectRatioMixin} which
 * calls {@link #getRatio()} once per projection-matrix recomputation.
 */
public final class AspectRatio extends Module {
    private static final AspectRatio INSTANCE = new AspectRatio();

    public final vorga.phazeclient.api.feature.module.setting.implement.SectionSetting generalSection =
            new vorga.phazeclient.api.feature.module.setting.implement.SectionSetting("General");

    public final BooleanSetting usePreset = new BooleanSetting(
            "Use Preset",
            "Pick from a list of common aspect ratios instead of a free-form factor"
    ).setValue(false);

    public final SelectSetting preset = new SelectSetting(
            "Aspect Preset",
            "Common cinematic / vanilla aspect ratios"
    ).value("16:9", "5:4", "4:3", "21:9").selected("16:9");

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
     * slider value. Default fall-through is {@code 16:9} so an unknown
     * preset string can never blank the world out.
     */
    public float getRatio() {
        if (usePreset.isValue()) {
            return switch (preset.getSelected()) {
                case "16:9" -> 16.0F / 9.0F;
                case "5:4" -> 5.0F / 4.0F;
                case "4:3" -> 4.0F / 3.0F;
                case "21:9" -> 21.0F / 9.0F;
                default -> 16.0F / 9.0F;
            };
        }
        return factor.getValue();
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
        return 21.0F;
    }
}
