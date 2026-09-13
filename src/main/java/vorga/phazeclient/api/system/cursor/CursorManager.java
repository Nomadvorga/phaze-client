package vorga.phazeclient.api.system.cursor;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class CursorManager {

    public static final int SHAPE_ARROW = GLFW.GLFW_ARROW_CURSOR;
    public static final int SHAPE_HAND = GLFW.GLFW_HAND_CURSOR;
    public static final int SHAPE_BEAM = GLFW.GLFW_IBEAM_CURSOR;

    public static final int SHAPE_NOT_ALLOWED = GLFW.GLFW_NOT_ALLOWED_CURSOR;

    public static final int SHAPE_MOVE = GLFW.GLFW_RESIZE_ALL_CURSOR;

    public static final int SHAPE_VRESIZE = GLFW.GLFW_VRESIZE_CURSOR;

    public static final int SHAPE_HRESIZE = GLFW.GLFW_HRESIZE_CURSOR;

    private static final long SCROLL_OVERRIDE_NANOS = 150L * 1_000_000L;

    private static long arrowCursor = 0L;
    private static long handCursor = 0L;
    private static long beamCursor = 0L;
    private static long notAllowedCursor = 0L;
    private static long moveCursor = 0L;
    private static long vResizeCursor = 0L;
    private static long hResizeCursor = 0L;

    private static int requestedShape = SHAPE_ARROW;

    private static int requestedPriority = 0;

    private static int lastAppliedShape = -1;

    private static boolean screenActive = false;

    private static int scrollOverrideShape = 0;

    private static long scrollOverrideUntilNanos = 0L;

    private static int dragOverrideShape = 0;

    private CursorManager() {}

    public static void beginFrame() {
        requestedShape = SHAPE_ARROW;
        requestedPriority = 0;
        screenActive = true;
    }

    public static void requestHand() {
        requestShape(SHAPE_HAND, 1);
    }

    public static void requestBeam() {
        requestShape(SHAPE_BEAM, 5);
    }

    public static void requestNotAllowed() {
        requestShape(SHAPE_NOT_ALLOWED, 4);
    }

    public static void requestMove() {
        requestShape(SHAPE_MOVE, 3);
    }

    public static void requestHorizontalResize() {
        requestShape(SHAPE_HRESIZE, 2);
    }

    public static void requestVerticalResize() {
        requestShape(SHAPE_VRESIZE, 2);
    }

    public static void beginDrag(int shape) {
        if (dragOverrideShape == shape) {
            return;
        }
        dragOverrideShape = shape;

        applyShape(shape);
    }

    public static void endDrag() {
        dragOverrideShape = 0;
    }

    public static void notifyScroll(double horizontal, double vertical) {
        double absH = Math.abs(horizontal);
        double absV = Math.abs(vertical);
        if (absH == 0.0 && absV == 0.0) {
            return;
        }

        int shape = absV >= absH ? SHAPE_VRESIZE : SHAPE_HRESIZE;
        scrollOverrideShape = shape;
        scrollOverrideUntilNanos = System.nanoTime() + SCROLL_OVERRIDE_NANOS;

        applyShape(shape);
    }

    public static void endFrame(boolean dynamicEnabled) {
        if (!screenActive) {
            return;
        }
        screenActive = false;
        int target;
        if (!dynamicEnabled) {
            target = SHAPE_ARROW;

            scrollOverrideShape = 0;
            scrollOverrideUntilNanos = 0L;
            dragOverrideShape = 0;
        } else if (dragOverrideShape != 0) {

            target = dragOverrideShape;
        } else if (scrollOverrideShape != 0 && System.nanoTime() < scrollOverrideUntilNanos) {

            target = scrollOverrideShape;
        } else {

            if (scrollOverrideShape != 0) {
                scrollOverrideShape = 0;
                scrollOverrideUntilNanos = 0L;
            }
            target = requestedShape;
        }
        applyShape(target);
    }

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
            case SHAPE_NOT_ALLOWED -> {
                if (notAllowedCursor == 0L) {
                    notAllowedCursor = GLFW.glfwCreateStandardCursor(SHAPE_NOT_ALLOWED);
                }
                yield notAllowedCursor;
            }
            case SHAPE_MOVE -> {
                if (moveCursor == 0L) {
                    moveCursor = GLFW.glfwCreateStandardCursor(SHAPE_MOVE);
                }
                yield moveCursor;
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

    private static void requestShape(int shape, int priority) {
        if (priority < requestedPriority) {
            return;
        }
        if (priority == requestedPriority && requestedShape == SHAPE_BEAM && shape != SHAPE_BEAM) {
            return;
        }
        requestedShape = shape;
        requestedPriority = priority;
    }

    private static long windowHandle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 0L;
        }
        return mc.getWindow().getHandle();
    }
}
