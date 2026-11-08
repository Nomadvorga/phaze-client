package vorga.phazeclient.api.system.shape.batched;

/** Render-thread counters for GUI draw submissions; read by benchmarks, reset by the caller. */
public final class GuiDrawStats {
    public static int rectDraws;
    public static int msdfDraws;
    public static int stateSnapshots;
    public static int bufferDraws;
    public static int scissorChanges;
    public static int batchInstances;

    private GuiDrawStats() {
    }

    public static void reset() {
        rectDraws = 0;
        msdfDraws = 0;
        stateSnapshots = 0;
        bufferDraws = 0;
        scissorChanges = 0;
        batchInstances = 0;
    }
}
