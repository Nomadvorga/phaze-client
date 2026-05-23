package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.features.modules.client.Theme;

/**
 * Independent fog override. Wraps the vanilla
 * {@code BackgroundRenderer.applyFog} return value so the user
 * can dictate distance, density and colour without depending on
 * vanilla's fog enabled / disabled state, biome, or the F3+F
 * toggle. Submersion fog (water / lava / powder snow) is left
 * alone - those have UX-critical visibility implications and
 * should be controlled by the dedicated NoFluid module.
 *
 * <h3>Distance vs density</h3>
 * Vanilla's fog is a linear ramp between {@code start} and
 * {@code end} in world units, applied by the shader as
 * {@code (z - start) / (end - start)} clamped to [0, 1]. To give
 * the user one intuitive "how thick" knob and one "how far" knob:
 * <ul>
 *   <li>{@code distance} is the {@code end} of the ramp.</li>
 *   <li>{@code density} controls how much of that ramp is
 *       actually fogged: {@code start = end * (1 - density)}.
 *       At density=0 the fog only kicks in at the far plane (a
 *       razor-thin band) and at density=1 it starts at the camera
 *       (a flat solid wall).</li>
 * </ul>
 * This is the same parameterisation OptiFine / Iris use for their
 * fog density slider and reads naturally to anyone who's touched
 * those mods.
 *
 * <h3>Theme colour</h3>
 * When {@code colorMode = "Theme"} the fog colour is pulled from
 * {@link Theme#getCurrentMenuPalette}'s {@code chipActive} - the
 * accent the rest of the GUI uses. Switching themes from the menu
 * therefore retints fog automatically with no need to reach for the
 * colour picker. {@code "Custom"} reads from {@link #color} directly.
 */
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
    ).setColor(0xFF6E83C7);
    public final ValueSetting opacity = new ValueSetting(
            "Opacity",
            "Final fog alpha multiplier. 1 = fully opaque, 0 = invisible"
    ).range(0.0F, 1.0F).step(0.01F).setValue(1.0F);

    public final SectionSetting scopeSection = new SectionSetting("Scope");
    public final BooleanSetting affectSky = new BooleanSetting(
            "Affect Sky Fog",
            "Apply the override to the sky-dome fog pass too. Disable to keep the horizon clear and only fog terrain."
    ).setValue(true);

    public final SectionSetting smoothSkiesSection = new SectionSetting("Smooth Skies");
    public final BooleanSetting smoothSkies = new BooleanSetting(
            "Smooth Skies",
            "Smooths the transition between fog and sky so lower fog opacity doesn't produce a hard horizon line. Active only when Custom Fog is on."
    ).setValue(true);
    public final BooleanSetting fixSkyboxClipping = new BooleanSetting(
            "Fix Skybox Clipping",
            "Pushes the far plane out so a low render-distance skybox doesn't clip into the fog band. Inspired by SmoothSkies (MIT)."
    ).setValue(true);
    public final BooleanSetting lowerVoidDarkness = new BooleanSetting(
            "Lower Void Darkness",
            "Stops the fog from going pitch-black when the camera is below world height. Inspired by SmoothSkies (MIT)."
    ).setValue(true);

    private CustomFog() {
        super("custom_fog", "Custom Fog", ModuleCategory.OTHER);

        distance.setFullWidth(true);
        density.setFullWidth(true);
        colorMode.setFullWidth(true);
        color.setFullWidth(true);
        opacity.setFullWidth(true);
        affectSky.setFullWidth(true);
        smoothSkies.setFullWidth(true);
        fixSkyboxClipping.setFullWidth(true);
        lowerVoidDarkness.setFullWidth(true);

        // Hide the colour picker when the user is on Theme - the
        // value is ignored anyway and the dead control is just
        // visual clutter.
        color.visible(() -> "Custom".equalsIgnoreCase(colorMode.getSelected()));
        // Sub-toggles are useful only when the master Smooth Skies
        // switch is on; collapse them otherwise so the panel stays
        // tidy.
        fixSkyboxClipping.visible(smoothSkies::isValue);
        lowerVoidDarkness.visible(smoothSkies::isValue);

        setup(
                distanceSection, distance, density,
                colorSection, colorMode, color, opacity,
                scopeSection, affectSky,
                smoothSkiesSection, smoothSkies, fixSkyboxClipping, lowerVoidDarkness
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
        return "no_render.png";
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

    public boolean isSmoothSkiesEnabled() {
        return isEnabled() && smoothSkies.isValue();
    }

    public boolean isFixSkyboxClipping() {
        return isSmoothSkiesEnabled() && fixSkyboxClipping.isValue();
    }

    public boolean isLowerVoidDarkness() {
        return isSmoothSkiesEnabled() && lowerVoidDarkness.isValue();
    }

    /**
     * Final ARGB colour the fog should fade to, with the user-
     * configured alpha multiplier already folded into the alpha
     * channel. Callers can read the four channels straight off this
     * int - no further alpha math required.
     */
    public int getResolvedColorArgb() {
        int base;
        if ("Theme".equalsIgnoreCase(colorMode.getSelected())) {
            // chipActive is the accent the menu uses for selected
            // chips / sliders, so picking it for "theme fog" gives
            // the strongest visual link to the rest of the UI.
            base = Theme.getInstance().getCurrentMenuPalette().chipActive();
        } else {
            base = color.getColor();
        }
        int srcA = (base >>> 24) & 0xFF;
        int finalA = Math.round(srcA * opacity.getValue());
        if (finalA < 0) finalA = 0;
        if (finalA > 255) finalA = 255;
        return (finalA << 24) | (base & 0x00FFFFFF);
    }
}
