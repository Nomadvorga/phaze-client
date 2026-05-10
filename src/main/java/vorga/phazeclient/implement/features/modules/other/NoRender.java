package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

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

    public final BooleanSetting hitParticles = new BooleanSetting(
            "Hit Particles",
            "Skip only the on-hit particles: damage indicators, crit, enchanted_hit, sweep_attack"
    ).setValue(true);

    private NoRender() {
        super("no_render", "No Render", ModuleCategory.OTHER);
        glowing.setFullWidth(true);
        fire.setFullWidth(true);
        particles.setFullWidth(true);
        // Hide the more-specific toggle while the broader one is on -
        // there is no useful state where Particles=ON + HitParticles=anything,
        // so collapsing the redundant control keeps the panel readable.
        hitParticles.setFullWidth(true);
        hitParticles.visible(() -> !particles.isValue());
        setup(glowing, fire, particles, hitParticles);
    }

    public static NoRender getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Hides individual client-side renders: glowing outlines, fire overlay, particles";
    }

    @Override
    public String getIcon() {
        return "other.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
