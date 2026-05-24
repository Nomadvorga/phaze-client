package vorga.phazeclient.implement.features.modules.other;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.render.Render3DUtil;

/**
 * World-render side of {@link Predictions}. Walks the held throwables
 * + own-projectile lists each frame, simulates a forward trajectory
 * via {@link Predictions#predict}, and paints the polyline + impact
 * marker.
 */
public final class PredictionsRenderer {
    /** Soft-falloff bloom texture used for the optional glow halo. */
    private static final net.minecraft.util.Identifier GLOW_TEXTURE =
            net.minecraft.util.Identifier.of("phaze", "textures/particles/bloom/bloom_soft.png");

    private PredictionsRenderer() {
    }

    /**
     * Smooth grow-in / shrink-out animation for entity-hit markers.
     * The renderer flips the direction to {@code FORWARDS} every
     * frame the prediction lands on an entity and to {@code BACKWARDS}
     * every frame it doesn't; the {@link DecelerateAnimation}
     * counter handles the ease curve internally so we read a
     * settled 0..1 progress without any per-frame interpolation
     * code on our side. Single static animation because the
     * renderer never needs to track more than one marker at a time.
     */
    private static final Animation entityGrow;
    static {
        entityGrow = new DecelerateAnimation().setMs(180).setValue(1);
        entityGrow.setDirection(Direction.BACKWARDS);
    }

