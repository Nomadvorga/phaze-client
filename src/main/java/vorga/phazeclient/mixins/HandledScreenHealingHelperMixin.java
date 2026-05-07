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
import vorga.phazeclient.implement.features.modules.other.HealingHelper;

/**
 * Paints the {@link HealingHelper}'s pulsing colored fill over inventory
 * slots inside any {@link HandledScreen}. Hooks at the very end of
 * {@code render} so the highlight overlays whatever vanilla just drew
 * (slot backgrounds, items, hover glow, dragged stack), making the flash
 * unambiguously visible regardless of cursor position.
 *
 * <p>The iteration is restricted to the trailing 36 slots which are
 * always the player inventory + hotbar across every screen handler
 * vanilla ships - this matches the upstream
 * {@code ItemHighlighterModule} reference and avoids spuriously
 * highlighting matching items inside containers (e.g. a healing potion
 * sitting inside a chest).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenHealingHelperMixin {

    @Shadow protected int x;
    @Shadow protected int y;

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$drawHealingHighlights(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HealingHelper module = HealingHelper.getInstance();
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

        // Flush any pending GUI batch so our fills land on top of items
        // instead of getting batched underneath them in the same draw.
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
