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
import vorga.phazeclient.api.system.hud.Render2DState;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;

/** Restores both RenderSystem's cached state and the bindings touched by immediate text draws. */
final class TextRenderState implements AutoCloseable {
    private final boolean joined;
    private boolean blend, cull, depth, depthMask;
    private int depthFunction, sourceRgb, targetRgb, sourceAlpha, targetAlpha;
    private final ShaderProgram shader = RenderSystem.getShader();
    private int program;
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final int shaderTexture = RenderSystem.getShaderTexture(0);
    private int activeTexture, texture;
    private final boolean vanilla;
    private int shaderLightmapTexture, fallbackTexture1, fallbackTexture2;
    private int colorMask = 15;
    private int vertexArray, arrayBuffer, elementBuffer;

    TextRenderState() {
        this(false);
    }

    TextRenderState(boolean vanilla) {
        this.vanilla = vanilla;
        // An outer menu/HUD boundary restores the real GL state when it closes. Querying
        // GL here stalls the driver once per string, so only RenderSystem's cache is kept.
        joined = !vanilla && MsdfBatch.isEnabled() && Render2DState.isJoined();
        if (joined) return;
        GuiDrawStats.stateSnapshots++;
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        targetRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        targetAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
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
        }
        GlStateManager._activeTexture(activeTexture);
    }

    @Override
    public void close() {
        if (joined) {
            restoreJoined(shader, shaderTexture, color);
            return;
        }
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

    /** Same exit state as a joined rectangle flush: cached selections restored, cull on, blend off. */
    static void restoreJoined(ShaderProgram shader, int shaderTexture, float[] color) {
        RenderSystem.setShader(shader);
        RenderSystem.setShaderTexture(0, shaderTexture);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
