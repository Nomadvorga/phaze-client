package vorga.phazeclient.implement.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.world.Difficulty;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;
import vorga.phazeclient.implement.features.modules.client.Theme;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Whole in-world frames at 1080p: world alone versus world plus the menu, interleaved per round
 * so background load affects every scene alike. CPU is submission time, GPU a GL_TIME_ELAPSED
 * query around the same frame.
 */
public final class GuiWorldBenchmarkClientTest implements FabricClientGameTest {
    private static final int ROUNDS = 12;
    private static final int WARMUP_FRAMES = 20;
    private static final int FRAMES_PER_ROUND = 50;

    private record Scene(String name, Consumer<MinecraftClient> apply) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            client.options.pauseOnLostFocus = false;
            client.options.getViewDistance().setValue(2);
            client.options.getGuiScale().setValue(2);
            client.getToastManager().clear();
        });
        context.getInput().resizeWindow(1920, 1080);
        TestSingleplayerContext world = context.worldBuilder().adjustSettings(creator -> {
            creator.setWorldName("Phaze GUI world benchmark");
            creator.setGameMode(WorldCreator.Mode.CREATIVE);
            creator.setDifficulty(Difficulty.PEACEFUL);
        }).create();
        float savedBlur = context.computeOnClient(client -> Theme.getInstance().blurRadius.getValue());
        Scene[] scenes = {
                new Scene("world only", client -> {
                    BatchedRectangle.setEnabled(true);
                    client.setScreen(null);
                }),
                new Scene("world + mods page", client -> openMenu(client, savedBlur, false)),
                new Scene("world + mods page, unbatched", client -> {
                    openMenu(client, savedBlur, false);
                    BatchedRectangle.setEnabled(false);
                }),
                new Scene("world + mods page, blur 0", client -> openMenu(client, 0.0F, false)),
                new Scene("world + settings page", client -> openMenu(client, savedBlur, true)),
                new Scene("world + cosmetics page", client -> {
                    openMenu(client, savedBlur, false);
                    MenuScreen.INSTANCE.openCosmeticsView();
                }),
        };
        try {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> client.options.getViewDistance().setValue(8));
            context.waitTicks(300);
            if (Boolean.getBoolean("phaze.guiBench.profile")) {
                context.runOnClient(client -> {
                    openMenu(client, savedBlur, false);
                    if (Boolean.getBoolean("phaze.guiBench.cosmetics")) MenuScreen.INSTANCE.openCosmeticsView();
                    int query = GL15C.glGenQueries();
                    for (int i = 0; i < 15000; i++) frame(client, query);
                    GL15C.glDeleteQueries(query);
                });
                return;
            }
            context.runOnClient(client -> bench(client, scenes));
            context.runOnClient(client -> scenes[scenes.length - 1].apply().accept(client));
            context.waitTicks(10);
            System.out.println("GUI world bench screenshot: " + context.takeScreenshot("cosmetics-cached"));
            context.runOnClient(client -> System.out.println("GUI world bench preview state: live="
                    + previewField("livePlayerPreview") + " validated=" + previewField("playerPreviewValidated")
                    + " liveCards=" + previewField("livePreviewFallback")));
            context.runOnClient(client -> setLivePreview(true));
            context.waitTicks(2);
            System.out.println("GUI world bench screenshot: " + context.takeScreenshot("cosmetics-live"));
            context.runOnClient(client -> setLivePreview(false));
        } finally {
            context.runOnClient(client -> {
                BatchedRectangle.setEnabled(true);
                Theme.getInstance().blurRadius.setValue(savedBlur);
                MenuScreen.INSTANCE.closeModuleDetail();
                client.setScreen(null);
            });
            world.close();
        }
        System.out.println("GUI world benchmark finished");
    }

    private static Object previewField(String name) {
        try {
            var viewField = MenuScreen.class.getDeclaredField("cosmeticsView");
            viewField.setAccessible(true);
            Object view = viewField.get(MenuScreen.INSTANCE);
            var field = view.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(view);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setLivePreview(boolean live) {
        setPreviewField("livePlayerPreview", live);
    }

    private static void setPreviewField(String name, boolean value) {
        try {
            var viewField = MenuScreen.class.getDeclaredField("cosmeticsView");
            viewField.setAccessible(true);
            Object view = viewField.get(MenuScreen.INSTANCE);
            var field = view.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(view, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void openMenu(MinecraftClient client, float blur, boolean settings) {
        BatchedRectangle.setEnabled(true);
        Theme.getInstance().blurRadius.setValue(blur);
        if (client.currentScreen != MenuScreen.INSTANCE) MenuScreen.INSTANCE.openGui();
        MenuScreen.INSTANCE.animation.setDirectionAndFinish(Direction.FORWARDS);
        MenuScreen.INSTANCE.closeCosmeticsView();
        if (settings) MenuScreen.INSTANCE.openModuleDetail(Theme.getInstance());
        else MenuScreen.INSTANCE.closeModuleDetail();
    }

    private static void bench(MinecraftClient client, Scene[] scenes) {
        int query = GL15C.glGenQueries();
        try {
            double[][] cpu = new double[scenes.length][ROUNDS], gpu = new double[scenes.length][ROUNDS];
            double[][] total = new double[scenes.length][ROUNDS], pipelined = new double[scenes.length][ROUNDS];
            int[][] draws = new int[scenes.length][];
            for (int round = 0; round < ROUNDS; round++) {
                for (int order = 0; order < scenes.length; order++) {
                    int s = (round + order) % scenes.length;
                    scenes[s].apply().accept(client);
                    for (int i = 0; i < WARMUP_FRAMES; i++) frame(client, query);
                    long cpuNanos = 0, gpuNanos = 0, totalNanos = 0;
                    for (int i = 0; i < FRAMES_PER_ROUND; i++) {
                        GuiDrawStats.reset();
                        long[] times = frame(client, query);
                        cpuNanos += times[0];
                        gpuNanos += times[1];
                        totalNanos += times[2];
                    }
                    draws[s] = new int[]{GuiDrawStats.rectDraws, GuiDrawStats.bufferDraws, GuiDrawStats.scissorChanges,
                            GuiDrawStats.batchInstances};
                    cpu[s][round] = cpuNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                    gpu[s][round] = gpuNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                    total[s][round] = totalNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                    // CPU and GPU overlap across frames here, as in the real render loop.
                    GL11C.glFinish();
                    long start = System.nanoTime();
                    for (int i = 0; i < FRAMES_PER_ROUND; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
                    GL11C.glFinish();
                    pipelined[s][round] = (System.nanoTime() - start) / (double) FRAMES_PER_ROUND / 1_000_000.0;
                }
            }
            double base = median(total[0]), pipelinedBase = median(pipelined[0]);
            for (int s = 0; s < scenes.length; s++) {
                double frame = median(total[s]), throughput = median(pipelined[s]);
                System.out.printf(Locale.ROOT,
                        "GUI world bench [%s] %dx%d: cpu %.3f ms, gpu %.3f ms, frame %.3f ms (%.0f fps), GUI cost %.3f ms;"
                                + " pipelined %.3f ms (%.0f fps), GUI cost %.3f ms; batch draws %d (%d instances), buffer draws %d, scissor changes %d%n",
                        scenes[s].name(), client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(),
                        median(cpu[s]), median(gpu[s]), frame, 1000.0 / frame, frame - base,
                        throughput, 1000.0 / throughput, throughput - pipelinedBase,
                        draws[s][0], draws[s][3], draws[s][1], draws[s][2]);
            }
        } finally {
            GL15C.glDeleteQueries(query);
        }
    }

    /** Returns {submission nanos, GPU elapsed nanos, nanos including glFinish}. */
    private static long[] frame(MinecraftClient client, int query) {
        GL11C.glFinish();
        long start = System.nanoTime();
        GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        client.gameRenderer.render(RenderTickCounter.ZERO, true);
        GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        long submitted = System.nanoTime();
        GL11C.glFinish();
        long end = System.nanoTime();
        long gpu = GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
        return new long[]{submitted - start, gpu, end - start};
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
    }
}
