package vorga.phazeclient.api.system.shape.batched;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * One deferred stream of rounded rects, MSDF glyphs/icons and plain textured quads, drawn with a
 * single instanced {@code phaze:core/gui_batched} call in exact submission order.
 *
 * <p>Every primitive is one 116-byte instance; the vertex shader expands it into its quad. All a
 * primitive needs travels with it: geometry, SDF or MSDF parameters, a texture slot and the scissor
 * box active at submission ({@link GuiClipState}). Overlap, scissor changes and per-string shader
 * parameters therefore never split the batch. It is drained only before foreign draws and
 * framebuffer work, and when a ninth texture is needed.</p>
 */
public final class GuiBatch {
    public static final int KIND_RECT = 0;
    public static final int KIND_MSDF = 1;
    public static final int KIND_TEXTURE = 2;
    /** Quad UVs rotated a quarter turn, as {@code UiMsdfIconAtlas} legacy-oriented icons are. */
    public static final int UV_SWAP = 1 << 7;
    private static final int MAX_TEXTURES = 8;

    /*
     * Instance layout (offset: attribute):
     *   0  Geom0         origin.xy, edge-s.xy        (GUI space, after the CPU matrix)
     *  16  Geom1         edge-t.xy, z, meta          (meta = kind | slot << 3 | flags)
     *  32  Uv            u0, v0, u1, v1
     *  48  Color0..3     RGBA8 at corners (s, t) = (0,0), (1,0), (0,1), (1,1)
     *  64  ShapeA        rect: base.xy, size.xy | msdf: fade left, right, width, range
     *  80  ShapeB        rect: corner radii     | msdf: fade strength l, r, thickness, smoothness
     *  96  Params        rect: thickness, softness
     * 104  OutlineColor  RGBA8
     * 108  Clip          short4 min.xy, max.xy (framebuffer px, y-up)
     */
    private static final int STRIDE = 116;
    private static final String[] ATTRIBUTES = {"Geom0", "Geom1", "Uv", "Color0", "Color1", "Color2", "Color3",
            "ShapeA", "ShapeB", "Params", "OutlineColor", "Clip"};

    private static final ShaderProgramKey SHADER_KEY = new ShaderProgramKey(
            Identifier.of("phaze", "core/gui_batched"), attributeFormat(), Defines.EMPTY);

    private static long data;
    private static int capacity;
    private static int count;
    private static final int[] textures = new int[MAX_TEXTURES];
    private static int textureCount;
    private static int vao, vbo;
    private static Boolean supported;
    private static final Run RUN = new Run();

    private GuiBatch() {
    }

    /**
     * Only binds the attribute names to locations 0..n at link time; instance attributes are
     * configured by {@link #setupVertexArray}, so the vanilla element is a placeholder.
     */
    private static VertexFormat attributeFormat() {
        VertexFormat.Builder builder = VertexFormat.builder();
        for (String name : ATTRIBUTES) builder.add(name, VertexFormatElement.POSITION);
        return builder.build();
    }

    /** Instanced arrays are core in GL 3.3; Minecraft only guarantees 3.2. */
    public static boolean isAvailable() {
        Boolean value = supported;
        if (value == null) {
            GLCapabilities caps = GL.getCapabilities();
            supported = value = caps.OpenGL33 || caps.GL_ARB_instanced_arrays;
        }
        return value;
    }

    public static boolean hasPending() {
        return count > 0;
    }

    private static long reserve() {
        if (count == capacity) {
            int grown = Math.max(1024, capacity * 2);
            data = data == 0L ? MemoryUtil.nmemAlloc((long) grown * STRIDE)
                    : MemoryUtil.nmemRealloc(data, (long) grown * STRIDE);
            if (data == 0L) throw new OutOfMemoryError("GUI batch instance buffer");
            capacity = grown;
        }
        return data + (long) count++ * STRIDE;
    }

    /** Slot for {@code texture}; drains the batch first when all eight slots hold other textures. */
    private static int slot(int texture) {
        for (int i = 0; i < textureCount; i++) {
            if (textures[i] == texture) return i;
        }
        if (textureCount == MAX_TEXTURES) BatchedRectangle.flush();
        textures[textureCount] = texture;
        return textureCount++;
    }

