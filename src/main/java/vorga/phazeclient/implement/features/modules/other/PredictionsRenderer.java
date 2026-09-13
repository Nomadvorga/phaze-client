package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.render.Render3DUtil;

public final class PredictionsRenderer {

    private static final net.minecraft.util.Identifier GLOW_TEXTURE =
            net.minecraft.util.Identifier.of("phaze", "textures/particles/bloom/bloom_soft.png");
    private static final float[] MULTISHOT_YAW_OFFSETS = {-10.0F, 0.0F, 10.0F};
    private static final java.util.ArrayList<Predictions.TrajectoryResult> TRAJECTORIES = new java.util.ArrayList<>(3);
    private static final java.util.ArrayList<ImpactMark> IMPACT_MARKS = new java.util.ArrayList<>(3);
    private static final SnapCache[] SNAP_CACHE = new SnapCache[4];
    private static Object snapCacheWorld;
    private static long snapCacheTick = Long.MIN_VALUE;
    private static int snapCacheCount;
    private static int snapCacheWriteIndex;

    private PredictionsRenderer() {
    }

    private static final Animation entityGrow;
    static {
        entityGrow = new DecelerateAnimation().setMs(180).setValue(1);
        entityGrow.setDirection(Direction.BACKWARDS);
    }

    private static Vec3d lerpedEyePos(MinecraftClient mc, RenderTickCounter tickCounter) {
        PlayerEntity p = mc.player;
        float td = tickCounter.getTickProgress(false);
        return p.getCameraPosVec(td);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        Predictions module = Predictions.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        int accent = module.resolveAccentColor();

        int entityAccent = module.resolveEntityHitColor();

        var trails = module.getTrails();
        if (!trails.isEmpty()) {
            float trailWidth = Math.max(1.0F, module.lineWidth.getInt());

            float tickDelta = tickCounter.getTickProgress(false);

            for (var t : trails) {
                if (t.entity == null) continue;
                double lx = net.minecraft.util.math.MathHelper.lerp(tickDelta, t.entity.lastRenderX, t.entity.getX());
                double ly = net.minecraft.util.math.MathHelper.lerp(tickDelta, t.entity.lastRenderY, t.entity.getY());
                double lz = net.minecraft.util.math.MathHelper.lerp(tickDelta, t.entity.lastRenderZ, t.entity.getZ());
                Vec3d lerpedPos = new Vec3d(lx, ly, lz);
                var pts = t.getRemainingPath(lerpedPos);
                if (pts != null && pts.size() >= 2) {

                    if (t.smoothedImpact != null
                            && t.result != null
                            && t.result.type() != HitResult.Type.MISS) {
                        pts.set(pts.size() - 1, t.smoothedImpact);
                    }

                    int lineColor = (t.result != null && t.result.type() == HitResult.Type.ENTITY)
                            ? entityAccent : accent;
                    if (module.fadeTrail.isValue()) {

                        float fadeDist = module.fadeDistance.getValue();
                        Render3DUtil.drawPolylineFadedOffset(
                                matrices, pts,
                                -cameraPos.x, -cameraPos.y, -cameraPos.z,
                                lineColor, trailWidth, fadeDist, true
                        );
                    } else {
                        Render3DUtil.drawPolylineOffset(
                                matrices, pts,
                                -cameraPos.x, -cameraPos.y, -cameraPos.z,
                                lineColor, trailWidth, true
                        );
                    }
                }
            }

            if (module.showImpactSphere.isValue()) {
                float baseRadius = module.impactSphereRadius.getValue();
                String style = module.impactMarkerStyle.getSelected();
                boolean sphere = !"Circle".equalsIgnoreCase(style);
                int sphereAlpha = Math.max(0, Math.min(255,
                        Math.round(module.sphereOpacity.getValue() * 2.55F)));

                long nowNanos = System.nanoTime();
                for (var t : trails) {
                    var result = t.result;
                    if (result == null || result.type() == HitResult.Type.MISS) continue;
                    Vec3d impactRaw = result.impact();
                    if (impactRaw == null) continue;

                    t.smoothedImpact = impactRaw;
                    t.lastSmoothNanos = nowNanos;

                    Vec3d impact = impactRaw;
                    boolean isEntity = result.type() == HitResult.Type.ENTITY;
                    if (isEntity && !phaze$shouldTargetEntityForPrediction(result.entity())) {
                        continue;
                    }
                    float radius = baseRadius;

                    int markerAccent = isEntity ? entityAccent : accent;
                    int sphereColor = (sphereAlpha << 24) | (markerAccent & 0x00FFFFFF);

                    Vec3d toward = cameraPos.subtract(impact).normalize();
                    double frontShift = 0.0;
                    double cx = impact.x + toward.x * frontShift;
                    double cy = impact.y + toward.y * frontShift;
                    double cz = impact.z + toward.z * frontShift;

                    if (sphere) {
                        double sphereCy = isEntity ? cy : cy + module.sphereYOffset.getValue();
                        if (module.showGlow.isValue()) {
                            float strength = module.glowStrength.getValue();
                            float haloRadius = radius * (1.6F + strength * 1.4F);
                            float pulse = phaze$pulseFactor(module);
                            int haloAlpha = Math.max(0, Math.min(255, Math.round(110.0F * Math.min(2.0F, strength) * pulse)));
                            int haloColor = (haloAlpha << 24) | (markerAccent & 0x00FFFFFF);

                            Render3DUtil.drawBillboard(matrices, GLOW_TEXTURE,
                                    (float) (cx - cameraPos.x), (float) (sphereCy - cameraPos.y), (float) (cz - cameraPos.z),
                                    haloRadius, haloColor, true);
                        }
                        Render3DUtil.drawSphereSolid(matrices, (float) (cx - cameraPos.x), (float) (sphereCy - cameraPos.y), (float) (cz - cameraPos.z),
                                radius, sphereColor, 12, 18, true);
                    } else {
                        net.minecraft.util.math.Direction face = result.face();
                        float nx = face.getOffsetX();
                        float ny = face.getOffsetY();
                        float nz = face.getOffsetZ();
                        float surfaceLift = 0.02F;
                        double markerCx = isEntity ? cx : impact.x + nx * surfaceLift;
                        double markerCy = isEntity ? cy : impact.y + ny * surfaceLift;
                        double markerCz = isEntity ? cz : impact.z + nz * surfaceLift;
                        if (module.showGlow.isValue()) {
                            float strength = module.glowStrength.getValue();
                            float haloRadius = radius * (1.6F + strength * 1.4F);
                            float pulse = phaze$pulseFactor(module);
                            int haloAlpha = Math.max(0, Math.min(255, Math.round(110.0F * Math.min(2.0F, strength) * pulse)));
                            int haloColor = (haloAlpha << 24) | (markerAccent & 0x00FFFFFF);
                            Render3DUtil.drawBillboard(matrices, GLOW_TEXTURE,
                                    (float) (markerCx - cameraPos.x), (float) (markerCy - cameraPos.y), (float) (markerCz - cameraPos.z),
                                    haloRadius, haloColor, true);
                        }
                        float ringThickness = module.circleThickness.getValue();
                        if (isEntity) {
                            Render3DUtil.drawThickRingOnFace(matrices,
                                    (float) (cx - cameraPos.x), (float) (cy - cameraPos.y), (float) (cz - cameraPos.z),
                                    0.0F, 1.0F, 0.0F,
                                    radius, ringThickness, markerAccent, 64, true);
                        } else {
                            Render3DUtil.drawThickRingOnFace(matrices,
                                    (float) (markerCx - cameraPos.x), (float) (markerCy - cameraPos.y), (float) (markerCz - cameraPos.z),
                                    nx, ny, nz,
                                    radius * 1.35F, ringThickness, markerAccent, 64, true);
                        }
                    }
                }
            }
        }

        TRAJECTORIES.clear();
        IMPACT_MARKS.clear();
        java.util.List<Predictions.TrajectoryResult> trajectories = TRAJECTORIES;
        java.util.List<ImpactMark> marks = IMPACT_MARKS;

        if (module.shouldPredictHeld()) {
            collectHeldHand(mc, module, cameraPos, tickCounter, trajectories, marks);
        }

        boolean hasLines = false;
        for (Predictions.TrajectoryResult r : trajectories) {
            if (r != null && r.path() != null && r.path().size() >= 2) {
                hasLines = true;
                break;
            }
        }

        if (hasLines) {

            float lineWidth = Math.max(1.0F, module.lineWidth.getInt());

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            for (Predictions.TrajectoryResult r : trajectories) {

                int lineColor = (r != null && r.type() == HitResult.Type.ENTITY)
                        ? entityAccent : accent;
                emitPath(matrices, buffer, r, lineColor, cameraPos, lineWidth);
            }

            net.minecraft.client.render.BuiltBuffer built = buffer.endNullable();
            if (built != null) {
                vorga.phazeclient.util.render.PhazeRenderLayers.getThickLines(lineWidth).draw(built);
            }

        }

        if (!marks.isEmpty() && module.showImpactSphere.isValue()) {
            float baseRadius = module.impactSphereRadius.getValue();
            String style = module.impactMarkerStyle.getSelected();
            boolean sphere = !"Circle".equalsIgnoreCase(style);

            int sphereAlpha = Math.max(0, Math.min(255,
                    Math.round(module.sphereOpacity.getValue() * 2.55F)));

            boolean anyEntity = false;
            for (ImpactMark m : marks) {
                if (m.entity) { anyEntity = true; break; }
            }
            entityGrow.setDirection(anyEntity ? Direction.FORWARDS : Direction.BACKWARDS);
            float growProgress = entityGrow.getOutputFloat();
            float entityScale = 1.0F + 0.30F * growProgress;

            for (ImpactMark m : marks) {
                if (m.entity && !phaze$shouldTargetEntityForPrediction(m.entityRef)) {
                    continue;
                }
                float radius = m.entity ? baseRadius * entityScale : baseRadius;

                int markerAccent = m.entity ? entityAccent : accent;
                int sphereColor = (sphereAlpha << 24) | (markerAccent & 0x00FFFFFF);

                Vec3d toward = cameraPos.subtract(m.pos).normalize();
                double frontShift = m.entity ? radius * 1.05 : 0.0;
                double cx = m.pos.x + toward.x * frontShift;
                double cy = m.pos.y + toward.y * frontShift;
                double cz = m.pos.z + toward.z * frontShift;

                if (sphere) {

                    double sphereCy = m.entity ? cy : cy + module.sphereYOffset.getValue();

                    if (module.showGlow.isValue()) {
                        float strength = module.glowStrength.getValue();
                        float haloRadius = radius * (1.6F + strength * 1.4F);
                        float pulse = phaze$pulseFactor(module);
                        int haloAlpha = Math.max(0, Math.min(255, Math.round(110.0F * Math.min(2.0F, strength) * pulse)));
                        int haloColor = (haloAlpha << 24) | (markerAccent & 0x00FFFFFF);
                        Render3DUtil.drawBillboard(matrices, GLOW_TEXTURE,
                                (float) (cx - cameraPos.x), (float) (sphereCy - cameraPos.y), (float) (cz - cameraPos.z),
                                haloRadius, haloColor, true);
                    }

                    Render3DUtil.drawSphereSolid(matrices, (float) (cx - cameraPos.x), (float) (sphereCy - cameraPos.y), (float) (cz - cameraPos.z),
                            radius, sphereColor, 12, 18, true);

                } else {

                    net.minecraft.util.math.Direction face = m.face;
                    float nx = face.getOffsetX();
                    float ny = face.getOffsetY();
                    float nz = face.getOffsetZ();

                    float surfaceLift = 0.02F;
                    double markerCx, markerCy, markerCz;
                    if (m.entity) {
                        markerCx = cx;
                        markerCy = cy;
                        markerCz = cz;
                    } else {
                        markerCx = m.pos.x + nx * surfaceLift;
                        markerCy = m.pos.y + ny * surfaceLift;
                        markerCz = m.pos.z + nz * surfaceLift;
                    }
                    if (module.showGlow.isValue()) {
                        float strength = module.glowStrength.getValue();
                        float haloRadius = radius * (1.6F + strength * 1.4F);
                        float pulse = phaze$pulseFactor(module);
                        int haloAlpha = Math.max(0, Math.min(255, Math.round(110.0F * Math.min(2.0F, strength) * pulse)));
                        int haloColor = (haloAlpha << 24) | (markerAccent & 0x00FFFFFF);
                        Render3DUtil.drawBillboard(matrices, GLOW_TEXTURE,
                                (float) (markerCx - cameraPos.x), (float) (markerCy - cameraPos.y), (float) (markerCz - cameraPos.z),
                                haloRadius, haloColor, true);
                    }

                    float ringThickness = module.circleThickness.getValue();
                    if (m.entity) {

                        Render3DUtil.drawThickRingOnFace(matrices,
                                (float) (cx - cameraPos.x), (float) (cy - cameraPos.y), (float) (cz - cameraPos.z),
                                0.0F, 1.0F, 0.0F,
                                radius, ringThickness, markerAccent, 64, true);
                    } else {
                        Render3DUtil.drawThickRingOnFace(matrices,
                                (float) (markerCx - cameraPos.x), (float) (markerCy - cameraPos.y), (float) (markerCz - cameraPos.z),
                                nx, ny, nz,
                                radius * 1.35F, ringThickness, markerAccent, 64, true);
                    }
                }
            }
        } else {

            entityGrow.setDirection(Direction.BACKWARDS);
        }
    }

