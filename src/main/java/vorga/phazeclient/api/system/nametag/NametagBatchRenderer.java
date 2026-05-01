package vorga.phazeclient.api.system.nametag;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class NametagBatchRenderer {
    private static final List<NametagBatchEntry> batchEntries = new ArrayList<>();
    private static boolean batchingEnabled = false;

    public static class NametagBatchEntry {
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final int color;
        public final Matrix4f matrix;
        public final float quality;

        public NametagBatchEntry(float x, float y, float width, float height, int color, Matrix4f matrix, float quality) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.matrix = new Matrix4f(matrix);
            this.quality = quality;
        }
    }

    public static void startBatching() {
        batchingEnabled = true;
        batchEntries.clear();
    }

    public static void endBatching() {
        batchingEnabled = false;
    }

    public static void addBlurRect(float x, float y, float width, float height, int color, Matrix4f matrix, float quality) {
        if (batchingEnabled) {
            batchEntries.add(new NametagBatchEntry(x, y, width, height, color, matrix, quality));
        }
    }

    public static List<NametagBatchEntry> getBatchEntries() {
        return batchEntries;
    }

    public static boolean isBatchingEnabled() {
        return batchingEnabled;
    }

    public static void clear() {
        batchEntries.clear();
    }
}
