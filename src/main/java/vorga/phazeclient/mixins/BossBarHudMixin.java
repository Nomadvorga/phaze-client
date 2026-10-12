package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.BossbarTweakerHud;

import java.util.Map;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {

    @Shadow
    Map<?, ?> bossBars;

    @Unique
    private boolean phaze$pushedMatrix = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$onRenderBossBarHead(DrawContext context, CallbackInfo ci) {
        this.phaze$pushedMatrix = false;
        BossbarTweakerHud module = BossbarTweakerHud.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        if (module.hideBossbar.isValue()) {
            ci.cancel();
            return;
        }

        if (this.bossBars == null || this.bossBars.isEmpty()) {
            return;
        }

        context.getMatrices().push();
        this.phaze$pushedMatrix = true;
        float s = module.scale.getValue();
        float offsetY = module.offsetY.getValue();
        float centerX = context.getScaledWindowWidth() / 2.0f;
        context.getMatrices().translate(centerX * (1.0f - s), offsetY, 0.0f);
        context.getMatrices().scale(s, s, 1.0f);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$onRenderBossBarReturn(DrawContext context, CallbackInfo ci) {
        if (this.phaze$pushedMatrix) {
            this.phaze$pushedMatrix = false;
            context.getMatrices().pop();
        }
    }
}
