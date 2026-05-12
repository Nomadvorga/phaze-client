package vorga.phazeclient.api.system.shape.batched;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;

/**
 * Batched rounded-rect renderer. Replaces the per-rectangle BufferBuilder
 * begin/end + 8-uniform draw call cycle of {@link vorga.phazeclient.api.system.shape.implement.Rectangle}
 * with a deferred-emit + single flush model so an arbitrary number of
 * independent rounded rectangles - each with its own size, screen
 * position, corner radii, gradient colors, outline color, softness and
 * thickness - can be issued in ONE BufferBuilder pass and ONE
 * draw call against the {@code phaze:core/round_batched} shader.
 *
 * <h3>How it batches per-rect data without uniforms</h3>
 *
 * The legacy {@code phaze:core/round.fsh} read the SDF parameters from
 * shader uniforms set right before each draw, which forced a separate
 * BufferBuilder + BufferRenderer.drawWithGlobalProgram per rect. To
 * batch, the parameters must travel WITH the geometry, so we register
 * five {@link VertexFormatElement}s with {@link VertexFormatElement.Usage#GENERIC}:
 * {@code RectBase} (vec2), {@code RectSize} (vec2), {@code Radius} (vec4),
 * {@code Params} (vec2 = thickness, softness) and {@code OutlineColor}
 * (vec4). All four vertices of a single rect carry identical values for
 * those five attributes (see {@link #emit}), so smooth interpolation in
 * the rasterizer == constant value at every fragment, exactly as the
 * uniform-based version intended. The per-corner gradient that the
 * legacy shader rebuilt from {@code color1..color4} uniforms via
 * {@code createGradient(coords, ...)} is reproduced for free by writing
 * each vertex's standard {@link VertexFormatElement#COLOR} attribute as
 * the gradient corner color and letting the rasterizer's bilinear
 * interpolation do the rest.
 *
 * <h3>Scope-based deferral protocol</h3>
 *
 * Callers wrap a region of rect-heavy rendering in {@link #beginScope()}
 * / {@link #endScope()} (re-entrant counter). Inside that region,
 * {@link vorga.phazeclient.api.system.shape.implement.Rectangle#render}
 * routes to {@link #submit} instead of issuing its own draw call, and
 * any non-rect renderer that must preserve drawing order against the
 * pending rects (text, image, blur, scissor change) calls
 * {@link #flushIfBatching()} first. {@code endScope()} drains whatever
 * is left. Outside a scope, behavior is unchanged - {@code Rectangle.render}
 * still does its eager per-rect draw, so HUD and other call sites that
 * never opt in are not affected.
 */
public final class BatchedRectangle {
    /**
     * Per-rect SDF data, as five GENERIC vertex attributes. Vanilla
     * 1.21.4 reserves element ids 0..6 for POSITION/COLOR/UV_0/UV/UV_1/
     * UV_2/NORMAL, but in practice OTHER mods aggressively claim 7+ at
     * mod-init time - Sodium grabs several slots for its terrain
     * vertex format, Iris a few more for shader overrides, and so on.
     * Hard-coding 7..11 crashed with {@code IllegalArgumentException:
     * Duplicate element registration for: 7} on a Sodium+Iris setup.
     *
     * <p>To stay robust we probe vanilla's element table at class init
     * via {@link VertexFormatElement#get(int)} (returns {@code null}
     * for unused slots) and grab the first five free ones. The
     * {@code uvIndex} parameter is irrelevant for {@code GENERIC}
     * usage (vanilla only consults it for the {@code UV} usage's
     * setupTask), so it stays 0 across the board.
     *
     * <p>If allocation fails for any reason (table full, classloading
     * race, future MC API change) we fall back to a permanent disabled
     * state - {@link #isBatching()} stays {@code false} forever, every
     * call site keeps using the legacy eager {@code Rectangle.render}
     * path, and the menu still draws correctly, just without the
     * batching speedup.
     */
    public static final VertexFormatElement RECT_BASE;
    public static final VertexFormatElement RECT_SIZE;
    public static final VertexFormatElement RECT_RADIUS;
    public static final VertexFormatElement RECT_PARAMS;
    public static final VertexFormatElement RECT_OUTLINE;

