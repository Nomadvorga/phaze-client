package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ServerListPlus;

/**
 * Augments the vanilla server-list entry render with the extras
 * the {@link ServerListPlus} module exposes: tier-coloured numeric
 * ping, version line under the server name, and optional raw MOTD
 * with legacy formatting preserved.
 *
 * <p>Hook is TAIL of
 * {@code MultiplayerServerListWidget.ServerEntry.render(...)}, so
 * vanilla has already painted the icon + name + MOTD + player
 * count strip, and we layer our additions on top. Skip cleanly
 * when the module is disabled or the server has no metadata yet
 * (still pinging).
 */
@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryServerListPlusMixin {

    @Shadow
    @Final
    private ServerInfo server;

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawExtras(DrawContext context, int index, int y, int x,
                                  int entryWidth, int entryHeight, int mouseX, int mouseY,
                                  boolean hovered, float tickDelta, CallbackInfo ci) {
        ServerListPlus module = ServerListPlus.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (server == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        // Numeric ping. Vanilla shows only the bars icon; we add a
        // small "{ping}ms" string just left of the icon column so
        // the user can read the latency tier without hovering.
        if (module.showPingNumber.isValue() && server.ping >= 0L) {
            int color = module.colorPing.isValue()
                    ? module.colorForPing(server.ping)
                    : 0xFFFFFFFF;
            String pingText = server.ping + "ms";
            int textW = mc.textRenderer.getWidth(pingText);
            // Right-align inside the entry, just left of where vanilla
            // paints the bars icon (which sits at x + entryWidth - 15).
            int pingX = x + entryWidth - 25 - textW;
            int pingY = y + 4;
            context.drawTextWithShadow(mc.textRenderer, pingText, pingX, pingY, color);
        }

        // Version line. Vanilla packs the version into the upper-
        // right corner only when the protocol mismatches the
        // client; we always show it so the user can scan their
        // list at a glance.
        if (module.showVersion.isValue()) {
            String version = server.version != null ? server.version.getString() : "";
            if (!version.isEmpty()) {
                int versionY = module.compactRows.isValue() ? y + 12 : y + 14;
                context.drawTextWithShadow(mc.textRenderer, "§7" + version,
                        x + 35, versionY, 0xFFAAAAAA);
            }
        }

        // Raw MOTD: vanilla strips legacy formatting from the second
        // line; we re-render it raw if the user wants to preserve
        // server colors / formatting.
        if (module.showRawMotd.isValue() && server.label != null) {
            String motd = server.label.getString();
            if (!motd.isEmpty()) {
                int motdY = module.compactRows.isValue() ? y + 22 : y + 26;
                // Truncate to fit within the entry width minus the
                // ping/icon column on the right.
                int maxW = entryWidth - 60;
                String trimmed = mc.textRenderer.trimToWidth(motd, maxW);
                context.drawTextWithShadow(mc.textRenderer, trimmed,
                        x + 35, motdY, 0xFFFFFFFF);
            }
        }
    }
}