    /**
     * One rounded rect over the local box {@code (x0, y0, w, h)}; corner colors are given at
     * (s, t) = (0,0), (1,0), (0,1), (1,1) with s along x and t along y.
     */
    static void rect(Matrix4f m, float x0, float y0, float w, float h,
                     int color00, int color10, int color01, int color11,
                     float baseX, float baseY, float sizeX, float sizeY,
                     float r0, float r1, float r2, float r3,
                     float thickness, float softness, int outlineColor) {
        long p = reserve();
        putGeometry(p, m, x0, y0, w, h, 0.0F, KIND_RECT);
        putFloats(p + 32L, 0.0F, 0.0F, 0.0F, 0.0F);
        MemoryUtil.memPutInt(p + 48L, abgr(color00));
        MemoryUtil.memPutInt(p + 52L, abgr(color10));
        MemoryUtil.memPutInt(p + 56L, abgr(color01));
        MemoryUtil.memPutInt(p + 60L, abgr(color11));
        putFloats(p + 64L, baseX, baseY, sizeX, sizeY);
        putFloats(p + 80L, r0, r1, r2, r3);
        MemoryUtil.memPutFloat(p + 96L, thickness);
        MemoryUtil.memPutFloat(p + 100L, softness);
        MemoryUtil.memPutInt(p + 104L, abgr(outlineColor));
        putClip(p + 108L);
    }

    /** Run of MSDF quads; a {@code fadeWidth} of zero disables the label fade. Valid until the next batch call. */
    public static Run msdf(int texture, float range, float thickness, float smoothness,
                           float fadeLeft, float fadeRight, float fadeWidth,
                           float fadeLeftStrength, float fadeRightStrength) {
        int slot = slot(texture);
        return RUN.begin(KIND_MSDF | slot << 3, fadeLeft, fadeRight, fadeWidth, range,
                fadeLeftStrength, fadeRightStrength, thickness, smoothness);
    }

