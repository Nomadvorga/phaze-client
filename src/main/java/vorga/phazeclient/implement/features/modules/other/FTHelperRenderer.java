package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import vorga.phazeclient.api.system.render.Render3DUtil;

/**
 * Rendering side of {@link FTHelper}. Stateless - the world-render
 * mixin calls into {@link #render} once per frame, this class
 * inspects the module's current {@link FTHelper#getHighlightType()}
 * and dispatches to the appropriate visualiser.
 *
 * <h3>Visualisers</h3>
 * <ul>
 *   <li><b>TRAPKA</b>: 3x3x3 axis-aligned block-snapped box at
 *       the player's voxel position (same anchor style as plast).</li>
 *   <li><b>TRAPKA_DRAGON</b>: 7x7x3 wider variant for the
 *       драконья трапка - same anchoring as TRAPKA.</li>
 *   <li><b>CIRCLE_10</b>: 10-block radius thick flat ring at feet,
 *       optional flat glow disc underneath. Used for
 *       dezorientation / явная пыль / огненный смерч.</li>
 *   <li><b>BOZHESTVENNAYA_AURA</b>: 2-block radius flat ring around
 *       the player feet, same family as CIRCLE_10 but smaller.</li>
 *   <li><b>SNEZHOK_PREDICTION</b>: thick flat ring at the predicted
 *       snowball impact point, computed via a forward Euler raycast
 *       that mirrors vanilla's snowball physics.</li>
 *   <li><b>PLAST</b>: 5x2x5 block placement plane oriented to the
 *       crosshair-targeted block face.</li>
 * </ul>
 *
 * <h3>Logic credits</h3>
 * Adapted from {@code winvi.moscow.soupbetter.render.FTHelperRenderer}.
 * The Phaze port replaces upstream's texture-mapped circle with a
 * line-strip outline so we don't need to ship the additional
 * {@code particle_circle.png} asset, and substitutes the per-call
 * Theme helper-colour with a fixed cyan-ish accent that contrasts
 * cleanly against the FT terrain palette.
 */
public final class FTHelperRenderer {
    /** Default fill colour scale factor (per-shape adjustable). */
    private static final float FILL_ALPHA_SCALE = 0.18F;

    /** Reference radius the user-set thickness corresponds to. The
     *  10-block CIRCLE_10 ring is the calibration target - smaller
     *  rings divide thickness proportionally so a 2-block ring is
     *  5x thinner than a 10-block ring at the same slider value.
     *  Keeps every visualiser visually balanced regardless of size. */
    private static final float REFERENCE_THICKNESS_RADIUS = 10.0F;

    private static float scaledThickness(FTHelper module, float radius) {
        return module.circleThickness.getValue() * (radius / REFERENCE_THICKNESS_RADIUS);
    }

    private FTHelperRenderer() {
    }

    /** Top-level dispatch invoked from {@code WorldRendererFTRendererMixin}. */
    public static void render(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        FTHelper module = FTHelper.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        FTHelper.HighlightType type = module.getHighlightType();
        if (type == FTHelper.HighlightType.NONE) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }

