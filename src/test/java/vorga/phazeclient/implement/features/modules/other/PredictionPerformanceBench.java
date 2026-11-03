package vorga.phazeclient.implement.features.modules.other;

import com.sun.management.ThreadMXBean;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import net.minecraft.world.RaycastContext;
import vorga.phazeclient.api.system.render.Render3DUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Optional reproducible CPU-only benchmark. No GPU, shaderpack, FPS promise or timing-based assertions. */
public final class PredictionPerformanceBench {
    private static volatile long sink;
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9, 0.05F, 1024);

    public static void main(String[] args) throws Exception {
        System.out.println("CPU-only median timings; GPU/state-switch cost is excluded. These are NOT FPS measurements.");
        for (int[] scene : new int[][]{{1, 0}, {3, 0}, {3, 12}}) {
            try (GeometryWork work = new GeometryWork(scene[0], scene[1])) {
                benchmark(scene[0] + " held + " + scene[1] + " live geometry", work::oldFrame, work::newFrame, 1600);
                System.out.println("  draw submissions: " + (1 + scene[0] * 2 + scene[1] * 3) + " -> 3");
            }
        }
        String baseline = System.getProperty("phaze.prediction.baseline");
        if (baseline != null && !baseline.isBlank()) {
            Class<?> old = loadOldView(baseline);
            for (boolean moving : new boolean[]{false, true}) {
                ViewWork before = new ViewWork(old, moving), after = new ViewWork(HeldPredictionView.class, moving);
                benchmark(moving ? "held view, changing immediate aim" : "held view, stationary cached frame", before, after, 20_000);
            }
        } else System.out.println("Held-view comparison: pass -PpredictionBaseline=<previous named dev JAR> to enable.");
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        ProjectileQueryChecks.bindFluids(false);
        for (boolean wet : new boolean[]{false, true}) {
            QueryWork queries = new QueryWork(wet);
            benchmark(wet ? "300-step synthetic block/fluid simulation, wet start" : "300-step synthetic block/fluid simulation, air",
                    queries::oldFlight, queries::newFlight, 600);
        }
        if (sink == 0) throw new AssertionError("Benchmark work was not consumed");
    }

    private static void benchmark(String name, Runnable old, Runnable current, int frames) {
        for (int warmup = 0; warmup < frames * 2; warmup++) { old.run(); current.run(); }
        double[] before = new double[7], after = new double[7], oldBytes = new double[7], newBytes = new double[7];
        for (int round = 0; round < 7; round++) {
            if ((round & 1) == 0) {
                before[round] = measure(old, frames, oldBytes, round);
                after[round] = measure(current, frames, newBytes, round);
            } else {
                after[round] = measure(current, frames, newBytes, round);
                before[round] = measure(old, frames, oldBytes, round);
            }
        }
        Arrays.sort(before); Arrays.sort(after); Arrays.sort(oldBytes); Arrays.sort(newBytes);
        System.out.printf(Locale.ROOT, "%s: %.3f -> %.3f us/frame (%.2fx), heap %.0f -> %.0f B/frame%n",
                name, before[3] / 1000, after[3] / 1000, before[3] / after[3], oldBytes[3], newBytes[3]);
    }

    private static double measure(Runnable work, int frames, double[] allocation, int round) {
        var bean = ManagementFactory.getThreadMXBean();
        ThreadMXBean memory = bean instanceof ThreadMXBean supported && supported.isThreadAllocatedMemorySupported() ? supported : null;
        if (memory != null && !memory.isThreadAllocatedMemoryEnabled()) memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long bytes = memory == null ? 0 : memory.getThreadAllocatedBytes(thread), start = System.nanoTime();
        for (int frame = 0; frame < frames; frame++) work.run();
        long elapsed = System.nanoTime() - start;
        allocation[round] = memory == null ? Double.NaN : (double) (memory.getThreadAllocatedBytes(thread) - bytes) / frames;
        return (double) elapsed / frames;
    }

    private static final class GeometryWork implements AutoCloseable {
        private final int held, live;
        private final PredictionRenderBatch batch = new PredictionRenderBatch();
        private final BufferAllocator allocator = new BufferAllocator(262_144);
        private final MatrixStack matrices = new MatrixStack();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f right = new Vector3f(), up = new Vector3f();
        private final List<HeldPredictionView> views = new ArrayList<>();
        private final List<List<Vec3d>> trails = new ArrayList<>();

        GeometryWork(int held, int live) {
            this.held = held;
            this.live = live;
            for (int i = 0; i < held; i++) {
                HeldPredictionView view = new HeldPredictionView();
                view.update(this, this, Predictions.HeldType.SNOWBALL, held, i, 1, 0, 0, trajectory(i * 0.15));
                view.prepare(1);
                view.setLineOrigin(new Vec3d(0, 0, -0.052), PROJECTION, new Matrix4f(PROJECTION).invert(), Vec3d.ZERO);
                views.add(view);
            }
            for (int i = 0; i < live; i++) trails.add(trajectory(i * 0.15).path());
        }

        void oldFrame() {
            BufferBuilder lines = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            for (HeldPredictionView view : views) {
                for (int i = 0; i < view.size() - 1; i++) Render3DUtil.vertexLine(matrices, lines,
                        view.x(i), view.y(i), view.z(i), view.x(i + 1), view.y(i + 1), view.z(i + 1), -1, -1);
            }
            consume(lines.end()); allocator.clear();
            for (List<Vec3d> trail : trails) {
                lines = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                PredictionBatchChecks.emitOldTrail(matrices, lines, trail, Vec3d.ZERO, -1, 2);
                consume(lines.end()); allocator.clear();
            }
            for (int marker = 0; marker < held + live; marker++) {
                float x = marker * 0.15F, z = -25;
                rotation.transform(1, 0, 0, right); rotation.transform(0, 1, 0, up);
                float radius = 1.295F;
                BufferBuilder glow = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                glow.vertex(matrix, x - right.x * radius - up.x * radius, -right.y * radius - up.y * radius, z - right.z * radius - up.z * radius).texture(0, 1).color(0xA0FFFFFF);
                glow.vertex(matrix, x + right.x * radius - up.x * radius, right.y * radius - up.y * radius, z + right.z * radius - up.z * radius).texture(1, 1).color(0xA0FFFFFF);
                glow.vertex(matrix, x + right.x * radius + up.x * radius, right.y * radius + up.y * radius, z + right.z * radius + up.z * radius).texture(1, 0).color(0xA0FFFFFF);
                glow.vertex(matrix, x - right.x * radius + up.x * radius, -right.y * radius + up.y * radius, z - right.z * radius + up.z * radius).texture(0, 0).color(0xA0FFFFFF);
                consume(glow.end()); allocator.clear();
                BufferBuilder sphere = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                PredictionBatchChecks.emitOldSphere(matrix, sphere, x, 0, z, 0.35F, -1, 12, 18);
                consume(sphere.end()); allocator.clear();
            }
        }

        void newFrame() {
            batch.begin(matrices, rotation, PROJECTION, 800, 2, true);
            for (HeldPredictionView view : views) batch.path(view, Vec3d.ZERO, -1);
            for (List<Vec3d> trail : trails) batch.trail(trail, Vec3d.ZERO, -1, 2);
            for (int marker = 0; marker < held + live; marker++) {
                batch.glow(marker * 0.15F, 0, -25, 1.295F, 0xA0FFFFFF);
                batch.sphere(marker * 0.15F, 0, -25, 0.35F, -1);
            }
            consume(batch.endLines()); consume(batch.endGlows()); consume(batch.endMarkers());
        }

        @Override public void close() { batch.close(); allocator.close(); }
    }

    private static void consume(BuiltBuffer buffer) {
        try (buffer) { sink += buffer.getDrawParameters().vertexCount() + Float.floatToRawIntBits(buffer.getBuffer().getFloat(0)); }
    }

    private static final class QueryWork {
        private final ProjectileQueryChecks.TestWorld world;
        private final List<Vec3d> path = new ArrayList<>(301);
        private final Vec3d start, motion = new Vec3d(1.3, 0.05, -0.4);

        QueryWork(boolean wet) {
            world = new ProjectileQueryChecks.TestWorld();
            if (wet) {
                // No random solid blocks: both workloads must complete all 300 steps.
                for (int x = -4; x <= 40; x++) {
                    for (int y = -2; y <= 4; y++) {
                        for (int z = -20; z <= 4; z++) {
                            world.fluids.put(new BlockPos(x, y, z),
                                    Fluids.FLOWING_WATER.getFlowing(8 - ((x ^ z) & 3), false));
                        }
                    }
                }
                world.fluids.put(BlockPos.ORIGIN, Fluids.WATER.getDefaultState());
                if (!world.getFluidState(BlockPos.ORIGIN).isIn(FluidTags.WATER))
                    throw new AssertionError("Benchmark water tags were not initialized");
            }
            start = wet ? new Vec3d(0.25, 0.25, 0.25) : new Vec3d(0.25, 64, 0.25);
            HitResult before = flight(false);
            List<Vec3d> reference = List.copyOf(path);
            HitResult after = flight(true);
            if (before.getType() != HitResult.Type.MISS || after.getType() != HitResult.Type.MISS
                    || reference.size() != 301 || !reference.equals(path) || !before.getPos().equals(after.getPos()))
                throw new AssertionError("Benchmark must compare equal complete 300-step flights");
        }

        void oldFlight() { flight(false); }
        void newFlight() { flight(true); }

        private HitResult flight(boolean optimized) {
            PredictionBlockAccess blocks = optimized ? new PredictionBlockAccess(world, ShapeContext.absent()) : null;
            ProjectileFluidSampler sampler = optimized ? new ProjectileFluidSampler(world, blocks, false) : null;
            HitResult hit = ProjectilePhysics.simulate(start, motion, ProjectilePhysics.Profile.THROWN, 0,
                    0.25F, 0.25F, false, new ProjectilePhysics.Environment() {
                        @Override public BlockHitResult blockCollision(Vec3d start, Vec3d end) {
                            return optimized ? blocks.collision(start, end, world.border)
                                    : world.getCollisionsIncludingWorldBorder(new RaycastContext(start, end,
                                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
                        }
                        @Override public HitResult entityCollision(Vec3d start, Vec3d end, Box bounds) { return null; }
                        @Override public ProjectilePhysics.FluidMotion fluids(Box bounds, Vec3d motion) {
                            return optimized ? sampler.sample(bounds, motion, true)
                                    : ProjectileQueryChecks.referenceFluids(world, bounds, motion, true, false);
                        }
                    }, path, 300, -1000);
            sink += path.size() + Double.doubleToRawLongBits(hit.getPos().x);
            return hit;
        }
    }

    private static Class<?> loadOldView(String path) throws Exception {
        String original = "vorga/phazeclient/implement/features/modules/other/HeldPredictionView";
        String renamed = "vorga/phazeclient/implement/features/modules/other/LegacyHeldPredictionView";
        byte[] bytes;
        try (ZipFile zip = new ZipFile(path); var input = zip.getInputStream(zip.getEntry(original + ".class"))) {
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(input).accept(new ClassRemapper(writer, new SimpleRemapper(original, renamed)), 0);
            bytes = writer.toByteArray();
        }
        return new ClassLoader(PredictionPerformanceBench.class.getClassLoader()) {
            Class<?> define() { return defineClass(renamed.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }

    private static final class ViewWork implements Runnable {
        private final Object state;
        private final MethodHandle update, prepare, origin;
        private final boolean moving;
        private final Object world = new Object(), owner = new Object();
        private final Predictions.TrajectoryResult first = trajectory(0), second = trajectory(0.15);
        private final Vec3d a = new Vec3d(0, 0, -0.052), b = new Vec3d(0.001, 0, -0.052);
        private final Matrix4f inverse = new Matrix4f(PROJECTION).invert();
        private int frame;

        ViewWork(Class<?> type, boolean moving) throws Exception {
            Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            state = constructor.newInstance();
            this.moving = moving;
            update = handle(type, "update", Object.class, Object.class, Predictions.HeldType.class, int.class, float.class,
                    long.class, float.class, float.class, Predictions.TrajectoryResult.class);
            prepare = handle(type, "prepare", float.class);
            origin = handle(type, "setLineOrigin", Vec3d.class, Matrix4f.class, Matrix4f.class, Vec3d.class);
            try { update.invoke(state, world, owner, Predictions.HeldType.SNOWBALL, 1, 0F, 1L, 0F, 0F, first); }
            catch (Throwable failure) { throw new RuntimeException(failure); }
        }

        @Override public void run() {
            try {
                if (moving) update.invoke(state, world, owner, Predictions.HeldType.SNOWBALL, 1, 0F, 1L,
                        (float) frame, 0F, (frame & 1) == 0 ? first : second);
                prepare.invoke(state, 0.5F);
                origin.invoke(state, moving && (frame & 1) != 0 ? b : a, PROJECTION, inverse, Vec3d.ZERO);
                frame++;
                sink++;
            } catch (Throwable failure) { throw new RuntimeException(failure); }
        }
    }

    private static MethodHandle handle(Class<?> type, String name, Class<?>... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, arguments); method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method);
    }

    private static Predictions.TrajectoryResult trajectory(double x) {
        List<Vec3d> path = new ArrayList<>();
        for (int i = 0; i < 100; i++) { double t = i / 99.0; path.add(new Vec3d(x + t * 2, (1 - t) * 1.5, -t * 25)); }
        return new Predictions.TrajectoryResult(path, path.getLast(), HitResult.Type.BLOCK, Direction.UP, null);
    }
}
