package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.MaceIndicator;

/**
 * Hotbar counterpart to {@link HandledScreenMaceIndicatorMixin}.
 * Mirrors {@link InGameHudHealingHelperMixin} verbatim except for the
 * module reference - kept as a separate mixin (rather than folded into
 * the HealingHelper one) so each highlighter module owns its own
 * dispatch path. Disabling MaceIndicator at the module level is enough
 * to short-circuit this whole pass via the early return on
 * {@link MaceIndicator#isEnabled()}.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMaceIndicatorMixin {

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarMaceHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MaceIndicator module = MaceIndicator.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        PlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int centerX = screenW / 2;
        int itemY = screenH - 16 - 3;

        // Commit anything queued by vanilla's hotbar pass first so our
        // fills definitely render on top of the item sprites instead
        // of getting lost under the next batch the InGameHud submits.
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int n = 0; n < 9; n++) {
            ItemStack stack = player.getInventory().main.get(n);
            int color = module.colorForStack(stack);
            if ((color & 0xFF000000) == 0) {
                continue;
            }
            int itemX = centerX - 90 + n * 20 + 2;
            context.fill(itemX, itemY, itemX + 16, itemY + 16, color);
        }

        context.draw();
    }
}
