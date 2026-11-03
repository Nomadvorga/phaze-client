package vorga.phazeclient.implement.features.modules.other;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;

/** Immediate held aim and interpolated live trails, buffered independently of world/entity consumers. */
public final class PredictionsRenderer {
    private static final PredictionRenderBatch BATCH = new PredictionRenderBatch();
    private static final java.util.ArrayList<HeldPredictionView> TRAJECTORIES = new java.util.ArrayList<>(3);
    private static final java.util.ArrayList<HeldPredictionView> HELD_VIEWS = new java.util.ArrayList<>(3);
    private static final java.util.ArrayList<ImpactMark> IMPACT_MARKS = new java.util.ArrayList<>(3);
    private static final Matrix4f CENTER_FORWARD = new Matrix4f();
    private static final Matrix4f CENTER_INVERSE = new Matrix4f();
    private static final HeldLaunchState HELD_LAUNCH = new HeldLaunchState();
    private static final HeldLineAnchor HELD_LINE_ANCHOR = new HeldLineAnchor();
    private static final Animation entityGrow;

    static {
        entityGrow = new DecelerateAnimation().setMs(180).setValue(1);
        entityGrow.setDirection(Direction.BACKWARDS);
    }

    private PredictionsRenderer() {}

    /** Third-person launch anchor. First-person uses the real camera and its smooth eye height. */
    private static Vec3d lerpedEyePos(MinecraftClient mc, RenderTickCounter tickCounter) {
        return mc.player.getCameraPosVec(tickCounter.getTickDelta(false));
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        Predictions module = Predictions.getInstance();
        if (module == null || !module.isEnabled()) {
            HELD_LINE_ANCHOR.reset();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            HELD_LINE_ANCHOR.reset();
            return;
        }

        // Resolve frame inputs once; geometry is appended to reusable independent buffers.
        int accent = module.resolveAccentColor();
        int entityAccent = module.entityColorEnabled.isValue() ? module.resolveEntityHitColor() : accent;
        float tickDelta = tickCounter.getTickDelta(false);
        float width = Math.max(1.0F, module.lineWidth.getInt());
        float fadeDistance = module.fadeTrail.isValue() ? module.fadeDistance.getValue() : 0;
        boolean showMarkers = module.showImpactSphere.isValue();
        boolean sphere = !"Circle".equalsIgnoreCase(module.impactMarkerStyle.getSelected());
        float baseRadius = module.impactSphereRadius.getValue();
        float yOffset = module.sphereYOffset.getValue();
        float thickness = module.circleThickness.getValue();
        int sphereAlpha = Math.max(0, Math.min(255, Math.round(module.sphereOpacity.getValue() * 2.55F)));
        float strength = module.glowStrength.getValue();
        float haloScale = 1.6F + strength * 1.4F;
        int haloAlpha = showMarkers && module.showGlow.isValue()
                ? Math.max(0, Math.min(255, Math.round(110.0F * Math.min(2.0F, strength) * phaze$pulseFactor(module)))) : 0;
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        CENTER_FORWARD.set(projection).mul(RenderSystem.getModelViewMatrix()).mul(matrices.peek().getPositionMatrix());
        float focalPixels = Math.max(Math.abs(projection.m00()) * mc.getWindow().getFramebufferWidth(),
                Math.abs(projection.m11()) * mc.getWindow().getFramebufferHeight()) * 0.5F;

        TRAJECTORIES.clear();
        IMPACT_MARKS.clear();
        java.util.List<HeldPredictionView> trajectories = TRAJECTORIES;
        java.util.List<ImpactMark> marks = IMPACT_MARKS;
        if (module.shouldPredictHeld()) {
            collectHeldHand(mc, module, tickCounter, matrices, cameraPos, trajectories, marks);
        }
        if (trajectories.isEmpty()) {
            for (HeldPredictionView view : HELD_VIEWS) view.reset();
            HELD_LAUNCH.reset();
            HELD_LINE_ANCHOR.reset();
        }

        boolean anyEntity = false;
        if (showMarkers) {
            for (ImpactMark mark : marks) {
                if (mark.entity) { anyEntity = true; break; }
            }
        }
        entityGrow.setDirection(anyEntity ? Direction.FORWARDS : Direction.BACKWARDS);
        float entityScale = 1.0F + 0.30F * entityGrow.getOutputFloat();

        BATCH.begin(matrices, mc.gameRenderer.getCamera().getRotation(), CENTER_FORWARD, focalPixels, width, sphere);
        try {
            for (var trail : module.getTrails()) {
                var result = trail.result;
                if (trail.entity != null) {
                    Vec3d position = new Vec3d(
                            net.minecraft.util.math.MathHelper.lerp(tickDelta, trail.entity.lastRenderX, trail.entity.getX()),
                            net.minecraft.util.math.MathHelper.lerp(tickDelta, trail.entity.lastRenderY, trail.entity.getY()),
                            net.minecraft.util.math.MathHelper.lerp(tickDelta, trail.entity.lastRenderZ, trail.entity.getZ()));
                    var points = trail.getRemainingPath(position);
                    if (points != null && points.size() >= 2) {
                        if (result != null && result.type() != HitResult.Type.MISS) points.set(points.size() - 1, result.impact());
                        int color = result != null && result.type() == HitResult.Type.ENTITY ? entityAccent : accent;
                        BATCH.trail(points, cameraPos, color, fadeDistance);
                    }
                }
                // Live markers stay pinned to the exact hit, without held-entity front shifting/easing.
                if (showMarkers && result != null && result.type() != HitResult.Type.MISS && result.impact() != null) {
                    boolean entity = result.type() == HitResult.Type.ENTITY;
                    appendMarker(result.impact(), entity, result.face(), false, cameraPos, sphere, baseRadius,
                            yOffset, thickness, entity ? entityAccent : accent, sphereAlpha, haloScale, haloAlpha);
                }
            }
            for (HeldPredictionView view : trajectories) {
                BATCH.path(view, cameraPos, view.result().type() == HitResult.Type.ENTITY ? entityAccent : accent);
            }
            if (showMarkers) {
                for (ImpactMark mark : marks) {
                    appendMarker(mark.pos, mark.entity, mark.face, true, cameraPos, sphere,
                            mark.entity ? baseRadius * entityScale : baseRadius, yOffset, thickness,
                            mark.entity ? entityAccent : accent, sphereAlpha, haloScale, haloAlpha);
                }
            }
            // At most three draws: every line, then additive halos, then marker bodies.
            BATCH.draw();
        } finally {
            BATCH.discard();
        }
    }