    /** Run of textured quads drawn like {@code POSITION_TEX_COLOR}: texture times color. */
    public static Run texture(int texture) {
        int slot = slot(texture);
        return RUN.begin(KIND_TEXTURE | slot << 3, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Draws every pending primitive. Callers go through {@link BatchedRectangle#flush}. */
    static void draw() {
        int instances = count;
        int slots = textureCount;
        count = 0;
        textureCount = 0;
        if (instances == 0) return;
        GuiDrawStats.rectDraws++;
        GuiDrawStats.batchInstances += instances;

        ShaderProgram previousShader = RenderSystem.getShader();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        int[] previousTextures = new int[MAX_TEXTURES];
        for (int i = 0; i < MAX_TEXTURES; i++) previousTextures[i] = RenderSystem.getShaderTexture(i);
        boolean scissor = GuiClipState.isEnabled();
        try {
            for (int i = 0; i < MAX_TEXTURES; i++) {
                RenderSystem.setShaderTexture(i, i < slots ? textures[i] : slots > 0 ? textures[0] : 0);
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            // Every instance carries its own clip.
            if (scissor) GlStateManager._disableScissorTest();
            ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);
            if (shader == null) {
                // Later scopes stay closed, so every renderer falls back to its eager path.
                supported = false;
                System.err.println("[Phaze/GuiBatch] Disabled: shader " + SHADER_KEY + " failed to load");
                return;
            }

            BufferRenderer.resetCurrentVertexBuffer();
            if (vao == 0) setupVertexArray();
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
            GL15C.nglBufferData(GL15C.GL_ARRAY_BUFFER, (long) instances * STRIDE, data, GL15C.GL_STREAM_DRAW);
            shader.initializeUniforms(VertexFormat.DrawMode.QUADS, RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(), MinecraftClient.getInstance().getWindow());
            shader.bind();
            GL31C.glDrawArraysInstanced(GL11C.GL_TRIANGLE_STRIP, 0, 4, instances);
            shader.unbind();
        } finally {
            GlStateManager._glBindVertexArray(0);
            if (scissor) GlStateManager._enableScissorTest();
            for (int i = 0; i < MAX_TEXTURES; i++) RenderSystem.setShaderTexture(i, previousTextures[i]);
            RenderSystem.setShader(previousShader);
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void setupVertexArray() {
        vao = GlStateManager._glGenVertexArrays();
        vbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        attribute(0, 4, GL11C.GL_FLOAT, false, 0);
        attribute(1, 4, GL11C.GL_FLOAT, false, 16);
        attribute(2, 4, GL11C.GL_FLOAT, false, 32);
        for (int i = 0; i < 4; i++) attribute(3 + i, 4, GL11C.GL_UNSIGNED_BYTE, true, 48 + 4 * i);
        attribute(7, 4, GL11C.GL_FLOAT, false, 64);
        attribute(8, 4, GL11C.GL_FLOAT, false, 80);
        attribute(9, 2, GL11C.GL_FLOAT, false, 96);
        attribute(10, 4, GL11C.GL_UNSIGNED_BYTE, true, 104);
        attribute(11, 4, GL11C.GL_SHORT, false, 108);
    }

    private static void attribute(int location, int size, int type, boolean normalized, int offset) {
        GL20C.glEnableVertexAttribArray(location);
        GL20C.glVertexAttribPointer(location, size, type, normalized, STRIDE, offset);
        if (GL.getCapabilities().OpenGL33) GL33C.glVertexAttribDivisor(location, 1);
        else ARBInstancedArrays.glVertexAttribDivisorARB(location, 1);
    }

    /** Drops pending primitives without drawing them. */
    public static void discard() {
        count = 0;
        textureCount = 0;
    }

    /** Releases the GL objects and the instance buffer; the next batch recreates them. */
    public static void close() {
        discard();
        if (vao != 0) {
            GlStateManager._glDeleteVertexArrays(vao);
            GlStateManager._glDeleteBuffers(vbo);
            vao = vbo = 0;
        }
        if (data != 0L) {
            MemoryUtil.nmemFree(data);
            data = 0L;
            capacity = 0;
        }
    }

    /** Origin and edge vectors of the local box under the affine GUI matrix, plus z and meta. */
    private static void putGeometry(long p, Matrix4f m, float x, float y, float w, float h, float z, int meta) {
        MemoryUtil.memPutFloat(p, m.m00() * x + m.m10() * y + m.m20() * z + m.m30());
        MemoryUtil.memPutFloat(p + 4L, m.m01() * x + m.m11() * y + m.m21() * z + m.m31());
        MemoryUtil.memPutFloat(p + 8L, m.m00() * w);
        MemoryUtil.memPutFloat(p + 12L, m.m01() * w);
        MemoryUtil.memPutFloat(p + 16L, m.m10() * h);
        MemoryUtil.memPutFloat(p + 20L, m.m11() * h);
        MemoryUtil.memPutFloat(p + 24L, m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
        MemoryUtil.memPutFloat(p + 28L, meta);
    }

    private static void putFloats(long p, float x, float y, float z, float w) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4L, y);
        MemoryUtil.memPutFloat(p + 8L, z);
        MemoryUtil.memPutFloat(p + 12L, w);
    }

    private static void putClip(long p) {
        MemoryUtil.memPutShort(p, GuiClipState.minX());
        MemoryUtil.memPutShort(p + 2L, GuiClipState.minY());
        MemoryUtil.memPutShort(p + 4L, GuiClipState.maxX());
        MemoryUtil.memPutShort(p + 6L, GuiClipState.maxY());
    }

    /** ARGB to the RGBA byte order of a little-endian int write. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /** Quads sharing one kind, texture slot and parameter set. */
    public static final class Run {
        private int meta;
        private float ax, ay, az, aw, bx, by, bz, bw;

        private Run begin(int meta, float ax, float ay, float az, float aw,
                          float bx, float by, float bz, float bw) {
            this.meta = meta;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.aw = aw;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.bw = bw;
            return this;
        }

        /**
         * Quad over the local box {@code (x, y, w, h)}: u runs {@code u0..u1} along x and v
         * {@code v0..v1} along y, or, with {@link #UV_SWAP}, u along y and v along x.
         */
        public void quad(Matrix4f m, float x, float y, float w, float h, float z,
                         float u0, float v0, float u1, float v1, int color, int flags) {
            long p = reserve();
            putGeometry(p, m, x, y, w, h, z, meta | flags);
            putFloats(p + 32L, u0, v0, u1, v1);
            int c = abgr(color);
            MemoryUtil.memPutInt(p + 48L, c);
            MemoryUtil.memPutInt(p + 52L, c);
            MemoryUtil.memPutInt(p + 56L, c);
            MemoryUtil.memPutInt(p + 60L, c);
            putFloats(p + 64L, ax, ay, az, aw);
            putFloats(p + 80L, bx, by, bz, bw);
            MemoryUtil.memPutFloat(p + 96L, 0.0F);
            MemoryUtil.memPutFloat(p + 100L, 0.0F);
            MemoryUtil.memPutInt(p + 104L, 0);
            putClip(p + 108L);
        }
    }
}
