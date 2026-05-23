package vorga.phazeclient.api.system.cursor;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Per-frame GLFW cursor switcher backing the Animations ➔ Dynamic Cursor
 * toggle. Modeled after the way modern UIs (1.21.11+ included) flip the
 * pointer to a hand on clickable elements and an I-beam on text inputs:
 * each frame the renderer "requests" what cursor the hovered element
 * should display, and at end-of-frame we apply the most recent request.
 *
 * <h3>Frame lifecycle</h3>
 * <ol>
 *   <li>{@link #beginFrame()} clears the per-frame request back to
 *       {@link #SHAPE_ARROW}. Called from {@code ScreenCursorMixin} HEAD.</li>
 *   <li>Hovered widgets call {@link #requestHand()} or
 *       {@link #requestBeam()} during their render pass.</li>
 *   <li>{@link #endFrame()} commits the highest-priority request to GLFW
 *       via {@code glfwSetCursor}, and only when the shape differs from
 *       the last applied one - avoids OS-level cursor churn at 60 fps.
 *       Called from {@code ScreenCursorMixin} TAIL.</li>
 * </ol>
 *
 * <h3>Priority</h3>
 * If multiple widgets request a cursor in the same frame (e.g. a hovered
 * button rendered behind a hovered tooltip frame), {@code requestBeam()}
 * wins over {@code requestHand()} which wins over the default arrow,
 * because text inputs are typically drawn LAST on top of buttons and
 * the user expects the foremost element's affordance.
 *
 * <p>Active scroll gestures take precedence over every hover request:
 * while the user is spinning the wheel, {@link #notifyScroll(double, double)}
 * picks a {@code VRESIZE} / {@code HRESIZE} shape and applies it
 * <em>immediately</em>, then arms a {@link #SCROLL_OVERRIDE_NANOS}
 * window so {@code endFrame} keeps the resize shape stable across the
 * spin. The window expires after the last wheel event so the regular
 * hand/beam/arrow logic resumes - that's what makes the cursor "return
 * to normal" once the user stops scrolling, fixing the original
 * "анимация зависает и не возвращается" symptom.
 *
 * <h3>Disabled / non-screen state</h3>
 * When {@link vorga.phazeclient.implement.features.modules.other.Animations#dynamicCursor}
 * is off, or when there's no screen open (in-game without GUI), we
 * fall back to the system arrow so the in-world crosshair / camera-
 * grab UX is untouched.
 */
public final class CursorManager {

    /** GLFW standard cursor shape codes. Cached locally so callers
     *  don't have to import GLFW. */
    public static final int SHAPE_ARROW = GLFW.GLFW_ARROW_CURSOR;
    public static final int SHAPE_HAND = GLFW.GLFW_HAND_CURSOR;
    public static final int SHAPE_BEAM = GLFW.GLFW_IBEAM_CURSOR;
    /** Vertical resize (the ↕ "изменение вертикальных размеров" shape).
     *  Used while the user spins the wheel vertically. */
    public static final int SHAPE_VRESIZE = GLFW.GLFW_VRESIZE_CURSOR;
    /** Horizontal resize (the ↔ "изменение горизонтальных размеров"
     *  shape). Used for shift-scroll / horizontal-wheel gestures. */
    public static final int SHAPE_HRESIZE = GLFW.GLFW_HRESIZE_CURSOR;

    /**
     * Time (nanoseconds) the scroll-resize cursor lingers after the
     * last wheel event. Picked to feel "as long as I'm scrolling" -
     * fast wheel flicks come in 30-60 ms apart so 150 ms keeps the
     * shape stable across a spin without leaving a visible stale
     * arrow when the user lifts off and the next hover reading takes
     * over.
     */
    private static final long SCROLL_OVERRIDE_NANOS = 150L * 1_000_000L;

    /** Lazily-created GLFW cursor handles. {@code 0L} means "not yet
     *  created" (or "creation failed - skip"); we retry on every
     *  request because GLFW's standard-cursor creation is cheap and
     *  early failures on some Linux setups self-heal once the windowing
     *  system finishes warming up. */
    private static long arrowCursor = 0L;
    private static long handCursor = 0L;
    private static long beamCursor = 0L;
    private static long vResizeCursor = 0L;
    private static long hResizeCursor = 0L;

    /** Most recently requested shape this frame. Reset at frame start. */
    private static int requestedShape = SHAPE_ARROW;
    /** Last shape committed to the OS. Used to skip redundant
     *  glfwSetCursor calls. {@code -1} forces the first commit through
     *  even when the request matches the (uninitialized) ARROW default. */
    private static int lastAppliedShape = -1;
    /** True while at least one Screen is open this frame. We use this
     *  to gate {@link #endFrame()} - on an in-game-no-GUI frame we
     *  leave the cursor alone so the F1-screenshot / camera-grab paths
     *  aren't disturbed. */
    private static boolean screenActive = false;

    /** Active scroll-override shape, or {@code 0} if none. Set by
     *  {@link #notifyScroll(double, double)} and consumed by
     *  {@link #endFrame(boolean)} when its expiry time hasn't passed. */
    private static int scrollOverrideShape = 0;
    /** {@link System#nanoTime()} stamp at which the scroll override
     *  stops applying. Compared at end-of-frame instead of via a
     *  timer thread so we never apply a cursor change after the
     *  render loop has already moved on. */
    private static long scrollOverrideUntilNanos = 0L;

    /** Active drag-override shape, or {@code 0} if no drag is in
     *  progress. Unlike the scroll override this has no expiry timer -
     *  it stays pinned until {@link #endDrag()} is called explicitly,
     *  because a slider drag can pause arbitrarily long with the
     *  button held but no movement (user picking the exact value)
     *  and the cursor must not snap back to the hand pointer during
     *  that pause. */
    private static int dragOverrideShape = 0;

    private CursorManager() {}

    /** Start of a Screen render frame; clears the request. */
    public static void beginFrame() {
        requestedShape = SHAPE_ARROW;
        screenActive = true;
    }

    /** Request the hand pointer (typical button hover). */
    public static void requestHand() {
        // Beam wins over hand because text fields are usually layered
        // on top of buttons in the same screen and the user expects the
        // input affordance to take precedence at the click point.
        if (requestedShape != SHAPE_BEAM) {
            requestedShape = SHAPE_HAND;
        }
    }

    /** Request the I-beam pointer (typical text-input hover). */
    public static void requestBeam() {
        requestedShape = SHAPE_BEAM;
    }

    /**
     * Pin a drag shape (e.g. {@link #SHAPE_HRESIZE} for a horizontal
     * slider being dragged) until {@link #endDrag()} is called. Unlike
     * {@link #notifyScroll(double, double)} this has no expiry timer -
     * the cursor stays in the chosen shape across arbitrarily long
     * holds-without-movement, which matches what a user expects while
     * fine-tuning a slider value with a still mouse.
     *
     * <p>Idempotent: calling with the same shape repeatedly has no
     * effect. Calling with a different shape replaces the active drag
     * shape, useful if a future widget needs to swap mid-drag (e.g.
     * a 2D pad switching from H to diagonal resize).
     */
    public static void beginDrag(int shape) {
        if (dragOverrideShape == shape) {
            return;
        }
        dragOverrideShape = shape;
        // Apply right now so the user sees the resize cursor on the
        // very same press tick. endFrame will reapply if anything
        // overwrites it before the next frame.
        applyShape(shape);
    }

    /**
     * Release the drag pin set by {@link #beginDrag(int)}. The next
     * {@link #endFrame(boolean)} falls back to the regular hover
     * priority - hand if the pointer is still over a clickable, arrow
     * otherwise.
     */
    public static void endDrag() {
        dragOverrideShape = 0;
    }

    /**
     * Called from any scroll hook (Mouse.onMouseScroll mixin, slider
     * widgets, our own MenuScreen handler) with the raw wheel deltas.
     * Picks {@link #SHAPE_VRESIZE} when the vertical delta dominates
     * and {@link #SHAPE_HRESIZE} when the horizontal delta dominates,
     * applies it to the OS cursor immediately, and arms a
     * {@link #SCROLL_OVERRIDE_NANOS} window during which
     * {@link #endFrame(boolean)} keeps the chosen resize shape stable
     * regardless of what the hovered widget asks for.
     *
     * <p>Why apply immediately instead of waiting for endFrame:
     * scroll events on slider widgets (FOV, render distance, etc.)
     * can change the slider value and trigger an option-applied
     * callback that issues its own {@code glfwSetCursor} or
     * {@code Mouse.lockCursor}-adjacent calls in the same tick;
     * waiting for the next render frame's endFrame would let those
     * intermediate calls win and the resize shape would never be
     * visible to the user. Applying inside notifyScroll guarantees
     * the shape lands during the same wheel-event handler before any
     * downstream code can clobber it. The endFrame override window
     * then keeps it pinned across subsequent frames until the spin
     * ends.
     *
     * <p>If both deltas are zero (some platforms emit a {@code (0, 0)}
     * event on touch-pad lift) we leave the override untouched so the
     * shape doesn't reset mid-spin.
     */
    public static void notifyScroll(double horizontal, double vertical) {
        // Defence-in-depth: even though every call site checks
        // {@code Animations.isDynamicCursorEnabled()} before calling
        // here, there's a race where the toggle can flip OFF between
        // the call-site check and the {@code applyShape} invocation
        // below. The user reported "при выключенном Animations всё
        // равно появляется на 1 кадр" - that one frame is precisely
        // this race plus the following {@code applyShape} call. A
        // second guard inside the manager closes the gap: if the
        // toggle is off right now, we drop the scroll notification
        // entirely.
        if (!vorga.phazeclient.implement.features.modules.other.Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        double absH = Math.abs(horizontal);
        double absV = Math.abs(vertical);
        if (absH == 0.0 && absV == 0.0) {
            return;
        }
        // Vertical wins on ties because the standard mouse wheel only
        // emits the vertical axis - users associate "scrolling" with
        // up/down movement first. Shift-scroll on most platforms re-
        // routes the wheel to the horizontal axis, which is when the
        // ↔ shape is actually expected.
        int shape = absV >= absH ? SHAPE_VRESIZE : SHAPE_HRESIZE;
        scrollOverrideShape = shape;
        scrollOverrideUntilNanos = System.nanoTime() + SCROLL_OVERRIDE_NANOS;
        // Apply right now so the user sees the resize cursor on the
        // very same wheel tick. endFrame will reapply if anything
        // overwrites it before the next frame.
        applyShape(shape);
    }

    /**
     * Commit the most-recent request to GLFW. Called at the end of
     * {@code Screen.render}; if the {@code Dynamic Cursor} animation
     * setting is off, callers should pass {@code false} for
     * {@code dynamicEnabled} so we restore the system default and
     * stop interfering.
     */
    public static void endFrame(boolean dynamicEnabled) {
        if (!screenActive) {
            return;
        }
        screenActive = false;
        int target;
        if (!dynamicEnabled) {
            target = SHAPE_ARROW;
            // Drop any pending overrides so the cursor doesn't
            // resurrect a resize shape the moment the user re-enables
            // the toggle.
            scrollOverrideShape = 0;
            scrollOverrideUntilNanos = 0L;
            dragOverrideShape = 0;
        } else if (dragOverrideShape != 0) {
            // Drag pin beats everything: the user is physically
            // holding the mouse button on a slider/draggable widget
            // and the resize shape must stay glued to the cursor for
            // the entire press → drag → release arc, including pauses
            // mid-drag where mouseDragged stops firing.
            target = dragOverrideShape;
        } else if (scrollOverrideShape != 0 && System.nanoTime() < scrollOverrideUntilNanos) {
            // Active scroll gesture beats every hover request - the
            // user is in the middle of a wheel spin and the resize
            // arrow communicates "this is what's happening" until the
            // override timer expires.
            target = scrollOverrideShape;
        } else {
            // Either no scroll, or the window expired this frame -
            // either way fall back to the per-frame request so the
            // hovered widget reclaims the cursor immediately. Clearing
            // the fields on expiry keeps the next zero-delta event
            // (e.g. trackpad lift) from re-arming a stale shape.
            if (scrollOverrideShape != 0) {
                scrollOverrideShape = 0;
                scrollOverrideUntilNanos = 0L;
            }
            target = requestedShape;
        }
        applyShape(target);
    }

    /**
     * Force the cursor back to the system arrow regardless of state.
     * Called when a screen closes so the in-world cursor (which is
     * normally invisible / grabbed) can't inherit a stale hand/beam
     * shape if grabbing fails.
     */
    public static void forceArrow() {
        applyShape(SHAPE_ARROW);
        screenActive = false;
        scrollOverrideShape = 0;
        scrollOverrideUntilNanos = 0L;
        dragOverrideShape = 0;
    }

    private static void applyShape(int shape) {
        if (shape == lastAppliedShape) {
            return;
        }
        long handle = windowHandle();
        if (handle == 0L) {
            return;
        }
        long cursor = cursorFor(shape);
        // glfwSetCursor with NULL returns the OS default arrow, which
        // is exactly what we want when the requested shape's creation
        // failed (cursor == 0L) or the user just turned the feature
        // off.
        GLFW.glfwSetCursor(handle, cursor);
        lastAppliedShape = shape;
    }

    private static long cursorFor(int shape) {
        return switch (shape) {
            case SHAPE_HAND -> {
                if (handCursor == 0L) {
                    handCursor = GLFW.glfwCreateStandardCursor(SHAPE_HAND);
                }
                yield handCursor;
            }
            case SHAPE_BEAM -> {
                if (beamCursor == 0L) {
                    beamCursor = GLFW.glfwCreateStandardCursor(SHAPE_BEAM);
                }
                yield beamCursor;
            }
            case SHAPE_VRESIZE -> {
                if (vResizeCursor == 0L) {
                    vResizeCursor = GLFW.glfwCreateStandardCursor(SHAPE_VRESIZE);
                }
                yield vResizeCursor;
            }
            case SHAPE_HRESIZE -> {
                if (hResizeCursor == 0L) {
                    hResizeCursor = GLFW.glfwCreateStandardCursor(SHAPE_HRESIZE);
                }
                yield hResizeCursor;
            }
            default -> {
                if (arrowCursor == 0L) {
                    arrowCursor = GLFW.glfwCreateStandardCursor(SHAPE_ARROW);
                }
                yield arrowCursor;
            }
        };
    }

    private static long windowHandle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 0L;
        }
        return mc.getWindow().getHandle();
    }
}
