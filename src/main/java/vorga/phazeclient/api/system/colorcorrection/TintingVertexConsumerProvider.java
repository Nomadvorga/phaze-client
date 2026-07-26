package vorga.phazeclient.api.system.colorcorrection;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.fabricmc.loader.api.FabricLoader;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TintingVertexConsumerProvider implements VertexConsumerProvider {
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private final VertexConsumerProvider parent;
    private final WorldColorCorrectionController.Target target;
    private final Map<RenderLayer, VertexConsumer> cache = new IdentityHashMap<>();

    public TintingVertexConsumerProvider(VertexConsumerProvider parent, WorldColorCorrectionController.Target target) {
        this.parent = parent;
        this.target = target;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        if (layer == null) {
            return parent.getBuffer(null);
        }
        return cache.computeIfAbsent(layer, currentLayer -> createConsumer(parent.getBuffer(currentLayer)));
    }

    private VertexConsumer createConsumer(VertexConsumer parentConsumer) {
        // The Sodium fast path is parked under port-staging/ along with the
        // rest of the Sodium compat: this build targets vanilla plus Fabric
        // API only, and the Sodium vertex API it needs is not on the
        // compile classpath. The generic consumer produces identical
        // colours, just without the bulk-transform shortcut.
        return new TintingVertexConsumer(parentConsumer, target);
    }
}
