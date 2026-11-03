package vorga.phazeclient.implement.menu.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.ARBSamplerObjects;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Saves the state touched by the standalone renderer.
 *
 * <p>The loading renderer is intentionally independent from Minecraft's shader
 * loader, but it still runs in Minecraft's GL context. Its reserved texture
 * unit must therefore be changed through {@link RenderSystem}: GlStateManager
 * caches the active unit and its 2D bindings, and raw calls can leave that
 * cache out of sync even when the final OpenGL values look correct.</p>
 */
final class LoadingGlState implements AutoCloseable {
    /**
     * Keep the bootstrap atlas away from Minecraft's primary sampler. OpenGL
     * 3.2 guarantees at least 16 texture units, so unit 7 is available even
     * with Sodium/Iris and does not interfere with the block atlas on unit 0.
     */
    static final int ATLAS_TEXTURE_UNIT = GL_TEXTURE7;
    static final int ATLAS_TEXTURE_INDEX = ATLAS_TEXTURE_UNIT - GL_TEXTURE0;

    private static final int[] ENABLES = {
            GL_BLEND, GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST,
            GL_COLOR_LOGIC_OP, GL_RASTERIZER_DISCARD, GL_SAMPLE_ALPHA_TO_COVERAGE,
            GL_SAMPLE_COVERAGE, GL_FRAMEBUFFER_SRGB
    };
    private static final int[] UNPACK = {
            GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS
    };

    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int atlasTexture;
    private final boolean samplerSupported = GL.getCapabilities().OpenGL33
            || GL.getCapabilities().GL_ARB_sampler_objects;
    private final int sampler;
    private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB);
    private final int dstRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
    private final int equationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int polygonMode;
    private final int[] viewport = new int[4];
    private final boolean[] colorMask = new boolean[4];
    private final boolean[] enabled = new boolean[ENABLES.length];
    private final int[] unpack = new int[UNPACK.length];

    LoadingGlState() {
        for (int i = 0; i < ENABLES.length; i++) enabled[i] = glIsEnabled(ENABLES[i]);
        for (int i = 0; i < UNPACK.length; i++) unpack[i] = glGetInteger(UNPACK[i]);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, values);
            for (int i = 0; i < 4; i++) viewport[i] = values.get(i);
            glGetIntegerv(GL_POLYGON_MODE, values);
            polygonMode = values.get(0); // Core-profile front/back modes are identical.
            ByteBuffer mask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        RenderSystem.activeTexture(ATLAS_TEXTURE_UNIT);
        atlasTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        sampler = samplerSupported ? glGetInteger(ARBSamplerObjects.GL_SAMPLER_BINDING) : 0;
        RenderSystem.activeTexture(activeTexture);
    }

    void prepare(int width, int height) {
        for (int capability : ENABLES) glDisable(capability);
        glEnable(GL_BLEND);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glColorMask(true, true, true, true);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glViewport(0, 0, width, height);
        RenderSystem.activeTexture(ATLAS_TEXTURE_UNIT);
        if (samplerSupported) ARBSamplerObjects.glBindSampler(ATLAS_TEXTURE_INDEX, 0);
    }

    @Override
    public void close() {
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        for (int i = 0; i < UNPACK.length; i++) glPixelStorei(UNPACK[i], unpack[i]);
        RenderSystem.activeTexture(ATLAS_TEXTURE_UNIT);
        RenderSystem.bindTexture(atlasTexture);
        if (samplerSupported) ARBSamplerObjects.glBindSampler(ATLAS_TEXTURE_INDEX, sampler);
        RenderSystem.activeTexture(activeTexture);
        glBlendEquationSeparate(equationRgb, equationAlpha);
        glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        glDepthMask(depthMask);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        glPolygonMode(GL_FRONT_AND_BACK, polygonMode);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        for (int i = 0; i < ENABLES.length; i++) {
            if (enabled[i]) glEnable(ENABLES[i]);
            else glDisable(ENABLES[i]);
        }
    }
}
