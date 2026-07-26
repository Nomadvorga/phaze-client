package vorga.phazeclient.mixins;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.SequencedMap;
import java.util.Map;

@Mixin(VertexConsumerProvider.Immediate.class)
public interface VertexConsumerProviderImmediateAccessor {
    @Accessor("layerBuffers")
    SequencedMap<RenderLayer, BufferAllocator> phaze$getLayerBuffers();

    @Accessor("pending")
    Map<RenderLayer, BufferBuilder> phaze$getPendingLayers();
}
