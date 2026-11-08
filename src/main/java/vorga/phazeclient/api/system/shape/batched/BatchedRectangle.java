package vorga.phazeclient.api.system.shape.batched;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.hud.Render2DState;
import vorga.phazeclient.base.util.color.ColorUtil;

/**
 * Scope and flush protocol of the deferred GUI batch ({@link GuiBatch}).
 *
 * <h3>Scope-based deferral protocol</h3>
 *
 * Callers wrap a region of rect-heavy rendering in {@link #beginScope()}
 * / {@link #endScope()} (re-entrant counter). Inside that region,
 * {@link vorga.phazeclient.api.system.shape.implement.Rectangle#render}
 * routes to {@link #submit} instead of issuing its own draw call, MSDF text
 * and icons append to the same stream, and any renderer that draws by
 * itself (foreign draws, blur, framebuffer work) calls
 * {@link #flushIfBatching()} first. {@code endScope()} drains whatever
 * is left. Outside a scope, behavior is unchanged - {@code Rectangle.render}
 * still does its eager per-rect draw, so HUD and other call sites that
 * never opt in are not affected.
 *
 * <p>The SDF parameters travel with the geometry: the per-corner gradient is
 * the per-vertex color interpolated by the rasterizer, and base, size, radii,
 * thickness, softness and outline are constant per-rect attributes, exactly
 * what the uniform-based {@code round.fsh} computed.</p>
 */
public final class BatchedRectangle {
    /**
     * Scope depth lets nested {@code beginScope/endScope} calls coexist
     * without the inner pair flushing prematurely. Only the outermost
     * {@code endScope()} drains the buffer.
     */
    private static int scopeDepth = 0;
    private static boolean enabled = true;
    private static boolean flushing;
    private static Render2DState scopeState;
    private static int stateScopeDepth;
    private static final Vector3f SCRATCH_BASE_POS = new Vector3f();
    private static final Vector3f SCRATCH_SIZE = new Vector3f();
    private static final Vector4f SCRATCH_RADIUS = new Vector4f();

    /**
     * Optional override for the framebuffer height that {@link #submit}
     * uses to compute the GL bottom-left corner of the rect in framebuffer
     * pixels. The rounded-rect SDF reconstructs each fragment's signed
     * distance from {@code gl_FragCoord}, which is always relative to the
     * framebuffer currently bound for writes. So whenever a caller binds a
     * non-default framebuffer (e.g. {@link
     * vorga.phazeclient.api.system.snapshot.CardSnapshotCache#beginCapture}
     * binds a small per-card FBO) the rect math has to switch to the bound
     * FB height, or every fragment is discarded as outside the SDF.
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
        if (stateScopeDepth == 0) scopeState = Render2DState.join();
        stateScopeDepth++;
        // Without the batch shader's vertex format the scope never opens and
        // every renderer keeps its eager path.
        if (!GuiBatch.isAvailable() || (!enabled && scopeDepth == 0)) {
            return;
        }
        scopeDepth++;
    }

    public static void endScope() {
        if (stateScopeDepth <= 0) return;
        try {
            if (scopeDepth > 0 && --scopeDepth == 0) flush();
        } finally {
            if (--stateScopeDepth == 0) {
                Render2DState state = scopeState;
                scopeState = null;
                state.close();
            }
        }
    }

    public static boolean isBatching() {
        return scopeDepth > 0;
    }

    /** A/B switch for benchmarks: scopes opened while disabled render everything eagerly. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Drains the pending batch if one is open. Safe to call any time;
     * no-op when nothing is queued. Used by renderers that draw by
     * themselves (images, blur, framebuffer rebinds) and must appear
     * ABOVE everything already submitted.
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
     * snapshot capture).
     *
     * <p>Must be called AFTER any pending batch has been drained -
     * otherwise the queued rects already hold pre-baked base values from
     * the previous height and would mis-render once flushed against the
     * new framebuffer.
     */
    public static void setRenderTargetFbHeight(float fbHeight) {
        flushIfBatching();
        renderTargetFbHeightOverride = fbHeight;
    }

    public static void clearRenderTargetFbHeight() {
        try {
            flushIfBatching();
        } finally {
            renderTargetFbHeightOverride = -1.0F;
        }
    }

    /** Raw override, including the -1 sentinel; used to restore nested FBO captures. */
    public static float getRenderTargetFbHeight() { return renderTargetFbHeightOverride; }

    /** Restore after pending geometry has been flushed or discarded, without drawing on the wrong FBO. */
    public static void restoreRenderTargetFbHeight(float height) { renderTargetFbHeightOverride = height; }

