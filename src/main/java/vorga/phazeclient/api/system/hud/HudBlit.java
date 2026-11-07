package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

/** Shared premultiplied-alpha cache composition. Restores the caller, not an assumed main target. */
final class HudBlit {
    private HudBlit() {}

    static boolean draw(Framebuffer source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (source == null || mc == null || mc.getWindow() == null) return false;
        int width = mc.getWindow().getFramebufferWidth(), height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return false;
        try (Render2DState state = Render2DState.capture()) {
            // Keep the framebuffer that the caller bound (including Exordium targets).
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.viewport(0, 0, width, height);
            ShaderProgram shader = RenderSystem.setShader(ShaderProgramKeys.BLIT_SCREEN);
            if (shader == null) return false;
            shader.addSamplerTexture("InSampler", source.getColorAttachment());
            BufferBuilder builder = Render2DBuffers.begin(VertexFormats.BLIT_SCREEN);
            try {
                builder.vertex(0, 0, 0);
                builder.vertex(1, 0, 0);
                builder.vertex(1, 1, 0);
                builder.vertex(0, 1, 0);
                try (BuiltBuffer built = builder.end()) {
                    BufferRenderer.drawWithGlobalProgram(built);
                }
            } catch (RuntimeException | Error failure) {
                Render2DBuffers.discardImmediate();
                throw failure;
            }
            return true;
        }
    }

    /** A single cropped quad: mouse motion changes only vertices, never the cached pixels or blur source. */
    static boolean drawTransformed(Framebuffer source, HudCachedTransform captured, float x, float y, float scale) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (source == null || captured == null || mc == null || mc.getWindow() == null
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scale) || scale <= 0) return false;
        int width = mc.getWindow().getFramebufferWidth(), height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return false;
        try (Render2DState state = Render2DState.capture()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.viewport(0, 0, width, height);
            RenderSystem.setProjectionMatrix(new Matrix4f().ortho2D(0, width, height, 0), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.getModelViewStack().identity();
            if (RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR) == null) return false;
            RenderSystem.setShaderTexture(0, source.getColorAttachment());
            float left = captured.mapX(captured.left(), x, scale), right = captured.mapX(captured.right(), x, scale);
            float top = captured.mapY(captured.top(), y, scale), bottom = captured.mapY(captured.bottom(), y, scale);
            float u0 = captured.left() / (float) source.textureWidth, u1 = captured.right() / (float) source.textureWidth;
            float v0 = 1 - captured.top() / (float) source.textureHeight, v1 = 1 - captured.bottom() / (float) source.textureHeight;
            BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION_TEXTURE_COLOR);
            try {
                builder.vertex(left, top, 0).texture(u0, v0).color(-1);
                builder.vertex(left, bottom, 0).texture(u0, v1).color(-1);
                builder.vertex(right, bottom, 0).texture(u1, v1).color(-1);
                builder.vertex(right, top, 0).texture(u1, v0).color(-1);
                try (BuiltBuffer built = builder.end()) { BufferRenderer.drawWithGlobalProgram(built); }
            } catch (RuntimeException | Error failure) {
                Render2DBuffers.discardImmediate();
                throw failure;
            }
            return true;
        }
    }
}
