package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Recolors the sky. Hooks {@code ClientWorld.getSkyColor} via
 * {@code ClientWorldSkyCustomizerMixin} and applies the configured
 * tint blended with the vanilla output, so day-night transitions still
 * happen naturally and the user's custom tint just leans the colour
 * in their preferred direction instead of stamping a flat colour over
 * everything.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>Tint</b> - linearly interpolate vanilla colour towards
 *       the configured colour by {@code intensity}. {@code 0.0}
 *       leaves vanilla untouched, {@code 1.0} fully replaces it.</li>
 *   <li><b>Replace</b> - hard override, ignores vanilla. Useful for
 *       photoshoots / consistent custom-shader looks.</li>
 *   <li><b>Gradient</b> - sky leans toward {@code colorDay} while
 *       the sun is up and toward {@code colorNight} while it's
 *       down, with a smooth blend driven by {@code skyBrightness}
 *       (vanilla 0..1 day-night phase).</li>
 * </ul>
 *
 * <h3>Sunrise / sunset intensity</h3>
 * The {@code sunsetBoost} slider amplifies the existing vanilla
 * sunset colour pass (the warm orange / red tint Mojang adds during
 * the dawn / dusk window). Implemented inside the colour-mix path
 * by detecting "twilight" via {@code skyBrightness in [0.15, 0.85]}
 * and bumping the red/green channels of the tinted colour by the
 * boost factor before clamping. {@code 1.0} is a no-op, {@code 2.0}
 * roughly doubles the warm component, the cap at {@code 4.0} stops
 * the channel from wrapping to white.
 *
 * <h3>Compatibility</h3>
 * Two mixin points: {@code ClientWorld.getSkyColor} (sky-dome and
 * Iris {@code skyColor} uniform) and {@code BackgroundRenderer.getFogColor}
 * (horizon haze, fog uniform, framebuffer clear). Together they
 * cover vanilla, Sodium, BadOptimizations cache hits, and Iris with
 * shader packs.
 *
 * <p><b>Shader-pack caveat.</b> Packs like Complementary, BSL,
 * Sildur's and Photon draw their own sky through
 * {@code gbuffers_skybasic} which procedurally scatters light from
 * the sun direction and ignores the vanilla sky colour. The dome
 * itself stays on the pack's atmospheric model; what we change in
 * that case is the {@code fogColor} uniform, which most packs read
 * for the horizon haze and distance fade. End result with shaders:
 * the horizon and distant air pick up the tint, the procedural
 * sky-dome stays as the pack drew it.
 */
public final class SkyCustomizer extends Module {
    private static final SkyCustomizer INSTANCE = new SkyCustomizer();

    public final SectionSetting modeSection = new SectionSetting("Mode");
    public final SelectSetting mode = new SelectSetting(
            "Mode",
            "How the configured color is applied: Tint blends with vanilla, Replace overrides it, Gradient smooths between day/night colors."
    ).value("Tint", "Replace", "Gradient").selected("Tint");

    public final SectionSetting tintSection = new SectionSetting("Color");
    public final ColorSetting baseColor = new ColorSetting(
            "Base Color",
            "Used by Tint and Replace modes; also the day color in Gradient mode"
    ).setColor(0xFF7AB6FF).noAlpha().popupRow();
    public final ColorSetting nightColor = new ColorSetting(
            "Night Color",
            "Gradient mode only - the colour the sky leans toward at night"
    ).setColor(0xFF1A1233).noAlpha().popupRow();
    public final ValueSetting intensity = new ValueSetting(
            "Intensity",
            "Tint strength. 0 = vanilla, 1 = fully tinted"
    ).range(0.0F, 1.0F).step(0.05F).setValue(0.5F);

    public final SectionSetting twilightSection = new SectionSetting("Twilight");
    public final ValueSetting sunsetBoost = new ValueSetting(
            "Sunrise/Sunset Boost",
            "Amplifies the warm channels during dawn / dusk. 1 = neutral, higher = more orange/red"
    ).range(1.0F, 4.0F).step(0.05F).setValue(1.0F);

    private SkyCustomizer() {
        super("sky_customizer", "Sky Customizer", ModuleCategory.OTHER);

        mode.setFullWidth(true);
        baseColor.setFullWidth(true);
        nightColor.setFullWidth(true);
        intensity.setFullWidth(true);
        sunsetBoost.setFullWidth(true);

        // Hide nightColor unless the user actually picked Gradient -
        // the slider would just confuse Tint/Replace users otherwise.
        nightColor.visible(() -> "Gradient".equalsIgnoreCase(mode.getSelected()));
        intensity.visible(() -> !"Replace".equalsIgnoreCase(mode.getSelected()));

        setup(
                modeSection, mode,
                tintSection, baseColor, nightColor, intensity,
                twilightSection, sunsetBoost
        );
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }

    public static SkyCustomizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Custom sky tint with day/night gradient and sunset boost";
    }

    @Override
    public String getIcon() {
        return "sky_customizer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Compute the final sky colour given vanilla's value and the
     * current sky brightness (0..1, vanilla day-night phase). Called
     * from the {@code ClientWorld.getSkyColor} mixin.
     */
    public int applyToSky(int vanillaArgb, float skyBrightness) {
        if (!isEnabled()) {
            return vanillaArgb;
        }
        int target = pickTargetColor(skyBrightness);
        int blended = blendForCurrentMode(vanillaArgb, target);
        return applySunsetBoost(blended, skyBrightness);
    }

    private int pickTargetColor(float skyBrightness) {
        if ("Gradient".equalsIgnoreCase(mode.getSelected())) {
            // Lerp between night and day colour by skyBrightness.
            // Vanilla skyBrightness is roughly the sun-up factor:
            // ~0.0 deep night, ~1.0 high noon. Linear blend reads
            // naturally as the sky ramps through dawn / dusk.
            return blend(nightColor.getColor(), baseColor.getColor(), clamp01(skyBrightness));
        }
        return baseColor.getColor();
    }

    private int blendForCurrentMode(int vanilla, int target) {
        String m = mode.getSelected();
        if ("Replace".equalsIgnoreCase(m)) {
            return target;
        }
        return blend(vanilla, target, clamp01(intensity.getValue()));
    }

    private int applySunsetBoost(int color, float skyBrightness) {
        float boost = sunsetBoost.getValue();
        if (boost <= 1.001F) {
            return color;
        }
        // Twilight window: vanilla sky brightness lives roughly in
        // [0.0, 1.0]; the warm-tint pass concentrates around
        // [0.15, 0.85]. Outside that range (full noon / full midnight)
        // the boost has nothing to amplify, so we taper it off with
        // a triangular curve peaking at 0.5.
        float window = 1.0F - Math.abs(skyBrightness - 0.5F) * 2.0F;
        if (window <= 0.0F) {
            return color;
        }
        float effective = 1.0F + (boost - 1.0F) * clamp01(window);
        int a = (color >> 24) & 0xFF;
        int r = clamp255(Math.round(((color >> 16) & 0xFF) * effective));
        int g = clamp255(Math.round(((color >> 8) & 0xFF) * (1.0F + (effective - 1.0F) * 0.6F)));
        int b = (color) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float t) {
        if (t <= 0.0F) return a;
        if (t >= 1.0F) return b;
        int aa = (a >> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = (a) & 0xFF;
        int ba = (b >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = (b) & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