    private static void appendMarker(Vec3d impact, boolean entity, net.minecraft.util.math.Direction face,
                                     boolean held, Vec3d camera, boolean sphere, float radius, float yOffset,
                                     float thickness, int accent, int sphereAlpha, float haloScale, int haloAlpha) {
        double x = impact.x - camera.x, y = impact.y - camera.y, z = impact.z - camera.z;
        if (held && entity) {
            double distance = Math.sqrt(x * x + y * y + z * z);
            if (distance >= 1.0E-4) {
                double shift = radius * 1.05 / distance;
                x -= x * shift;
                y -= y * shift;
                z -= z * shift;
            }
        }
        float nx = entity ? 0 : face.getOffsetX(), ny = entity ? 1 : face.getOffsetY(), nz = entity ? 0 : face.getOffsetZ();
        if (!entity) {
            if (sphere) y += yOffset;
            else { x += nx * 0.02F; y += ny * 0.02F; z += nz * 0.02F; }
        }
        float cx = (float) x, cy = (float) y, cz = (float) z;
        float haloRadius = radius * haloScale;
        // Test the full billboard (including corners), not just the impact point at screen edges.
        if (haloAlpha > 0 && BATCH.visible(cx, cy, cz, haloRadius * 1.415F)) {
            BATCH.glow(cx, cy, cz, haloRadius, (haloAlpha << 24) | (accent & 0x00FFFFFF));
        }
        if (sphere) {
            if (sphereAlpha > 0 && BATCH.visible(cx, cy, cz, radius)) {
                BATCH.sphere(cx, cy, cz, radius, (sphereAlpha << 24) | (accent & 0x00FFFFFF));
            }
        } else {
            float ringRadius = entity ? radius : radius * 1.35F;
            if (BATCH.visible(cx, cy, cz, ringRadius + thickness * 0.5F)) {
                BATCH.ring(cx, cy, cz, nx, ny, nz, ringRadius, thickness, accent);
            }
        }
    }

