package vorga.phazeclient.mixins;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OutlineVertexConsumerProvider.class)
public interface OutlineVertexConsumerProviderAccessor {

    // 1.21.11 renamed OutlineVertexConsumerProvider's backing immediate
    // provider from "parent" to "plainDrawer" (same type,
    // VertexConsumerProvider.Immediate). The accessor method name is kept as
    // phaze$getParent() because EntityRendererMixin calls it.
    @Accessor("plainDrawer")
    VertexConsumerProvider.Immediate phaze$getParent();
}
