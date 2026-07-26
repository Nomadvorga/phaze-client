package vorga.phazeclient.mixins;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OutlineVertexConsumerProvider.class)
public interface OutlineVertexConsumerProviderAccessor {

    @Accessor("parent")
    VertexConsumerProvider.Immediate phaze$getParent();
}