    private static void collectHeldHand(MinecraftClient mc, Predictions module,
                                        RenderTickCounter tickCounter,
                                        MatrixStack matrices, Vec3d cameraPos,
                                        java.util.List<HeldPredictionView> out,
                                        java.util.List<ImpactMark> marks) {
        PlayerEntity p = mc.player;
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        ItemStack chosen = pickThrowable(module, main, off);
        if (chosen == null) return;

        Predictions.HeldType type = module.classifyHeldStack(chosen);
        if (type == Predictions.HeldType.BOW || type == Predictions.HeldType.TRIDENT) {
            // These weapons launch only when a sufficiently charged active item is released.
            if (!p.isUsingItem()) return;
            ItemStack active = p.getActiveItem();
            if (active == null || active.isEmpty()) return;
            if (!ItemStack.areItemsAndComponentsEqual(active, chosen)) return;
        }
        double speed = module.initialVelocityFor(type, chosen, mc);
        if (speed <= 0.0) return;

        float delta = tickCounter.getTickDelta(false);
        HELD_LAUNCH.update(mc.world, p, type, mc.world.getTime(), p.getYaw(), p.getPitch(),
                p.getMovement(), p.isOnGround(), speed);
        boolean aiming = HELD_LAUNCH.needsFrameSampling();
        Vec3d look = p.getRotationVec(1.0F);
        // Movement alone costs one simulation per tick/bolt; rendering interpolates cached paths.
        Vec3d start = ProjectilePhysics.launchPosition(new Vec3d(p.getX(), p.getEyeY(), p.getZ()));
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        Vec3d visualStart = ProjectilePhysics.launchPosition(firstPerson ? cameraPos : lerpedEyePos(mc, tickCounter));
        if (aiming) {
            // ONE immediate simulation from smoothly sampled launch inputs during mouse input.
            start = visualStart;
            speed = HELD_LAUNCH.speed(delta);
        }
        Vec3d motion = Predictions.initialMotionFor(type, p.getYaw(), p.getPitch(), speed,
                aiming ? HELD_LAUNCH.movement(delta) : p.getMovement(), aiming ? false : p.isOnGround());
        if (firstPerson) CENTER_INVERSE.set(CENTER_FORWARD).invert();
        ProjectilePhysics.Profile profile = ProjectilePhysics.Profile.forHeld(type);

        // Crossbow shots rotate around local up, not world Y; never replace the ballistic arc.
        int boltCount = type == Predictions.HeldType.CROSSBOW ? module.loadedCrossbowProjectileCount(chosen) : 1;
        if (boltCount > 1) {
            Vec3d up = p.getOppositeRotationVector(1.0F);
            float spread = module.hasMultishot(chosen) ? 10.0F : 0.0F;
            for (int bolt = 0; bolt < boltCount; bolt++) {
                float angle = ProjectilePhysics.crossbowAngle(bolt, boltCount, spread);
                Vec3d fanMotion = ProjectilePhysics.crossbowMotion(look, up, angle, speed);
                Predictions.TrajectoryResult r = module.predictCached(start, fanMotion, profile, p);
                if (r == null || r.path() == null || r.path().size() < 2) continue;
                collectHeldView(mc, type, bolt, boltCount, angle, r, tickCounter, aiming, firstPerson, visualStart,
                        matrices, cameraPos, out, marks);
            }
            return;
        }

        if (type == Predictions.HeldType.CROSSBOW) {
            motion = ProjectilePhysics.crossbowMotion(look, p.getOppositeRotationVector(1.0F), 0.0F, speed);
        }
        Predictions.TrajectoryResult result = module.predictCached(start, motion, profile, p);
        if (result == null || result.path() == null || result.path().size() < 2) return;
        collectHeldView(mc, type, 0, 1, 0.0F, result, tickCounter, aiming, firstPerson, visualStart,
                matrices, cameraPos, out, marks);
    }

