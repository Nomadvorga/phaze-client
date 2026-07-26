package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

import java.util.Locale;

public final class ReachHud extends RectHudModule {
    /**
     * Window during which the last successful entity hit stays
     * displayed. Once {@link System#currentTimeMillis()} surpasses
     * {@link #lastHitTimeMillis} by more than this value, the HUD
     * collapses back to {@code 0 blocks}.
     */
    private static final long RESET_TIMEOUT_MS = 5_000L;

    private static final ReachHud INSTANCE = new ReachHud();

    /**
     * Last measured reach distance in blocks. Defaults to {@code 0} so
     * a fresh client shows {@code "0 blocks"} instead of the previous
     * "{@code -- blocks"} placeholder; the user reads {@code 0} as
     * "I haven't hit anything yet" without needing to learn a glyph.
     */
    private float lastReach = 0.0f;

    /**
     * Wall-clock timestamp of the most recent successful entity hit.
     * {@code 0} when no hit has been recorded yet; in that case the
     * idle-timeout check in {@link #getFormattedReach()} short-circuits
     * and leaves {@link #lastReach} untouched.
     */
    private long lastHitTimeMillis = 0L;

    public final SectionSetting otherSection = new SectionSetting("Other");

    /**
     * Swap the {@code Reach} label position. Default OFF renders the
     * minimal {@code "4 blocks"} form (back-compat with how the HUD
     * has always looked); ON adds the {@code "Reach: "} prefix so the
     * value reads as {@code "Reach: 4 blocks"}. The toggle is named
     * "Reverse Order" for consistency with the rest of the HUD set.
     */
    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Add \"Reach:\" prefix instead of just \"X blocks\"").setValue(false);

    public static ReachHud getInstance() {
        return INSTANCE;
    }

    private ReachHud() {
        super("reach_hud", "Reach HUD");
        reverseOrder.setFullWidth(true);
        setup(otherSection, reverseOrder);
    }

    @Override
    public String getDescription() {
        return "Shows hit distance on HUD";
    }

    @Override
    public String getIcon() {
        return "reach_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public void recordHitDistance(PlayerEntity player, Entity target) {
        if (player == null || target == null) {
            return;
        }

        Vec3d eyePos = player.getEyePos();
        Box box = target.getBoundingBox();
        double closestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double closestY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double closestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);

        double dx = eyePos.x - closestX;
        double dy = eyePos.y - closestY;
        double dz = eyePos.z - closestZ;
        this.lastReach = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.lastHitTimeMillis = System.currentTimeMillis();
    }

    /**
     * Called by the {@code doAttack} mixin whenever the player swings
     * without a valid entity under the crosshair. Air swings clear the
     * displayed value because the previous reading would otherwise
     * linger on screen even though the player is no longer in combat.
     */
    public void notifyAirSwing() {
        this.lastReach = 0.0f;
        this.lastHitTimeMillis = 0L;
    }

    public String getFormattedReach() {
        // Idle-timeout collapse: if more than RESET_TIMEOUT_MS has
        // elapsed since the last successful hit, the value goes back
        // to 0. The {@code lastHitTimeMillis > 0} guard prevents the
        // very first frame after world-load (where lastHitTimeMillis
        // is still its 0 sentinel) from being treated as a 5-second
        // overrun and reset, which would look identical to the user
        // but is wasted work.
        if (lastHitTimeMillis > 0L
                && System.currentTimeMillis() - lastHitTimeMillis > RESET_TIMEOUT_MS) {
            lastReach = 0.0f;
            lastHitTimeMillis = 0L;
        }

        float rounded = Math.round(lastReach);
        String value;
        if (Math.abs(lastReach - rounded) < 0.005f) {
            value = (int) rounded + " blocks";
        } else {
            value = String.format(Locale.US, "%.2f blocks", lastReach);
        }
        // Optional "Reach: " prefix when the user wants the labelled
        // form. Default OFF preserves the minimal value-only display
        // the HUD has always shown.
        return reverseOrder.isValue() ? "Reach: " + value : value;
    }
}
