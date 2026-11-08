package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import vorga.phazeclient.implement.features.modules.hud.PaperDoll;
import vorga.phazeclient.mixins.AbstractClientPlayerEntityAccessor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws {@link PaperDoll}: the camera entity (plus whatever it rides) posed
 * from a fixed view. Every field it overrides is put back before returning.
 */
public final class PaperDollRenderer {
    private static final long AUTO_HIDE_LINGER_MS = 500L;
    private static final float RADIANS = (float) (Math.PI / 180.0);
    private static final int MAX_ENTITIES = 8;

    private static final Quaternionf ORIENTATION = new Quaternionf();
    private static final Quaternionf TILT = new Quaternionf();
    private static final Quaternionf CAMERA = new Quaternionf();
    private static final Snapshot[] SNAPSHOTS = new Snapshot[MAX_ENTITIES];
    private static final Consumer<VertexConsumerProvider> RENDER_ALL = PaperDollRenderer::renderAll;

    static {
        for (int i = 0; i < MAX_ENTITIES; i++) SNAPSHOTS[i] = new Snapshot();
    }

    private static int count;
    private static boolean drawing;
    private static long visibleUntilMs;
    private static MatrixStack renderMatrices;
    private static EntityRenderDispatcher renderDispatcher;
    private static float renderTickDelta;

    private PaperDollRenderer() {
    }

    public static boolean isDrawing() {
        return drawing;
    }

    public static Entity subject(MinecraftClient client) {
        Entity camera = client.getCameraEntity();
        return camera != null ? camera : client.player;
    }

