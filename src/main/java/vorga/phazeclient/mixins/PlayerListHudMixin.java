package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.TabHud;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void phaze$renderPingAsNumber(DrawContext context, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        TabHud tabHud = TabHud.getInstance();
        if (!tabHud.isEnabled() || !tabHud.displayPingAsNumber.isValue()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        int ping = Math.max(0, entry.getLatency());
        int color = 0xFFFFFFFF;
        if (tabHud.dynamicPingColor.isValue()) {
            if (ping < 70) color = 0xFF55FF55;
            else if (ping < 150) color = 0xFFFFFF55;
            else if (ping < 300) color = 0xFFFFAA00;
            else color = 0xFFFF5555;
        }

        String text = String.valueOf(ping);
        int textWidth = client.textRenderer.getWidth(text);
        int textX = x + width - textWidth - 2;
        context.drawText(client.textRenderer, text, textX, y, color, tabHud.pingNumberShadow.isValue());
        ci.cancel();
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void phaze$styleOwnName(PlayerListEntry entry, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Text> cir) {
        TabHud tabHud = TabHud.getInstance();
        if (!tabHud.isEnabled() || !tabHud.highlightOwn.isValue()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        if (!entry.getProfile().getId().equals(client.player.getUuid())) {
            return;
        }

        MutableText styled = cir.getReturnValue().copy().formatted(Formatting.AQUA, Formatting.BOLD);
        cir.setReturnValue(styled);
    }
}

