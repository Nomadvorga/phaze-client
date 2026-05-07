package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.FastExp;

/**
 * Resets {@code MinecraftClient.itemUseCooldown} to 0 at the start of every
 * input-event tick while the user is holding an experience bottle and the
 * Fast Exp module is enabled. Vanilla normally sets this counter to 4 after
 * each successful right-click and only fires {@code doItemUse()} when the
 * counter reaches 0, throttling held-right-click to once every 4 ticks.
 * Forcing it to 0 here lets the vanilla input check fire {@code doItemUse()}
 * on every single tick, throwing bottles as fast as the server tick rate
 * allows.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientFastExpMixin {

    @Shadow
    private int itemUseCooldown;

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void phaze$bypassExperienceBottleCooldown(CallbackInfo ci) {
        if (FastExp.shouldFastThrow()) {
            this.itemUseCooldown = 0;
        }
    }
}
