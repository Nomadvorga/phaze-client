package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;

/** GL-context regression fixture. Root suite registers this client-gametest entrypoint. */
public final class RenderSafetyClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            HudRenderLifecycle.cleanup();
            try (Render2DState original = Render2DState.capture()) {
                SimpleFramebuffer draw = new SimpleFramebuffer(48, 48, true);
                SimpleFramebuffer read = new SimpleFramebuffer(32, 32, true);
                HudBuffer nested = new HudBuffer();
                try {
                    GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw.fbo);
                    GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read.fbo);
                    RenderSystem.viewport(3, 4, 25, 26);
                    RenderSystem.enableScissor(5, 6, 9, 10);
                    GlStateManager._colorMask(false, true, false, true);
                    RenderSystem.depthMask(false);
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableCull();
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ZERO, GL11C.GL_ZERO, GL11C.GL_ONE);
                    RenderSystem.setShaderColor(0.25F, 0.5F, 0.75F, 0.375F);
                    BatchedRectangle.restoreRenderTargetFbHeight(48);
                    Snapshot expected = Snapshot.read();

                    RuntimeException injected = new RuntimeException("injected capture render failure");
                    try (HudCapture capture = BatchedHudBuffer.INSTANCE.openCapture()) {
                        check(capture != null, "capture ready");
                        check(BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer() != null, "batch redirect active");
                        check(!GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST), "full clear ignores old scissor");
                        throw injected;
                    } catch (RuntimeException failure) {
                        check(failure == injected, "original failure preserved");
                    }
                    expected.assertCurrent("abort");
                    check(!BatchedHudBuffer.INSTANCE.hasContent(), "partial capture is never cached");
                    check(HudBuffer.activeCaptureTarget == -1, "abort restores capture sentinel");
                    check(BatchedRectangle.getRenderTargetFbHeight() == 48, "abort restores parent height");

                    try (HudCapture batch = BatchedHudBuffer.INSTANCE.openCapture()) {
                        check(batch != null, "second capture ready after failure");
                        int parent = HudBuffer.activeCaptureTarget;
                        float parentHeight = BatchedRectangle.getRenderTargetFbHeight();
                        Snapshot parentState = Snapshot.read();
                        try (HudCapture child = nested.openCapture()) {
                            check(child != null, "nested capture ready");
                            check(HudBuffer.activeCaptureTarget != parent, "child owns its target");
                            check(BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer() == null, "unrelated per-HUD target cannot redirect to global batch FBO");
                            child.commit();
                        }
                        parentState.assertCurrent("nested close");
                        check(HudBuffer.activeCaptureTarget == parent, "nested close restores parent redirect");
                        check(BatchedRectangle.getRenderTargetFbHeight() == parentHeight, "nested close restores parent height");
                        batch.commit();
                    }
                    expected.assertCurrent("commit");
                    check(BatchedHudBuffer.INSTANCE.hasContent(), "completed capture published");
                    BatchedHudBuffer.INSTANCE.blit();
                    expected.assertCurrent("blit");

                    // An injected error after beginScope must not poison future nesting or height state.
                    BatchedRectangle.beginScope();
                    try {
                        throw injected;
                    } catch (RuntimeException failure) {
                        check(failure == injected, "batch failure propagated");
                    } finally {
                        BatchedRectangle.endScope();
                    }
                    check(!BatchedRectangle.isBatching(), "batch scope unwound");
                    expected.assertCurrent("batch scope");
                    BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
                    builder.vertex(0, 0, 0); builder.vertex(0, 1, 0); builder.vertex(1, 1, 0); builder.vertex(1, 0, 0);
                    try (BuiltBuffer built = builder.end()) { check(built.getDrawParameters().vertexCount() == 4, "tessellator usable after capture failure"); }

                    HudRenderLifecycle.cleanup();
                    HudRenderLifecycle.cleanup();
                    check(HudBuffer.activeCaptureTarget == -1, "repeated lifecycle clears redirect");
                    check(BatchedRectangle.getRenderTargetFbHeight() == -1, "lifecycle clears height override");
                    check(!BatchedHudBuffer.INSTANCE.hasContent() && !nested.hasContent(), "lifecycle invalidates all HUD caches");
                } finally {
                    nested.cleanup();
                    BatchedHudBuffer.INSTANCE.cleanup();
                    BatchedRectangle.restoreRenderTargetFbHeight(-1);
                    draw.delete();
                    read.delete();
                }
            }
        });
        System.out.println("HUD/2D GL render safety checks passed");
    }

    private record Snapshot(int draw, int readTarget, int[] viewport, int[] scissorBox, boolean scissor,
                            boolean depth, boolean depthMask, boolean cull, boolean blend,
                            int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, int program,
                            byte[] colorMask, float[] color) {
        static Snapshot read() {
            int[] viewport = new int[4], box = new int[4];
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, box);
            byte[] mask = new byte[4];
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var buffer = stack.malloc(4);
                GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, buffer);
                buffer.get(mask);
            }
            return new Snapshot(GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING), viewport, box,
                    GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST), GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                    GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK), GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    GL11C.glIsEnabled(GL11C.GL_BLEND), GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB),
                    GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB), GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA),
                    GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA), GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    mask, RenderSystem.getShaderColor().clone());
        }

        void assertCurrent(String stage) {
            Snapshot actual = read();
            check(draw == actual.draw && readTarget == actual.readTarget, stage + " restores independent read/draw targets");
            check(Arrays.equals(viewport, actual.viewport), stage + " restores viewport");
            check(scissor == actual.scissor && Arrays.equals(scissorBox, actual.scissorBox), stage + " restores scissor enable/box");
            check(depth == actual.depth && depthMask == actual.depthMask && cull == actual.cull && blend == actual.blend,
                    stage + " restores enable/mask state");
            check(srcRgb == actual.srcRgb && dstRgb == actual.dstRgb && srcAlpha == actual.srcAlpha && dstAlpha == actual.dstAlpha,
                    stage + " restores separate blend factors");
            check(program == actual.program && Arrays.equals(colorMask, actual.colorMask) && Arrays.equals(color, actual.color),
                    stage + " restores shader/color/masks");
        }
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
