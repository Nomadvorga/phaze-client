package vorga.phazeclient.mixins;

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
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.hud.TabHud;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.NickHider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Unique
    private boolean phaze$tabTransformPushed = false;

    @ModifyVariable(
            method = "render",
            at = @At("STORE"),
            ordinal = 0
    )
    private List<PlayerListEntry> phaze$moveSelfToTop(List<PlayerListEntry> original) {
        TabHud tabHud = TabHud.getInstance();
        if (!tabHud.isEnabled() || !tabHud.showSelfOnTop.isValue()) {
            return original;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || original == null || original.size() < 2) {
            return original;
        }

        UUID selfUuid = client.player.getUuid();
        int selfIndex = -1;
        for (int i = 0; i < original.size(); i++) {
            if (original.get(i).getProfile().getId().equals(selfUuid)) {
                selfIndex = i;
                break;
            }
        }

        if (selfIndex <= 0) {
            return original;
        }

        List<PlayerListEntry> reordered = new ArrayList<>(original);
        PlayerListEntry self = reordered.remove(selfIndex);
        reordered.add(0, self);
        return reordered;
    }


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
        ExordiumAnimationBridge.recordTabElement(
                context, textX - 1.0F, y - 1.0F, textX + textWidth + 1.0F, y + 10.0F
        );
        context.drawText(client.textRenderer, text, textX, y, phaze$applyTabAlpha(color), tabHud.pingNumberShadow.isValue());
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
    private void phaze$resetTabAnimationState(DrawContext context, int scaledWindowWidth,
                                              net.minecraft.scoreboard.Scoreboard scoreboard,
                                              net.minecraft.scoreboard.ScoreboardObjective objective,
                                              CallbackInfo ci) {
        phaze$tabTransformPushed = false;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
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

    /**
     * Rewrite the tab list display name through {@link NickHider} so the
     * configured replacement string surfaces in the vanilla TAB overlay.
     * Chained at RETURN so it observes the value set by
     * {@link #phaze$styleOwnName} - if highlightOwn is on we still want
     * the AQUA/BOLD styling, just with the username swapped to the
     * replacement. {@link NickHider#rewrite} preserves style per-fragment
     * via {@link Text#visit}, so the highlight survives intact.
     *
     * <p>The hider short-circuits when the module is disabled or the
     * decorated name doesn't contain the local username (e.g. a server
     * that hides usernames in tab and only shows ranks), so the per-tick
     * cost on a vanilla server is a single {@code String.contains}.
     */
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void phaze$nickHideTabName(PlayerListEntry entry, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Text> cir) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) {
            return;
        }
        Text current = cir.getReturnValue();
        Text rewritten = hider.rewrite(current);
        if (rewritten != current) {
            cir.setReturnValue(rewritten);
        }
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void phaze$badgeTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (entry == null || !PhazeBadgeUtil.isPhazeUser(entry.getProfile().getName())) {
            return;
        }

        Text current = cir.getReturnValue();
        if (current != null) {
            int paddingSpaces = phaze$needsExtraNickHiderPadding(entry) ? 3 : 2;
            cir.setReturnValue(PhazeBadgeUtil.withBadgePadding(current, paddingSpaces));
        }
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
            float regularSize = PhazeBadgeUtil.guiBadgeSize(renderer);
            // Keep the regular Phaze badge unchanged. The developer emblem is
            // intentionally 1.3x smaller in TAB, centred in the same slot.
            float size = codeBadge ? regularSize / 1.3F : regularSize;
            float inset = (regularSize - size) / 2.0F;
            PhazeBadgeUtil.drawGuiBadge(
                    context, x - 2.5F + inset, y - 2.5F + inset, size,
                    PhazeBadgeUtil.alphaWhite(fadedColor), codeBadge
            );
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

