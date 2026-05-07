package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.hud.TabHud;
import vorga.phazeclient.implement.features.modules.other.Animations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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

    /**
     * Push a translation matrix at the start of the tab list render so the
     * whole list slides up/down. The offset itself is computed once per
     * frame inside {@link InGameHudTabSlideMixin} so the open and close
     * branches share the same interpolated value.
     *
     * We also flush prior batches before pushing and again at the matching
     * pop so DrawContext doesn't merge our translated text/icons with HUDs
     * rendered immediately before/after the tab list - that's the source
     * of the "text seems to lag behind the background" effect, since text
     * runs through a deferred font batch that previously inherited a stale
     * matrix.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$pushTabSlide(DrawContext context, int scaledWindowWidth,
                                    net.minecraft.scoreboard.Scoreboard scoreboard,
                                    net.minecraft.scoreboard.ScoreboardObjective objective,
                                    CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            return;
        }
        // Flush prior batches so they render with normal (1,1,1,1) shader
        // color, not our faded one.
        context.draw();

        float offsetY = module.currentTabSlideOffset();
        context.getMatrices().push();
        if (offsetY != 0.0F) {
            context.getMatrices().translate(0.0F, offsetY, 0.0F);
        }

        // Tint subsequent draws with our fade alpha. This works for both
        // the rectangle/texture batch and the font batch because every
        // draw layer multiplies the fragment alpha by RenderSystem's
        // current shader color. Reset in the RETURN inject below.
        float alpha = module.currentTabAlpha();
        if (alpha < 1.0F) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$popTabSlide(DrawContext context, int scaledWindowWidth,
                                   net.minecraft.scoreboard.Scoreboard scoreboard,
                                   net.minecraft.scoreboard.ScoreboardObjective objective,
                                   CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            return;
        }
        // Flush our (potentially translated and tinted) draws BEFORE we
        // restore the matrix and shader color - DrawContext snapshots both
        // at submit time, but the GL state at flush time still affects
        // anything that pulls from it (e.g. text glyph batching that
        // re-reads ShaderColor). Flushing first guarantees this batch goes
        // out with the faded alpha and the next HUD element starts clean.
        context.draw();
        context.getMatrices().pop();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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

        Text original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        MutableText styled = original.copy().formatted(Formatting.AQUA, Formatting.BOLD);
        cir.setReturnValue(styled);
    }



}