    private static void collectHeldView(MinecraftClient mc, Predictions.HeldType type, int bolt, int boltCount,
                                        float angle, Predictions.TrajectoryResult result, RenderTickCounter tickCounter,
                                        boolean immediate, boolean firstPerson, Vec3d visualStart,
                                        MatrixStack matrices, Vec3d cameraPos, java.util.List<HeldPredictionView> out,
                                        java.util.List<ImpactMark> marks) {
        while (HELD_VIEWS.size() <= bolt) HELD_VIEWS.add(new HeldPredictionView());
        HeldPredictionView view = HELD_VIEWS.get(bolt);
        if (!immediate && (HELD_LAUNCH.isSamplingSurface()
                || view.needsSurfaceSampling(mc.world, mc.player, type, mc.world.getTime(), result))) {
            // Stairs/ledges cannot be blended between collision planes without inventing an impact.
            HELD_LAUNCH.sampleSurfaceTransition();
            immediate = true;
            float delta = tickCounter.getTickDelta(false);
            double speed = HELD_LAUNCH.speed(delta);
            Vec3d motion = type == Predictions.HeldType.CROSSBOW
                    ? ProjectilePhysics.crossbowMotion(mc.player.getRotationVec(1), mc.player.getOppositeRotationVector(1), angle, speed)
                    : Predictions.initialMotionFor(type, mc.player.getYaw(), mc.player.getPitch(), speed,
                            HELD_LAUNCH.movement(delta), false);
            Predictions.TrajectoryResult sampled = Predictions.getInstance().predictCached(visualStart, motion,
                    ProjectilePhysics.Profile.forHeld(type), mc.player);
            if (sampled != null && sampled.path() != null && sampled.path().size() >= 2) result = sampled;
        }
        view.update(mc.world, mc.player, type, boltCount, angle, mc.world.getTime(),
                mc.player.getYaw(), mc.player.getPitch(), result);
        if (immediate) view.useImmediateResult();
        view.prepare(tickCounter.getTickDelta(false));
        if (!firstPerson) {
            HELD_LINE_ANCHOR.reset();
            view.setWorldLaunchOrigin(visualStart);
        } else {
            Vec3d lineOrigin;
            if (out.isEmpty()) {
                // Turning alone must not drag the origin; share a fixed-depth movement sample across the fan.
                float delta = tickCounter.getTickDelta(false);
                double speed = HELD_LAUNCH.speed(delta);
                Vec3d movement = HELD_LAUNCH.movement(delta);
                Vec3d aimMotion = type == Predictions.HeldType.CROSSBOW
                        ? ProjectilePhysics.crossbowMotion(mc.player.getRotationVec(1), mc.player.getOppositeRotationVector(1), 0, speed)
                        : Predictions.initialMotionFor(type, mc.player.getYaw(), mc.player.getPitch(), speed,
                                Vec3d.ZERO, false);
                lineOrigin = HELD_LINE_ANCHOR.updateForLaunch(mc.world, mc.player, type, mc.world.getTime(), System.nanoTime(),
                        RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix(),
                        matrices.peek().getPositionMatrix(), CENTER_INVERSE,
                        cameraPos, visualStart, aimMotion, movement, ProjectilePhysics.Profile.forHeld(type));
            } else {
                lineOrigin = HELD_LINE_ANCHOR.origin();
            }
            view.setLineOrigin(lineOrigin, CENTER_FORWARD, CENTER_INVERSE, cameraPos);
        }
        out.add(view);
        if (result.type() != HitResult.Type.MISS) {
            marks.add(new ImpactMark(view.impact(), result.type() == HitResult.Type.ENTITY, result.face(), result.entity()));
        }
    }

    /** Frame-wide glow pulse, independent of tick rate and without a fade-in on aiming. */
    private static float phaze$pulseFactor(Predictions module) {
        if (!module.glowPulsate.isValue()) return 1.0F;
        float speed = module.glowPulsateSpeed.getValue();
        if (speed <= 0.0F) speed = 0.0001F;
        double t = (System.nanoTime() / 1_000_000_000.0) * speed;
        float s = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0));
        return 0.1F + s * 0.9F;
    }

    private static ItemStack pickThrowable(Predictions module, ItemStack main, ItemStack off) {
        if (module.classifyHeldStack(main) != Predictions.HeldType.NONE) return main;
        if (module.classifyHeldStack(off) != Predictions.HeldType.NONE) return off;
        return null;
    }

    /** Physical hit position and face, used only for marker placement. */
    private record ImpactMark(Vec3d pos, boolean entity, net.minecraft.util.math.Direction face, Entity entityRef) {}
}