    private static void collectHeldHand(MinecraftClient mc, Predictions module,
                                        Vec3d renderCameraPos,
                                        RenderTickCounter tickCounter,
                                        java.util.List<Predictions.TrajectoryResult> out,
                                        java.util.List<ImpactMark> marks) {
        PlayerEntity p = mc.player;
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        ItemStack chosen = pickThrowable(module, main, off);
        if (chosen == null) return;

        Predictions.HeldType type = module.classifyHeldStack(chosen);
        if (type == Predictions.HeldType.BOW) {

            if (!p.isUsingItem()) return;
            ItemStack active = p.getActiveItem();
            if (active == null || active.isEmpty()) return;
            if (!ItemStack.areItemsAndComponentsEqual(active, chosen)) return;
        }
        double speed = module.initialVelocityFor(type, chosen, mc);
        if (speed <= 0.0) return;

        Vec3d look = p.getRotationVec(1.0F);
        Vec3d motion = look.multiply(speed);

        net.minecraft.client.render.Camera activeCamera = mc.gameRenderer == null
                ? null : mc.gameRenderer.getCamera();
        boolean firstPersonPlayerCamera = activeCamera != null
                && !activeCamera.isThirdPerson()
                && activeCamera.getFocusedEntity() == p;
        Vec3d eye = firstPersonPlayerCamera ? renderCameraPos : lerpedEyePos(mc, tickCounter);
        double gravity = switch (type) {
            case SNOWBALL, EGG, ENDER_PEARL, EXPERIENCE_BOTTLE, SPLASH_POTION -> 0.03;
            case BOW, CROSSBOW, TRIDENT -> 0.05;
            default -> 0.03;
        };
        boolean trident = type == Predictions.HeldType.TRIDENT;

        boolean multishot = type == Predictions.HeldType.CROSSBOW && module.hasMultishot(chosen);
        if (multishot) {
            for (float yawOffset : MULTISHOT_YAW_OFFSETS) {
                Vec3d fanLook = phaze$rotateLookYaw(look, yawOffset);
                Vec3d fanMotion = fanLook.multiply(speed);
                Predictions.TrajectoryResult r = module.predictCached(eye, fanMotion, gravity, trident, p);
                if (r == null || r.path() == null || r.path().size() < 2) continue;
                Predictions.TrajectoryResult s = phaze$snapToTargetedEntity(mc, p, eye, fanLook, r, speed);
                if (s != null) r = s;
                out.add(r);
                if (r.type() != HitResult.Type.MISS) {
                    marks.add(new ImpactMark(r.impact(), r.type() == HitResult.Type.ENTITY, r.face(), r.entity()));
                }
            }
            return;
        }

        Predictions.TrajectoryResult result = module.predictCached(eye, motion, gravity, trident, p);
        if (result == null || result.path() == null || result.path().size() < 2) return;

        Predictions.TrajectoryResult snapped = phaze$snapToTargetedEntity(mc, p, eye, look, result, speed);
        if (snapped != null) {
            result = snapped;
        }

        out.add(result);
        if (result.type() != HitResult.Type.MISS) {
            marks.add(new ImpactMark(result.impact(), result.type() == HitResult.Type.ENTITY, result.face(), result.entity()));
        }
    }

