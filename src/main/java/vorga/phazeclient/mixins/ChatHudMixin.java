package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.api.system.hud.ChatAnimationFrameAccess;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.helpers.ChatScrollState;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;
import vorga.phazeclient.implement.features.modules.other.MentionHighlight;
import vorga.phazeclient.implement.features.modules.other.NickHider;
import vorga.phazeclient.implement.features.modules.other.Translator;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatAnimationFrameAccess {

    @Shadow private int scrolledLines;

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow protected abstract int getLineHeight();

    @Shadow public abstract int getWidth();

    @Unique private static final float UP_DISPLACEMENT_SCALE = 0.2F;
    @Unique private static final float LEFT_DISPLACEMENT_SCALE = 1.0F;

    @Unique private long phaze$lastMessageNanos = 0L;
    @Unique private int phaze$latestAddedTick = Integer.MIN_VALUE;

    @Unique private float phaze$frameDx = 0.0F;
    @Unique private float phaze$frameDy = 0.0F;
    @Unique private boolean phaze$frameActive = false;
    @Unique private boolean phaze$pendingBadgeForNextLine = false;
    @Unique private boolean phaze$pendingCodeBadgeForNextLine = false;
    @Unique private final Set<Integer> phaze$badgedChatTicks = new LinkedHashSet<>();
    @Unique private final Map<Integer, Boolean> phaze$codeBadgeChatTicks = new LinkedHashMap<>();
    @Unique private final Set<Integer> phaze$drawnBadgeTicksThisFrame = new HashSet<>();

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text phaze$mentionThenHide(Text original) {
        phaze$pendingBadgeForNextLine = false;
        phaze$pendingCodeBadgeForNextLine = false;
        if (PhazeBadgeUtil.hasBadgePadding(original)) {
            phaze$pendingBadgeForNextLine = true;
            String paddedSender = PhazeBadgeUtil.extractChatSender(original.getString());
            phaze$pendingCodeBadgeForNextLine = PhazeBadgeUtil.isCodeBadgeUser(paddedSender);
            return original;
        }

        Text afterMention = original;
        MentionHighlight mention = MentionHighlight.getInstance();
        if (mention != null && mention.isEnabled()) {
            afterMention = mention.processIncoming(original);
        }
        String sender = PhazeBadgeUtil.extractChatSender(afterMention != null ? afterMention.getString() : null);
        NickHider hider = NickHider.getInstance();
        Text result = hider == null ? afterMention : hider.rewrite(afterMention);

        if (sender != null && PhazeBadgeUtil.isPhazeUser(sender)) {
            phaze$pendingBadgeForNextLine = true;
            phaze$pendingCodeBadgeForNextLine = PhazeBadgeUtil.isCodeBadgeUser(sender);
            return PhazeBadgeUtil.withBadgePadding(result);
        }
        return result;
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$collapseRepeats(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null || !helper.isEnabled() || helper.isBypassActive()) {
            return;
        }

        ChatHud hud = (ChatHud) (Object) this;
        Text replacement = helper.tryCollapse(hud, message);
        if (replacement == null) {
            return;
        }

        ci.cancel();
        helper.runWithBypass(() -> hud.addMessage(replacement));
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void phaze$translateIncoming(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        Translator translator = Translator.getInstance();
        if (translator == null || !translator.isEnabled() || translator.isBypassActive()) {
            return;
        }
        translator.onIncomingChat(message, signature);
    }

    @ModifyExpressionValue(
            method = "forEachVisibleLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHud$OpacityRule;calculate(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;)F"
            ),
            require = 0
    )
    private float phaze$applyFadeIn(float original, @Local ChatHudLine.Visible visible) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isChatFadeEnabled()) {
            return original;
        }

        if (ExordiumAnimationBridge.isCapturingChat()) {
            return original;
        }
        if (visible == null) {
            return original;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return original;
        }
        float fadeIn = module.computeChatFadeInMultiplier(client.inGameHud.getTicks() - visible.addedTime());
        if (fadeIn >= 1.0F) {
            return original;
        }
        return original * fadeIn;
    }

    @ModifyExpressionValue(
            method = {
                    "addVisibleMessage",
                    "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
                    "addToMessageHistory"
            },
            at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int phaze$expandHistoryLimit(int original) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null) {
            return original;
        }
        return helper.getChatHistoryLimit();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            at = @At("TAIL")
    )
    private void phaze$stampMessageArrival(ChatHudLine line, CallbackInfo ci) {
        phaze$lastMessageNanos = System.nanoTime();
        if (!visibleMessages.isEmpty()) {
            phaze$latestAddedTick = visibleMessages.get(0).addedTime();
        }

        Translator translatorForTick = Translator.getInstance();
        if (translatorForTick != null && line != null) {
            translatorForTick.captureStoredLine(line.creationTick());
        }
        if (phaze$pendingBadgeForNextLine && line != null) {
            phaze$rememberBadgedChatTick(line.creationTick(), phaze$pendingCodeBadgeForNextLine);
        }
        phaze$pendingBadgeForNextLine = false;
        phaze$pendingCodeBadgeForNextLine = false;

        ExordiumAnimationBridge.requestImmediateCapture(ExordiumAnimationBridge.CHAT);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("HEAD")
    )
    private void phaze$prepareFrame(DrawContext context, TextRenderer textRenderer, int currentTick,
                                    int mouseX, int mouseY, boolean interactable, boolean insertMode,
                                    CallbackInfo ci) {
        phaze$drawnBadgeTicksThisFrame.clear();
        phaze$tickAnimationFrame();
    }

    @Override
    public void phaze$tickAnimationFrame() {
        phaze$frameActive = false;
        phaze$frameDx = 0.0F;
        phaze$frameDy = 0.0F;

        Animations module = Animations.getInstance();
        if (module == null || phaze$lastMessageNanos == 0L) {
            ExordiumAnimationBridge.updateChatAnimation(0.0F, 0.0F, 1.0F, false);
            return;
        }

        float lifetimeMs = (System.nanoTime() - phaze$lastMessageNanos) / 1_000_000.0F;

        float cachedRowAlpha = 1.0F;
        if (module.isChatFadeEnabled() && lifetimeMs < 200.0F) {
            cachedRowAlpha = Math.max(0.0F, Math.min(1.0F, (lifetimeMs + 50.0F) / 200.0F));
        }

        if (module.isChatSmoothScrollEnabled()
                && !ChatScrollState.suppressSlide
                && scrolledLines == 0) {
            boolean left = module.isChatMessageSlideLeft();
            float fadeMs = left ? module.chatLeftSlideFadeMs() : module.chatSlideFadeMs();
            if (fadeMs > 0.0F && lifetimeMs < fadeMs) {
                float alpha = lifetimeMs / fadeMs;
                if (alpha < 0.0F) alpha = 0.0F;
                if (alpha > 1.0F) alpha = 1.0F;

                if (left) {
                    float maxLeft = getWidth() * LEFT_DISPLACEMENT_SCALE;
                    Interpolation interp = module.getChatLeftInterpolation();
                    float shaped = (float) interp.interpolate(alpha);
                    phaze$frameDx = -maxLeft * (1.0F - shaped);
                    phaze$frameDy = 0.0F;
                    phaze$frameActive = Math.abs(phaze$frameDx) >= 1.0F;
                } else {
                    float maxUp = getLineHeight() * UP_DISPLACEMENT_SCALE;
                    phaze$frameDx = 0.0F;
                    phaze$frameDy = Math.round(maxUp * (1.0F - alpha));
                    phaze$frameActive = phaze$frameDy >= 1.0F;
                }
            }
        }

        ExordiumAnimationBridge.updateChatAnimation(
                phaze$frameDx,
                phaze$frameDy,
                cachedRowAlpha,
                phaze$frameActive
        );
    }

    @Unique
    private boolean phaze$shouldShift(ChatHudLine.Visible visible) {
        return phaze$frameActive
                && visible != null
                && visible.addedTime() == phaze$latestAddedTick;
    }

    @Override
    public boolean phaze$shouldShiftChatLine(ChatHudLine.Visible line) {
        return phaze$shouldShift(line);
    }

    @Override
    public float phaze$getChatFrameDx() {
        return phaze$frameDx;
    }

    @Override
    public float phaze$getChatFrameDy() {
        return phaze$frameDy;
    }

    @ModifyArgs(
            method = "method_75802",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud$Backend;fill(IIIII)V")
    )
    private static void phaze$shiftBackendFill(Args args, @Local(argsOnly = true) ChatHudLine.Visible visible) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null
                || !(client.inGameHud.getChatHud() instanceof ChatAnimationFrameAccess access)
                || !access.phaze$shouldShiftChatLine(visible)) {
            return;
        }
        int dx = Math.round(access.phaze$getChatFrameDx());
        int dy = Math.round(access.phaze$getChatFrameDy());
        args.set(0, (Integer) args.get(0) + dx);
        args.set(1, (Integer) args.get(1) + dy);
        args.set(2, (Integer) args.get(2) + dx);
        args.set(3, (Integer) args.get(3) + dy);
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
            require = 0
    )
    private void phaze$shiftFill(DrawContext ctx, int x1, int y1, int x2, int y2, int color,
                                 Operation<Void> op,
                                 @Local ChatHudLine.Visible visible) {
        if (ExordiumAnimationBridge.isCapturingChat()) {
            ExordiumAnimationBridge.recordChatElement(
                    ctx, x1, y1, x2, y2,
                    visible != null && visible.addedTime() == phaze$latestAddedTick
            );
            op.call(ctx, x1, y1, x2, y2, color);
            return;
        }
        if (phaze$shouldShift(visible)) {
            int dx = Math.round(phaze$frameDx);
            int dy = Math.round(phaze$frameDy);
            op.call(ctx, x1 + dx, y1 + dy, x2 + dx, y2 + dy, color);
        } else {
            op.call(ctx, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"),
            require = 0
    )
    private void phaze$shiftText(DrawContext ctx, TextRenderer renderer, OrderedText text,
                                 int x, int y, int color,
                                 Operation<Void> op,
                                 @Local ChatHudLine.Visible visible) {
        if (ExordiumAnimationBridge.isCapturingChat()) {
            ExordiumAnimationBridge.recordChatElement(
                    ctx,
                    x - 1.0F,
                    y - 1.0F,
                    x + renderer.getWidth(text) + 2.0F,
                    y + 10.0F,
                    visible != null && visible.addedTime() == phaze$latestAddedTick
            );
            if (phaze$shouldDrawChatBadge(visible)) {
                PhazeBadgeUtil.drawChatBadgeAsText(
                        ctx, renderer, x - 1.0F, y - 1.0F, PhazeBadgeUtil.alphaWhite(color),
                        phaze$isCodeBadge(visible)
                );
            }
            op.call(ctx, renderer, text, x, y, color);
            return;
        }

        int drawX = x;
        int drawY = y;
        if (phaze$shouldShift(visible)) {
            drawX += Math.round(phaze$frameDx);
            drawY += Math.round(phaze$frameDy);
        }

        if (phaze$shouldDrawChatBadge(visible)) {
            PhazeBadgeUtil.drawChatBadgeAsText(
                    ctx, renderer, drawX - 1.0F, drawY - 1.0F,
                    PhazeBadgeUtil.alphaWhite(color), phaze$isCodeBadge(visible)
            );
        }

        op.call(ctx, renderer, text, drawX, drawY, color);
    }

    @Unique
    private void phaze$rememberBadgedChatTick(int tick, boolean codeBadge) {
        phaze$badgedChatTicks.add(tick);
        phaze$codeBadgeChatTicks.put(tick, codeBadge);
        while (phaze$badgedChatTicks.size() > 512) {
            Integer oldest = phaze$badgedChatTicks.iterator().next();
            phaze$badgedChatTicks.remove(oldest);
            phaze$codeBadgeChatTicks.remove(oldest);
        }
    }

    @Unique
    @Override
    public boolean phaze$shouldDrawChatBadge(ChatHudLine.Visible visible) {
        return visible != null
                && phaze$badgedChatTicks.contains(visible.addedTime())
                && phaze$drawnBadgeTicksThisFrame.add(visible.addedTime());
    }

    @Override
    public boolean phaze$isCodeBadge(ChatHudLine.Visible visible) {
        return visible != null && Boolean.TRUE.equals(phaze$codeBadgeChatTicks.get(visible.addedTime()));
    }
}
