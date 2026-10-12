package vorga.phazeclient.mixins;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cuboids")
    List<ModelPart.Cuboid> phaze$getCuboids();

    @Mutable
    @Accessor("cuboids")
    void phaze$setCuboids(List<ModelPart.Cuboid> cuboids);
}