    /**
     * Names MUST match the {@code in} attribute names declared in
     * {@code round_batched.vsh} AND the entries in the shader json's
     * {@code attributes} array. {@link VertexFormat#bindAttributes(int)}
     * issues {@code glBindAttribLocation(program, element.id, name)}
     * before link, which is what hooks each {@code in <name>} in the
     * vertex shader to the corresponding element id.
     */
    public static final VertexFormat BATCHED_FORMAT;

    public static final ShaderProgramKey SHADER_KEY;

    /**
     * Latched off if {@link VertexFormatElement#register} threw or any
     * other init step failed. {@link #isBatching()} forces {@code false}
     * while disabled so {@link vorga.phazeclient.api.system.shape.implement.Rectangle#render}
     * keeps using the legacy eager path - failure mode is "no
     * speedup", never "crashed menu".
     */
    private static final boolean DISABLED;

    static {
        VertexFormatElement base = null, size = null, radius = null, params = null, outline = null;
        VertexFormat format = null;
        ShaderProgramKey shaderKey = null;
        boolean disabled = true;
        try {
            int id = findFreeSlot(7);
            base = VertexFormatElement.register(id, 0, VertexFormatElement.ComponentType.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
            id = findFreeSlot(id + 1);
            size = VertexFormatElement.register(id, 0, VertexFormatElement.ComponentType.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
            id = findFreeSlot(id + 1);
            radius = VertexFormatElement.register(id, 0, VertexFormatElement.ComponentType.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
            id = findFreeSlot(id + 1);
            params = VertexFormatElement.register(id, 0, VertexFormatElement.ComponentType.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
            id = findFreeSlot(id + 1);
            outline = VertexFormatElement.register(id, 0, VertexFormatElement.ComponentType.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

            format = VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("Color", VertexFormatElement.COLOR)
                    .add("RectBase", base)
                    .add("RectSize", size)
                    .add("Radius", radius)
                    .add("Params", params)
                    .add("OutlineColor", outline)
                    .build();
            shaderKey = new ShaderProgramKey(
                    Identifier.of("phaze", "core/round_batched"),
                    format,
                    Defines.EMPTY);
            disabled = false;
        } catch (Throwable t) {
            // Common offender: another mod (Sodium, Iris, etc.) already
            // claimed every slot from 7 to 31. Log once at init and
            // accept eager rendering as the permanent path.
            System.err.println("[Phaze/BatchedRectangle] Disabled (VertexFormatElement allocation failed): " + t);
        }
        RECT_BASE = base;
        RECT_SIZE = size;
        RECT_RADIUS = radius;
        RECT_PARAMS = params;
        RECT_OUTLINE = outline;
        BATCHED_FORMAT = format;
        SHADER_KEY = shaderKey;
        DISABLED = disabled;
    }

    private static int findFreeSlot(int startFrom) {
        // Element ids are encoded as 1 << id in the requiredMask /
        // currentMask bitfields VertexFormat / BufferBuilder use to
        // validate per-vertex element coverage, so the GL-portable
        // ceiling is 32. We start the scan at the caller's hint
        // (typically previous_slot + 1) so the five elements we
        // register end up contiguous - not strictly required, but
        // makes the resulting VertexFormat layout reproducible across
        // Phaze versions for debugging.
        for (int id = startFrom; id < 32; id++) {
            if (VertexFormatElement.get(id) == null) {
                return id;
            }
        }
        throw new IllegalStateException("No free VertexFormatElement slot >= " + startFrom);
    }

    /**
     * Scope depth lets nested {@code beginScope/endScope} calls coexist
     * without the inner pair flushing prematurely. Only the outermost
     * {@code endScope()} drains the buffer.
     */
    private static int scopeDepth = 0;
    private static BufferBuilder activeBuilder = null;
    private static int pendingRects = 0;

    /**
     * Optional override for the framebuffer height that {@link #submit}
     * uses to compute {@code RECT_BASE.y} (the GL bottom-left corner
     * of the rect in framebuffer pixels). The rounded-rect SDF in
     * {@code round_batched.fsh} reconstructs each fragment's signed
     * distance from {@code gl_FragCoord.xy - vRectBase - halfSize},
     * and {@code gl_FragCoord} is always reported relative to whatever
     * framebuffer is currently bound for writes. So whenever a caller
     * binds a non-default framebuffer (e.g. {@link
     * vorga.phazeclient.api.system.snapshot.CardSnapshotCache#beginCapture}
     * binds a small per-card FBO) the rect math has to switch from
     * "main window FB height" to "currently bound FB height" or every
     * fragment's distance balloons by the height delta and the entire
     * rect renders fully transparent (every pixel discarded as outside
     * the SDF).
     *
     * <p>{@code -1.0F} (default) means "use the live main window
     * framebuffer height", which is what every non-capture caller
     * wants. Capture code sets this to the bound FBO's height and
     * resets to {@code -1.0F} when the capture pass ends.
     */
    private static float renderTargetFbHeightOverride = -1.0F;

    private BatchedRectangle() {
    }

    public static void beginScope() {
        // No-op when batching infrastructure failed to initialize -
        // beginScope/endScope counters never increment, isBatching()
        // stays false, every Rectangle.render call falls through to
        // the legacy eager path.
        if (DISABLED) {
            return;
        }
        scopeDepth++;
    }

    public static void endScope() {
        if (DISABLED || scopeDepth <= 0) {
            return;
        }
        if (--scopeDepth == 0) {
            flush();
        }
    }

    public static boolean isBatching() {
        return !DISABLED && scopeDepth > 0;
    }

    /**
     * Drains the pending batch if one is open. Safe to call any time;
     * no-op when nothing is queued. Used by non-rect renderers (text,
     * image, blur, scissor change, framebuffer rebind) that need to
     * appear ABOVE any rectangles already submitted - if they drew
     * without flushing first, the pending rects would render after
     * them and visually punch through.
     */
    public static void flushIfBatching() {
        if (isBatching()) {
            flush();
        }
    }

    /**
     * Override the framebuffer height that subsequent {@link #submit}
     * calls use for the SDF math, until {@link #clearRenderTargetFbHeight}
     * resets to "use main window FB height". Required by any caller
     * that has bound a custom framebuffer for writes (e.g. card-FBO
     * snapshot capture) so the per-fragment SDF distance is computed
     * against the correct {@code gl_FragCoord} frame of reference.
     *
     * <p>Must be called AFTER any pending batch has been drained -
     * otherwise the queued rects already hold pre-baked
     * {@code RECT_BASE} values from the previous height and would
     * mis-render once flushed against the new framebuffer.
     */
    public static void setRenderTargetFbHeight(float fbHeight) {
        flushIfBatching();
        renderTargetFbHeightOverride = fbHeight;
    }

    public static void clearRenderTargetFbHeight() {
        flushIfBatching();
        renderTargetFbHeightOverride = -1.0F;
    }

    /**
     * Returns the framebuffer height (in framebuffer pixels) that any
     * SDF-rect shader should treat as the {@code gl_FragCoord} y-axis
     * extent for the currently-bound render target. Defaults to the
     * main window's framebuffer height; replaced via
     * {@link #setRenderTargetFbHeight} during card-FBO snapshot
     * capture. Exposed so non-batched rect / arc shapes can consult
     * the same value when batching is disabled (e.g. Sodium / Iris
     * exhausted the GENERIC vertex slot pool and {@link #DISABLED} is
     * latched on, forcing every shape through its eager per-rect
     * uniform path).
     */
    public static float getActiveFbHeight() {
        if (renderTargetFbHeightOverride > 0.0F) {
            return renderTargetFbHeightOverride;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 0.0F;
        }
        return mc.getWindow().getFramebufferHeight();
    }

    public static void flush() {
        BufferBuilder builder = activeBuilder;
        int rects = pendingRects;
        activeBuilder = null;
        pendingRects = 0;
        if (builder == null || rects == 0) {
            return;
        }

        BuiltBuffer built = builder.endNullable();
        if (built == null) {
            return;
        }

        // Match the legacy Rectangle.render GL state setup so this
        // path is a drop-in replacement: alpha-blended with
        // src=src.alpha, dst=1-src.alpha (also declared in the shader
        // json's "blend" block), no depth writes from these fragments
        // but depth test stays on so ordering matches the eager path,
        // back-face cull stays on (we emit in the same winding the
        // legacy quad emitter does, so no flipped quads).
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        try {
            ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);
            if (shader == null) {
                built.close();
                return;
            }
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    /**
     * Appends one rounded-rect quad (4 vertices) to the active batch,
     * lazily allocating the BufferBuilder on first submit per flush
     * window. Math mirrors {@link vorga.phazeclient.api.system.shape.implement.Rectangle#render}
     * exactly:
     * <ul>
     *   <li>{@code basePos} = matrix-transformed top-left corner in
     *       framebuffer pixels;</li>
     *   <li>{@code RECT_BASE} = (basePos.x, windowHeight - scaledHeight - basePos.y),
     *       i.e. the GL-space (y-up) bottom-left of the rect, which is
     *       what the SDF shader needs as its origin;</li>
     *   <li>{@code RECT_SIZE} = (scaledWidth, scaledHeight);</li>
     *   <li>{@code RADIUS} = the four corner radii pre-multiplied by
     *       {@code size.y} (matches the legacy
     *       {@code shape.getRound().mul(size.y)} computation);</li>
     *   <li>per-vertex {@code COLOR} maps to the gradient corner that
     *       coincides with that vertex (see corner-to-screen mapping
     *       comment inside the method).</li>
     * </ul>
     * The expanded quad ({@code softness/2} bleed on each side) is
     * identical to {@code drawEngine.quad(matrix, buffer, x - softness/2,
     * y - softness/2, w + softness, h + softness)} so the SDF anti-
     * aliasing band falls inside the geometry just like the legacy
     * shader expects.
     */
    public static void submit(ShapeProperties shape) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        float scale = (float) mc.getWindow().getScaleFactor();
        // Use the per-FBO override when one is active so SDF math is
        // expressed in the bound framebuffer's pixel space - main FB
        // height while the menu draws to the screen, card-FBO height
        // while CardSnapshotCache.beginCapture has redirected writes
        // to a snapshot. Without this override every rect captured
        // into a card FBO renders fully transparent because its
        // baseY would be computed against a 1080-tall main window
        // while gl_FragCoord only spans the ~160-tall card FBO,
        // yielding SDF distances large enough to discard every
        // fragment.
        float windowHeight = renderTargetFbHeightOverride > 0.0F
                ? renderTargetFbHeightOverride
                : mc.getWindow().getFramebufferHeight();
        float globalAlpha = RenderSystem.getShaderColor()[3];

        Matrix4f matrix = shape.getMatrix().peek().getPositionMatrix();
        Vector3f basePos = matrix.transformPosition(shape.getX(), shape.getY(), 0, new Vector3f()).mul(scale);
        Vector3f sizeVec = matrix.getScale(new Vector3f()).mul(scale);

        float scaledWidth = shape.getWidth() * sizeVec.x;
        float scaledHeight = shape.getHeight() * sizeVec.y;

        float baseX = basePos.x;
        float baseY = windowHeight - scaledHeight - basePos.y;

        Vector4f scaledRadius = new Vector4f(shape.getRound()).mul(sizeVec.y);
        float softness = shape.getSoftness();
        float thickness = shape.getThickness();

        Vector4i colors = shape.getColor();
        // ColorUtil.multAlpha keeps RGB intact and scales the int's
        // alpha byte by globalAlpha, which is the same per-vertex
        // pre-multiplication the legacy uniform path applied to its
        // color1..color4 / outlineColor uniforms.
        int color1 = ColorUtil.multAlpha(colors.x, globalAlpha); // BL screen / coords (0,0)
        int color2 = ColorUtil.multAlpha(colors.y, globalAlpha); // TL screen / coords (0,1)
        int color3 = ColorUtil.multAlpha(colors.z, globalAlpha); // BR screen / coords (1,0)
        int color4 = ColorUtil.multAlpha(colors.w, globalAlpha); // TR screen / coords (1,1)
        int outlineColor = ColorUtil.multAlpha(shape.getOutlineColor(), globalAlpha);

        if (activeBuilder == null) {
            activeBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, BATCHED_FORMAT);
        }

        float x0 = shape.getX() - softness / 2.0F;
        float y0 = shape.getY() - softness / 2.0F;
        float w = shape.getWidth() + softness;
        float h = shape.getHeight() + softness;

        // drawEngine.quad emission order (preserved here so winding
        // stays the same as the legacy Rectangle path):
        //   v0 = (x, y)       screen TL == GL BL == coords (0, 1) -> color2
        //   v1 = (x, y+h)     screen BL == GL TL == coords (0, 0) -> color1
        //   v2 = (x+w, y+h)   screen BR == GL TR == coords (1, 0) -> color3
        //   v3 = (x+w, y)     screen TR == GL BR == coords (1, 1) -> color4
        //
        // The "screen X == GL Y" axis flip happens because gl_FragCoord
        // is y-up while MC's GUI matrix is y-down; the
        // RECT_BASE/RECT_SIZE values we pass are pre-flipped to GL
        // space (see baseY computation above) so the SDF math is
        // expressed in framebuffer pixels and the per-corner colors
        // align with the legacy shader's coords-(x,y) gradient mapping.
        emit(activeBuilder, matrix, x0, y0, color2,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);
        emit(activeBuilder, matrix, x0, y0 + h, color1,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);
        emit(activeBuilder, matrix, x0 + w, y0 + h, color3,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);
        emit(activeBuilder, matrix, x0 + w, y0, color4,
                baseX, baseY, scaledWidth, scaledHeight, scaledRadius, thickness, softness, outlineColor);

        pendingRects++;
    }

    /**
     * Emits one vertex with all 7 attributes filled in. The standard
     * POSITION + COLOR are written via vanilla helpers; the five
     * GENERIC attrs are written via {@link BufferBuilder#beginElement}
     * (made accessible by {@code phaze.accesswidener}) to land at the
     * correct byte offsets that the format declared.
     */
    private static void emit(BufferBuilder buffer, Matrix4f matrix,
                             float x, float y, int color,
                             float baseX, float baseY, float sizeX, float sizeY,
                             Vector4f radius, float thickness, float softness,
                             int outlineColor) {
        buffer.vertex(matrix, x, y, 0.0F).color(color);

        long ptr = buffer.beginElement(RECT_BASE);
        MemoryUtil.memPutFloat(ptr, baseX);
        MemoryUtil.memPutFloat(ptr + 4L, baseY);

        ptr = buffer.beginElement(RECT_SIZE);
        MemoryUtil.memPutFloat(ptr, sizeX);
        MemoryUtil.memPutFloat(ptr + 4L, sizeY);

        ptr = buffer.beginElement(RECT_RADIUS);
        MemoryUtil.memPutFloat(ptr, radius.x);
        MemoryUtil.memPutFloat(ptr + 4L, radius.y);
        MemoryUtil.memPutFloat(ptr + 8L, radius.z);
        MemoryUtil.memPutFloat(ptr + 12L, radius.w);

        ptr = buffer.beginElement(RECT_PARAMS);
        MemoryUtil.memPutFloat(ptr, thickness);
        MemoryUtil.memPutFloat(ptr + 4L, softness);

        ptr = buffer.beginElement(RECT_OUTLINE);
        MemoryUtil.memPutFloat(ptr, ColorUtil.redf(outlineColor));
        MemoryUtil.memPutFloat(ptr + 4L, ColorUtil.greenf(outlineColor));
        MemoryUtil.memPutFloat(ptr + 8L, ColorUtil.bluef(outlineColor));
        MemoryUtil.memPutFloat(ptr + 12L, ColorUtil.alphaf(outlineColor));
    }
}
