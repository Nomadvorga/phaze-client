package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

/** Real GL pixel reference for cropped, fractional, premultiplied editor-cache composition. */
public final class HudCachedReplayClientTest implements FabricClientGameTest {
    private static int checks;

    @Override
    public void runTest(ClientGameTestContext context) {
        checks = 0;
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            int width = client.getWindow().getFramebufferWidth(), height = client.getWindow().getFramebufferHeight();
            HudBuffer source = new HudBuffer(GL11C.GL_LINEAR), neighbour = new HudBuffer();
            try (Render2DState original = Render2DState.capture()) {
                boolean dither = GL11C.glIsEnabled(GL11C.GL_DITHER);
                GL11C.glDisable(GL11C.GL_DITHER);
                SimpleFramebuffer target = new SimpleFramebuffer(width, height, true);
                int sentinel = TextureUtil.generateTextureId();
                try {
                    Framebuffer pixels;
                    try (HudCapture capture = source.openCapture()) {
                        check(capture != null, "isolated cache capture ready");
                        pixels = client.getFramebuffer();
                        check(pixels == HudBuffer.getActiveCaptureFramebuffer(), "entity/item render layers see the child target");
                        clearRect(height, 32, 48, 16, 12, 1, 0, 0, 1);
                        clearRect(height, 48, 48, 16, 12, 0, 0, 1, 1);
                        clearRect(height, 32, 60, 16, 12, 0, 1, 0, 1);
                        clearRect(height, 48, 60, 16, 12, 0.5F, 0.25F, 0, 0.5F);
                        // This unrelated image must not appear in the cropped replay.
                        clearRect(height, 220, 24, 12, 12, 1, 0, 1, 1);
                        capture.commit();
                    }
                    try (HudCapture capture = neighbour.openCapture()) {
                        check(capture != null, "ordinary neighbour capture ready");
                        check(filter(client.getFramebuffer().getColorAttachment()) == GL11C.GL_NEAREST, "ordinary HUD textures remain nearest");
                        capture.commit();
                    }
                    byte[] reference = read(pixels);
                    HudCachedTransform transform = HudCachedTransform.capture(32, 48, 32, 24, 2, 4, width, height);
                    check(filter(pixels.getColorAttachment()) == GL11C.GL_LINEAR, "only the isolated editor texture is linear");
                    GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
                    GlStateManager._bindTexture(sentinel);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                    for (float[] live : new float[][]{{143, 91, 2}, {153.25F, 93.25F, 3}, {210.125F, 141.375F, 1.5F}}) {
                        RenderSystem.disableScissor();
                        GlStateManager._colorMask(true, true, true, true);
                        RenderSystem.depthMask(true);
                        target.setClearColor(0, 0, 0, 0);
                        target.clear();
                        target.beginWrite(false);
                        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, client.getFramebuffer().fbo);
                        RenderSystem.viewport(3, 4, 25, 26);
                        RenderSystem.setProjectionMatrix(new Matrix4f().ortho2D(0, 17, 23, 0), ProjectionType.ORTHOGRAPHIC);
                        RenderSystem.getModelViewStack().identity().translate(3, 5, 7);
                        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
                        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
                        RenderSafetyClientTest.Snapshot state = RenderSafetyClientTest.Snapshot.read();
                        int shaderTexture = RenderSystem.getShaderTexture(0);
                        check(source.drawCached(transform, live[0], live[1], live[2]), "transformed replay submitted");
                        state.assertCurrent("transformed replay");
                        check(projection.equals(RenderSystem.getProjectionMatrix()) && modelView.equals(RenderSystem.getModelViewStack()),
                                "replay restores projection and model-view matrices");
                        check(shaderTexture == RenderSystem.getShaderTexture(0), "replay restores the caller's sampler selection");
                        check(filter(sentinel) == GL11C.GL_NEAREST, "replay never changes a foreign texture's filtering");
                        compare(read(target), reference, width, height, transform, live);
                    }
                    check(filter(pixels.getColorAttachment()) == GL11C.GL_LINEAR, "replay does not mutate its own allocation policy");
                    check(GL11C.glGetError() == GL11C.GL_NO_ERROR, "cropped replay introduces no GL errors");
                } finally {
                    source.cleanup();
                    neighbour.cleanup();
                    target.delete();
                    TextureUtil.releaseTextureId(sentinel);
                    if (dither) GL11C.glEnable(GL11C.GL_DITHER);
                }
            }
        });
        System.out.println("HUD transformed replay GL checks passed: " + checks);
    }

    private static void clearRect(int framebufferHeight, int x, int y, int width, int height, float r, float g, float b, float a) {
        RenderSystem.enableScissor(x, framebufferHeight - y - height, width, height);
        GlStateManager._clearColor(r, g, b, a);
        GlStateManager._clear(GL11C.GL_COLOR_BUFFER_BIT);
    }

    private static int filter(int texture) {
        try (Render2DState state = Render2DState.capture()) {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            GlStateManager._bindTexture(texture);
            return GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER);
        }
    }

    private static byte[] read(Framebuffer framebuffer) {
        var pixels = MemoryUtil.memAlloc(framebuffer.textureWidth * framebuffer.textureHeight * 4);
        try (Render2DState state = Render2DState.capture()) {
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer.fbo);
            GL11C.glReadPixels(0, 0, framebuffer.textureWidth, framebuffer.textureHeight, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.capacity()];
            pixels.get(result);
            return result;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void compare(byte[] actual, byte[] source, int width, int height, HudCachedTransform captured, float[] live) {
        float ratio = live[2] / captured.scale();
        float left = captured.mapX(captured.left(), live[0], live[2]);
        float right = captured.mapX(captured.right(), live[0], live[2]);
        float top = captured.mapY(captured.top(), live[1], live[2]);
        float bottom = captured.mapY(captured.bottom(), live[1], live[2]);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean inside = x + 0.5F >= left && x + 0.5F < right && y + 0.5F >= top && y + 0.5F < bottom;
            float sx = captured.x() + (x + 0.5F - live[0]) / ratio - 0.5F;
            float sy = captured.y() + (y + 0.5F - live[1]) / ratio - 0.5F;
            int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
            float fx = sx - ix, fy = sy - iy;
            for (int channel = 0; channel < 4; channel++) {
                float expected = 0;
                if (inside) {
                    expected = sample(source, width, height, ix, iy, channel) * (1 - fx) * (1 - fy)
                            + sample(source, width, height, ix + 1, iy, channel) * fx * (1 - fy)
                            + sample(source, width, height, ix, iy + 1, channel) * (1 - fx) * fy
                            + sample(source, width, height, ix + 1, iy + 1, channel) * fx * fy;
                }
                int value = sample(actual, width, height, x, y, channel);
                checks++;
                if (Math.abs(value - expected) > 2) {
                    throw new AssertionError("bilinear/crop/premultiplied mismatch at " + x + "," + y + "/" + channel
                            + ": " + value + " vs " + expected);
                }
            }
        }
    }

    private static int sample(byte[] pixels, int width, int height, int x, int y, int channel) {
        x = Math.clamp(x, 0, width - 1);
        y = Math.clamp(y, 0, height - 1);
        return pixels[((height - 1 - y) * width + x) * 4 + channel] & 255;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
