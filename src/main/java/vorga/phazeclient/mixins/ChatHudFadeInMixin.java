package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Multiplies vanilla's chat-line opacity by an additional fade-in factor for
 * the first few ticks after a message arrives. Vanilla calls
 * {@code getMessageOpacityMultiplier(int)} once per visible line every frame
 * to compute its alpha (this is the same hook it uses for fade-OUT of old
 * messages), so injecting at RETURN gives us a clean per-line ramp without
 * touching the render method's locals.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudFadeInMixin {

    @Inject(method = "getMessageOpacityMultiplier", at = @At("RETURN"), cancellable = true)
    private static void phaze$applyFadeIn(int messageAge, CallbackInfoReturnable<Double> cir) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isChatFadeEnabled()) {
            return;
        }
        float fadeIn = module.computeChatFadeInMultiplier(messageAge);
        if (fadeIn >= 1.0F) {
            return;
        }
        Double original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        cir.setReturnValue(original * fadeIn);
    }
}
