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
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;

/**
 * Hotbar overlay for {@link ItemHighlighter}. Walks all 9 hotbar
 * stacks at the tail of {@code renderHotbar} and fills each slot
 * whose stack matches a configured highlight family. Mirrors the
 * structure of {@link InGameHudMaceIndicatorMixin} verbatim.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudItemHighlighterMixin {

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarItemHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ItemHighlighter module = ItemHighlighter.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        PlayerEntity player = mc.player;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int centerX = screenW / 2;
        int itemY = screenH - 16 - 3;

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