    /** Visibility rules that only apply outside the HUD editor. */
    public static boolean isHidden(MinecraftClient client, PaperDoll doll, Entity subject) {
        if (doll.hideDuringF3.isValue() && client.getDebugHud().shouldShowDebugHud()) return true;
        if (doll.hideDuringScreens.isValue() && client.currentScreen != null) return true;
        if (doll.hideInF5.isValue() && !client.options.getPerspective().isFirstPerson()) return true;
        if (doll.autoHide.isValue() && subject instanceof LivingEntity living) {
            long now = Util.getMeasuringTimeMs();
            if (isBusy(living)) {
                visibleUntilMs = now + AUTO_HIDE_LINGER_MS;
            } else if (now > visibleUntilMs) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBusy(LivingEntity entity) {
        return entity.isInSneakingPose() || entity.isSprinting() || entity.isGliding() || entity.isInSwimmingPose()
                || entity.hasVehicle() || entity.isBlocking() || entity.isUsingItem() || entity.handSwinging
                || entity.hurtTime > 0 || entity.isOnFire() || entity.inPowderSnow;
    }

    /**
     * @param feetX GUI x of the subject's feet
     * @param feetY GUI y of the subject's feet
     * @param size  GUI pixels per block
     */
    public static void draw(DrawContext context, PaperDoll doll, Entity subject,
                            float feetX, float feetY, float size, float tickDelta) {
        float turn = (float) Math.atan(-doll.lookingSides.getValue() / 40.0F);
        float tilt = (float) Math.atan(doll.lookingUpDown.getValue() / 40.0F);
        float yaw = 180.0F + turn * 40.0F;
        float bodyYaw = 180.0F + turn * 20.0F;
        float fixedPitch = -tilt * 20.0F;

        if (subject instanceof LivingEntity living && (living.isGliding() || living.isUsingRiptide())) {
            // A horizontal body pivots around the feet, so lift it back into the panel.
            float ticks = living.getGlidingTicks() + tickDelta;
            float progress = MathHelper.clamp(ticks * ticks / 100.0F, 0.0F, 1.0F);
            feetY -= size * (1.0F + progress / 90.0F) - size * 0.2F;
        }

        Entity root = subject.getRootVehicle();
        collect(subject, root);

        float turnBy = (facing(root, yaw, bodyYaw) - realFacing(root, tickDelta)) * RADIANS;
        float sin = MathHelper.sin(turnBy);
        float cos = MathHelper.cos(turnBy);
        double rootX = MathHelper.lerp(tickDelta, root.prevX, root.getX());
        double rootZ = MathHelper.lerp(tickDelta, root.prevZ, root.getZ());
        double subjectY = MathHelper.lerp(tickDelta, subject.prevY, subject.getY());

        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        MatrixStack matrices = context.getMatrices();
        int captured = 0;
        drawing = true;
        try {
            for (; captured < count; captured++) {
                Snapshot snapshot = SNAPSHOTS[captured];
                Entity entity = snapshot.entity;
                snapshot.capture();
                double dx = MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) - rootX;
                double dz = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) - rootZ;
                snapshot.offsetX = dx * cos - dz * sin;
                snapshot.offsetZ = dx * sin + dz * cos;
                snapshot.offsetY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) - subjectY;
                pose(snapshot, doll, yaw, bodyYaw, fixedPitch);
            }

            matrices.push();
            try {
                matrices.translate(feetX, feetY, 50.0F);
                matrices.scale(size, size, -size);
                TILT.rotationX(-tilt * 20.0F * RADIANS);
                ORIENTATION.rotationZ((float) Math.PI).mul(TILT);
                matrices.multiply(ORIENTATION);
                TILT.conjugate(CAMERA).rotateY((float) Math.PI);

                DiffuseLighting.method_34742();
                dispatcher.setRotation(CAMERA);
                dispatcher.setRenderShadows(false);
                renderMatrices = matrices;
                renderDispatcher = dispatcher;
                renderTickDelta = tickDelta;
                context.draw(RENDER_ALL);
            } finally {
                renderMatrices = null;
                renderDispatcher = null;
                dispatcher.setRenderShadows(true);
                matrices.pop();
                DiffuseLighting.enableGuiDepthLighting();
            }
        } finally {
            for (int i = captured - 1; i >= 0; i--) SNAPSHOTS[i].restore();
            for (int i = 0; i < count; i++) SNAPSHOTS[i].entity = null;
            count = 0;
            drawing = false;
        }
    }

    private static void renderAll(VertexConsumerProvider consumers) {
        for (int i = 0; i < count; i++) {
            Snapshot snapshot = SNAPSHOTS[i];
            renderDispatcher.render(snapshot.entity, snapshot.offsetX, snapshot.offsetY, snapshot.offsetZ,
                    renderTickDelta, renderMatrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    /** The vehicle first so riders composite over it, the subject last. */
    private static void collect(Entity subject, Entity root) {
        count = 0;
        if (root != subject) {
            add(root);
            List<Entity> passengers = root.getPassengerList();
            for (int i = 0, n = passengers.size(); i < n; i++) {
                Entity passenger = passengers.get(i);
                if (passenger != subject) add(passenger);
            }
        }
        add(subject);
    }

    private static void add(Entity entity) {
        if (count < MAX_ENTITIES) SNAPSHOTS[count++].entity = entity;
    }

    private static float facing(Entity entity, float yaw, float bodyYaw) {
        return entity instanceof LivingEntity ? bodyYaw : yaw;
    }

    private static float realFacing(Entity entity, float tickDelta) {
        if (entity instanceof LivingEntity living) {
            return MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw);
        }
        return MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
    }

    private static void pose(Snapshot snapshot, PaperDoll doll, float yaw, float bodyYaw, float fixedPitch) {
        Entity entity = snapshot.entity;
        boolean pitchLocked = doll.pitchLocked();
        if (entity instanceof AbstractClientPlayerEntity player) {
            ((AbstractClientPlayerEntityAccessor) player).phaze$setLastVelocity(Vec3d.ZERO);
        }
        if (entity instanceof LivingEntity living) {
            boolean gliding = living.isGliding();
            boolean spinning = living.isUsingRiptide();
            if (gliding || spinning) entity.setVelocity(Vec3d.ZERO);
            pitchLocked |= gliding && doll.lockElytraRotation.isValue()
                    || spinning && doll.lockSpinningRotation.isValue();

            living.bodyYaw = bodyYaw;
            living.prevBodyYaw = bodyYaw;
            if (doll.headLocked()) {
                living.headYaw = yaw;
                living.prevHeadYaw = yaw;
            } else if (doll.headFollowsBody()) {
                living.headYaw = yaw + snapshot.headYaw - snapshot.bodyYaw;
                living.prevHeadYaw = yaw + snapshot.prevHeadYaw - snapshot.prevBodyYaw;
            } else {
                living.headYaw = yaw + snapshot.headYaw - snapshot.yaw;
                living.prevHeadYaw = yaw + snapshot.prevHeadYaw - snapshot.prevYaw;
            }
        }
        entity.setYaw(yaw);
        entity.prevYaw = yaw;
        if (pitchLocked) {
            entity.setPitch(fixedPitch);
            entity.prevPitch = fixedPitch;
        }
    }

    private static final class Snapshot {
        Entity entity;
        float yaw;
        float prevYaw;
        float pitch;
        float prevPitch;
        float bodyYaw;
        float prevBodyYaw;
        float headYaw;
        float prevHeadYaw;
        Vec3d velocity;
        Vec3d lastVelocity;
        double offsetX;
        double offsetY;
        double offsetZ;

        void capture() {
            yaw = entity.getYaw();
            prevYaw = entity.prevYaw;
            pitch = entity.getPitch();
            prevPitch = entity.prevPitch;
            velocity = entity.getVelocity();
            if (entity instanceof LivingEntity living) {
                bodyYaw = living.bodyYaw;
                prevBodyYaw = living.prevBodyYaw;
                headYaw = living.headYaw;
                prevHeadYaw = living.prevHeadYaw;
            }
            lastVelocity = entity instanceof AbstractClientPlayerEntity player
                    ? ((AbstractClientPlayerEntityAccessor) player).phaze$getLastVelocity() : null;
        }

        void restore() {
            entity.setYaw(yaw);
            entity.prevYaw = prevYaw;
            entity.setPitch(pitch);
            entity.prevPitch = prevPitch;
            entity.setVelocity(velocity);
            if (entity instanceof LivingEntity living) {
                living.bodyYaw = bodyYaw;
                living.prevBodyYaw = prevBodyYaw;
                living.headYaw = headYaw;
                living.prevHeadYaw = prevHeadYaw;
            }
            if (entity instanceof AbstractClientPlayerEntity player) {
                ((AbstractClientPlayerEntityAccessor) player).phaze$setLastVelocity(lastVelocity);
            }
            velocity = null;
            lastVelocity = null;
        }
    }
}