        switch (type) {
            case TRAPKA -> drawTrapkaBox(matrices, cameraPos, tickCounter, 1);
            case TRAPKA_DRAGON -> drawTrapkaBox(matrices, cameraPos, tickCounter, 3);
            case CIRCLE_10 -> drawCircleAtFeet(matrices, cameraPos, tickCounter, 10.0F);
            case BOZHESTVENNAYA_AURA -> drawCircleAtFeet(matrices, cameraPos, tickCounter, 2.0F);
            case SNEZHOK_PREDICTION -> drawSnezhokPrediction(matrices, cameraPos);
            case PLAST -> drawPlastBox(matrices, cameraPos);
            case NONE -> { /* unreachable */ }
        }
    }

    private static Vec3d lerpedPlayerPos(MinecraftClient mc, RenderTickCounter tickCounter) {
        ClientPlayerEntity player = mc.player;
        float td = tickCounter.getTickDelta(false);
        return new Vec3d(
                MathHelper.lerp(td, player.prevX, player.getX()),
                MathHelper.lerp(td, player.prevY, player.getY()),
                MathHelper.lerp(td, player.prevZ, player.getZ())
        );
    }

    /**
     * Trapka visualiser - axis-aligned box snapped to the block the
     * player is standing in (same voxel-aligned anchor as the plast
     * renderer). The {@code halfExtent} parameter chooses the
     * footprint: {@code 1} produces a 3x3x3 region (regular трапка),
     * {@code 3} produces 7x7x3 (драконья трапка). Snapping to a
     * BlockPos avoids the "swimming" look the box had when it was
     * centred on the lerped player position - now the trap region
     * matches the actual server-side block range. Depth-test on so
     * the box occludes correctly behind terrain instead of bleeding
     * through walls.
     */
    private static void drawTrapkaBox(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter, int halfExtent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        FTHelper module = FTHelper.getInstance();
        int color = module.resolveBoxColor();
        // Block-aligned anchor: floor of the player's foot position.
        // BlockPos.ofFloored handles negative coordinates correctly.
        BlockPos block = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());
        // Region centred on the block: inset by halfExtent on each
        // horizontal axis. Total footprint = (halfExtent * 2 + 1)^2
        // blocks. Height fixed at 3 for both variants - the trap
        // mechanic itself is the same vertical reach.
        float x1 = block.getX() - halfExtent;
        float y1 = block.getY();
        float z1 = block.getZ() - halfExtent;
        float x2 = block.getX() + halfExtent + 1;
        float y2 = block.getY() + 3;
        float z2 = block.getZ() + halfExtent + 1;
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        // Block-aligned trapka uses depth-test on so the box reads
        // as a physical voxel range instead of a ghost overlay.
        Render3DUtil.drawBox(matrices, x1, y1, z1, x2, y2, z2, color, FILL_ALPHA_SCALE, 2.0F);
        matrices.pop();
    }

    private static void drawCircleAtFeet(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FTHelper module = FTHelper.getInstance();
        int color = module.resolveCircleColor();
        Vec3d p = lerpedPlayerPos(mc, tickCounter);
        // Scale thickness by radius so a 2-block aura ring isn't as
        // chonky as a 10-block dezo ring at the same slider value -
        // visually balanced across all FT helper circles.
        float thickness = scaledThickness(module, radius);
        float ringY = (float) (p.y + 0.05);
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Optional flat glow disc UNDER the ring. Drawn first so
        // the ring paints on top of the halo. The disc fades from
        // bright centre alpha to fully transparent at the edge,
        // which gives the soft falloff without using a billboard.
        if (module.circleGlow.isValue()) {
            float strength = module.circleGlowStrength.getValue();
            int centerAlpha = Math.max(0, Math.min(255, Math.round(80.0F * Math.min(2.0F, strength))));
            int centerColor = (centerAlpha << 24) | (color & 0x00FFFFFF);
            int rimColor = color & 0x00FFFFFF; // alpha=0 at rim
            // Glow disc extends 1.4x the ring radius - large enough
            // to read as a halo, small enough not to overlap
            // adjacent abilities.
            Render3DUtil.drawFlatGlowDisc(matrices, (float) p.x, ringY, (float) p.z,
                    radius * (1.0F + 0.2F * Math.min(2.0F, strength)),
                    centerColor, rimColor, 96, true);
        }

        // The ring itself. Thick flat annulus on the XZ plane so
        // the visible thickness matches the user's slider value -
        // OpenGL line width clamps to 1px on most drivers, so we
        // emit real geometry instead of relying on RenderSystem
        // .lineWidth which would silently fail.
        Render3DUtil.drawThickFlatRing(matrices, (float) p.x, ringY, (float) p.z,
                radius, thickness, color, 96, true);
        matrices.pop();
    }

    /**
     * Snezhok zamorozki impact predictor - simulates the snowball
     * trajectory the player would launch from the current eye
     * position and paints a 7x7 (radius=3.5) AOE ring at the
     * predicted impact point. Same forward-Euler raycast vanilla
     * uses for snowballs (drag 0.99, gravity 0.03).
     */
    private static void drawSnezhokPrediction(MatrixStack matrices, Vec3d cameraPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        FTHelper module = FTHelper.getInstance();
        PlayerEntity player = mc.player;
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d motion = look.multiply(1.5);
        Vec3d pos = eye;
        Vec3d hit = null;
        for (int i = 0; i < 200; i++) {
            Vec3d next = pos.add(motion);
            HitResult result = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, player));
            if (result.getType() != HitResult.Type.MISS) {
                hit = result.getPos();
                break;
            }
            motion = motion.multiply(0.99).add(0.0, -0.03, 0.0);
            pos = next;
            if (next.y < -64 || next.distanceTo(eye) > 100) break;
        }
        if (hit == null) return;

        int color = module.resolveCircleColor();
        // 7x7 AOE: radius 3.5 blocks. Same as the in-flight tracker
        // ring so a thrown снежок's predicted area matches its
        // actual visible area while it travels.
        float radius = 3.5F;
        float thickness = scaledThickness(module, radius);
        float ringY = (float) hit.y + 0.05F;
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        if (module.circleGlow.isValue()) {
            float strength = module.circleGlowStrength.getValue();
            int centerAlpha = Math.max(0, Math.min(255, Math.round(80.0F * Math.min(2.0F, strength))));
            int centerColor = (centerAlpha << 24) | (color & 0x00FFFFFF);
            int rimColor = color & 0x00FFFFFF;
            Render3DUtil.drawFlatGlowDisc(matrices, (float) hit.x, ringY, (float) hit.z,
                    radius * (1.0F + 0.2F * Math.min(2.0F, strength)),
                    centerColor, rimColor, 64, true);
        }
        Render3DUtil.drawThickFlatRing(matrices, (float) hit.x, ringY, (float) hit.z,
                radius, thickness, color, 64, true);
        matrices.pop();
    }

    private static void drawPlastBox(MatrixStack matrices, Vec3d cameraPos) {
        FTHelper module = FTHelper.getInstance();
        BlockPos target = module.getTargetBlockPos();
        Direction side = module.getTargetBlockSide();
        if (target == null || side == null) return;
        // Plast is a 5x2x5 placement plane oriented so the 2-thick axis
        // lies along the targeted face's normal, mirroring upstream.
        float x1, y1, z1, x2, y2, z2;
        switch (side) {
            case UP -> {
                x1 = target.getX() - 2;
                y1 = target.getY() + 1;
                z1 = target.getZ() - 2;
                x2 = target.getX() + 3;
                y2 = target.getY() + 3;
                z2 = target.getZ() + 3;
            }
            case DOWN -> {
                x1 = target.getX() - 2;
                y1 = target.getY() - 1;
                z1 = target.getZ() - 2;
                x2 = target.getX() + 3;
                y2 = target.getY() + 1;
                z2 = target.getZ() + 3;
            }
            case NORTH, SOUTH -> {
                x1 = target.getX() - 2;
                y1 = target.getY() - 2;
                z1 = side == Direction.SOUTH ? target.getZ() + 1 : target.getZ() - 1;
                x2 = target.getX() + 3;
                y2 = target.getY() + 3;
                z2 = side == Direction.SOUTH ? target.getZ() + 3 : target.getZ() + 1;
            }
            default -> { // EAST / WEST
                x1 = side == Direction.EAST ? target.getX() + 1 : target.getX() - 1;
                y1 = target.getY() - 2;
                z1 = target.getZ() - 2;
                x2 = side == Direction.EAST ? target.getX() + 3 : target.getX() + 1;
                y2 = target.getY() + 3;
                z2 = target.getZ() + 3;
            }
        }
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Render3DUtil.drawBox(matrices, x1, y1, z1, x2, y2, z2, module.resolveBoxColor(), FILL_ALPHA_SCALE, 2.0F);
        matrices.pop();
    }

    /**
     * Per-frame snowball-tracker pass. Walks the FT helper's tracked
     * "снежок заморозки" list and paints a flat ring under each
     * live snowball. The tracking lives in {@link FTHelper} now -
     * the standalone SnowballTracker module was retired so the
     * single FT helper exposes both detection and rendering for
     * every FunTime ability in one place.
     */
    public static void renderSnowballs(MatrixStack matrices, Vec3d cameraPos) {
        FTHelper module = FTHelper.getInstance();
        if (module == null || !module.isEnabled() || !module.snezhokZamorozkaEnabled.isValue()) return;
        var list = module.getTrackedSnowballs();
        if (list.isEmpty()) return;
        int color = module.resolveCircleColor();
        // Scale by the snowball ring's radius (3.5) relative to the
        // CIRCLE_10 reference so all FT rings stay visually balanced.
        float thickness = scaledThickness(module, 3.5F);
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (var t : list) {
            Vec3d p = t.getPosition();
            float ringY = (float) p.y + 0.05F;
            if (module.circleGlow.isValue()) {
                float strength = module.circleGlowStrength.getValue();
                int centerAlpha = Math.max(0, Math.min(255, Math.round(80.0F * Math.min(2.0F, strength))));
                int centerColor = (centerAlpha << 24) | (color & 0x00FFFFFF);
                int rimColor = color & 0x00FFFFFF;
                Render3DUtil.drawFlatGlowDisc(matrices,
                        (float) p.x, ringY, (float) p.z,
                        3.5F * (1.0F + 0.2F * Math.min(2.0F, strength)),
                        centerColor, rimColor, 64, true);
            }
            Render3DUtil.drawThickFlatRing(matrices,
                    (float) p.x, ringY, (float) p.z,
                    3.5F, thickness, color, 64, true);

            // Flight-trail polyline. Connects every recorded
            // tick-position so the user can see exactly where the
            // tracked snowball came from after they threw it. Camera
            // matrix is already pushed above so we feed raw world
            // coords into vertexLine.
            var trail = t.getTrail();
            if (trail != null && trail.size() >= 2) {
                Render3DUtil.drawPolyline(matrices, trail, color, 2.0F, true);
            }
        }
        matrices.pop();
    }

    /** Public alias used by the world-render mixin. */
    public static void renderHighlight(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        render(matrices, cameraPos, tickCounter);
    }
}
