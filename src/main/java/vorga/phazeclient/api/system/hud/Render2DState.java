package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import com.mojang.blaze3d.systems.ProjectionType;
import net.minecraft.client.render.BufferRenderer;
import org.lwjgl.opengl.GL15C;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

/** Render-thread-only state boundary. Join an existing pass instead of querying GL per primitive. */
public final class Render2DState implements AutoCloseable {
    private static int depth;
    private static final Render2DState JOINED = new Render2DState(false);
    private boolean open;
    private boolean blend, depthTest, cull, depthMask, scissor;
    private int srcRgb, dstRgb, srcAlpha, dstAlpha, depthFunc, program, activeTexture;
    private final int[] viewport = new int[4], scissorBox = new int[4], bindings = new int[3];
    private final boolean[] colorMask = new boolean[4];
    private int drawFramebuffer, readFramebuffer;
    private int vertexArray, arrayBuffer;
    private ShaderProgram shader;
    private int texture0, texture1, texture2;
    private double clearDepth;
    private float[] color, clearColor;
    private Matrix4f projection, modelView;
    private ProjectionType projectionType;

    private Render2DState(boolean snapshot) {
        if (!snapshot) return;
        RenderSystem.assertOnRenderThread();
        blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        srcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
        dstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
        srcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
        dstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
        drawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        vertexArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBox);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        clearColor = new float[4];
        GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, clearColor);
        clearDepth = GL11C.glGetDouble(GL11C.GL_DEPTH_CLEAR_VALUE);
        shader = RenderSystem.getShader();
        program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        texture0 = RenderSystem.getShaderTexture(0);
        texture1 = RenderSystem.getShaderTexture(1);
        texture2 = RenderSystem.getShaderTexture(2);
        activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        // BufferRenderer binds sampler textures independently of RenderSystem's selected textures.
        for (int i = 0; i < bindings.length; i++) {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + i);
            bindings[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        }
        GlStateManager._activeTexture(activeTexture);
        color = RenderSystem.getShaderColor().clone();
        projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        projectionType = RenderSystem.getProjectionType();
        modelView = new Matrix4f(RenderSystem.getModelViewStack());
        open = true;
        depth++;
    }

    /** Always snapshot: framebuffer capture and offscreen refresh must restore their immediate caller. */
    public static Render2DState capture() { return new Render2DState(true); }

    /** Snapshot only for a standalone draw; HUD/menu primitives share their outer boundary. */
    public static Render2DState join() { return depth > 0 ? JOINED : capture(); }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        depth--;
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (!scissor) RenderSystem.disableScissor();
        GlStateManager._colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        GlStateManager._clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GlStateManager._clearDepth(clearDepth);
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.setProjectionMatrix(projection, projectionType);
        RenderSystem.getModelViewStack().set(modelView);
        RenderSystem.setShader(shader);
        GlStateManager._glUseProgram(program);
        BufferRenderer.resetCurrentVertexBuffer();
        GlStateManager._glBindVertexArray(vertexArray);
        GlStateManager._glBindBuffer(GL15C.GL_ARRAY_BUFFER, arrayBuffer);
        RenderSystem.setShaderTexture(0, texture0);
        RenderSystem.setShaderTexture(1, texture1);
        RenderSystem.setShaderTexture(2, texture2);
        for (int i = 0; i < bindings.length; i++) {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(bindings[i]);
        }
        GlStateManager._activeTexture(activeTexture);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
    }
}
