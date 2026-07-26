package vorga.phazeclient.api.system.nametag;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class NametagBlurStorage {
    private static final List<NametagBlurRect> blurRects = new ArrayList<>();
    private static boolean enabled = false;

    public static void setEnabled(boolean enabled) {
        NametagBlurStorage.enabled = enabled;
        if (!enabled) {
            blurRects.clear();
        }
    }

    public static void addBlurRect(float x, float y, float width, float height, float quality, Matrix4f matrix) {
        if (enabled) {
            blurRects.add(new NametagBlurRect(x, y, width, height, quality, new Matrix4f(matrix)));
        }
    }

    public static List<NametagBlurRect> getBlurRects() {
        return blurRects;
    }

    public static void clear() {
        blurRects.clear();
    }

    public record NametagBlurRect(float x, float y, float width, float height, float quality, Matrix4f matrix) {}
}