    private static Vec3d phaze$rotateLookYaw(Vec3d look, float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double x = look.x * cos + look.z * sin;
        double z = -look.x * sin + look.z * cos;
        return new Vec3d(x, look.y, z).normalize();
    }

    private static Predictions.TrajectoryResult phaze$snapToTargetedEntity(
            MinecraftClient mc, PlayerEntity self, Vec3d eye, Vec3d look,
            Predictions.TrajectoryResult ballistic, double speed) {
        if (mc.world == null) return null;
        long worldTick = mc.world.getTime();
        if (snapCacheWorld != mc.world || snapCacheTick != worldTick) {
            snapCacheWorld = mc.world;
            snapCacheTick = worldTick;
            snapCacheCount = 0;
            snapCacheWriteIndex = 0;
        }
        for (int i = 0; i < snapCacheCount; i++) {
            SnapCache cached = SNAP_CACHE[i];
            if (cached != null && cached.matches(self, eye, look, ballistic, speed)) {
                return cached.result;
            }
        }

        Predictions.TrajectoryResult result = phaze$computeSnapToTargetedEntity(mc, self, eye, look, ballistic, speed);
        SNAP_CACHE[snapCacheWriteIndex] = new SnapCache(self, eye, look, ballistic, speed, result);
        snapCacheWriteIndex = (snapCacheWriteIndex + 1) % SNAP_CACHE.length;
        snapCacheCount = Math.min(SNAP_CACHE.length, snapCacheCount + 1);
        return result;
    }

