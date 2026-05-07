package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Full Bright. Forces the lightmap's gamma input toward maximum so dim
 * areas (caves, night, deep water) render at near-day brightness. The
 * {@link #brightness} slider scales the override - 1.0 is "as bright as
 * the engine will let us go", lower values bring the cap down so users
 * who only want a slight night-vision lift aren't stuck with the
 * full-blast look.
 *
 * <p>Logic mirrors the upstream <code>padej.soup.world.Bright</code>
 * port: a single ValueSetting clamped to {@code [0.0, 1.0]}, multiplied
 * by 10 inside {@link vorga.phazeclient.mixins.LightmapTextureManagerBrightMixin}
 * to overshoot the engine's gamma curve into the saturated region. The
 * x10 factor is preserved verbatim from the reference so the output
 * matches user expectations from that source.
 */
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