    /** Drop partial vertices after failure; never publish them in the next frame. */
    public static void discardPending() {
        GuiBatch.discard();
    }

    public static void cleanup() {
        try {
            discardPending();
        } finally {
            scopeDepth = 0;
            stateScopeDepth = 0;
            renderTargetFbHeightOverride = -1.0F;
            Render2DState state = scopeState;
            scopeState = null;
            if (state != null) state.close();
        }
    }

    /**
     * Returns the framebuffer height (in framebuffer pixels) that any
     * SDF-rect shader should treat as the {@code gl_FragCoord} y-axis
     * extent for the currently-bound render target. Defaults to the
     * main window's framebuffer height; replaced via
     * {@link #setRenderTargetFbHeight} during card-FBO snapshot capture.
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

    /** True while this class draws its own pending geometry, so foreign-draw hooks do not re-enter. */
    public static boolean isFlushing() {
        return flushing;
    }

    /**
     * Flush from inside a foreign draw. The caller has already selected its own
     * shader, textures and blend state, so they are snapshotted around the flush.
     */
    public static void flushForForeignDraw() {
        if (flushing || !isBatching() || !GuiBatch.hasPending()) return;
        try (Render2DState state = Render2DState.capture()) {
            flush();
        }
    }

    public static void flush() {
        if (flushing) return;
        flushing = true;
        try (Render2DState state = Render2DState.join()) {
            GuiBatch.draw();
        } finally {
            flushing = false;
        }
    }

    /**
     * Appends one rounded-rect quad (4 vertices) to the batch. Math mirrors
     * {@link vorga.phazeclient.api.system.shape.implement.Rectangle#render}:
     * <ul>
     *   <li>base = matrix-transformed top-left corner in framebuffer pixels,
     *       flipped to the GL-space (y-up) bottom-left of the rect;</li>
     *   <li>size = (scaledWidth, scaledHeight);</li>
     *   <li>radii = the four corner radii pre-multiplied by {@code size.y};</li>
     *   <li>per-vertex color = the gradient corner that coincides with that vertex.</li>
     * </ul>
     * The quad is expanded by {@code softness/2} on each side so the SDF
     * anti-aliasing band falls inside the geometry.
     */
    public static void submit(ShapeProperties shape) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        float scale = (float) mc.getWindow().getScaleFactor();
        float windowHeight = renderTargetFbHeightOverride > 0.0F
                ? renderTargetFbHeightOverride
                : mc.getWindow().getFramebufferHeight();
        float globalAlpha = RenderSystem.getShaderColor()[3];

        Matrix4f matrix = shape.getMatrix().peek().getPositionMatrix();
        Vector3f basePos = matrix.transformPosition(shape.getX(), shape.getY(), 0, SCRATCH_BASE_POS).mul(scale);
        Vector3f sizeVec = matrix.getScale(SCRATCH_SIZE).mul(scale);

        float scaledWidth = shape.getWidth() * sizeVec.x;
        float scaledHeight = shape.getHeight() * sizeVec.y;

        float baseX = basePos.x;
        float baseY = windowHeight - scaledHeight - basePos.y;

        Vector4f radius = SCRATCH_RADIUS.set(shape.getRound()).mul(sizeVec.y);
        float softness = shape.getSoftness();

        // Same per-vertex alpha pre-multiplication the uniform path applies to its colors.
        Vector4i colors = shape.getColor();
        int color1 = ColorUtil.multAlpha(colors.x, globalAlpha); // BL screen / coords (0,0)
        int color2 = ColorUtil.multAlpha(colors.y, globalAlpha); // TL screen / coords (0,1)
        int color3 = ColorUtil.multAlpha(colors.z, globalAlpha); // BR screen / coords (1,0)
        int color4 = ColorUtil.multAlpha(colors.w, globalAlpha); // TR screen / coords (1,1)
        int outlineColor = ColorUtil.multAlpha(shape.getOutlineColor(), globalAlpha);

        try {
            // gl_FragCoord is y-up while the GUI matrix is y-down, so the screen top-left
            // corner carries the coords-(0,1) gradient color, and so on.
            GuiBatch.rect(matrix, shape.getX() - softness / 2.0F, shape.getY() - softness / 2.0F,
                    shape.getWidth() + softness, shape.getHeight() + softness,
                    color2, color4, color1, color3,
                    baseX, baseY, scaledWidth, scaledHeight,
                    radius.x, radius.y, radius.z, radius.w,
                    shape.getThickness(), softness, outlineColor);
        } catch (RuntimeException | Error failure) {
            discardPending();
            throw failure;
        }
    }
}
