package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.WorldChunk;

/** Live block reads with a tiny per-simulation chunk cache, not a stale block/shape snapshot. */
final class PredictionBlockAccess {
    private static final int CHUNK_CACHE_SIZE = 16;
    private final BlockView view;
    private final World world;
    private final ShapeContext shapeContext;
    private final WorldChunk[] chunks = new WorldChunk[CHUNK_CACHE_SIZE];
    private final long[] chunkKeys = new long[CHUNK_CACHE_SIZE];
    private Vec3d start, end;

    PredictionBlockAccess(BlockView view, ShapeContext shapeContext) {
        this.view = view;
        this.world = view instanceof World realWorld ? realWorld : null;
        this.shapeContext = shapeContext;
    }

    BlockHitResult collision(Vec3d start, Vec3d end, WorldBorder border) {
        this.start = start;
        this.end = end;
        // Vanilla's voxel traversal, epsilon, tie ordering and raycast-shape normals are retained.
        // FluidHandling.NONE never needs the extra fluid lookup done by BlockView.raycast.
        BlockHitResult hit = BlockView.raycast(start, end, this,
                PredictionBlockAccess::raycastBlock, PredictionBlockAccess::miss);
        if (border != null && border.contains(start) && !border.contains(hit.getPos())) {
            Vec3d position = border.clamp(hit.getPos());
            Vec3d direction = hit.getPos().subtract(start);
            return new BlockHitResult(position, Direction.getFacing(direction.x, direction.y, direction.z),
                    BlockPos.ofFloored(position), false, true);
        }
        return hit;
    }

    private BlockHitResult raycastBlock(BlockPos pos) {
        BlockState state = blockState(pos);
        if (state.isAir()) return null;
        // Context-sensitive and neighbour-dependent shapes still see the REAL world and shooter.
        var shape = state.getCollisionShape(view, pos, shapeContext);
        return shape.isEmpty() ? null : view.raycastBlock(start, end, pos, shape, state);
    }

    private BlockHitResult miss() {
        Vec3d direction = start.subtract(end);
        return BlockHitResult.createMissed(end, Direction.getFacing(direction.x, direction.y, direction.z),
                BlockPos.ofFloored(end));
    }

    BlockState blockState(BlockPos pos) {
        return world == null || view.isOutOfHeightLimit(pos) ? view.getBlockState(pos) : chunk(pos).getBlockState(pos);
    }

    FluidState fluidState(BlockPos pos) {
        return world == null || view.isOutOfHeightLimit(pos) ? view.getFluidState(pos) : chunk(pos).getFluidState(pos);
    }

    private WorldChunk chunk(BlockPos pos) {
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        int index = (x * 31 + z) & (CHUNK_CACHE_SIZE - 1);
        long key = ChunkPos.toLong(x, z);
        WorldChunk chunk = chunks[index];
        if (chunk == null || chunkKeys[index] != key) {
            chunks[index] = chunk = world.getChunk(x, z);
            chunkKeys[index] = key;
        }
        return chunk;
    }
}
