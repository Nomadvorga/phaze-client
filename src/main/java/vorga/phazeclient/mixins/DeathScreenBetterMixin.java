package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.BetterDeathScreen;

/**
 * Paints {@link BetterDeathScreen}'s stats panel on top of the
 * vanilla death screen. Render-only mixin - we never touch the
 * spectate / respawn buttons, so the user explicitly stated
 * spectate-skip preference is preserved.
 *
 * <h3>Layout</h3>
 * Vanilla uses the centre half of the screen for the title/buttons;
 * we drop our panel in the lower-right corner where it doesn't
 * collide. Each line gets a translucent backdrop for readability
 * against any post-death scene (e.g. a lava pool).
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenBetterMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawStats(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BetterDeathScreen module = BetterDeathScreen.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;
        BetterDeathScreen.DeathRecord last = module.getLastDeath();

        DeathScreen self = (DeathScreen) (Object) this;
        int screenW = self.width;
        int screenH = self.height;

        // Anchor the panel at lower-right, leaving a 12 px margin.
        // Each line is 12 px tall, so a 6-line panel needs ~80 px
        // of height. Left edge floats based on widest text width.
        int lineH = 12;
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (last != null) {
            if (module.showKiller.isValue()) {
                lines.add("Killed by: " + BetterDeathScreen.summarise(last));
            }
        }
        if (module.showSessionDeaths.isValue()) {
            lines.add("Session deaths: " + module.getSessionDeaths());
            if (last != null && last.deltaSinceLastMs() > 0) {
                lines.add("Time since last death: " + formatDuration(last.deltaSinceLastMs()));
            }
        }
        if (module.showDeathHistory.isValue() && !module.getHistory().isEmpty()) {
            lines.add("Recent:");
            int shown = 0;
            for (BetterDeathScreen.DeathRecord r : module.getHistory()) {
                if (shown >= 5) break;
                lines.add("  - " + BetterDeathScreen.summarise(r));
                shown++;
            }
        }
        if (lines.isEmpty()) return;

        int maxW = 0;
        for (String l : lines) {
            int w = mc.textRenderer.getWidth(l);
            if (w > maxW) maxW = w;
        }

        int panelW = maxW + 8;
        int panelH = lines.size() * lineH + 6;
        int panelX = screenW - panelW - 12;
        int panelY = screenH - panelH - 12;

        // Backdrop: dark fill, subtle border so the panel reads as
        // a contained UI element rather than floating text.
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xB0000000);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFFAAAAAA); // top border
        context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFFAAAAAA); // bottom

        int x = panelX + 4;
        int y = panelY + 4;
        for (String l : lines) {
            context.drawTextWithShadow(mc.textRenderer, l, x, y, 0xFFFFFFFF);
            y += lineH;
        }
    }

    /**
     * Format a millisecond delta as {@code Hh Mm Ss}, dropping
     * leading zero-units so 73 seconds shows as "1m 13s" not
     * "0h 1m 13s". Sub-second precision dropped intentionally - the
     * panel is informational, not a stopwatch.
     */
    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
