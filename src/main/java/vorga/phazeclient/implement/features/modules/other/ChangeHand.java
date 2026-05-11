package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Arm;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Flip the main-arm side on demand without touching the vanilla
 * {@code options.mainArm} setting. Two mutually-exclusive trigger
 * modes mirror how the feature is usually surfaced in PvP UX configs:
 *
 * <ul>
 *   <li><b>Upon Impact</b> (default): every successful attack on an
 *       entity flips the main arm. Convenient for "alternating slap"
 *       tells where the rendered arm switches per hit.</li>
 *   <li><b>Bind</b>: a single key-press flips the arm. Visible only
 *       when Upon Impact is off so the user isn't presented with two
 *       redundant triggers that fight over the arm side per-hit.</li>
 * </ul>
 *
 * <p><b>Decoupled from vanilla:</b> we write the local
 * {@code DataTracker.MAIN_ARM} on {@code client.player} directly
 * instead of going through {@code client.options.getMainArm()}.
 * Reasons:
 *
 * <ul>
 *   <li>The option write would persist to {@code options.txt} and
 *       leak the alternating value back on relogin - users complained
 *       that their vanilla preference kept getting clobbered.</li>
 *   <li>The option write also triggers
 *       {@code GameOptions.sendClientSettings()} which spams a
 *       {@code ClientOptionsC2SPacket} per hit; many servers
 *       rate-limit or kick for that.</li>
 * </ul>
 *
 * <p>The trade-off is that the alternating side is purely a local
 * cosmetic - other clients see our swings on whichever arm vanilla's
 * options say, not the flipped one. That's intentional: this module
 * is a client-side tell for the local player, not a network-visible
 * effect.
 */
public final class ChangeHand extends Module {
    private static final ChangeHand INSTANCE = new ChangeHand();

