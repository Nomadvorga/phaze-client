package vorga.phazeclient.implement.hitcolor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public interface OverlayReloadListener {

    List<OverlayReloadListener> listeners = new CopyOnWriteArrayList<>();

    void setColor();

    static void registerOverlay(OverlayReloadListener listener) {

        if (listener == null || listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
    }

    static void unregisterOverlay(OverlayReloadListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    static void event() {
        for (int i = 0, n = listeners.size(); i < n; i++) {
            listeners.get(i).setColor();
        }
    }
}
