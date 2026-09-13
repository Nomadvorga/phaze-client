package vorga.phazeclient.api.system.nametag;

import net.minecraft.text.Text;

import java.util.IdentityHashMap;
import java.util.Map;

public class NametagBlurStorage {
    private static final int MAX_ENTRIES = 512;
    private static final Map<Text, Float> labelDistances = new IdentityHashMap<>();

    public static void recordLabelDistance(Text label, float distance) {
        if (label == null) {
            return;
        }
        if (labelDistances.size() >= MAX_ENTRIES) {
            labelDistances.clear();
        }
        labelDistances.put(label, distance);
    }

    public static float consumeLabelDistance(Text label) {
        Float distance = label == null ? null : labelDistances.remove(label);
        return distance == null ? Float.NaN : distance;
    }
}
