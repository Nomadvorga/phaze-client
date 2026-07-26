package vorga.phazeclient.implement.hitcolor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public interface OverlayReloadListener {
    /**
     * Registered overlay textures.
     *
     * <p>Copy-on-write for two reasons:
     * <ul>
     *   <li>{@link #registerOverlay} runs from the {@code OverlayTexture}
     *       constructor while {@link #event} is called from the entity
     *       render path. With a plain {@code ArrayList} an add during
     *       iteration throws {@link java.util.ConcurrentModificationException}
     *       mid-frame.</li>
     *   <li>{@link #event} is on a hot path, so it is iterated by index -
     *       {@code size()} / {@code get(int)} on a copy-on-write list are
     *       O(1) and allocate nothing, unlike an iterator.</li>
     * </ul>
     */
    List<OverlayReloadListener> listeners = new CopyOnWriteArrayList<>();

    void setColor();

    static void registerOverlay(OverlayReloadListener listener) {
        // Guard against double registration: the list used to only ever
        // grow, so every re-registered overlay added another instance that
        // event() had to walk on every call, and kept its NativeImage /
        // GL texture alive forever.
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
