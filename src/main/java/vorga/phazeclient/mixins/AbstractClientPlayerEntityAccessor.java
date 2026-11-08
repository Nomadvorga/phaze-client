package vorga.phazeclient.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractClientPlayerEntity.class)
public interface AbstractClientPlayerEntityAccessor {

    @Accessor("lastVelocity")
    Vec3d phaze$getLastVelocity();

    @Accessor("lastVelocity")
    void phaze$setLastVelocity(Vec3d velocity);
}
