package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;

/**
 * Suppresses selected client-side renders. Each toggle is independent
 * and gates exactly one render path through a dedicated mixin:
 *
 * <ul>
 *   <li>{@link #glowing} - skips the entity-outline pass for entities
 *       that have the {@code minecraft:glowing} effect (or any team-
 *       set glow). Hooked at {@code EntityRenderer.hasOutline}, which
 *       is the single decision point vanilla calls before allocating
 *       the outline framebuffer attachment.</li>
 *   <li>{@link #fire} - cancels the burning-overlay quad drawn over
 *       the player's view while on fire. Hooked at
 *       {@code InGameOverlayRenderer.renderFireOverlay}.</li>
 *   <li>{@link #particles} - drops every {@code ParticleManager.addParticle}
 *       call on the floor so no client-side particle is ever spawned.
 *       Server-driven entity effects (potions, etc.) and packet-based
 *       particles still travel; we just refuse to materialise them.</li>
 *   <li>{@link #hitParticles} - finer-grained version of {@link #particles}:
 *       only cancels the four particle types Minecraft emits when an
 *       entity is hit:
 *       <ul>
 *         <li>{@code damage_indicator} - the red heart spawned on the
 *             entity's body when it takes damage.</li>
 *         <li>{@code crit} - the yellow stars from a critical hit
 *             (jumping while attacking).</li>
 *         <li>{@code enchanted_hit} - the cyan stars from a magic-
 *             enchanted attack.</li>
 *         <li>{@code sweep_attack} - the white arc from a sword
 *             sweep that hits multiple entities.</li>
 *       </ul>
 *       This gives the user a clean visual on hit without losing all
 *       ambient particle effects.</li>
 * </ul>
 *
 * <p>Each toggle is read on the hot path inside its own mixin via
 * {@link #getInstance()}, gated by {@link #isEnabled()} so disabling
 * the module restores vanilla behaviour even if individual toggles
 * are still ticked.
 */
public final class NoRender extends Module {
    private static final NoRender INSTANCE = new NoRender();

    public final BooleanSetting glowing = new BooleanSetting(
            "Glowing",
            "Hide the outline drawn around entities with the minecraft:glowing effect"
    ).setValue(true);

    public final BooleanSetting fire = new BooleanSetting(
            "Fire",
            "Hide the burning fire overlay drawn over the screen while on fire"
    ).setValue(true);

    public final BooleanSetting particles = new BooleanSetting(
            "Particles",
            "Skip every particle the client would spawn (ambient, weather, hit, etc.)"
    ).setValue(false);

    /** Sub-set of particle categories to skip when {@link #particles}
     *  is OFF. Replaces five separate {@link BooleanSetting}s so the
     *  picker reads as one cohesive multiselect chip row instead of
     *  a vertical wall of toggles. The {@code BooleanLike} accessors
     *  below preserve the existing {@code module.hitParticles.isValue()}
     *  call sites in mixins.
     */
    public final MultiSelectSetting particleTypes = new MultiSelectSetting(
            "Particle Types",
            "Pick which particle categories to skip. Click a label to toggle that category on / off."
    ).value(
            "Hit Particles", "Potion Particles", "Break Block Particles",
            "Splash Potion Particles", "Food Particles", "Mace Particles",
            "Scoreboard", "Boss Bar", "Rain"
    ).selected(
            "Hit Particles"
    );

    /** {@code BooleanSetting}-style accessors so each particle mixin
     *  keeps its existing {@code mod.hitParticles.isValue()} call
     *  site unchanged after the migration to MultiSelect. Each is a
     *  thin lambda over {@link MultiSelectSetting#getSelected()}.
     */
    public final BooleanLike hitParticles = () -> particleTypes.getSelected().contains("Hit Particles");
    public final BooleanLike potionParticles = () -> particleTypes.getSelected().contains("Potion Particles");
    public final BooleanLike breakBlockParticles = () -> particleTypes.getSelected().contains("Break Block Particles");
    public final BooleanLike splashPotionParticles = () -> particleTypes.getSelected().contains("Splash Potion Particles");
    public final BooleanLike foodParticles = () -> particleTypes.getSelected().contains("Food Particles");
    public final BooleanLike maceParticles = () -> particleTypes.getSelected().contains("Mace Particles");
    public final BooleanLike scoreboard = () -> particleTypes.getSelected().contains("Scoreboard");
    public final BooleanLike bossBar = () -> particleTypes.getSelected().contains("Boss Bar");
    public final BooleanLike rain = () -> particleTypes.getSelected().contains("Rain");

    /** Functional shim mimicking {@link BooleanSetting#isValue()} so
     *  every mixin's existing {@code .isValue()} call against a
     *  particle category still compiles after the consolidation. */
    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    private NoRender() {
        // Storage id stays "no_render" for backwards-compatible config
        // loading - users who already have this module saved in their
        // profile keep that key. Only the displayed label changes,
        // since the module now does more than just suppress renders.
        super("no_render", "Render Tweaks", ModuleCategory.OTHER);
        glowing.setFullWidth(true);
        fire.setFullWidth(true);
        particles.setFullWidth(true);
        // Hide the multiselect while {@code Particles=ON} - everything
        // it offers is already a no-op when the broader switch wins,
        // so collapsing the picker keeps the panel readable.
        particleTypes.setFullWidth(true);
        particleTypes.visible(() -> !particles.isValue());
        setup(glowing, fire, particles, particleTypes);
    }

    public static NoRender getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Render-pipeline tweaks: hide glowing, fire, particles, scoreboard, boss bar and rain";
    }

    @Override
    public String getIcon() {
        return "no_render.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
