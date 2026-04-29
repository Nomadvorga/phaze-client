package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public final class ReachHud extends RectHudModule {
    private static final ReachHud INSTANCE = new ReachHud();
    private float lastReach = -1.0f;

    public static ReachHud getInstance() {
        return INSTANCE;
    }

    private ReachHud() {
        super("reach_hud", "Reach HUD");
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
    }

    public String getFormattedReach() {
        if (lastReach < 0.0f) {
            return "-- blocks";
        }

        float rounded = Math.round(lastReach);
        if (Math.abs(lastReach - rounded) < 0.005f) {
            return (int) rounded + " blocks";
        }
        return String.format(Locale.US, "%.2f blocks", lastReach);
    }
}
