package vorga.phazeclient.implement.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.opengl.GL11C;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;
import vorga.phazeclient.implement.features.modules.client.Theme;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Interleaved A/B of whole off-screen frames: every primitive drawn eagerly
 * versus the shared GUI batch. CPU is submission time; total includes glFinish.
 */
public final class GuiRenderBenchmarkClientTest implements FabricClientGameTest {
    private static final int WARMUP_FRAMES = 60;
    private static final int ROUNDS = 10;
    private static final int FRAMES_PER_ROUND = 40;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        Screen saved = context.computeOnClient(client -> client.currentScreen);
        try {
            context.runOnClient(client -> {
                MenuScreen.INSTANCE.openGui();
                MenuScreen.INSTANCE.animation.setDirectionAndFinish(Direction.FORWARDS);
            });
            context.waitTicks(10);
            context.runOnClient(client -> bench(client, "mods page"));

            context.runOnClient(client -> MenuScreen.INSTANCE.openModuleDetail(Theme.getInstance()));
            context.waitTicks(10);
            context.runOnClient(client -> bench(client, "settings page"));

            context.runOnClient(client -> {
                MenuScreen.INSTANCE.closeModuleDetail();
                MenuScreen.INSTANCE.openMessengerView();
            });
            context.waitTicks(10);
            context.runOnClient(client -> bench(client, "messenger"));

            context.runOnClient(client -> {
                MenuScreen.INSTANCE.closeMessengerView();
                client.setScreen(new MainMenuScreen());
            });
            context.waitTicks(10);
            context.runOnClient(client -> bench(client, "main menu"));
        } finally {
            context.runOnClient(client -> {
                BatchedRectangle.setEnabled(true);
                MenuScreen.INSTANCE.closeModuleDetail();
                client.setScreen(saved);
            });
        }
        System.out.println("GUI render benchmark finished");
    }

    /** Pixel A/B of the same scene; legacy twice gives the animation noise floor. */
    private static void compare(MinecraftClient client, String scene) {
        NativeImage legacy = capture(client, false);
        NativeImage batched = capture(client, true);
        NativeImage legacyAgain = capture(client, false);
        try {
            int[] noise = diff(legacy, legacyAgain, null);
            // The batched frame was rendered between the two legacy frames: a pixel both legacy
            // frames agree on, yet the batched frame differs from, is a real difference.
            int[] change = diff(legacy, batched, legacyAgain);
            System.out.printf(Locale.ROOT, "GUI pixels [%s]: animated (legacy vs legacy) %d px; stable pixels changed by batching %d px (max %d)%n",
                    scene, noise[0], change[0], change[1]);
            if (change[0] > 0) {
                String name = scene.replace(' ', '-');
                legacy.writeTo(new File(client.runDirectory, "gui-bench-" + name + "-legacy.png"));
                batched.writeTo(new File(client.runDirectory, "gui-bench-" + name + "-batched.png"));
                legacyAgain.writeTo(new File(client.runDirectory, "gui-bench-" + name + "-legacy2.png"));
            }
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        } finally {
            legacy.close();
            batched.close();
            legacyAgain.close();
        }
    }

    private static NativeImage capture(MinecraftClient client, boolean batched) {
        // Menu blur blends across frames; settle it in this mode before reading pixels.
        for (int i = 0; i < 10; i++) frame(client, batched);
        return ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
    }

    /**
     * {pixels whose largest channel difference exceeds 2, largest difference}. With {@code stable},
     * only pixels where {@code a} and {@code stable} agree, and {@code b} differs from both, count.
     */
    private static int[] diff(NativeImage a, NativeImage b, NativeImage stable) {
        int count = 0, max = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getColorArgb(x, y);
                int d = channelDiff(p, b.getColorArgb(x, y));
                if (stable != null) {
                    int s = stable.getColorArgb(x, y);
                    if (channelDiff(p, s) > 2) continue;
                    d = Math.min(d, channelDiff(s, b.getColorArgb(x, y)));
                }
                if (d > 2) count++;
                max = Math.max(max, d);
            }
        }
        return new int[]{count, max};
    }

    private static int channelDiff(int p, int q) {
        int d = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            d = Math.max(d, Math.abs(((p >>> shift) & 0xFF) - ((q >>> shift) & 0xFF)));
        }
        return d;
    }

    private static void bench(MinecraftClient client, String scene) {
        compare(client, scene);
        if (Boolean.getBoolean("phaze.guiBench.profile")) {
            // Batched frames only, long enough for a sampling profiler to see the steady state.
            for (int i = 0; i < 4000; i++) frame(client, true);
            return;
        }
        for (int i = 0; i < WARMUP_FRAMES; i++) frame(client, i % 2 == 0);
        double[][] cpu = new double[2][ROUNDS], total = new double[2][ROUNDS];
        int[][] stats = new int[2][3];
        for (int round = 0; round < ROUNDS; round++) {
            for (int order = 0; order < 2; order++) {
                int variant = (round + order) % 2;
                boolean batched = variant == 1;
                long cpuNanos = 0, totalNanos = 0;
                for (int i = 0; i < FRAMES_PER_ROUND; i++) {
                    GuiDrawStats.reset();
                    long[] times = frame(client, batched);
                    cpuNanos += times[0];
                    totalNanos += times[1];
                }
                stats[variant][0] = GuiDrawStats.rectDraws;
                stats[variant][1] = GuiDrawStats.msdfDraws;
                stats[variant][2] = GuiDrawStats.stateSnapshots;
                cpu[variant][round] = cpuNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                total[variant][round] = totalNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
            }
        }
        BatchedRectangle.setEnabled(true);
        double legacyTotal = median(total[0]), batchedTotal = median(total[1]);
        System.out.printf(Locale.ROOT,
                "GUI bench [%s] %dx%d %s%n"
                        + "  legacy : cpu %.3f ms, frame %.3f ms (%.0f fps), rect draws %d, msdf draws %d, GL state snapshots %d%n"
                        + "  batched: cpu %.3f ms, frame %.3f ms (%.0f fps), rect draws %d, msdf draws %d, GL state snapshots %d%n"
                        + "  speedup: %.2fx frame time%n",
                scene, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(),
                GL11C.glGetString(GL11C.GL_RENDERER),
                median(cpu[0]), legacyTotal, 1000.0 / legacyTotal, stats[0][0], stats[0][1], stats[0][2],
                median(cpu[1]), batchedTotal, 1000.0 / batchedTotal, stats[1][0], stats[1][1], stats[1][2],
                legacyTotal / batchedTotal);
    }

    /** Returns {submission nanos, nanos including glFinish}. */
    private static long[] frame(MinecraftClient client, boolean batched) {
        BatchedRectangle.setEnabled(batched);
        GL11C.glFinish();
        long start = System.nanoTime();
        client.gameRenderer.render(RenderTickCounter.ZERO, true);
        long submitted = System.nanoTime();
        GL11C.glFinish();
        return new long[]{submitted - start, System.nanoTime() - start};
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
    }
}
