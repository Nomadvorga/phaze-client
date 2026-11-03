package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.border.WorldBorder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Query optimization parity against vanilla voxel traversal and the original two-pass fluid sampler. */
public final class ProjectileQueryChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        entityQueries();
        entityQueryBoundaries();
        queryCounts();
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        bindFluids(false);
        blockRaycasts();
        fluidSampling(false);
        bindFluids(true);
        fluidSampling(true);
        bindFluids(false);
        tinyFluidPushes();
        completeFlights();
        System.out.println("Projectile query checks passed: " + checks);
    }

    private static void entityQueries() {
        Random random = new Random(1214300);
        for (int scene = 0; scene < 16; scene++) {
            List<Target> targets = new ArrayList<>();
            for (int entity = 0; entity < 500; entity++) {
                double x = random.nextDouble() * 80 - 40, y = random.nextDouble() * 12 - 6, z = random.nextDouble() * 80 - 40;
                targets.add(new Target(new Box(x, y, z, x + 0.2 + random.nextDouble() * 3,
                        y + 0.2 + random.nextDouble() * 3, z + 0.2 + random.nextDouble() * 3), entity % 7 != 0, entity % 5 != 0));
            }
            EntitySource source = new EntitySource(targets);
            ProjectileEntityQuery<Target> cache = new ProjectileEntityQuery<>(source);
            Vec3d position = new Vec3d(-35, 1, -35);
            for (int step = 0; step < 300; step++) {
                Vec3d end = position.add(random.nextDouble() * 3 - 1.2, random.nextDouble() * 2 - 1, random.nextDouble() * 3 - 1.2);
                Box search = new Box(position, end).expand(1);
                contact(cache.collision(position, end, search), referenceEntity(source, position, end, search));
                position = end;
            }
        }
    }

    private static void entityQueryBoundaries() {
        Target far = new Target(new Box(3, -0.2, -0.2, 4, 0.2, 0.2), true, true);
        Target near = new Target(new Box(1, -0.2, -0.2, 2, 0.2, 0.2), true, false);
        Target same = new Target(near.bounds, true, true);
        EntitySource source = new EntitySource(new ArrayList<>(List.of(far, near, same)));
        ProjectileEntityQuery<Target> cache = new ProjectileEntityQuery<>(source);
        Vec3d start = Vec3d.ZERO, end = new Vec3d(6, 0, 0);
        Box search = new Box(start, end).expand(1);
        check(cache.collision(start, end, search).entity() == same, "nearest eligible hit wins, not broad-phase iteration order");
        near.allowed = true;
        check(cache.collision(start, end, search).entity() == near, "owner eligibility is re-evaluated after leaving the owner");
        check(source.queries == 1, "owner eligibility changes do not force another broad-phase query");
        near.bounds = new Box(8, -0.2, -0.2, 9, 0.2, 0.2);
        check(cache.collision(start, end, search).entity() == same, "candidate bounds are live, not a stale collision snapshot");
        contact(cache.collision(new Vec3d(3.5, 0, 0), end, search), referenceEntity(source, new Vec3d(3.5, 0, 0), end, search));

        EntitySource outside = new EntitySource(List.of(new Target(new Box(1, 0.1, -0.1, 2, 0.4, 0.1), true, true)));
        Box narrow = new Box(0, -0.1, -0.1, 4, 0.05, 0.1);
        check(new ProjectileEntityQuery<>(outside).collision(start, end, narrow) == null,
                "padded candidate query never expands the ORIGINAL eligibility search box");
        source.targets.add(new Target(new Box(0.5, -0.2, -0.2, 0.7, 0.2, 0.2), true, true));
        contact(new ProjectileEntityQuery<>(source).collision(start, end, search), referenceEntity(source, start, end, search));
        cache.collision(new Vec3d(100, 0, 0), new Vec3d(102, 0, 0), new Box(99, -1, -1, 103, 1, 1));
        check(source.queries > 1, "leaving a cached region refreshes the local entity broad phase");
    }

    private static void queryCounts() {
        EntitySource source = new EntitySource(List.of());
        ProjectileEntityQuery<Target> cache = new ProjectileEntityQuery<>(source);
        for (int step = 0; step < 300; step++) {
            Vec3d start = new Vec3d(step, 64, 0), end = start.add(1, 0, 0);
            check(cache.collision(start, end, new Box(start, end).expand(1)) == null, "empty-space cache remains collision-free");
        }
        check(source.queries <= 40, "300 flight steps need at most 40 local broad-phase queries, not 300");
        System.out.println("Entity broad-phase queries in 300-step empty flight: 300 -> " + source.queries);
    }

    /** Same collection, eligibility, 0.3F hit margin and strict nearest-hit ordering as ProjectileUtil. */
    private static ProjectileEntityQuery.Contact<Target> referenceEntity(EntitySource source, Vec3d start, Vec3d end, Box search) {
        Target nearest = null;
        Vec3d position = null;
        double distance = Double.MAX_VALUE;
        for (Target target : source.collect(search)) {
            if (!source.canHit(target)) continue;
            var hit = target.bounds.expand(0.3F).raycast(start, end);
            if (hit.isPresent() && start.squaredDistanceTo(hit.get()) < distance) {
                nearest = target;
                position = hit.get();
                distance = start.squaredDistanceTo(position);
            }
        }
        return nearest == null ? null : new ProjectileEntityQuery.Contact<>(nearest, position);
    }

    private static void contact(ProjectileEntityQuery.Contact<Target> actual, ProjectileEntityQuery.Contact<Target> expected) {
        check((actual == null) == (expected == null), "cached query and vanilla narrow phase agree on hit/miss");
        if (actual != null) {
            check(actual.entity() == expected.entity(), "cached broad phase preserves the closest target and ties");
            vector(actual.position(), expected.position(), 0, "entity contact coordinates and hit margins remain exact");
        }
    }

    private static void blockRaycasts() {
        Random random = new Random(21804);
        Block[] blocks = {Blocks.STONE, Blocks.OAK_STAIRS, Blocks.STONE_SLAB, Blocks.OAK_FENCE, Blocks.COBBLESTONE_WALL,
                Blocks.OAK_TRAPDOOR, Blocks.SNOW, Blocks.SCAFFOLDING, Blocks.POWDER_SNOW, Blocks.BAMBOO, Blocks.LILY_PAD};
        ShapeContext[] contexts = {ShapeContext.absent(), new TestShapeContext(false, true), new TestShapeContext(true, false)};
        for (int offset : new int[]{0, 29_998_992}) {
            TestWorld world = new TestWorld();
            world.border.setCenter(offset, -offset);
            world.border.setSize(32);
            for (int x = -7; x <= 7; x++) {
                for (int z = -7; z <= 7; z++) {
                    world.blocks.put(new BlockPos(offset + x, -1, -offset + z), Blocks.STONE.getDefaultState());
                    Block block = blocks[random.nextInt(blocks.length)];
                    List<BlockState> states = block.getStateManager().getStates();
                    world.blocks.put(new BlockPos(offset + x, random.nextInt(3), -offset + z), states.get(random.nextInt(states.size())));
                }
            }
            for (ShapeContext context : contexts) {
                PredictionBlockAccess access = new PredictionBlockAccess(world, context);
                for (int ray = 0; ray < 1500; ray++) {
                    Vec3d start = new Vec3d(offset + random.nextDouble() * 36 - 18, random.nextDouble() * 9 - 2,
                            -offset + random.nextDouble() * 36 - 18);
                    Vec3d end = new Vec3d(offset + random.nextDouble() * 36 - 18, random.nextDouble() * 9 - 2,
                            -offset + random.nextDouble() * 36 - 18);
                    hit(access.collision(start, end, world.border), world.getCollisionsIncludingWorldBorder(context(start, end, context)));
                }
                for (Vec3d start : new Vec3d[]{new Vec3d(offset, 0, -offset), new Vec3d(offset + 0.5, 0.5, -offset + 0.5)}) {
                    for (Vec3d delta : new Vec3d[]{Vec3d.ZERO, new Vec3d(16, 0, 0), new Vec3d(0, -4, 0),
                            new Vec3d(16, 16, 16), new Vec3d(-16, -16, -16)}) {
                        Vec3d end = start.add(delta);
                        hit(access.collision(start, end, world.border), world.getCollisionsIncludingWorldBorder(context(start, end, context)));
                    }
                }
            }
        }
        TestWorld air = new TestWorld();
        Vec3d start = new Vec3d(-250, 60, 0), end = new Vec3d(250, 60, 0);
        BlockHitResult expected = air.raycast(context(start, end, ShapeContext.absent()));
        int oldReads = air.fluidReads;
        air.fluidReads = 0;
        PredictionBlockAccess access = new PredictionBlockAccess(air, ShapeContext.absent());
        hit(access.collision(start, end, null), expected);
        check(oldReads > 400 && air.fluidReads == 0, "collision-only rays omit vanilla's redundant FluidHandling.NONE lookups");
        System.out.println("Redundant fluid reads in a 500-block air ray: " + oldReads + " -> " + air.fluidReads);
        air.blocks.put(new BlockPos(0, 60, 0), Blocks.STONE.getDefaultState());
        check(access.collision(start, end, null).getType() == HitResult.Type.BLOCK, "block access observes live block updates");
        air.blocks.clear();
        check(access.collision(start, end, null).getType() == HitResult.Type.MISS, "removed blocks do not remain in a cached shape snapshot");
    }

    private static RaycastContext context(Vec3d start, Vec3d end, ShapeContext context) {
        return new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, context);
    }

    private static void hit(BlockHitResult actual, BlockHitResult expected) {
        check(actual.getType() == expected.getType(), "optimized voxel traversal preserves hit/miss");
        check(actual.getSide() == expected.getSide() && actual.getBlockPos().equals(expected.getBlockPos()),
                "stairs, ledges, inside-block rays and border hits keep vanilla face/block ordering");
        check(actual.isInsideBlock() == expected.isInsideBlock() && actual.isAgainstWorldBorder() == expected.isAgainstWorldBorder(),
                "raycast flags and world-border treatment match vanilla");
        vector(actual.getPos(), expected.getPos(), 0, "block contact coordinates remain exactly vanilla");
    }

    static void bindFluids(boolean dualTags) {
        List<RegistryEntry<Fluid>> water = List.of(Fluids.WATER.getRegistryEntry(), Fluids.FLOWING_WATER.getRegistryEntry());
        List<RegistryEntry<Fluid>> lava = new ArrayList<>(List.of(Fluids.LAVA.getRegistryEntry(), Fluids.FLOWING_LAVA.getRegistryEntry()));
        if (dualTags) lava.addAll(water);
        Registries.FLUID.startTagReload(new TagGroupLoader.RegistryTags<>(RegistryKeys.FLUID,
                Map.of(FluidTags.WATER, water, FluidTags.LAVA, lava))).apply();
    }

    static TestWorld wetWorld(long seed) {
        Random random = new Random(seed);
        TestWorld world = new TestWorld();
        FluidState[] fluids = {Fluids.EMPTY.getDefaultState(), Fluids.WATER.getDefaultState(), Fluids.LAVA.getDefaultState(),
                Fluids.FLOWING_WATER.getFlowing(2, false), Fluids.FLOWING_WATER.getFlowing(7, true),
                Fluids.FLOWING_LAVA.getFlowing(3, false), Fluids.FLOWING_LAVA.getFlowing(6, true)};
        for (int x = -4; x <= 4; x++) for (int y = -2; y <= 2; y++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            world.fluids.put(pos, fluids[random.nextInt(fluids.length)]);
            if (random.nextInt(5) == 0) world.blocks.put(pos, Blocks.STONE.getDefaultState());
        }
        return world;
    }

    private static void fluidSampling(boolean dualTags) {
        Random random = new Random(dualTags ? 7414 : 1214);
        for (boolean ultrawarm : new boolean[]{false, true}) {
            TestWorld world = wetWorld(9182);
            PredictionBlockAccess blocks = new PredictionBlockAccess(world, ShapeContext.absent());
            ProjectileFluidSampler sampler = new ProjectileFluidSampler(world, blocks, ultrawarm);
            for (int sample = 0; sample < 2000; sample++) {
                double x = random.nextDouble() * 6 - 3, y = random.nextDouble() * 4 - 2, z = random.nextDouble() * 6 - 3;
                float size = sample % 2 == 0 ? 0.25F : 0.5F;
                Box bounds = new Box(x - size / 2, y, z - size / 2, x + size / 2, y + size, z + size / 2);
                Vec3d motion = sample % 4 == 0 ? Vec3d.ZERO : new Vec3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
                boolean pushed = sample % 3 != 0;
                ProjectilePhysics.FluidMotion expected = referenceFluids(world, bounds, motion, pushed, ultrawarm);
                ProjectilePhysics.FluidMotion actual = sampler.sample(bounds, motion, pushed);
                check(actual.touchingWater() == expected.touchingWater(), "single-pass fluid contact matches separate water/lava scans");
                vector(actual.motion(), expected.motion(), 0, "fluid depth, float height, averaging and water-before-lava push stay exact");
            }
        }
        TestWorld air = new TestWorld();
        Box bounds = new Box(-0.25, -0.25, -0.25, 0.25, 0.25, 0.25);
        referenceFluids(air, bounds, Vec3d.ZERO, false, false);
        int oldReads = air.fluidReads;
        air.fluidReads = 0;
        new ProjectileFluidSampler(air, new PredictionBlockAccess(air, ShapeContext.absent()), false).sample(bounds, Vec3d.ZERO, false);
        check(air.fluidReads * 2 == oldReads, "air-fluid sampling traverses the bounding box once, not twice");
    }

    static ProjectilePhysics.FluidMotion referenceFluids(TestWorld world, Box bounds, Vec3d motion, boolean pushed, boolean ultrawarm) {
        Box contracted = bounds.contract(0.001);
        ProjectilePhysics.FluidMotion water = referenceFluid(world, contracted, motion, FluidTags.WATER, 0.014, pushed);
        ProjectilePhysics.FluidMotion lava = referenceFluid(world, contracted, water.motion(), FluidTags.LAVA,
                ultrawarm ? 0.007 : 0.0023333333333333335, pushed);
        return new ProjectilePhysics.FluidMotion(water.touchingWater(), lava.motion());
    }

    private static ProjectilePhysics.FluidMotion referenceFluid(TestWorld world, Box bounds, Vec3d motion,
                                                                TagKey<Fluid> tag, double speed, boolean pushed) {
        boolean touching = false;
        double depth = 0;
        Vec3d flow = Vec3d.ZERO;
        int samples = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = MathHelper.floor(bounds.minX); x < MathHelper.ceil(bounds.maxX); x++) {
            for (int y = MathHelper.floor(bounds.minY); y < MathHelper.ceil(bounds.maxY); y++) {
                for (int z = MathHelper.floor(bounds.minZ); z < MathHelper.ceil(bounds.maxZ); z++) {
                    pos.set(x, y, z);
                    FluidState fluid = world.getFluidState(pos);
                    if (!fluid.isIn(tag)) continue;
                    double surface = (double) ((float) y + fluid.getHeight(world, pos));
                    if (surface < bounds.minY) continue;
                    touching = true;
                    depth = Math.max(depth, surface - bounds.minY);
                    if (pushed) {
                        Vec3d current = fluid.getVelocity(world, pos);
                        flow = flow.add(depth < 0.4 ? current.multiply(depth) : current);
                        samples++;
                    }
                }
            }
        }
        return new ProjectilePhysics.FluidMotion(touching, referencePush(motion, flow, samples, speed));
    }

    private static Vec3d referencePush(Vec3d motion, Vec3d flow, int samples, double speed) {
        if (flow.lengthSquared() <= 0) return motion;
        Vec3d push = flow.multiply(1.0 / samples).normalize().multiply(speed);
        if (Math.abs(motion.x) < 0.003 && Math.abs(motion.z) < 0.003 && push.length() < 0.0045)
            push = push.normalize().multiply(0.0045000000000000005);
        return motion.add(push);
    }

    private static void tinyFluidPushes() throws Exception {
        Method push = ProjectileFluidSampler.class.getDeclaredMethod("push", Vec3d.class, double.class, double.class,
                double.class, int.class, double.class);
        push.setAccessible(true);
        for (double scale : new double[]{0, 1E-10, 1E-5, 0.0001, 0.01, 1}) {
            for (Vec3d motion : new Vec3d[]{Vec3d.ZERO, new Vec3d(0.0029, 0.1, -0.0029), new Vec3d(0.003, 0, 0.003)}) {
                for (double speed : new double[]{0.014, 0.007, 0.0023333333333333335}) {
                    Vec3d flow = new Vec3d(scale, scale * 0.5, -scale * 0.25);
                    vector((Vec3d) push.invoke(null, motion, flow.x, flow.y, flow.z, 2, speed),
                            referencePush(motion, flow, 2, speed), 0, "tiny-flow normalization and minimum horizontal-push cutoff remain vanilla");
                }
            }
        }
    }

    private static void completeFlights() {
        TestWorld world = wetWorld(78423);
        world.border.setSize(48);
        Random random = new Random(904);
        for (ProjectilePhysics.Profile profile : ProjectilePhysics.Profile.values()) {
            for (int launch = 0; launch < 60; launch++) {
                Vec3d start = new Vec3d(random.nextDouble() * 4 - 2, random.nextDouble() * 5, random.nextDouble() * 4 - 2);
                Vec3d motion = new Vec3d(random.nextDouble() * 3 - 1.5, random.nextDouble() * 3 - 1, random.nextDouble() * 3 - 1.5);
                List<Vec3d> old = new ArrayList<>(), current = new ArrayList<>();
                HitResult expected = ProjectilePhysics.simulate(start, motion, profile, profile.gravity, profile.size, profile.size,
                        false, environment(world, false), old, 300, -100);
                HitResult actual = ProjectilePhysics.simulate(start, motion, profile, profile.gravity, profile.size, profile.size,
                        false, environment(world, true), current, 300, -100);
                hit((BlockHitResult) actual, (BlockHitResult) expected);
                check(old.size() == current.size(), "query optimizations cannot alter flight duration or landing tick");
                for (int i = 0; i < old.size(); i++) vector(current.get(i), old.get(i), 0, "complete fluid/block flights preserve every physical waypoint");
            }
        }
    }

    private static ProjectilePhysics.Environment environment(TestWorld world, boolean optimized) {
        PredictionBlockAccess blocks = new PredictionBlockAccess(world, ShapeContext.absent());
        ProjectileFluidSampler fluids = new ProjectileFluidSampler(world, blocks, false);
        return new ProjectilePhysics.Environment() {
            @Override public BlockHitResult blockCollision(Vec3d start, Vec3d end) {
                return optimized ? blocks.collision(start, end, world.border)
                        : world.getCollisionsIncludingWorldBorder(context(start, end, ShapeContext.absent()));
            }
            @Override public HitResult entityCollision(Vec3d start, Vec3d end, Box bounds) { return null; }
            @Override public ProjectilePhysics.FluidMotion fluids(Box bounds, Vec3d motion) {
                return optimized ? fluids.sample(bounds, motion, true) : referenceFluids(world, bounds, motion, true, false);
            }
        };
    }

    private static final class Target {
        Box bounds;
        final boolean eligible;
        boolean allowed;
        Target(Box bounds, boolean eligible, boolean allowed) { this.bounds = bounds; this.eligible = eligible; this.allowed = allowed; }
    }

    private static final class EntitySource implements ProjectileEntityQuery.Source<Target> {
        final List<Target> targets;
        int queries;
        EntitySource(List<Target> targets) { this.targets = targets; }
        @Override public List<Target> collect(Box bounds) {
            queries++;
            List<Target> result = new ArrayList<>();
            for (Target target : targets) if (target.eligible && target.bounds.intersects(bounds)) result.add(target);
            return result;
        }
        @Override public Box bounds(Target entity) { return entity.bounds; }
        @Override public boolean canHit(Target entity) { return entity.eligible && entity.allowed; }
    }

    static final class TestWorld implements CollisionView {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, FluidState> fluids = new HashMap<>();
        final WorldBorder border = new WorldBorder();
        int fluidReads;
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        @Override public FluidState getFluidState(BlockPos pos) { fluidReads++; return fluids.getOrDefault(pos, getBlockState(pos).getFluidState()); }
        @Override public int getHeight() { return 384; }
        @Override public int getBottomY() { return -64; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public BlockView getChunkAsView(int x, int z) { return this; }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity, Box bounds) { return List.of(); }
    }

    private record TestShapeContext(boolean descending, boolean above) implements ShapeContext {
        @Override public boolean isDescending() { return descending; }
        @Override public boolean isAbove(VoxelShape shape, BlockPos pos, boolean fallback) { return above; }
        @Override public boolean isHolding(Item item) { return false; }
        @Override public boolean canWalkOnFluid(FluidState state, FluidState other) { return false; }
        @Override public VoxelShape getCollisionShape(BlockState state, CollisionView world, BlockPos pos) { return state.getCollisionShape(world, pos, this); }
    }

    private static void vector(Vec3d actual, Vec3d expected, double epsilon, String message) {
        check(actual.distanceTo(expected) <= epsilon, message + " expected=" + expected + " actual=" + actual);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
