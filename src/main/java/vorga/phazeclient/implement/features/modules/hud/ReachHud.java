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

    private static final long RESET_TIMEOUT_MS = 5_000L;

    private static final ReachHud INSTANCE = new ReachHud();

    private float lastReach = 0.0f;

    private long lastHitTimeMillis = 0L;

    public final SectionSetting otherSection = new SectionSetting("Other");

    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Add \"Reach:\" prefix instead of just \"X blocks\"").setValue(false);

    public static ReachHud getInstance() {
        return INSTANCE;
    }

    private ReachHud() {
        super("reach_hud", "Reach HUD");
        reverseOrder.setFullWidth(true);
        setup(otherSection, reverseOrder, cornerRounding);
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

    public void notifyAirSwing() {
        this.lastReach = 0.0f;
        this.lastHitTimeMillis = 0L;
    }

    public String getFormattedReach() {

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

        return reverseOrder.isValue() ? "Reach: " + value : value;
    }
}
