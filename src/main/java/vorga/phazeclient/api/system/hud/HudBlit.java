package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;

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
}
