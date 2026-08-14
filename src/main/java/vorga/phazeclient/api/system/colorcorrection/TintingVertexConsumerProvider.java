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
    private final Map<RenderLayer, CachedConsumer> cache = new IdentityHashMap<>();

    public TintingVertexConsumerProvider(VertexConsumerProvider parent, WorldColorCorrectionController.Target target) {
        this.parent = parent;
        this.target = target;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        if (layer == null) {
            return parent.getBuffer(null);
        }
        // Immediate providers replace a layer's BufferBuilder after a flush.
        // Resolve the parent first and retain the wrapper only while its
        // delegate is still current (nametag blur can flush mid-entity).
        VertexConsumer parentConsumer = parent.getBuffer(layer);
        CachedConsumer cached = cache.get(layer);
        if (cached != null && cached.parent == parentConsumer) {
            return cached.tinted;
        }

        VertexConsumer tinted = createConsumer(parentConsumer);
        cache.put(layer, new CachedConsumer(parentConsumer, tinted));
        return tinted;
    }

    private VertexConsumer createConsumer(VertexConsumer parentConsumer) {
        if (SODIUM_LOADED) {
            VertexConsumer optimized = SodiumTintingVertexConsumer.create(parentConsumer, target);
            if (optimized != null) {
                return optimized;
            }
        }
        return new TintingVertexConsumer(parentConsumer, target);
    }

    private record CachedConsumer(VertexConsumer parent, VertexConsumer tinted) {
    }
}
