package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;

/**
 * Inventory-screen counterpart to
 * {@link InGameHudItemHighlighterMixin}. Walks the trailing 36
 * slots (player inventory + hotbar) of any handled screen and
 * fills each matching slot. Container slots (chests, shulkers)
 * are skipped by construction since the iteration starts at
 * {@code totalSlots - 36}.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenItemHighlighterMixin {

    @Shadow protected int x;
    @Shadow protected int y;

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$drawHandledItemHighlights(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ItemHighlighter module = ItemHighlighter.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        ScreenHandler handler = self.getScreenHandler();
        if (handler == null) {
            return;
        }
        int totalSlots = handler.slots.size();
        int playerInvStart = totalSlots - 36;
        if (playerInvStart < 0) {
            return;
        }

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            int color = module.colorForStack(stack);
            if ((color & 0xFF000000) == 0) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            context.fill(sx, sy, sx + 16, sy + 16, color);
        }

        context.draw();
    }
}