    private static Predictions.TrajectoryResult phaze$computeSnapToTargetedEntity(
            MinecraftClient mc, PlayerEntity self, Vec3d eye, Vec3d look,
            Predictions.TrajectoryResult ballistic, double speed) {
        if (mc.world == null) return null;

        double maxRange;
        if (ballistic != null && ballistic.impact() != null) {
            maxRange = Math.max(8.0, eye.distanceTo(ballistic.impact()) * 1.5);
        } else {
            maxRange = 64.0;
        }

        Vec3d end = eye.add(look.multiply(maxRange));
        net.minecraft.util.math.Box searchBox = self.getBoundingBox()
                .stretch(look.multiply(maxRange))
                .expand(1.0, 1.0, 1.0);
        net.minecraft.util.hit.EntityHitResult hit =
                net.minecraft.entity.projectile.ProjectileUtil.raycast(
                        self, eye, end, searchBox,
                        e -> !e.isSpectator()
                                && e instanceof net.minecraft.entity.LivingEntity
                                && e != self
                                && phaze$shouldTargetEntityForPrediction(e),
                        maxRange * maxRange);
        if (hit == null || hit.getEntity() == null) {
            return null;
        }

        net.minecraft.world.RaycastContext blockCtx = new net.minecraft.world.RaycastContext(
                eye, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                self);
        net.minecraft.util.hit.HitResult blockHit = mc.world.raycast(blockCtx);
        if (blockHit != null && blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            double blockDistSq = eye.squaredDistanceTo(blockHit.getPos());
            double entityDistSq = eye.squaredDistanceTo(hit.getPos());
            if (blockDistSq < entityDistSq) {
                return null;
            }
        }

        net.minecraft.entity.Entity target = hit.getEntity();

        Vec3d impactPos = hit.getPos();

        java.util.List<Vec3d> path = new java.util.ArrayList<>();
        path.add(eye);
        path.add(impactPos);
        return new Predictions.TrajectoryResult(
                path, impactPos, HitResult.Type.ENTITY,
                net.minecraft.util.math.Direction.UP, target);
    }

