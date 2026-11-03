package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/** Samples water and lava in one traversal, preserving vanilla's separate depth and push order. */
final class ProjectileFluidSampler {
    private final BlockView world;
    private final PredictionBlockAccess blocks;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final double lavaSpeed;

    ProjectileFluidSampler(BlockView world, PredictionBlockAccess blocks, boolean ultrawarm) {
        this.world = world;
        this.blocks = blocks;
        lavaSpeed = ultrawarm ? 0.007 : 0.0023333333333333335;
    }

    ProjectilePhysics.FluidMotion sample(Box bounds, Vec3d motion, boolean pushed) {
        double minY = bounds.minY + 0.001;
        int minX = MathHelper.floor(bounds.minX + 0.001), maxX = MathHelper.ceil(bounds.maxX - 0.001);
        int firstY = MathHelper.floor(minY), maxY = MathHelper.ceil(bounds.maxY - 0.001);
        int minZ = MathHelper.floor(bounds.minZ + 0.001), maxZ = MathHelper.ceil(bounds.maxZ - 0.001);
        boolean touchingWater = false;
        double waterDepth = 0, lavaDepth = 0;
        double wx = 0, wy = 0, wz = 0, lx = 0, ly = 0, lz = 0;
        int waterSamples = 0, lavaSamples = 0;
        for (int x = minX; x < maxX; x++) {
            for (int y = firstY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    var fluid = blocks.fluidState(pos);
                    if (fluid.isEmpty()) continue;
                    boolean water = fluid.isIn(FluidTags.WATER), lava = fluid.isIn(FluidTags.LAVA);
                    if (!water && !lava) continue;
                    double surface = (double) ((float) y + fluid.getHeight(world, pos));
                    if (surface < minY) continue;
                    Vec3d flow = pushed ? fluid.getVelocity(world, pos) : Vec3d.ZERO;
                    if (water) {
                        touchingWater = true;
                        waterDepth = Math.max(waterDepth, surface - minY);
                        if (pushed) {
                            double factor = waterDepth < 0.4 ? waterDepth : 1;
                            wx += flow.x * factor;
                            wy += flow.y * factor;
                            wz += flow.z * factor;
                            waterSamples++;
                        }
                    }
                    // Do not use else-if: modded fluids may belong to BOTH tags.
                    if (lava) {
                        lavaDepth = Math.max(lavaDepth, surface - minY);
                        if (pushed) {
                            double factor = lavaDepth < 0.4 ? lavaDepth : 1;
                            lx += flow.x * factor;
                            ly += flow.y * factor;
                            lz += flow.z * factor;
                            lavaSamples++;
                        }
                    }
                }
            }
        }
        motion = push(motion, wx, wy, wz, waterSamples, 0.014);
        motion = push(motion, lx, ly, lz, lavaSamples, lavaSpeed);
        return new ProjectilePhysics.FluidMotion(touchingWater, motion);
    }

    private static Vec3d push(Vec3d motion, double x, double y, double z, int samples, double speed) {
        if (x * x + y * y + z * z <= 0) return motion;
        // Keep the original multiply -> normalize -> multiply sequence (including its tiny-flow cutoff).
        Vec3d push = new Vec3d(x, y, z).multiply(1.0 / samples).normalize().multiply(speed);
        if (Math.abs(motion.x) < 0.003 && Math.abs(motion.z) < 0.003 && push.length() < 0.0045) {
            push = push.normalize().multiply(0.0045000000000000005);
        }
        return motion.add(push);
    }
}
