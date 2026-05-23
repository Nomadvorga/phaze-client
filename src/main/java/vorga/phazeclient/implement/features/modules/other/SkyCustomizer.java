package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Recolors the sky and clouds. Hooks
 * {@code ClientWorld.getSkyColor} / {@code ClientWorld.getCloudsColor}
 * via {@code ClientWorldSkyCustomizerMixin} and applies the
 * configured tint blended with the vanilla output, so day-night
 * transitions still happen naturally and the user's custom tint
 * just leans the colour in their preferred direction instead of
 * stamping a flat colour over everything.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>Tint</b> - linearly interpolate vanilla colour towards
 *       the configured colour by {@code intensity}. {@code 0.0}
 *       leaves vanilla untouched, {@code 1.0} fully replaces it.
 *       Clouds inherit the same tint at half-strength so the tint
 *       reads as "atmosphere" rather than two flat layers.</li>
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
    ).setColor(0xFF7AB6FF);
    public final ColorSetting nightColor = new ColorSetting(
            "Night Color",
            "Gradient mode only - the colour the sky leans toward at night"
    ).setColor(0xFF1A1233);
    public final ValueSetting intensity = new ValueSetting(
            "Intensity",
            "Tint strength. 0 = vanilla, 1 = fully tinted"
    ).range(0.0F, 1.0F).step(0.05F).setValue(0.5F);

    public final SectionSetting cloudsSection = new SectionSetting("Clouds");
    public final BooleanSetting affectClouds = new BooleanSetting(
            "Affect Clouds",
            "Apply the same tint to cloud color (at half strength)"
    ).setValue(true);
    public final ValueSetting cloudBrightness = new ValueSetting(
            "Cloud Brightness",
            "Multiplier on the final cloud color. 1.0 = unchanged, <1 darker, >1 brighter"
    ).range(0.2F, 2.0F).step(0.05F).setValue(1.0F);

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
        affectClouds.setFullWidth(true);
        cloudBrightness.setFullWidth(true);
        sunsetBoost.setFullWidth(true);

        // Hide nightColor unless the user actually picked Gradient -
        // the slider would just confuse Tint/Replace users otherwise.
        nightColor.visible(() -> "Gradient".equalsIgnoreCase(mode.getSelected()));
        intensity.visible(() -> !"Replace".equalsIgnoreCase(mode.getSelected()));

        // Sodium caches sky / cloud uniforms inside its own renderer
        // pipeline. Our per-frame {@code @ModifyReturnValue} on
        // {@code ClientWorld.getSkyColor} updates the vanilla path,
        // but Sodium reads from its own cache that only refreshes on
        // a full world-renderer reload. Without a kick the user has
        // to relog / change dimension before the tint becomes
        // visible, which the user reported as "переходить в мир
        // чтобы он применился".
        //
        // Reload triggers are picked carefully:
        //   - mode switch is a structural change (e.g. Replace bypass
        //     vs Tint blend) and definitely needs a fresh upload.
        //   - colour changes need a refresh because Sodium resolves
        //     fog / sky colour uniforms once per chunk-mesh upload.
        //   - the affectClouds toggle gates the cloud path entirely.
        // Sliders (intensity / brightness / boost) are kept off the
        // reload list - they update naturally per-frame and a reload
        // for every drag-tick would cause a perceptible stutter.
        mode.onChange(v -> reloadWorldRenderer());
        baseColor.onChange(v -> reloadWorldRenderer());
        nightColor.onChange(v -> reloadWorldRenderer());
        affectClouds.onChange(v -> reloadWorldRenderer());

        setup(
                modeSection, mode,
                tintSection, baseColor, nightColor, intensity,
                cloudsSection, affectClouds, cloudBrightness,
                twilightSection, sunsetBoost
        );
    }

    @Override
    public void activate() {
        reloadWorldRenderer();
    }

    @Override
    public void deactivate() {
        reloadWorldRenderer();
    }

    /**
     * Asks the active world renderer (vanilla or Sodium - same
     * {@code WorldRenderer.reload()} entry point) to drop its
     * cached chunk meshes and re-upload everything. That's the
     * supported way to invalidate Sodium's sky / cloud uniforms
     * without poking into its internals: Sodium's own settings
     * panel uses the exact same hook when its options change.
     *
     * <p>No-op when the world renderer hasn't been created yet
     * (main menu) or when there's no world loaded - either way
     * there's nothing cached to invalidate.
     *
     * <p><b>Debounced.</b> Sodium logs
     * {@code "Started/Stopping worker threads"} on every reload,
     * so calling this on every {@code ColorSetting} drag-tick
     * spammed the chat with hundreds of lines per second. The
     * scheduler holds a single pending reload up to
     * {@link #RELOAD_DEBOUNCE_MS}, coalescing rapid changes into
     * one final reload after the user stops fiddling.
     */
    private static final long RELOAD_DEBOUNCE_MS = 250L;
    private static volatile long pendingReloadAtMs = 0L;
    private static volatile boolean reloadScheduled = false;

    private static void reloadWorldRenderer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (mc.worldRenderer == null) return;
        if (mc.world == null) return;

        long now = System.currentTimeMillis();
        pendingReloadAtMs = now + RELOAD_DEBOUNCE_MS;
        if (reloadScheduled) {
            // Already a reload pending - the scheduled task will
            // pick up the new pendingReloadAtMs and wait further.
            return;
        }
        reloadScheduled = true;
        scheduleReloadTick(mc);
    }

    private static void scheduleReloadTick(MinecraftClient mc) {
        // execute() defers to the render thread - reload() must run
        // there because it touches GL state. Posting from setting
        // callbacks (which fire from the GUI thread on click) directly
        // would risk a glState-on-wrong-thread crash on AMD drivers.
        mc.execute(() -> {
            long now = System.currentTimeMillis();
            if (now < pendingReloadAtMs) {
                // Still inside the debounce window - re-post to the
                // render thread so we re-check after the next frame.
                // No tight loop / sleep: we just keep yielding back
                // until the user stops triggering changes.
                scheduleReloadTick(mc);
                return;
            }
            reloadScheduled = false;
            try {
                if (mc.worldRenderer != null && mc.world != null) {
                    mc.worldRenderer.reload();
                }
            } catch (Throwable ignored) {
                // Reload is best-effort: if Sodium is mid-frame we
                // swallow and try again on the next change. Better
                // than a hard crash from a transient internal state.
            }
        });
    }

    public static SkyCustomizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Custom sky and cloud tint with day/night gradient and sunset boost";
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

    /**
     * Compute the final cloud colour. Mirrors {@link #applyToSky}
     * with the half-strength rule and an optional brightness scale.
     */
    public int applyToClouds(int vanillaArgb, float skyBrightness) {
        if (!isEnabled()) {
            return vanillaArgb;
        }
        int result = vanillaArgb;
        if (affectClouds.isValue()) {
            int target = pickTargetColor(skyBrightness);
            // Half-strength tint on clouds so they read as
            // atmosphere-blended rather than a flat second layer
            // painted on top of the sky.
            float scaled = clamp01(intensity.getValue() * 0.5F);
            result = blend(vanillaArgb, target, scaled);
        }
        float bright = cloudBrightness.getValue();
        if (Math.abs(bright - 1.0F) > 0.001F) {
            result = scaleRgb(result, bright);
        }
        return result;
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

    private static int scaleRgb(int color, float scale) {
        int a = (color >> 24) & 0xFF;
        int r = clamp255(Math.round(((color >> 16) & 0xFF) * scale));
        int g = clamp255(Math.round(((color >> 8) & 0xFF) * scale));
        int b = clamp255(Math.round(((color) & 0xFF) * scale));
        return (a << 24) | (r << 16) | (g << 8) | b;
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
