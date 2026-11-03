package vorga.phazeclient.mixins;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderLayer.MultiPhaseParameters.class)
public interface OverlayLayerParametersAccessor {
    @Accessor("texture") RenderPhase.TextureBase phaze$getTexture();
}
