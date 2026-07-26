package vorga.phazeclient.mixins;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.memory.ObjectPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float invokeGetFov(Camera camera, float tickDelta, boolean changingFov);

    /**
     * 1.21.11 renamed {@code net.minecraft.client.util.Pool} to
     * {@code net.minecraft.client.util.memory.ObjectPool}, which is what
     * {@code PostEffectProcessor.render(Framebuffer, ObjectAllocator)}
     * consumes.
     */
    @Accessor
    ObjectPool getPool();
}
