package vorga.phazeclient.api.system.cursor;

public final class HudCursorRelay {
    private static int deferredShape = 0;
    private static int deferredPriority = 0;
    private static boolean deferredActive = false;

    private HudCursorRelay() {
    }

    public static void reset() {
        deferredShape = 0;
        deferredPriority = 0;
        deferredActive = false;
    }

    public static void defer(int shape, int priority) {
        if (!deferredActive || priority >= deferredPriority) {
            deferredShape = shape;
            deferredPriority = priority;
            deferredActive = true;
        }
    }

    public static void apply() {
        if (!deferredActive) {
            return;
        }
        switch (deferredShape) {
            case CursorManager.SHAPE_HAND -> CursorManager.requestHand();
            case CursorManager.SHAPE_MOVE -> CursorManager.requestMove();
            case CursorManager.SHAPE_HRESIZE -> CursorManager.requestHorizontalResize();
            case CursorManager.SHAPE_VRESIZE -> CursorManager.requestVerticalResize();
            case CursorManager.SHAPE_NOT_ALLOWED -> CursorManager.requestNotAllowed();
            default -> {
            }
        }
    }
}
