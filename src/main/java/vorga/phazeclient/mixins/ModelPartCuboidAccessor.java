package vorga.phazeclient.mixins;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.Cuboid.class)
public interface ModelPartCuboidAccessor {
    @Accessor("sides")
    ModelPart.Quad[] phaze$getSides();

    @Mutable
    @Accessor("sides")
    void phaze$setSides(ModelPart.Quad[] sides);
}
