package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.hud.TabEditor;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.NickHider;

import java.util.Collection;
import java.util.List;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Unique
    private boolean phaze$tabTransformPushed = false;
    @Unique
    private List<PlayerListEntry> phaze$rowEntries;
    @Unique
    private int phaze$rowIndex;

    @Inject(method = "collectPlayerEntries", at = @At("RETURN"), cancellable = true)
    private void phaze$moveSelfToTop(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        Collection<PlayerListEntry> listed = client.player == null ? null
                : client.player.networkHandler.getListedPlayerListEntries();
        List<PlayerListEntry> shown = TabEditor.getInstance().moveSelfFirst(cir.getReturnValue(), listed);
        phaze$rowEntries = shown;
        if (shown != cir.getReturnValue()) {
            cir.setReturnValue(shown);
        }
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;header:Lnet/minecraft/text/Text;", opcode = Opcodes.GETFIELD)
    )
    private Text phaze$disableHeader(Text header) {
        return TabEditor.getInstance().hidesHeader() ? null : header;
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;footer:Lnet/minecraft/text/Text;", opcode = Opcodes.GETFIELD)
    )
    private Text phaze$disableFooter(Text footer) {
        return TabEditor.getInstance().hidesFooter() ? null : footer;
    }

    @ModifyExpressionValue(
            method = "render",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;isInSingleplayer()Z"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;isEncrypted()Z")
            }
    )
    private boolean phaze$playerHeads(boolean original) {
        return original && TabEditor.getInstance().showsPlayerHeads();
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "CONSTANT", args = "intValue=13"))
    private int phaze$pingSlotWidth(int original) {
        MinecraftClient client = MinecraftClient.getInstance();
        return TabEditor.getInstance().pingSlotWidth(original, client.textRenderer);
    }

    // Vanilla fills the header, the list and the footer with this constant, in that order.
    @ModifyExpressionValue(method = "render", at = @At(value = "CONSTANT", args = "intValue=-2147483648", ordinal = 0))
    private int phaze$headerColor(int original) {
        return TabEditor.getInstance().headerColor(original);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "CONSTANT", args = "intValue=-2147483648", ordinal = 1))
    private int phaze$backgroundColor(int original) {
        return TabEditor.getInstance().backgroundColor(original);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "CONSTANT", args = "intValue=-2147483648", ordinal = 2))
    private int phaze$footerColor(int original) {
        return TabEditor.getInstance().footerColor(original);
    }

    /** The third fill is the per-row strip; rows are drawn in list order, one fill each. */
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 2)
    )
    private void phaze$rowColor(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> operation) {
        int row = phaze$rowIndex++;
        List<PlayerListEntry> entries = phaze$rowEntries;
        PlayerListEntry entry = entries != null && row < entries.size() ? entries.get(row) : null;
        operation.call(context, x1, y1, x2, y2, TabEditor.getInstance().rowColor(color, entry));
    }

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void phaze$renderPingAsNumber(DrawContext context, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        TabEditor editor = TabEditor.getInstance();
        if (!editor.isEnabled()) {
            return;
        }
        ci.cancel();
        int ping = entry.getLatency();
        if (editor.hidesPing(ping)) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        String text = String.valueOf(ping);
        int textWidth = renderer.getWidth(text);
        int textX = x + width - textWidth - 1;
        ExordiumAnimationBridge.recordTabElement(
                context, textX - 1.0F, y - 1.0F, textX + textWidth + 1.0F, y + 10.0F
        );
        context.drawText(renderer, text, textX, y, phaze$applyTabAlpha(editor.pingTextColor(ping)),
                editor.pingNumberShadow.isValue());
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
    private void phaze$resetTabAnimationState(DrawContext context, int scaledWindowWidth,
                                              net.minecraft.scoreboard.Scoreboard scoreboard,
                                              net.minecraft.scoreboard.ScoreboardObjective objective,
                                              CallbackInfo ci) {
        phaze$tabTransformPushed = false;
        phaze$rowIndex = 0;
    }

    // Anchored on the header read, not the header fill: that fill is skipped
    // when the server sends no header or Tab Editor hides it.
    @Inject(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/hud/PlayerListHud;header:Lnet/minecraft/text/Text;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void phaze$pushTabSlide(DrawContext context, int scaledWindowWidth,
                                    net.minecraft.scoreboard.Scoreboard scoreboard,
                                    net.minecraft.scoreboard.ScoreboardObjective objective,
                                    CallbackInfo ci,
                                    List<PlayerListEntry> entries,
                                    List<?> scoreEntries,
                                    int emptyWidth,
                                    int maxNameWidth,
                                    int maxScoreWidth,
                                    int totalPlayers,
                                    int rowsPerColumn,
                                    int columns,
                                    boolean showSkins,
                                    int scoreWidth,
                                    int columnWidth,
                                    int left,
                                    int top,
                                    int totalWidth,
                                    List<?> headerLines) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled() || phaze$tabTransformPushed) {
            return;
        }

        if (ExordiumAnimationBridge.isCapturingPlayerList()) {
            float pivotX = scaledWindowWidth * 0.5F;
            float pivotY = module.isTabSlideScaleStyle()
                    ? top
                    : top + rowsPerColumn * 9.0F * 0.5F;
            ExordiumAnimationBridge.recordTabGeometry(pivotX, pivotY);
            return;
        }

        context.draw();
        context.getMatrices().push();
        phaze$tabTransformPushed = true;

        if (module.isTabSlideStyle()) {
            float offsetY = module.currentTabSlideOffset();
            if (offsetY != 0.0F) {
                context.getMatrices().translate(0.0F, offsetY, 0.0F);
            }
            return;
        }

        float progress = module.currentTabProgress();
        float scale = Math.max(0.01F, progress);
        // Vanilla always centers the final TAB rectangle (including a wider
        // server header/footer) on the screen. "left" only belongs to the
        // player grid calculated before that widening, so combining it with
        // totalWidth shifts the animation pivot to the right on such servers.
        float pivotX = scaledWindowWidth * 0.5F;
        float pivotY = module.isTabSlideScaleStyle()
                ? top
                : top + rowsPerColumn * 9.0F * 0.5F;
        context.getMatrices().translate(pivotX, pivotY, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.getMatrices().translate(-pivotX, -pivotY, 0.0F);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$popTabSlide(DrawContext context, int scaledWindowWidth,
                                   net.minecraft.scoreboard.Scoreboard scoreboard,
                                   net.minecraft.scoreboard.ScoreboardObjective objective,
                                   CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled() || !phaze$tabTransformPushed) {
            return;
        }
        context.draw();
        context.getMatrices().pop();
        phaze$tabTransformPushed = false;
    }

    /**
     * Every tab-name edit in one chain: a cancellable RETURN inject that sets a value returns
     * on the spot and skips the injects after it. The badge goes last so its slot opens the line.
     */
    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"))
    private Text phaze$editTabName(Text name, PlayerListEntry entry) {
        if (name == null || entry == null) {
            return name;
        }
        name = TabEditor.getInstance().styleOwnName(entry, name);
        name = phaze$colorTeammate(entry, name);
        NickHider hider = NickHider.getInstance();
        if (hider != null && hider.isEnabled()) {
            name = hider.rewrite(name);
        }
        String username = entry.getProfile().getName();
        if (name != null && PhazeBadgeUtil.isPhazeUser(username)) {
            int extraSpaces = phaze$needsExtraNickHiderPadding(entry) ? 1 : 0;
            name = PhazeBadgeUtil.withBadge(name, PhazeBadgeUtil.isCodeBadgeUser(username), extraSpaces);
        }
        return name;
    }

    @Unique
    private static Text phaze$colorTeammate(PlayerListEntry entry, Text name) {
        if (name == null || vorga.phazeclient.base.util.MessengerService.getInstance().getTeammates().isEmpty()) return name;
        if (!vorga.phazeclient.implement.messenger.ChatWorld.isTeammate(entry.getProfile().getName())) return name;
        MutableText styled = name.copy();
        styled.setStyle(styled.getStyle().withColor(vorga.phazeclient.implement.messenger.ChatWorld.TEAMMATE_COLOR));
        return styled;
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
            ),
            require = 0
    )
    private void phaze$fadeTabFill(
            DrawContext context,
            int x1,
            int y1,
            int x2,
            int y2,
            int color,
            Operation<Void> operation
    ) {
        if (ExordiumAnimationBridge.isCapturingPlayerList()) {
            ExordiumAnimationBridge.recordTabElement(context, x1, y1, x2, y2);
            operation.call(context, x1, y1, x2, y2, color);
            return;
        }
        operation.call(context, x1, y1, x2, y2, phaze$applyTabFillAlpha(color));
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/PlayerSkinDrawer;draw(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;IIIZZI)V"
            ),
            require = 0
    )
    private void phaze$fadeTabPlayerHead(
            DrawContext context,
            Identifier texture,
            int x,
            int y,
            int size,
            boolean drawHat,
            boolean upsideDown,
            int color,
            Operation<Void> operation
    ) {
        if (ExordiumAnimationBridge.isCapturingPlayerList()) {
            ExordiumAnimationBridge.recordTabElement(context, x, y, x + size, y + size);
        }
        phaze$withTabTextureAlpha(context, () ->
                operation.call(context, texture, x, y, size, drawHat, upsideDown, phaze$applyTabAlpha(color))
        );
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
            ),
            require = 0
    )
    private int phaze$drawTabBadge(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int color,
            Operation<Integer> operation,
            @Local PlayerListEntry entry
    ) {
        ExordiumAnimationBridge.recordTabElement(
                context, x - 3.0F, y - 3.0F, x + renderer.getWidth(text) + 1.0F, y + 10.0F
        );
        int fadedColor = phaze$applyTabAlpha(color);
        if (entry != null && PhazeBadgeUtil.isPhazeUser(entry.getProfile().getName())) {
            context.draw();
            boolean codeBadge = PhazeBadgeUtil.isCodeBadgeUser(entry.getProfile().getName());
            PhazeBadgeUtil.drawGuiBadge(
                    context, x + 0.5F, y + 0.5F, PhazeBadgeUtil.GUI_BADGE_SIZE,
                    PhazeBadgeUtil.alphaWhite(fadedColor), codeBadge
            );
        }
        if (!TabEditor.getInstance().drawsNameShadow()) {
            return context.drawText(renderer, text, x, y, fadedColor, false);
        }
        return operation.call(context, renderer, text, x, y, fadedColor);
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"
            ),
            require = 0
    )
    private int phaze$fadeTabOrderedText(
            DrawContext context,
            TextRenderer renderer,
            OrderedText text,
            int x,
            int y,
            int color,
            Operation<Integer> operation
    ) {
        ExordiumAnimationBridge.recordTabElement(
                context, x - 1.0F, y - 1.0F, x + renderer.getWidth(text) + 1.0F, y + 10.0F
        );
        return operation.call(context, renderer, text, x, y, phaze$applyTabAlpha(color));
    }

    @WrapOperation(
            method = {"renderScoreboardObjective", "renderLatencyIcon"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"
            ),
            require = 0
    )
    private void phaze$fadeTabGuiTexture(
            DrawContext context,
            java.util.function.Function<?, ?> layerFactory,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            Operation<Void> operation
    ) {
        ExordiumAnimationBridge.recordTabElement(context, x, y, x + width, y + height);
        phaze$withTabTextureAlpha(context, () ->
                operation.call(context, layerFactory, texture, x, y, width, height)
        );
    }

    @WrapOperation(
            method = "renderScoreboardObjective",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
            ),
            require = 0
    )
    private int phaze$fadeTabScoreText(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int color,
            Operation<Integer> operation
    ) {
        ExordiumAnimationBridge.recordTabElement(
                context, x - 1.0F, y - 1.0F, x + renderer.getWidth(text) + 1.0F, y + 10.0F
        );
        return operation.call(context, renderer, text, x, y, phaze$applyTabAlpha(color));
    }

    private static boolean phaze$needsExtraNickHiderPadding(PlayerListEntry entry) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && client.player != null
                && entry.getProfile().getId().equals(client.player.getUuid());
    }

    @Unique
    private static int phaze$applyTabAlpha(int color) {
        if (ExordiumAnimationBridge.isCapturingPlayerList()) {
            return color;
        }
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            return color;
        }
        float alphaMultiplier = module.currentTabAlpha();
        if (alphaMultiplier >= 0.999F) {
            return color;
        }

        int baseAlpha = color >>> 24;
        if (baseAlpha == 0 && (color & 0x00FFFFFF) != 0) {
            baseAlpha = 0xFF;
        }
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alphaMultiplier)));
        return (color & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    @Unique
    private static int phaze$applyTabFillAlpha(int color) {
        // The animation may fade text, heads and icons, but the vanilla TAB
        // background must keep its original opacity throughout the motion.
        // Returning the existing color is also the cheapest possible path:
        // no extra fill, framebuffer or blend-state pass.
        return color;
    }

    @Unique
    private static void phaze$withTabTextureAlpha(DrawContext context, Runnable draw) {
        if (ExordiumAnimationBridge.isCapturingPlayerList()) {
            draw.run();
            return;
        }
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            draw.run();
            return;
        }

        float alphaMultiplier = module.currentTabAlpha();
        if (alphaMultiplier >= 0.999F) {
            draw.run();
            return;
        }

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alphaMultiplier);
        try {
            draw.run();
            context.draw();
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

}

