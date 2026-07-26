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
        if (SODIUM_LOADED) {
            VertexConsumer optimized = SodiumTintingVertexConsumer.create(parentConsumer, target);
            if (optimized != null) {
                return optimized;
            }
        }
        return new TintingVertexConsumer(parentConsumer, target);
    }
}
