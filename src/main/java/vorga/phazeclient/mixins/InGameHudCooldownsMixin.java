package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.Cooldowns;

/**
 * Paints the remaining cooldown seconds as a small number directly
 * above the hotbar slot icon when an item there is on use-cooldown.
 *
 * <h3>Why hook renderHotbarItem instead of renderHotbar</h3>
 * The hotbar method paints all 9 slots in a loop and would force us
 * to recompute the same x/y the loop already computed. Hooking the
 * per-slot helper lets us reuse the call's own x / y / stack /
 * player arguments verbatim, which:
 * <ol>
 *   <li>tracks any future shifts in vanilla's slot layout (e.g.
 *       camera-relative offsets or accessibility scaling) without
 *       us having to re-derive them;</li>
 *   <li>covers offhand-via-renderHotbarItem on those layouts that
 *       still call this helper for the off-hand companion slot
 *       (the offhand path also funnels through this method).</li>
 * </ol>
 *
 * <h3>Numeric source</h3>
 * Vanilla's {@link ItemCooldownManager} stores per-group entries
 * with a {@code startTick / endTick} pair relative to the manager's
 * own {@code tick} counter. {@code getCooldownProgress} returns the
 * normalised 1.0 -&gt; 0.0 ramp the cooldown overlay pie also uses.
 * We multiply that progress by the entry's total duration to recover
 * the remaining ticks, then divide by 20 to get seconds. Reading the
 * raw entry instead of inferring duration from progress alone gives
 * us the correct denominator regardless of how long the cooldown
 * has been running.
 *
 * <h3>Disabled / non-cooldown fast path</h3>
 * The first branch returns immediately if the module is off, the
 * stack is empty, or the manager reports no active cooldown for
 * this group. Cost on a typical player not on cooldown is one map
 * lookup per slot per frame - cheap enough to leave inline.
 *
 * <h3>Attribution</h3>
 * Cooldown detection mirrors the
 * {@code padej.soup.implement.features.draggables.CoolDowns}
 * upstream widget. The Phaze port differs only in the surface (an
 * over-slot number instead of a draggable list); the
 * {@code ItemCooldownManager} lookup is identical.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudCooldownsMixin {

    @Inject(
            method = "renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            at = @At("TAIL")
    )
    private void phaze$drawCooldownNumber(
            DrawContext context,
            int x,
            int y,
            RenderTickCounter tickCounter,
            PlayerEntity player,
            ItemStack stack,
            int seed,
            CallbackInfo ci
    ) {
        Cooldowns module = Cooldowns.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        if (stack == null || stack.isEmpty() || player == null) {
            return;
        }
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (manager == null) {
            return;
        }
        float tickDelta = tickCounter.getTickDelta(false);
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0.0F) {
            return;
        }

        // Recover remaining ticks: progress is the normalised
        // remaining fraction of the (endTick - startTick) window, but
        // we actually only need the absolute (endTick - currentTick)
        // delta in ticks - which is exactly what vanilla's
        // getCooldownProgress numerator computes anyway. Reading
        // endTick + the manager's own tick counter avoids needing
        // access to startTick (which is access-widened independently).
        Identifier groupId = manager.getGroup(stack);
        ItemCooldownManager.Entry entry = manager.entries.get(groupId);
        if (entry == null) {
            return;
        }
        float remainingTicks = entry.endTick - (manager.tick + tickDelta);
        if (remainingTicks <= 0.0F) {
            return;
        }
        float remainingSeconds = remainingTicks / 20.0F;

        String text = module.formatSeconds(remainingSeconds);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) {
            return;
        }
        int color = module.colorForProgress(progress);
        // Place the number just above the slot, horizontally centred
        // on the icon. y - 9 sits the baseline one row above the
        // 16x16 slot, with a 1 px gap before the slot border.
        int textWidth = mc.textRenderer.getWidth(text);
        int drawX = x + 8 - textWidth / 2;
        int drawY = y - 9;
        context.drawText(mc.textRenderer, text, drawX, drawY, color, module.textShadow.isValue());
    }
}
