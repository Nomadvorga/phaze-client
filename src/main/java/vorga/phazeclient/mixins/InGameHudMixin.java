package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.PotionHud;
import vorga.phazeclient.implement.features.modules.hud.TabEditor;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardHud;
import vorga.phazeclient.implement.features.modules.other.Crosshair;
import vorga.phazeclient.implement.features.modules.other.NickHider;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.hud.CustomHudRenderer;
import vorga.phazeclient.implement.hud.vanilla.HotbarTweaks;
import vorga.phazeclient.implement.hud.vanilla.SaturationOverlay;
import vorga.phazeclient.implement.hud.vanilla.TabListSlide;

import java.util.function.Function;

/**
 * Hook points into the vanilla in-game HUD. All behaviour lives in plain classes under
 * {@code implement.hud}; injectors sharing a target run in declaration order, so keep it stable.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private PlayerListHud playerListHud;

    @Inject(method = "render", at = @At("HEAD"))
    private void beginHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CustomHudRenderer.beginFrame(this.client, context);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$tabSlideTick(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        TabEditor.getInstance().pollToggle(this.client);
        TabListSlide.tick(this.client);
    }

    @WrapOperation(
            method = "renderPlayerList",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z")
    )
    private boolean phaze$tabToggle(KeyBinding key, Operation<Boolean> operation) {
        return TabEditor.isListKeyDown(this.client);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCustomHudFallback(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CustomHudRenderer.renderFrame(context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$tabSlideRenderTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        TabListSlide.renderClosing(this.client, this.playerListHud, context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$messengerToasts(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        vorga.phazeclient.implement.messenger.MessengerToasts.render(context);
    }

    /** The Phaze Potion HUD replaces the vanilla top-right effect icons instead of duplicating them. */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressVanillaStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PotionHud.getInstance().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressVanillaScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        boolean hideByNoRender = noRender != null && noRender.isEnabled() && noRender.scoreboard.isValue();
        if (ScoreboardHud.getInstance().isEnabled() || hideByNoRender) {
            ci.cancel();
        }
    }

    @Inject(method = "method_55808", at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$suppressVanillaBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.bossBar.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("TAIL"))
    private void phaze$onSaturationRenderStatusBars(DrawContext context, CallbackInfo ci) {
        SaturationOverlay.renderStatusBars(context);
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"))
    private void phaze$liftAirBubblesHead(DrawContext context, PlayerEntity player,
                                          int heartCount, int maxAirBubbles, int top, CallbackInfo ci) {
        SaturationOverlay.beforeAirBubbles(context);
    }

    @Inject(method = "renderAirBubbles", at = @At("RETURN"))
    private void phaze$liftAirBubblesReturn(DrawContext context, PlayerEntity player,
                                            int heartCount, int maxAirBubbles, int top, CallbackInfo ci) {
        SaturationOverlay.afterAirBubbles(context);
    }

    @Inject(
            method = "renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            at = @At("TAIL")
    )
    private void phaze$drawCooldownNumber(DrawContext context, int x, int y, RenderTickCounter tickCounter,
                                          PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        HotbarTweaks.drawCooldownNumber(context, x, y, tickCounter.getTickDelta(false), player, stack);
    }

    @WrapOperation(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1), require = 1
    )
    private void phaze$slideSelection(DrawContext context, Function<Identifier, RenderLayer> layerFactory,
                                      Identifier texture, int x, int y, int width, int height, Operation<Void> original) {
        HotbarTweaks.renderSelection(context, layerFactory, texture, x, y, width, height,
                drawX -> original.call(context, layerFactory, texture, drawX, y, width, height));
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarUtilityHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HotbarTweaks.drawUtilityHighlights(context);
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$flushHotbarBatch(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HotbarTweaks.flush(context);
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I")
    )
    private Text phaze$hideOwnNickInSidebar(Text original) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) return original;
        return hider.rewrite(original);
    }

    @Redirect(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z")
    )
    private boolean phaze$forceFirstPersonForCrosshair(Perspective perspective) {
        Crosshair module = Crosshair.getInstance();
        if (module != null && module.isEnabled() && module.showInThirdPerson.isValue()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && mc.options.hudHidden) {
                return perspective.isFirstPerson();
            }
            return true;
        }
        return perspective.isFirstPerson();
    }
}
