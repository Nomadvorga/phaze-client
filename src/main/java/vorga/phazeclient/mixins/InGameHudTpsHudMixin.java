package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.TpsHud;

/**
 * Renders {@link TpsHud}'s number + history bar at the module's
 * drag position. Direct paint (not batched) because the bar
 * updates every 200ms and would either flicker or stale-cache in
 * the throttled HUD pipeline.
 *
 * <h3>Bar layout</h3>
 * 60 px wide (1 px per sample), 16 px tall. Each bar's height
 * scales relative to 20 TPS (the vanilla server cap). Bar color
 * comes from the same {@link #colorForTps} tier function used for
 * the numeric reading so a "good" sample reads green and a "bad"
 * sample reads red regardless of which widget is showing it.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudTpsHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawTpsHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        TpsHud module = TpsHud.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || mc.textRenderer == null) return;
        if (mc.options.hudHidden) return;

        int x = Math.round(module.getHudX());
        int y = Math.round(module.getHudY());
        float scale = module.getHudScale();

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1.0F);

        int cursorY = 0;
        if (module.showNumeric.isValue()) {
            double tps = module.getSmoothedTps();
            String text = String.format("TPS: %.1f", tps);
            int color = module.colorByTps.isValue() ? colorForTps(tps) : 0xFFFFFFFF;
            int textW = mc.textRenderer.getWidth(text);
            // Translucent backdrop for legibility - same vibe as
            // vanilla's getTextBackgroundColor so the HUD reads
            // alongside the chat list.
            context.fill(-2, cursorY - 1, textW + 2, cursorY + 9, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, text, 0, cursorY, color);
            cursorY += 12;
        }

        if (module.showBar.isValue()) {
            int barW = TpsHud.SAMPLE_WINDOW;
            int barH = 16;
            context.fill(-2, cursorY - 1, barW + 2, cursorY + barH + 1, 0x90000000);
            // Walk the deque from oldest to newest. Deque is
            // addFirst-ordered so iteration goes newest-first; we
            // index from the right edge so the most recent sample
            // sits at the right of the bar (timeline reads
            // left-to-right past).
            int i = barW - 1;
            for (Double sample : module.getSamples()) {
                if (i < 0) break;
                double s = sample == null ? 0.0 : sample;
                int h = (int) Math.max(1, Math.round((s / 20.0) * barH));
                int color = colorForTps(s);
                context.fill(i, cursorY + barH - h, i + 1, cursorY + barH, color);
                i--;
            }
        }

        context.getMatrices().pop();
    }

    /**
     * TPS color tiers. Green when the server is keeping up,
     * yellow during minor lag spikes, red when it's clearly
     * dropping ticks. Threshold values picked to match what most
     * server admins consider "healthy" (>=18) vs "concerning"
     * (15..18) vs "broken" (<15).
     */
    private static int colorForTps(double tps) {
        if (tps >= 18.0) return 0xFF55FF55;
        if (tps >= 15.0) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
