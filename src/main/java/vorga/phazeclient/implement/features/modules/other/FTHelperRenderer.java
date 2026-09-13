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

public final class FTHelperRenderer {

    private static final float FILL_ALPHA_SCALE = 0.18F;

    private static final double TRAPKA_BLOCK_INSET = 0.01;

    private static final float REFERENCE_THICKNESS_RADIUS = 10.0F;

    private static float scaledThickness(FTHelper module, float radius) {
        return module.circleThickness.getValue() * (radius / REFERENCE_THICKNESS_RADIUS);
    }

    private FTHelperRenderer() {
    }

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
            case SNEZHOK_PREDICTION -> drawSnezhokPrediction(matrices, cameraPos, tickCounter);
            case PLAST -> drawPlastBox(matrices, cameraPos);
            case NONE -> {   }
        }
    }

    private static Vec3d lerpedPlayerPos(MinecraftClient mc, RenderTickCounter tickCounter) {
        ClientPlayerEntity player = mc.player;
        float td = tickCounter.getTickProgress(false);

        return new Vec3d(
                MathHelper.lerp(td, player.lastX, player.getX()),
                MathHelper.lerp(td, player.lastY, player.getY()),
                MathHelper.lerp(td, player.lastZ, player.getZ())
        );
    }

    private static void drawTrapkaBox(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter, int halfExtent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        FTHelper module = FTHelper.getInstance();
        int color = module.resolveBoxColor();

        BlockPos block = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());

        Render3DUtil.drawBox(matrices,
                (float) (block.getX() - halfExtent + TRAPKA_BLOCK_INSET - cameraPos.x),
                (float) (block.getY() + TRAPKA_BLOCK_INSET - cameraPos.y),
                (float) (block.getZ() - halfExtent + TRAPKA_BLOCK_INSET - cameraPos.z),
                (float) (block.getX() + halfExtent + 1 - TRAPKA_BLOCK_INSET - cameraPos.x),
                (float) (block.getY() + 3 - TRAPKA_BLOCK_INSET - cameraPos.y),
                (float) (block.getZ() + halfExtent + 1 - TRAPKA_BLOCK_INSET - cameraPos.z),
                color, FILL_ALPHA_SCALE, 2.0F);
    }

    private static void drawCircleAtFeet(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FTHelper module = FTHelper.getInstance();
        int color = module.resolveCircleColor();
        Vec3d p = lerpedPlayerPos(mc, tickCounter);

        float thickness = scaledThickness(module, radius);
        float ringY = (float) (p.y + 0.05);
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        if (module.circleGlow.isValue()) {
            float strength = module.circleGlowStrength.getValue();
            int centerAlpha = Math.max(0, Math.min(255, Math.round(80.0F * Math.min(2.0F, strength))));
            int centerColor = (centerAlpha << 24) | (color & 0x00FFFFFF);
            int rimColor = color & 0x00FFFFFF;

            Render3DUtil.drawFlatGlowDisc(matrices, (float) p.x, ringY, (float) p.z,
                    radius * (1.0F + 0.2F * Math.min(2.0F, strength)),
                    centerColor, rimColor, 96, true);
        }

        Render3DUtil.drawThickFlatRing(matrices, (float) p.x, ringY, (float) p.z,
                radius, thickness, color, 96, true);
        matrices.pop();
    }

    private static void drawSnezhokPrediction(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        FTHelper module = FTHelper.getInstance();
        PlayerEntity player = mc.player;
        float tickDelta = tickCounter.getTickProgress(false);

        Vec3d eye = player.getCameraPosVec(tickDelta);
        Vec3d look = player.getRotationVec(tickDelta);
        Vec3d hit = resolveSnezhokHit(mc, player, eye, look);
        if (hit == null) return;

        int color = module.resolveCircleColor();

        float radius = 3.5F;
        float thickness = scaledThickness(module, radius);
        float ringX = (float) (hit.x - cameraPos.x);
        float ringY = (float) (hit.y - cameraPos.y + 0.05F);
        float ringZ = (float) (hit.z - cameraPos.z);
        if (module.circleGlow.isValue()) {
            float strength = module.circleGlowStrength.getValue();
            int centerAlpha = Math.max(0, Math.min(255, Math.round(80.0F * Math.min(2.0F, strength))));
            int centerColor = (centerAlpha << 24) | (color & 0x00FFFFFF);
            int rimColor = color & 0x00FFFFFF;
            Render3DUtil.drawFlatGlowDisc(matrices, ringX, ringY, ringZ,
                    radius * (1.0F + 0.2F * Math.min(2.0F, strength)),
                    centerColor, rimColor, 64, true);
        }
        Render3DUtil.drawThickFlatRing(matrices, ringX, ringY, ringZ,
                radius, thickness, color, 64, true);
    }

    private static Vec3d resolveSnezhokHit(MinecraftClient mc, PlayerEntity player, Vec3d eye, Vec3d look) {
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
        return hit;
    }

    private static void drawPlastBox(MatrixStack matrices, Vec3d cameraPos) {
        FTHelper module = FTHelper.getInstance();
        BlockPos target = module.getTargetBlockPos();
        Direction side = module.getTargetBlockSide();
        if (target == null || side == null) return;

        double x1, y1, z1, x2, y2, z2;
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
            default -> {
                x1 = side == Direction.EAST ? target.getX() + 1 : target.getX() - 1;
                y1 = target.getY() - 2;
                z1 = target.getZ() - 2;
                x2 = side == Direction.EAST ? target.getX() + 3 : target.getX() + 1;
                y2 = target.getY() + 3;
                z2 = target.getZ() + 3;
            }
        }

        Render3DUtil.drawBox(matrices,
                (float) (x1 - cameraPos.x), (float) (y1 - cameraPos.y), (float) (z1 - cameraPos.z),
                (float) (x2 - cameraPos.x), (float) (y2 - cameraPos.y), (float) (z2 - cameraPos.z),
                module.resolveBoxColor(), FILL_ALPHA_SCALE, 2.0F);
    }

    public static void renderSnowballs(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        FTHelper module = FTHelper.getInstance();
        if (module == null || !module.isEnabled() || !module.snezhokZamorozkaEnabled.isValue()) return;
        var list = module.getTrackedSnowballs();
        if (list.isEmpty()) return;
        int color = module.resolveCircleColor();

        float thickness = scaledThickness(module, 3.5F);
        float tickDelta = tickCounter.getTickProgress(false);
        for (var t : list) {
            Vec3d p = t.getRenderPosition(tickDelta);
            float ringX = (float) (p.x - cameraPos.x);
            float ringY = (float) (p.y - cameraPos.y + 0.05F);
            float ringZ = (float) (p.z - cameraPos.z);
            if (module.circleGlow.isValue()) {
                float strength = module.circleGlowStrength.getValue();
                int centerAlpha = Math.max(0, Math.min(255, Math.round(80.0F * Math.min(2.0F, strength))));
                int centerColor = (centerAlpha << 24) | (color & 0x00FFFFFF);
                int rimColor = color & 0x00FFFFFF;
                Render3DUtil.drawFlatGlowDisc(matrices,
                        ringX, ringY, ringZ,
                        3.5F * (1.0F + 0.2F * Math.min(2.0F, strength)),
                        centerColor, rimColor, 64, true);
            }
            Render3DUtil.drawThickFlatRing(matrices,
                    ringX, ringY, ringZ,
                    3.5F, thickness, color, 64, true);

            var trail = t.getTrail();
            if (trail != null && trail.size() >= 2) {
                Render3DUtil.drawPolylineOffset(matrices, trail,
                        -cameraPos.x, -cameraPos.y, -cameraPos.z,
                        color, 2.0F, true);
            }
        }
    }

    public static void renderHighlight(MatrixStack matrices, Vec3d cameraPos, RenderTickCounter tickCounter) {
        render(matrices, cameraPos, tickCounter);
    }
}
