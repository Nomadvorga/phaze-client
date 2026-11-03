package vorga.phazeclient.implement.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import vorga.phazeclient.implement.menu.loading.BootstrapLoadingRenderer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL32C.*;

/** Exercises the real mixin with an incomplete reload, not a completed preview screen. */
public final class LoadingOverlayClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        Screen saved = context.computeOnClient(client -> client.currentScreen);
        TestReload reload = new TestReload();
        AtomicInteger callbacks = new AtomicInteger();
        SplashOverlay overlay = context.computeOnClient(client -> {
            verifyGlState();
            // Reinitialize programs and atlases with progress ZERO and a reload
            // that cannot complete. No font/shader readiness threshold can pass.
            BootstrapLoadingRenderer.close();
            SplashOverlay result = new SplashOverlay(client, reload, failure -> {
                check(failure.isEmpty(), "successful reload callback");
                callbacks.incrementAndGet();
            }, false);
            client.setScreen(null);
            client.setOverlay(result);
            return result;
        });
        try {
            screenshot(context, "loading-first-frame-zero", 0);
            check(callbacks.get() == 0, "zero progress does not complete reload");
            context.runOnClient(client -> {
                reload.progress = 0.5F;
                field(overlay, "progress", 0.5F);
            });
            screenshot(context, "loading-half-rounded", 0.5F);
            check(callbacks.get() == 0, "logo and rounded bar are present BEFORE completion");
            context.runOnClient(client -> {
                reload.progress = 1;
                field(overlay, "progress", 1.0F);
            });
            screenshot(context, "loading-full-rounded", 1);
            context.runOnClient(client -> {
                reload.complete = true;
                client.getOverlay().render(new net.minecraft.client.gui.DrawContext(client,
                        client.getBufferBuilders().getEntityVertexConsumers()), 0, 0, 0);
                field(overlay, "reloadCompleteTime", Util.getMeasuringTimeMs() - 1000L);
            });
            context.waitFor(client -> client.getOverlay() == null);
            check(callbacks.get() == 1, "completion callback fires exactly once and overlay closes");
            verifyFailureAndReplacement(context);
            System.out.println("Loading overlay first-frame MSDF/rounded pixels/lifecycle checks PASSED");
        } finally {
            context.runOnClient(client -> {
                client.setOverlay(null);
                client.setScreen(saved);
            });
        }
    }

    private static void screenshot(ClientGameTestContext context, String name, float progress) {
        Path screenshot = context.takeScreenshot(name);
        try (var input = Files.newInputStream(screenshot); NativeImage pixels = NativeImage.read(input)) {
            int w = pixels.getWidth(), h = pixels.getHeight();
            for (int[] corner : new int[][]{{2, 2}, {w - 3, 2}, {2, h - 3}, {w - 3, h - 3}}) {
                near(pixels.getColorArgb(corner[0], corner[1]), 0x4F9D76, 2, "opaque green background");
            }
            int whiteLogo = 0, minX = w, maxX = 0;
            int upper = 0, lower = 0;
            for (int y = (int) (h * 0.2F); y < h * 0.7F; y++) {
                for (int x = 0; x < w; x++) {
                    if (white(pixels.getColorArgb(x, y))) {
                        whiteLogo++;
                        if (y < h * 0.48F) upper++; else lower++;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                }
            }
            check(whiteLogo > 200 && upper > 100 && lower > 30, "PHAZE and CLIENT visible at progress " + progress);
            check(Math.abs((minX + maxX) / 2.0F - w / 2.0F) < 5, "MSDF logo horizontally centered");
            float barWidth = Math.min(w, Math.max(32, Math.min(w - 48, Math.max(220, w * 0.52F))));
            float barHeight = Math.min(h, Math.max(6, Math.min(12, h * 0.009F)) + 4.0F);
            float left = (w - barWidth) / 2;
            float top = Math.min(h - barHeight, h * 0.82F);
            int cy = (int) (top + barHeight / 2);
            if (progress == 0) {
                check(!white(pixels.getColorArgb(w / 2, cy)), "empty progress has no fabricated fill");
            } else {
                int filledMiddle = (int) (left + barWidth * progress / 2);
                check(white(pixels.getColorArgb(filledMiddle, cy)), "white shader fill");
                int corner = pixels.getColorArgb((int) Math.ceil(left), (int) Math.ceil(top));
                int center = pixels.getColorArgb(filledMiddle, cy);
                check(((corner >>> 16) & 255) + 30 < ((center >>> 16) & 255), "rounded shader clips corner, not a rectangle");
                if (progress < 1) {
                    check(!white(pixels.getColorArgb((int) (left + barWidth * 0.75F), cy)), "half progress leaves unfilled track");
                }
            }
            System.out.println("Loading overlay screenshot: " + screenshot);
        } catch (IOException e) {
            throw new RuntimeException("Cannot inspect loading screenshot", e);
        }
    }

    private static void verifyGlState() {
        int[] enums = {GL_CURRENT_PROGRAM, GL_VERTEX_ARRAY_BINDING, GL_ARRAY_BUFFER_BINDING,
                GL_ACTIVE_TEXTURE, GL_TEXTURE_BINDING_2D, GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB,
                GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_UNPACK_ALIGNMENT, GL_PIXEL_UNPACK_BUFFER_BINDING};
        int[] values = new int[enums.length];
        for (int i = 0; i < enums.length; i++) values[i] = glGetInteger(enums[i]);
        int cachedTexture0 = RenderSystem.getShaderTexture(0);
        int cachedAtlasTexture = RenderSystem.getShaderTexture(BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX);
        boolean depth = glIsEnabled(GL_DEPTH_TEST), blend = glIsEnabled(GL_BLEND);
        BootstrapLoadingRenderer.render(854, 480, 0, 1);
        for (int i = 0; i < enums.length; i++) {
            check(values[i] == glGetInteger(enums[i]), "loading renderer restores GL state " + enums[i]);
        }
        check(RenderSystem.getShaderTexture(0) == cachedTexture0,
                "loading renderer restores Minecraft's cached texture binding");
        check(RenderSystem.getShaderTexture(BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX) == cachedAtlasTexture,
                "loading renderer restores its reserved atlas texture binding");
        check(depth == glIsEnabled(GL_DEPTH_TEST) && blend == glIsEnabled(GL_BLEND), "restores GL enables");
        check(glGetError() == GL_NO_ERROR, "no GL errors on first bootstrap render");
    }

    private static void verifyFailureAndReplacement(ClientGameTestContext context) {
        context.runOnClient(client -> {
            TestReload failed = new TestReload();
            failed.failure = new IllegalStateException("Expected loading regression failure");
            failed.complete = true;
            SplashOverlay replacement = new SplashOverlay(client, new TestReload(), ignored -> {}, true);
            AtomicInteger errors = new AtomicInteger();
            SplashOverlay original = new SplashOverlay(client, failed, failure -> {
                check(failure.orElseThrow() == failed.failure, "reload exception delivered to recovery callback");
                errors.incrementAndGet();
                client.setOverlay(replacement);
            }, false);
            client.setOverlay(original);
            original.render(new net.minecraft.client.gui.DrawContext(client,
                    client.getBufferBuilders().getEntityVertexConsumers()), 0, 0, 0);
            check(errors.get() == 1, "failure callback fires once");
            check(client.getOverlay() == replacement, "recovery overlay is not cleared by old reload");
            client.setOverlay(null);
        });
    }

    private static boolean white(int color) {
        return ((color >>> 16) & 255) > 240 && ((color >>> 8) & 255) > 240 && (color & 255) > 240;
    }

    private static void near(int color, int expected, int tolerance, String message) {
        for (int shift : new int[]{0, 8, 16}) {
            check(Math.abs(((color >>> shift) & 255) - ((expected >>> shift) & 255)) <= tolerance, message);
        }
    }

    private static void field(SplashOverlay overlay, String name, Object value) {
        try {
            Field field = SplashOverlay.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(overlay, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect overlay lifecycle: " + name, e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestReload implements ResourceReload {
        private volatile float progress;
        private volatile boolean complete;
        private RuntimeException failure;
        @Override public CompletableFuture<?> whenComplete() { return new CompletableFuture<>(); }
        @Override public float getProgress() { return progress; }
        @Override public boolean isComplete() { return complete; }
        @Override public void throwException() { if (failure != null) throw failure; }
    }
}