    // --- Main hand offset / scale ----------------------------------
    // Ranges mirror padej/soup HandTweaks since those values are well
    // tested on first-person item rendering: X/Y stay within +/-1 (one
    // block worth of translation is more than enough on the camera-
    // space matrix), Z opens up to +/-2.5 so the user can push the
    // item right into the screen or hide it behind themselves, and
    // Scale clamps between 0.1 and 2.0 to avoid degenerate matrices
    // (0 collapses the model, 4+ tanks GPU fillrate).
    public final SectionSetting mainHandSection = new SectionSetting("Main Hand");
    public final ValueSetting mainHandX = new ValueSetting("Main X", "Horizontal offset of the main hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting mainHandY = new ValueSetting("Main Y", "Vertical offset of the main hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting mainHandZ = new ValueSetting("Main Z", "Depth (forward/back) offset of the main hand item")
            .range(-2.5f, 2.5f).step(0.01f).setValue(0.0f);
    public final ValueSetting mainHandScale = new ValueSetting("Main Scale", "Size multiplier of the main hand item")
            .range(0.1f, 2.0f).step(0.01f).setValue(1.0f);

    // --- Off hand offset / scale -----------------------------------
    public final SectionSetting offHandSection = new SectionSetting("Off Hand");
    public final ValueSetting offHandX = new ValueSetting("Off X", "Horizontal offset of the off hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting offHandY = new ValueSetting("Off Y", "Vertical offset of the off hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting offHandZ = new ValueSetting("Off Z", "Depth (forward/back) offset of the off hand item")
            .range(-2.5f, 2.5f).step(0.01f).setValue(0.0f);
    public final ValueSetting offHandScale = new ValueSetting("Off Scale", "Size multiplier of the off hand item")
            .range(0.1f, 2.0f).step(0.01f).setValue(1.0f);

    // --- Side switch -----------------------------------------------
    // Kept BELOW the position/scale sliders in the GUI because the
    // sliders are the more frequently tweaked controls; users land
    // on the module to nudge their item position, not to set up
    // alternating-arm slap binds.
    public final SectionSetting switchSection = new SectionSetting("Switch Side");
    public final BooleanSetting uponImpact = new BooleanSetting(
            "Upon Impact",
            "Flip the main arm every time you successfully hit an entity"
    ).setValue(true);
    public final BindSetting keybind = new BindSetting(
            "Bind",
            "Key to manually flip the main arm. Only used when Upon Impact is OFF."
    );

    private ChangeHand() {
        super("changehand", "Change Hand", ModuleCategory.OTHER);
        // Full-width on every slider keeps the panel readable - the
        // labels ("Main Scale", etc.) read awkwardly when split into
        // a two-column grid alongside a thin slider.
        mainHandX.setFullWidth(true);
        mainHandY.setFullWidth(true);
        mainHandZ.setFullWidth(true);
        mainHandScale.setFullWidth(true);
        offHandX.setFullWidth(true);
        offHandY.setFullWidth(true);
        offHandZ.setFullWidth(true);
        offHandScale.setFullWidth(true);
        uponImpact.setFullWidth(true);
        keybind.setFullWidth(true);
        // The bind only makes sense when Upon Impact is OFF - otherwise
        // impacts already flip the arm and a manual key would just be
        // a redundant second source of input that fights over the arm
        // side on every hit. Hiding the row keeps the panel tidy.
        keybind.visible(() -> !uponImpact.isValue());
        setup(
                mainHandSection, mainHandX, mainHandY, mainHandZ, mainHandScale,
                offHandSection, offHandX, offHandY, offHandZ, offHandScale,
                switchSection, uponImpact, keybind
        );
    }

    public static ChangeHand getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Changes the visual properties of your hands - position, scale, and active side";
    }

    @Override
    public String getIcon() {
        return "hand.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Convenience accessor used by {@code HeldItemRendererMixin} to
     * decide whether to apply the offset / scale transforms at all.
     * The default values (X=Y=Z=0, Scale=1) are no-ops and we can
     * skip the matrix work entirely on the common path.
     */
    public boolean hasMainHandTransform() {
        return mainHandX.getValue() != 0.0f
                || mainHandY.getValue() != 0.0f
                || mainHandZ.getValue() != 0.0f
                || mainHandScale.getValue() != 1.0f;
    }

    public boolean hasOffHandTransform() {
        return offHandX.getValue() != 0.0f
                || offHandY.getValue() != 0.0f
                || offHandZ.getValue() != 0.0f
                || offHandScale.getValue() != 1.0f;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    /**
     * Flip the local player's main-arm DataTracker entry. We
     * intentionally don't touch {@code client.options.getMainArm()}
     * here - that path persists to {@code options.txt} and pings the
     * server with a settings packet, neither of which we want for a
     * per-hit cosmetic toggle (see the class javadoc for the full
     * reasoning).
     *
     * <p>{@code HeldItemRenderer} and the player model both read arm
     * side from {@link net.minecraft.entity.player.PlayerEntity#getMainArm()},
     * which is backed by {@code DataTracker.MAIN_ARM}, so writing the
     * tracker via {@link net.minecraft.entity.player.PlayerEntity#setMainArm}
     * is sufficient for the visual flip and leaves no fingerprint in
     * the vanilla options file after relogin.
     *
     * <p>Reading the "current" arm from {@code player.getMainArm()}
     * (rather than the options) keeps the flip self-consistent: the
     * source of truth and the write target are the same field.
     */
    public void flipMainArm() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        Arm current = client.player.getMainArm();
        Arm flipped = (current == Arm.LEFT) ? Arm.RIGHT : Arm.LEFT;
        client.player.setMainArm(flipped);
    }

    /**
     * Called from {@code ClientPlayerInteractionManagerMixin} on every
     * successful {@code attackEntity}. Gated on both the module being
     * enabled and the Upon Impact toggle so the user can keep the
     * module loaded but switch to bind-only mode without leaking
     * arm-flips into ordinary combat.
     */
    public void onAttackEntity() {
        if (!isEnabled() || !uponImpact.isValue()) {
            return;
        }
        flipMainArm();
    }

    /**
     * Called from the keyboard mixin on every key event. Mirrors the
     * pattern used by {@code FreeLook.onBindStateChanged}: ignore key
     * RELEASE so we don't fire twice per tap, ignore unbound state so
     * a fresh user doesn't accidentally flip on the F-key default,
     * and short-circuit when Upon Impact is the active trigger so the
     * bind doesn't double up on hit-driven flips.
     *
     * <p>Also gated on {@code client.currentScreen == null} so
     * typing in chat / inventory doesn't smuggle a flip via the same
     * letter key.
     */
    public void onBindStateChanged(int code, int action) {
        if (!isEnabled() || uponImpact.isValue()) {
            return;
        }
        int bound = keybind.getKey();
        if (bound == GLFW.GLFW_KEY_UNKNOWN || code != bound) {
            return;
        }
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        flipMainArm();
    }
}
