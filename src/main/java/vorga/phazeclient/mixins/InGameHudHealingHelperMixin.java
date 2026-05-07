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
import vorga.phazeclient.implement.features.modules.other.HealingHelper;

/**
 * Hotbar counterpart to {@link HandledScreenHealingHelperMixin}. Hooks the
 * tail of {@link InGameHud#renderHotbar} and paints the
 * {@link HealingHelper}'s pulsing fill over each hotbar slot whose stack
 * the module says should be highlighted right now.
 *
 * <p>The slot layout math mirrors vanilla's own
 * {@code renderHotbarItem} loop in 1.21.4: the hotbar widget is 182px
 * wide, centered, sitting 22px above the bottom of the scaled window;
 * slot {@code n}'s 16x16 item lives at
 * {@code (centerX - 90 + n * 20 + 2, screenH - 16 - 3)}. We use the same
 * formula here so the fill aligns exactly with the rendered item sprite,
 * regardless of GUI scale.
 *
 * <p>An explicit batch flush bookends the fills so they land on top of
 * the hotbar background AND on top of the items themselves, instead of
 * piggybacking on whatever batch the next frame happens to commit.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudHealingHelperMixin {

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarHealingHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HealingHelper module = HealingHelper.getInstance();
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
        // fills definitely render on top.
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
