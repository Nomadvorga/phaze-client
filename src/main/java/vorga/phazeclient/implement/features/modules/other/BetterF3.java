package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Replaces vanilla F3 debug overlay with a compact custom layout.
 * Vanilla's overlay dumps ~40 lines on the left and another ~30
 * on the right - useful for chunk debugging but overwhelming for
 * normal play. This module renders a curated, color-coded subset
 * with an optional FPS history bar.
 *
 * <h3>Color semantics</h3>
 * Most lines follow a {@code Name: Value} layout where the name
 * stays gray-blue (label color) and the value picks from a
 * dynamic palette based on what it represents:
 * <ul>
 *   <li>FPS / TPS - tier-colored (green=high, yellow=medium,
 *       red=low). Thresholds match the standalone TPS HUD.</li>
 *   <li>X / Y / Z coords - red / green / blue respectively, the
 *       standard convention BetterF3 / Lunar / vanilla F3 all use
 *       so users instantly read which axis is which.</li>
 *   <li>Memory percentage - tier-colored same as FPS but with
 *       inverted thresholds (high used = red).</li>
 *   <li>Static labels (biome, dimension, server brand) - simple
 *       white value text.</li>
 * </ul>
 * The {@code Color Coding} toggle disables the dynamic recoloring
 * and reverts to a uniform white value text for users who prefer
 * the vanilla-monochrome look.
 *
 * <h3>Backdrop</h3>
 * Each line gets a per-line vanilla-style backdrop using the same
 * {@code GameOptions.getTextBackgroundColor()} call vanilla's chat
 * uses. That way the F3 overlay reads alongside chat at the same
 * visual weight regardless of the user's text-background-opacity
 * preference.
 *
 * <h3>Mixin coupling</h3>
 * {@code DebugHudBetterF3Mixin} cancels vanilla's
 * {@code DebugHud.render(DrawContext)} when this module is on and
 * delegates to {@link BetterF3Renderer#render}. Vanilla's chunk
 * profiler hooks (right column "DimensionType" etc.) are not
 * displayed here.
 */
public final class BetterF3 extends Module {
    private static final BetterF3 INSTANCE = new BetterF3();

    public final SectionSetting sectionsSection = new SectionSetting("Sections");
    public final MultiSelectSetting sectionToggles = new MultiSelectSetting(
            "Sections",
            "Pick which lines the F3 overlay shows. Click a label to toggle that line on / off."
    ).value(
            "FPS", "Coordinates", "Facing", "Biome",
            "Light", "Dimension", "Server", "Memory",
            "Time", "System"
    ).selected(
            "FPS", "Coordinates", "Facing", "Biome",
            "Light", "Dimension", "Server", "Memory"
    );

    /** {@code BooleanSetting}-style accessors so the renderer code can
     *  keep its existing {@code module.showXxx.isValue()} call sites
     *  unchanged. Each delegates to {@link MultiSelectSetting#getSelected()}.
     *  Marked private + paired with a tiny inner record for the renderer
     *  consumers below.
     *
     *  <p>{@code showFpsBar} and {@code showTargeted} were removed
     *  from the picker but the renderer still references them; the
     *  shims now return a hard {@code false} so the corresponding
     *  blocks short-circuit out of the F3 overlay entirely.
     */
    public final BooleanLike showFps = () -> sectionToggles.getSelected().contains("FPS");
    public final BooleanLike showFpsBar = () -> false;
    public final BooleanLike showCoords = () -> sectionToggles.getSelected().contains("Coordinates");
    public final BooleanLike showFacing = () -> sectionToggles.getSelected().contains("Facing");
    public final BooleanLike showBiome = () -> sectionToggles.getSelected().contains("Biome");
    public final BooleanLike showLight = () -> sectionToggles.getSelected().contains("Light");
    public final BooleanLike showDimension = () -> sectionToggles.getSelected().contains("Dimension");
    public final BooleanLike showServer = () -> sectionToggles.getSelected().contains("Server");
    public final BooleanLike showMemory = () -> sectionToggles.getSelected().contains("Memory");
    public final BooleanLike showTime = () -> sectionToggles.getSelected().contains("Time");
    public final BooleanLike showSystem = () -> sectionToggles.getSelected().contains("System");
    public final BooleanLike showTargeted = () -> false;

    /** Functional shim mimicking {@link BooleanSetting#isValue()} so all
     *  the renderer's existing {@code module.showFps.isValue()} call
     *  sites compile unchanged after the migration to MultiSelect. */
    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    public final SectionSetting styleSection = new SectionSetting("Style");
    public final BooleanSetting colorCoding = new BooleanSetting(
            "Color Coding",
            "Tint values dynamically (FPS tiers, X/Y/Z axis colors, memory pressure). Off = uniform white"
    ).setValue(true);
    public final BooleanSetting compactMode = new BooleanSetting(
            "Compact Mode",
            "Tighter line spacing for smaller footprint"
    ).setValue(false);
    public final BooleanSetting textShadow = new BooleanSetting(
            "Text Shadow",
            "Render text with vanilla drop-shadow"
    ).setValue(true);

    private BetterF3() {
        super("better_f3", "Better F3", ModuleCategory.OTHER);
        sectionToggles.setFullWidth(true);
        colorCoding.setFullWidth(true);
        compactMode.setFullWidth(true);
        textShadow.setFullWidth(true);
        setup(sectionsSection, sectionToggles,
                styleSection, colorCoding, compactMode, textShadow);
    }

    public static BetterF3 getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Compact custom F3 debug overlay with color-coded values and FPS bar";
    }

    @Override
    public String getIcon() {
        return "better_f3.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