    /**
     * Lerped eye position for the local player. Crucial: the camera
     * frustum that {@link MinecraftClient#crosshairTarget} (which the
     * crosshair locks onto) draws against is built from
     * {@code prevPosX -> posX} interpolated with the current
     * {@code tickDelta}. The held-hand prediction starts a forward
     * Euler simulation from this exact point so the trajectory locks
     * to the crosshair every frame.
     *
     * <p>{@link PlayerEntity#getCameraPosVec(float)} is vanilla's own
     * camera-position helper - it lerps the body coords AND picks the
     * correct eye-height offset for the current pose (1.62 standing,
     * 1.27 sneaking, 0.4 elytra-gliding, 0.4 swimming). Using a fixed
     * {@code getStandingEyeHeight()} like the previous implementation
     * left the line stuck at standing height for ~half a second
     * whenever the user crouched or popped elytra, because the
     * server-side eye height transition doesn't propagate to the
     * client-pose flag instantly.
     */
    private static Vec3d lerpedEyePos(MinecraftClient mc, RenderTickCounter tickCounter) {
        PlayerEntity p = mc.player;
        float td = tickCounter.getTickDelta(false);
        return p.getCameraPosVec(td);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        Predictions module = Predictions.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        // Resolve the accent color once per frame. All passes (line,
        // floor ring, entity ring, glow halo) share the same hue so
        // the visual reads as a single coherent highlight rather than
        // a multi-colored mix when the user picks a non-default theme.
        int accent = module.resolveAccentColor();
        // Entity-hit colour. May equal {@code accent} when the user
        // disabled the override (so call sites can branch on the
        // {@code isEntity} flag without reading the toggle every
        // time). Resolved once per frame for the same reason as
        // {@code accent}.
        int entityAccent = module.resolveEntityHitColor();

        // ---- Live projectile trails ---------------------------------
        // For every projectile WE threw / shot, render the REMAINING
        // path ahead of its current position (line "shrinks" as the
        // projectile moves through it) plus an impact marker at the
        // path end. Visual mirrors the held-hand prediction so the
        // user sees a continuous experience: aim -> see line + impact,
        // throw -> projectile flies along the same line into the same
        // impact marker, line gets shorter each tick.
        var trails = module.getTrails();
        if (!trails.isEmpty()) {
            float trailWidth = Math.max(1.0F, module.lineWidth.getInt());
            // Lerp the partial-tick delta so the line endpoint
            // tracks the visually-rendered projectile model rather
            // than its discrete tick position. Without this the
            // line endpoint snaps 20 times per second while the
            // projectile model interpolates smoothly between ticks,
            // producing a visible jitter at the join.
            float tickDelta = tickCounter.getTickDelta(false);
            // ---- Lines ----
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            for (var t : trails) {
                if (t.entity == null) continue;
                double lx = net.minecraft.util.math.MathHelper.lerp(tickDelta, t.entity.lastRenderX, t.entity.getX());
                double ly = net.minecraft.util.math.MathHelper.lerp(tickDelta, t.entity.lastRenderY, t.entity.getY());
                double lz = net.minecraft.util.math.MathHelper.lerp(tickDelta, t.entity.lastRenderZ, t.entity.getZ());
                Vec3d lerpedPos = new Vec3d(lx, ly, lz);
                var pts = t.getRemainingPath(lerpedPos);
                if (pts != null && pts.size() >= 2) {
                    // Replace the last point with the smoothed
                    // impact so the line endpoint and the impact
                    // marker stay glued together. Without this the
                    // line tip jitters per-tick (raw prediction)
                    // while the marker glides (smoothed), producing
                    // a visible gap at the tip.
                    if (t.smoothedImpact != null
                            && t.result != null
                            && t.result.type() != HitResult.Type.MISS) {
                        pts = new java.util.ArrayList<>(pts);
                        pts.set(pts.size() - 1, t.smoothedImpact);
                    }
                    // Trail lines that end on a living entity get
                    // the entity-hit colour so the user can tell at
                    // a glance whether their projectile is going to
                    // hit a target or just splash into a wall.
                    int lineColor = (t.result != null && t.result.type() == HitResult.Type.ENTITY)
                            ? entityAccent : accent;
                    if (module.fadeTrail.isValue()) {
                        // Fade the head end of the line (the part
                        // closest to the projectile, i.e. the bit
                        // it's "eating" right now). The fade region
                        // length is the user-configured slider, so
                        // visually the line dissolves smoothly just
                        // behind the projectile rather than ending
                        // abruptly at the head.
                        float fadeDist = module.fadeDistance.getValue();
                        Render3DUtil.drawPolylineFaded(matrices, pts, lineColor, trailWidth, fadeDist, true);
                    } else {
                        Render3DUtil.drawPolyline(matrices, pts, lineColor, trailWidth, true);
                    }
                }
            }
            matrices.pop();

            // ---- Impact markers at path end ----
            // Only draw if the user has the marker enabled. Reuse
            // the same Sphere / Circle style choice and color/glow
            // settings as the held-hand marker so the visual is
            // consistent.
            if (module.showImpactSphere.isValue()) {
                float baseRadius = module.impactSphereRadius.getValue();
                String style = module.impactMarkerStyle.getSelected();
                boolean sphere = !"Circle".equalsIgnoreCase(style);
                int sphereAlpha = Math.max(0, Math.min(255,
                        Math.round(module.sphereOpacity.getValue() * 2.55F)));

                matrices.push();
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                long nowNanos = System.nanoTime();
                for (var t : trails) {
                    var result = t.result;
                    if (result == null || result.type() == HitResult.Type.MISS) continue;
                    Vec3d impactRaw = result.impact();
                    if (impactRaw == null) continue;

                    // Exponential smoother: each frame we ease the
                    // marker position toward the live prediction by
                    // {@code 1 - exp(-rate*dt)}. dt is real elapsed
                    // time (not tickDelta) so the smoothing is
                    // frame-rate-independent. rate=12 gives a
                    // ~80ms half-life - fast enough to track the
                    // projectile but slow enough to hide per-tick
                    // jitter from the random-spread re-prediction.
                    if (t.smoothedImpact == null) {
                        t.smoothedImpact = impactRaw;
                    } else {
                        double dt = t.lastSmoothNanos == 0L ? 1.0 / 60.0
                                : (nowNanos - t.lastSmoothNanos) / 1_000_000_000.0;
                        if (dt > 0.25) dt = 0.25;
                        double alpha = 1.0 - Math.exp(-12.0 * dt);
                        t.smoothedImpact = t.smoothedImpact.add(impactRaw.subtract(t.smoothedImpact).multiply(alpha));
                    }
                    t.lastSmoothNanos = nowNanos;

                    Vec3d impact = t.smoothedImpact;
                    boolean isEntity = result.type() == HitResult.Type.ENTITY;
                    float radius = baseRadius;
                    // Per-marker accent: entity hits use the
                    // dedicated entity-hit colour so the warning
                    // signal "your projectile is locked onto a
                    // mob" reads instantly even from a distance.
                    int markerAccent = isEntity ? entityAccent : accent;
                    int sphereColor = (sphereAlpha << 24) | (markerAccent & 0x00FFFFFF);

                    Vec3d toward = cameraPos.subtract(impact).normalize();
                    double frontShift = isEntity ? radius * 1.05 : 0.0;
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
                            // Single bloom billboard - the user
                            // explicitly asked for the original
                            // halo look (not the volumetric stack).
                            // Pulse multiplier breathes the alpha
                            // 10%..100% when Glow Pulsate is on.
                            Render3DUtil.drawBillboard(matrices, GLOW_TEXTURE,
                                    (float) cx, (float) sphereCy, (float) cz,
                                    haloRadius, haloColor, true);
                        }
                        Render3DUtil.drawSphereSolid(matrices, (float) cx, (float) sphereCy, (float) cz,
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
                                    (float) markerCx, (float) markerCy, (float) markerCz,
                                    haloRadius, haloColor, true);
                        }
                        float ringThickness = module.circleThickness.getValue();
                        if (isEntity) {
                            Render3DUtil.drawThickRingOnFace(matrices,
                                    (float) cx, (float) cy, (float) cz,
                                    0.0F, 1.0F, 0.0F,
                                    radius, ringThickness, markerAccent, 64, true);
                        } else {
                            Render3DUtil.drawThickRingOnFace(matrices,
                                    (float) markerCx, (float) markerCy, (float) markerCz,
                                    nx, ny, nz,
                                    radius * 1.35F, ringThickness, markerAccent, 64, true);
                        }
                    }
                }
                matrices.pop();
            }
        }

        // First pass: collect trajectory results without touching the
        // GL buffer. Mojang's BufferBuilder throws "BufferBuilder was
        // empty" if we open it and submit nothing, so we have to know
        // up-front whether ANY trajectory is going to land vertices.
        java.util.List<Predictions.TrajectoryResult> trajectories = new java.util.ArrayList<>();
        java.util.List<ImpactMark> marks = new java.util.ArrayList<>();

        if (module.shouldPredictHeld()) {
            collectHeldHand(mc, module, tickCounter, trajectories, marks);
        }

        // Drop trajectories that have less than 2 path points - those
        // would emit no line segments and contribute zero vertices.
        boolean hasLines = false;
        for (Predictions.TrajectoryResult r : trajectories) {
            if (r != null && r.path() != null && r.path().size() >= 2) {
                hasLines = true;
                break;
            }
        }

        // Trajectory line is always rendered when there is a path -
        // it complements both marker styles by showing the arc that
        // reaches the impact point. The Circle / Sphere choice only
        // affects how the marker itself looks at the destination.
        if (hasLines) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            // Depth-test ON: trajectory lines and impact markers
            // respect world geometry, so the line gets occluded
            // behind walls / mobs instead of bleeding through. Mask
            // stays off so the line doesn't imprint into the depth
            // buffer either (which would block subsequent draws).
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            RenderSystem.lineWidth(Math.max(1.0F, module.lineWidth.getInt()));

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            for (Predictions.TrajectoryResult r : trajectories) {
                // Same per-trajectory rule as the trail lines above:
                // recolor to the entity-hit accent when this path
                // ends on a living entity, so the user sees a red
                // "you'll hit a target" line vs. the regular accent
                // for block hits / misses.
                int lineColor = (r != null && r.type() == HitResult.Type.ENTITY)
                        ? entityAccent : accent;
                emitPath(matrices, buffer, r, lineColor);
            }
            matrices.pop();

            net.minecraft.client.render.BuiltBuffer built = buffer.endNullable();
            if (built != null) {
                BufferRenderer.drawWithGlobalProgram(built);
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }

        // Impact markers: optional black sphere or floor circle at
        // the predicted impact. Style chosen via {@code Marker Style};
        // both are gated by the master Impact Marker toggle.
        if (!marks.isEmpty() && module.showImpactSphere.isValue()) {
            float baseRadius = module.impactSphereRadius.getValue();
            String style = module.impactMarkerStyle.getSelected();
            boolean sphere = !"Circle".equalsIgnoreCase(style);

            // Sphere body color: accent RGB modulated by the user's
            // opacity slider (0..100 -> 0..255 alpha). Keeping the
            // sphere on the same hue as the line/ring/glow makes the
            // whole marker read as a coherent visual; the opacity
            // slider lets the user tune from a ghost-like fade to a
            // solid sphere without adding a separate color picker.
            int sphereAlpha = Math.max(0, Math.min(255,
                    Math.round(module.sphereOpacity.getValue() * 2.55F)));

            // Drive the entity-hit grow animation. Flip the
            // direction every frame; the DecelerateAnimation
            // class clamps the counter so the progress only ever
            // moves toward the target without bouncing or
            // oscillating - one continuous transition between two
            // resting values, no breathing.
            boolean anyEntity = false;
            for (ImpactMark m : marks) {
                if (m.entity) { anyEntity = true; break; }
            }
            entityGrow.setDirection(anyEntity ? Direction.FORWARDS : Direction.BACKWARDS);
            float growProgress = entityGrow.getOutputFloat();
            float entityScale = 1.0F + 0.30F * growProgress;

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            for (ImpactMark m : marks) {
                float radius = m.entity ? baseRadius * entityScale : baseRadius;
                // Per-marker accent: entity hits use the dedicated
                // entity-hit colour. Falls back to {@code accent}
                // when the override is disabled (resolveEntityHitColor
                // returns the regular accent in that case).
                int markerAccent = m.entity ? entityAccent : accent;
                int sphereColor = (sphereAlpha << 24) | (markerAccent & 0x00FFFFFF);

                // Center the marker. For ENTITY hits we nudge it
                // slightly TOWARD the camera so the sphere reads as
                // "on the front of the entity" instead of getting
                // half-eaten by the entity model behind it.
                Vec3d toward = cameraPos.subtract(m.pos).normalize();
                double frontShift = m.entity ? radius * 1.05 : 0.0;
                double cx = m.pos.x + toward.x * frontShift;
                double cy = m.pos.y + toward.y * frontShift;
                double cz = m.pos.z + toward.z * frontShift;

                if (sphere) {
                    // Sphere centred at the impact point plus the
                    // user's Y offset. Negative offsets push the
                    // sphere down (sits half-buried in the floor),
                    // positive lift it up. For ENTITY hits we
                    // anchor directly at the hit point because the
                    // floor offset doesn't apply to mid-air targets.
                    double sphereCy = m.entity ? cy : cy + module.sphereYOffset.getValue();

                    // Optional bloom halo behind the sphere. Drawn
                    // BEFORE the sphere so the additive blend lands
                    // on world pixels, then the opaque sphere paints
                    // on top - giving the look of a glowing core
                    // surrounded by a soft halo. Strength scales the
                    // halo radius and modulates the per-vertex alpha
                    // so a 1.0 strength reads as a comfortable glow,
                    // 4.0 as a bright flare, 0.2 as a subtle hint.
                    if (module.showGlow.isValue()) {
                        float strength = module.glowStrength.getValue();
                        float haloRadius = radius * (1.6F + strength * 1.4F);
                        float pulse = phaze$pulseFactor(module);
                        int haloAlpha = Math.max(0, Math.min(255, Math.round(110.0F * Math.min(2.0F, strength) * pulse)));
                        int haloColor = (haloAlpha << 24) | (markerAccent & 0x00FFFFFF);
                        Render3DUtil.drawBillboard(matrices, GLOW_TEXTURE,
                                (float) cx, (float) sphereCy, (float) cz,
                                haloRadius, haloColor, true);
                    }

                    Render3DUtil.drawSphereSolid(matrices, (float) cx, (float) sphereCy, (float) cz,
                            radius, sphereColor, 12, 18, true);
                    // Floor ring intentionally omitted for Sphere
                    // style - the user wants only the sphere as the
                    // impact marker, no underlying circle cluttering
                    // the ground.
                } else {
                    // Circle / floor ring style. When Glow is on we
                    // still emit a billboard at the same position
                    // so the user gets a consistent visual cue
                    // regardless of which marker style they picked.
                    //
                    // For BLOCK hits we orient the ring against the
                    // surface normal of the face we struck - lands
                    // flat on floors, vertical on walls, upside-down
                    // on ceilings. For ENTITY hits there is no
                    // surface to align to, so we keep the camera-
                    // facing flat ring at the front-shifted point.
                    net.minecraft.util.math.Direction face = m.face;
                    float nx = face.getOffsetX();
                    float ny = face.getOffsetY();
                    float nz = face.getOffsetZ();
                    // Lift the ring along the normal by a hair so it
                    // doesn't z-fight with the surface itself.
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
                                (float) markerCx, (float) markerCy, (float) markerCz,
                                haloRadius, haloColor, true);
                    }
                    // World-space thick ring. depthTest=true so the
                    // ring is properly occluded by world geometry -
                    // matches the line and sphere passes which now
                    // also respect depth.
                    float ringThickness = module.circleThickness.getValue();
                    if (m.entity) {
                        // Entity ring stays a horizontal disc.
                        // Normal is UP because we want the ring on
                        // the floor under the entity hitbox.
                        Render3DUtil.drawThickRingOnFace(matrices,
                                (float) cx, (float) cy, (float) cz,
                                0.0F, 1.0F, 0.0F,
                                radius, ringThickness, markerAccent, 64, true);
                    } else {
                        Render3DUtil.drawThickRingOnFace(matrices,
                                (float) markerCx, (float) markerCy, (float) markerCz,
                                nx, ny, nz,
                                radius * 1.35F, ringThickness, markerAccent, 64, true);
                    }
                }
            }
            matrices.pop();
        } else {
            // No marker this frame - decay grow back to 0 so a
            // future entity-hit starts cleanly from the resting
            // size instead of the previous frame's grow value.
            entityGrow.setDirection(Direction.BACKWARDS);
        }
    }

    private static void collectHeldHand(MinecraftClient mc, Predictions module,
                                        RenderTickCounter tickCounter,
                                        java.util.List<Predictions.TrajectoryResult> out,
                                        java.util.List<ImpactMark> marks) {
        PlayerEntity p = mc.player;
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        ItemStack chosen = pickThrowable(module, main, off);
        if (chosen == null) return;

        Predictions.HeldType type = module.classifyHeldStack(chosen);
        double speed = module.initialVelocityFor(type, chosen, mc);
        if (speed <= 0.0) return;

        Vec3d look = p.getRotationVec(1.0F);
        Vec3d motion = look.multiply(speed);
        // Lerped eye position - matches the camera frustum the
        // crosshair was drawn through, so the trajectory line stays
        // glued to the crosshair while the player is moving instead
        // of trailing one tick behind their real eye.
        Vec3d eye = lerpedEyePos(mc, tickCounter);
        double gravity = switch (type) {
            case SNOWBALL, EGG, ENDER_PEARL, EXPERIENCE_BOTTLE, SPLASH_POTION -> 0.03;
            case BOW, CROSSBOW, TRIDENT -> 0.05;
            default -> 0.03;
        };
        boolean trident = type == Predictions.HeldType.TRIDENT;

        // Multishot crossbow: fan three trajectories at -10°, 0°,
        // +10° yaw around the look direction, mirroring vanilla's
        // {@code RangedWeaponItem.shootAll} fan logic. Each shot
        // gets its own snap-to-target and impact marker so the user
        // sees exactly where all three bolts will go.
        boolean multishot = type == Predictions.HeldType.CROSSBOW && module.hasMultishot(chosen);
        if (multishot) {
            float[] yawOffsets = { -10.0F, 0.0F, 10.0F };
            for (float yawOffset : yawOffsets) {
                Vec3d fanLook = phaze$rotateLookYaw(look, yawOffset);
                Vec3d fanMotion = fanLook.multiply(speed);
                Predictions.TrajectoryResult r = module.predict(eye, fanMotion, gravity, trident, p);
                if (r == null || r.path() == null || r.path().size() < 2) continue;
                Predictions.TrajectoryResult s = phaze$snapToTargetedEntity(mc, p, eye, fanLook, r, speed);
                if (s != null) r = s;
                out.add(r);
                if (r.type() != HitResult.Type.MISS) {
                    marks.add(new ImpactMark(r.impact(), r.type() == HitResult.Type.ENTITY, r.face()));
                }
            }
            return;
        }

        Predictions.TrajectoryResult result = module.predict(eye, motion, gravity, trident, p);
        if (result == null || result.path() == null || result.path().size() < 2) return;

        // Snap-to-target: if the player's crosshair is on a living
        // entity within reasonable projectile range, override the
        // ballistic result with a straight line to that entity. The
        // user wants the prediction to "lock" to the moving target
        // they're aiming at instead of computing a parabola that
        // technically misses by a few pixels because the simulation
        // doesn't perfectly model server-side hit reg. Rebuild the
        // result as a 2-point path (eye -> entity center) so the
        // line + marker land exactly on the target.
        Predictions.TrajectoryResult snapped = phaze$snapToTargetedEntity(mc, p, eye, look, result, speed);
        if (snapped != null) {
            result = snapped;
        }

        out.add(result);
        if (result.type() != HitResult.Type.MISS) {
            marks.add(new ImpactMark(result.impact(), result.type() == HitResult.Type.ENTITY, result.face()));
        }
    }

    /**
     * Rotate the look vector around the world Y axis by
     * {@code yawDegrees}. Used to fan multishot crossbow shots at
     * {@code -10° / 0° / +10°} relative to the player's actual aim.
     * Yaw rotation only - vanilla's multishot fan is purely
     * horizontal, so pitch is preserved untouched.
     */
    private static Vec3d phaze$rotateLookYaw(Vec3d look, float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // Rotation about Y: (x', z') = (x*cos + z*sin, -x*sin + z*cos)
        // Standard right-handed yaw matching Minecraft's coordinate
        // convention (positive yaw = clockwise looking from above).
        double x = look.x * cos + look.z * sin;
        double z = -look.x * sin + look.z * cos;
        return new Vec3d(x, look.y, z).normalize();
    }

    private static Predictions.TrajectoryResult phaze$snapToTargetedEntity(
            MinecraftClient mc, PlayerEntity self, Vec3d eye, Vec3d look,
            Predictions.TrajectoryResult ballistic, double speed) {
        if (mc.world == null) return null;

        // Cap snap range by the ballistic impact distance so we
        // don't lock onto unreachable far targets. Falls back to
        // a generous 64-block search when the ballistic ended in
        // MISS (e.g. trajectory cleared the horizon).
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
                        e -> !e.isSpectator() && e instanceof net.minecraft.entity.LivingEntity && e != self,
                        maxRange * maxRange);
        if (hit == null || hit.getEntity() == null) {
            return null;
        }

        // Block-raycast along the same look ray. If a block sits
        // closer than the entity, the user isn't actually looking
        // at the entity (a wall is in between) - the projectile
        // would smack the wall first, so we DON'T snap. Without
        // this check the marker would teleport across walls onto
        // mobs the player can't even see.
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
        // Marker lands exactly where the look-ray crossed the
        // entity's bounding box - i.e. the same point vanilla
        // would compute for a hit. This reads as "the projectile
        // strikes wherever the crosshair is on the body" instead
        // of always sticking to the bbox centre.
        Vec3d impactPos = hit.getPos();

        java.util.List<Vec3d> path = new java.util.ArrayList<>();
        path.add(eye);
        path.add(impactPos);
        return new Predictions.TrajectoryResult(
                path, impactPos, HitResult.Type.ENTITY,
                net.minecraft.util.math.Direction.UP);
    }

    /**
     * Pulse multiplier for the glow alpha. When the user enables
     * Glow Pulsate, the halo's alpha breathes between 10% and 100%
     * on a sine wave at the configured speed. When the toggle is
     * off, returns {@code 1.0} so the alpha stays at the strength-
     * driven baseline (no behaviour change).
     *
     * <p>Time source is {@link System#nanoTime} to stay independent
     * of game-tick rate / pause state - the user wanted the pulse
     * to keep breathing at a constant cadence regardless of fps.
     */
    private static float phaze$pulseFactor(Predictions module) {
        if (!module.glowPulsate.isValue()) return 1.0F;
        float speed = module.glowPulsateSpeed.getValue();
        if (speed <= 0.0F) speed = 0.0001F;
        double t = (System.nanoTime() / 1_000_000_000.0) * speed;
        // sin-derived 0..1 wave, then mapped to [0.1, 1.0] so the
        // halo never fully disappears - matches the user's "10% до
        // 100%" spec.
        float s = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0));
        return 0.1F + s * 0.9F;
    }

    private static void emitPath(MatrixStack matrices, BufferBuilder buffer, Predictions.TrajectoryResult result, int color) {
        var path = result.path();
        if (path.size() < 2) return;
        for (int i = 0; i < path.size() - 1; i++) {
            Render3DUtil.vertexLine(matrices, buffer, path.get(i), path.get(i + 1), color);
        }
    }

    private static ItemStack pickThrowable(Predictions module, ItemStack main, ItemStack off) {
        if (module.classifyHeldStack(main) != Predictions.HeldType.NONE) return main;
        if (module.classifyHeldStack(off) != Predictions.HeldType.NONE) return off;
        return null;
    }

    /** Marker geometry record. {@code entity} flips to true on entity-hit so the renderer can place the marker forward of the body. The {@code face} is the surface normal direction for block hits, used to orient the floor ring against walls / ceilings. */
    private record ImpactMark(Vec3d pos, boolean entity, net.minecraft.util.math.Direction face) {
    }
}
