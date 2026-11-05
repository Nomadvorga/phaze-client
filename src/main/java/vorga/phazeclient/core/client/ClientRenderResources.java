package vorga.phazeclient.core.client;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.hud.HudRenderLifecycle;
import vorga.phazeclient.api.system.snapshot.CardSnapshotCache;
import vorga.phazeclient.implement.features.modules.other.PackPreviewRenderer;

/** GPU cache lifecycle lives on the client thread, never in a JVM shutdown hook. */
public final class ClientRenderResources {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeRenderResources");

    private ClientRenderResources() {}

    public static void close(MinecraftClient client) {
        if (client == null) return;
        clear(client);
        client.execute(() -> release("MSDF text buffers", MsdfRenderer::close));
    }

    public static void clear(MinecraftClient client) {
        if (client == null) return;
        client.execute(() -> {
            release("snapshots", CardSnapshotCache::clearAll);
            release("HUD and 2D caches", HudRenderLifecycle::cleanup);
            release("world overlays", WorldOverlayRenderer::clear);
            release("pack previews", () -> PackPreviewRenderer.clear(client));
        });
    }

    private static void release(String name, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException failure) {
            // One failed cache must not prevent all the other owners from releasing theirs.
            LOG.warn("Failed to release {}", name, failure);
        }
    }
}
