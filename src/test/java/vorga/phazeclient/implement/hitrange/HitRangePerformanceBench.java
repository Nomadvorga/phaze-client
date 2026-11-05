package vorga.phazeclient.implement.hitrange;

import com.sun.management.ThreadMXBean;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.implement.features.modules.other.HitRange;

import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Optional CPU comparison. Includes vertex upload preparation and identical quad sorting, not GPU/FPS. */
public final class HitRangePerformanceBench {
    private static volatile long sink;

    public static void main(String[] args) throws Throwable {
        System.out.println("CPU-only geometry benchmark. Excludes player model updates, GL and GPU: NOT in-game FPS.");
        HitRange config = HitRange.getInstance();
        config.circleSegments.setValue(60); config.radius.setValue(3); config.thickness.setValue(0.15F);
        for (String mode : new String[]{"Line", "Thick", "Filled"}) {
            config.renderMode.setSelected(mode);
            MethodHandle emitter = HitRangeGeometryChecks.emitter(mode);
            HitRangeGeometryChecks.LegacyGeometry legacy = new HitRangeGeometryChecks.LegacyGeometry(config);
            for (int players : new int[]{1, 16}) {
                MatrixStack[] matrices = new MatrixStack[players];
                for (int p = 0; p < players; p++) { matrices[p] = new MatrixStack(); matrices[p].translate(p * 5.25, -1.5, -8.25); }
                try (BufferAllocator allocator = new BufferAllocator(262144)) {
                    Work old = () -> frame(mode, matrices, allocator, legacy, null);
                    Work current = () -> frame(mode, matrices, allocator, null, emitter);
                    for (int warm = 0; warm < 3000; warm++) { old.run(); current.run(); }
                    double[] before = new double[7], after = new double[7], beforeBytes = new double[7], afterBytes = new double[7];
                    for (int round = 0; round < 7; round++) {
                        if ((round & 1) == 0) { before[round] = measure(old, beforeBytes, round); after[round] = measure(current, afterBytes, round); }
                        else { after[round] = measure(current, afterBytes, round); before[round] = measure(old, beforeBytes, round); }
                    }
                    Arrays.sort(before); Arrays.sort(after); Arrays.sort(beforeBytes); Arrays.sort(afterBytes);
                    System.out.printf(Locale.ROOT, "%s, %d circles x 60 segments: %.3f -> %.3f us/frame, heap %.0f -> %.0f B/frame%n",
                            mode, players, before[3] / 1000, after[3] / 1000, beforeBytes[3], afterBytes[3]);
                }
            }
        }
    }

    private static void frame(String mode, MatrixStack[] matrices, BufferAllocator allocator,
                              HitRangeGeometryChecks.LegacyGeometry legacy, MethodHandle current) throws Throwable {
        BufferBuilder builder = new BufferBuilder(allocator, HitRangeGeometryChecks.drawMode(mode), VertexFormats.POSITION_COLOR);
        for (MatrixStack matrix : matrices) {
            if (legacy != null) legacy.emit(mode, matrix, builder, 0.125F, 0x804080C0);
            else current.invokeExact(matrix, builder, 0.125F, 0x804080C0);
        }
        try (BuiltBuffer built = builder.end()) {
            if (mode.equals("Thick")) built.sortQuads(allocator, com.mojang.blaze3d.systems.VertexSorter.BY_DISTANCE);
            sink += Float.floatToIntBits(built.getBuffer().getFloat(0));
        }
        allocator.clear();
    }

    private static double measure(Work work, double[] allocations, int round) throws Throwable {
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId(), bytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
        for (int frame = 0; frame < 2000; frame++) work.run();
        long elapsed = System.nanoTime() - start;
        allocations[round] = (double) (bean.getThreadAllocatedBytes(thread) - bytes) / 2000;
        return (double) elapsed / 2000;
    }
    private interface Work { void run() throws Throwable; }
}