    private static float phaze$pulseFactor(Predictions module) {
        if (!module.glowPulsate.isValue()) return 1.0F;
        float speed = module.glowPulsateSpeed.getValue();
        if (speed <= 0.0F) speed = 0.0001F;
        double t = (System.nanoTime() / 1_000_000_000.0) * speed;

        float s = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0));
        return 0.1F + s * 0.9F;
    }

    private static boolean phaze$shouldTargetEntityForPrediction(Entity entity) {
        if (entity == null || entity.isSpectator()) return false;
        if (entity.isGlowing()) return true;
        if (!entity.isInvisible()) return true;
        if (!(entity instanceof net.minecraft.entity.LivingEntity living)) return false;

        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.VALUES) {
            if (slot.getType() != net.minecraft.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack armor = living.getEquippedStack(slot);
            if (armor != null && !armor.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void emitPath(MatrixStack matrices, BufferBuilder buffer, Predictions.TrajectoryResult result, int color, Vec3d cameraPos, float lineWidth) {
        var path = result.path();
        if (path.size() < 2) return;
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d start = path.get(i);
            Vec3d end = path.get(i + 1);
            Render3DUtil.vertexLine(
                    matrices,
                    buffer,
                    start.x - cameraPos.x,
                    start.y - cameraPos.y,
                    start.z - cameraPos.z,
                    end.x - cameraPos.x,
                    end.y - cameraPos.y,
                    end.z - cameraPos.z,
                    color,
                    color,
                    lineWidth
            );
        }
    }

    private static ItemStack pickThrowable(Predictions module, ItemStack main, ItemStack off) {
        if (module.classifyHeldStack(main) != Predictions.HeldType.NONE) return main;
        if (module.classifyHeldStack(off) != Predictions.HeldType.NONE) return off;
        return null;
    }

    private record ImpactMark(Vec3d pos, boolean entity, net.minecraft.util.math.Direction face, Entity entityRef) {
    }

    private record SnapCache(
            PlayerEntity player,
            Vec3d eye,
            Vec3d look,
            Predictions.TrajectoryResult ballistic,
            double speed,
            Predictions.TrajectoryResult result
    ) {
        private boolean matches(
                PlayerEntity player,
                Vec3d eye,
                Vec3d look,
                Predictions.TrajectoryResult ballistic,
                double speed
        ) {
            return this.player == player
                    && this.ballistic == ballistic
                    && same(this.eye, eye)
                    && same(this.look, look)
                    && Double.doubleToLongBits(this.speed) == Double.doubleToLongBits(speed);
        }

        private static boolean same(Vec3d first, Vec3d second) {
            return first != null && second != null
                    && Double.doubleToLongBits(first.x) == Double.doubleToLongBits(second.x)
                    && Double.doubleToLongBits(first.y) == Double.doubleToLongBits(second.y)
                    && Double.doubleToLongBits(first.z) == Double.doubleToLongBits(second.z);
        }
    }
}
