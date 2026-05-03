package vorga.phazeclient.implement.hitcolor;

import java.util.ArrayList;
import java.util.List;

public interface OverlayReloadListener {
    List<OverlayReloadListener> listeners = new ArrayList();

    void setColor();

    static void registerOverlay(OverlayReloadListener listener) {
        listeners.add(listener);
    }

    static void event() {
        for(OverlayReloadListener listener : listeners) {
            listener.setColor();
        }
    }
}
