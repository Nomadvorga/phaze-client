package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientMouseInvoker {
    @Invoker("doAttack")
    boolean phaze$invokeDoAttack();

    @Invoker("doItemUse")
    void phaze$invokeDoItemUse();

    @Invoker("handleBlockBreaking")
    void phaze$invokeHandleBlockBreaking(boolean breaking);
}
