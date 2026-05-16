package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

/**
 * Removes the underwater / underlava view distortions ("fog") that
 * vanilla applies when the camera entity is submerged in a fluid.
 *
 * <h3>Implementation strategy</h3>
 * Rather than fighting Mojang's per-frame fog parameter calculation
 * inside {@code BackgroundRenderer.applyFog} (which has changed
 * shape three times in the 1.20 -&gt; 1.21.4 window and would need a
 * different mixin descriptor every minor version), this module
 * intercepts {@link net.minecraft.client.render.Camera#getSubmersionType()}
 * and rewrites a {@code WATER} / {@code LAVA} return value to
 * {@code NONE} when the user has asked for that fluid to be hidden.
 *
 * <p>Every downstream consumer of "is the camera in water/lava" -
 * the underwater overlay quad, the colour-tinted fog setup, the
 * {@code BackgroundRenderer.render} fluid-specific branch, the
 * sky-not-rendered guard, the bubble-particle suppressor - reads
 * the same {@code submersionType} field through this single accessor
 * (verified by grepping the 1.21.4 vanilla source). One redirect
 * therefore covers fog AND tint AND overlay without us having to
 * patch each call site individually, which is exactly the kind of
 * surgical-yet-robust hook the user asked for.
 *
 * <h3>Mode selection</h3>
 * The user requested arrow-style cycling through three options
 * ("стрелочками < > как в темах"). Phaze's {@link SelectSetting}
 * is rendered as a left/right arrow control in the menu by
 * {@code SelectComponent}, which is exactly the visual the user
 * referenced - the Theme module uses the same component for its
 * preset cycle.
 */
public final class NoFluid extends Module {
    private static final NoFluid INSTANCE = new NoFluid();

    public static final String MODE_WATER = "Water";
    public static final String MODE_LAVA = "Lava";
    public static final String MODE_BOTH = "Both";

    public final SectionSetting generalSection = new SectionSetting("General");

    /**
     * Which fluid's fog / overlay to suppress. {@code Both} is the
     * common case (PvP players want clear vision regardless of
     * what they jumped into); {@code Water} alone is useful for
     * mining (so lava warns you visually); {@code Lava} alone is
     * unusual but kept for symmetry with the user's "what to hide"
     * phrasing in the request.
     */
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

    /**
     * True when the user wants underwater fog / overlay hidden.
     * Hot-path helper called from
     * {@code CameraNoFluidMixin.getSubmersionType} - the inline
     * string-equality compare is cheap enough that caching the
     * outcome would be premature optimisation, but consolidating
     * the gating logic here keeps the mixin small and the menu
     * label / behaviour binding explicit.
     */
    public boolean shouldHideWater() {
        if (!isEnabled()) return false;
        String selected = mode.getSelected();
        return MODE_WATER.equals(selected) || MODE_BOTH.equals(selected);
    }

    /**
     * True when the user wants underlava fog / overlay hidden.
     * See {@link #shouldHideWater} for the rationale on the
     * non-cached compare.
     */
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
