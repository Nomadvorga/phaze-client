package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.hud.BossBarColorContext;

@Mixin(DrawContext.class)
public abstract class DrawContextBossBarMixin {
    @ModifyVariable(method = "drawTexturedQuad(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 1)
    private int phaze$bossBarVertexTint(int color) {
        return BossBarColorContext.applyTint(color);
    }
}
