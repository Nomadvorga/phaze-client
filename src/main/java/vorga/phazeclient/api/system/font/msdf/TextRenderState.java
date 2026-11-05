package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Restores both RenderSystem's cached state and the bindings touched by immediate text draws. */
final class TextRenderState implements AutoCloseable {
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int targetRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int targetAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final ShaderProgram shader = RenderSystem.getShader();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final int shaderTexture = RenderSystem.getShaderTexture(0);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int texture;
    private final boolean vanilla;
    private final int shaderLightmapTexture;
    private final int fallbackTexture1;
    private final int fallbackTexture2;
    private final int colorMask;
    private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

    TextRenderState() {
        this(false);
    }

    TextRenderState(boolean vanilla) {
        this.vanilla = vanilla;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        shaderLightmapTexture = vanilla ? RenderSystem.getShaderTexture(2) : 0;
        if (vanilla) {
            // Vanilla binds named Sampler2 to sampler-list index 1, not necessarily GL texture unit 2.
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            fallbackTexture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._activeTexture(GL13.GL_TEXTURE2);
            fallbackTexture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                colorMask = (mask.get(0) != 0 ? 1 : 0) | (mask.get(1) != 0 ? 2 : 0)
                        | (mask.get(2) != 0 ? 4 : 0) | (mask.get(3) != 0 ? 8 : 0);
            }
        } else {
            fallbackTexture1 = 0;
            fallbackTexture2 = 0;
            colorMask = 15;
        }
        GlStateManager._activeTexture(activeTexture);
    }

    @Override
    public void close() {
        // BufferRenderer caches the VAO it last bound; invalidate that cache before restoring another VAO.
        BufferRenderer.resetCurrentVertexBuffer();
        GlStateManager._glBindVertexArray(vertexArray);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        if (vertexArray != 0) GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        RenderSystem.setShader(shader);
        GlStateManager._glUseProgram(program);
        RenderSystem.setShaderTexture(0, shaderTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture);
        if (vanilla) {
            RenderSystem.setShaderTexture(2, shaderLightmapTexture);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(fallbackTexture1);
            GlStateManager._activeTexture(GL13.GL_TEXTURE2);
            GlStateManager._bindTexture(fallbackTexture2);
            RenderSystem.colorMask((colorMask & 1) != 0, (colorMask & 2) != 0,
                    (colorMask & 4) != 0, (colorMask & 8) != 0);
        }
        GlStateManager._activeTexture(activeTexture);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunction);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.blendFuncSeparate(sourceRgb, targetRgb, sourceAlpha, targetAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
    }
}
