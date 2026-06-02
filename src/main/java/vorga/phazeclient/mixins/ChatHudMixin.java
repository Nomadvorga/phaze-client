package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.base.util.PhazeBadgeUtil;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.helpers.ChatScrollState;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;
import vorga.phazeclient.implement.features.modules.other.MentionHighlight;
import vorga.phazeclient.implement.features.modules.other.NickHider;
import vorga.phazeclient.implement.features.modules.other.Translator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Consolidated mixin for {@link ChatHud}. Combines the previous seven
 * sibling mixins (Collapse, FadeIn, HistoryLimit, MentionHighlight,
 * MessageSlide, NickHider, Translator) into a single class to cut the
 * mixin-config entry count without changing semantics. Every original
 * injector is preserved verbatim with a unique {@code phaze$} method
 * name; the shadowed fields belong to MessageSlide and are unused by
 * the other modules. {@code ChatHudAccessor} (interface) stays as a
 * separate file because Mixin doesn't allow merging accessor
 * interfaces with class-form mixins.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    // ---------------------------------------------------------------
    // ChatHudMessageSlideMixin: shadows + unique state
    // ---------------------------------------------------------------

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
    @Unique private final Set<Integer> phaze$badgedChatTicks = new LinkedHashSet<>();
    @Unique private final Set<Integer> phaze$drawnBadgeTicksThisFrame = new HashSet<>();

    // ---------------------------------------------------------------
    // ChatHudNickHiderMixin + MentionHighlight (combined HEAD)
    // ---------------------------------------------------------------
    // Order matters: MentionHighlight must see the ORIGINAL text so
    // it can match against the player's real username; only AFTER the
    // mention has been processed do we let NickHider replace the
    // username with the user's configured stand-in. The previous
    // setup ran the two as separate {@code @ModifyVariable} hooks at
    // the same HEAD point - the order between sibling ModifyVariable
    // hooks is undefined per Mixin spec, and on the user's machine it
    // happened to land NickHider first, which silently stripped the
    // username before MentionHighlight could match it. Combining the
    // two into a single hook (mention → hide) guarantees the order
    // and fixes the "ping doesn't fire when other players say my
    // name" bug.

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text phaze$mentionThenHide(Text original) {
        phaze$pendingBadgeForNextLine = false;
        if (PhazeBadgeUtil.hasBadgePadding(original)) {
            phaze$pendingBadgeForNextLine = true;
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
            return PhazeBadgeUtil.withBadgePadding(result);
        }
        return result;
    }

    // ---------------------------------------------------------------
    // ChatHudCollapseMixin
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // ChatHudTranslatorMixin
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // ChatHudFadeInMixin
    // ---------------------------------------------------------------

    @Inject(method = "getMessageOpacityMultiplier", at = @At("RETURN"), cancellable = true)
    private static void phaze$applyFadeIn(int messageAge, CallbackInfoReturnable<Double> cir) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isChatFadeEnabled()) {
            return;
        }
        float fadeIn = module.computeChatFadeInMultiplier(messageAge);
        if (fadeIn >= 1.0F) {
            return;
        }
        Double original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        cir.setReturnValue(original * fadeIn);
    }

    // ---------------------------------------------------------------
    // ChatHudHistoryLimitMixin
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // ChatHudMessageSlideMixin: arrival stamp + per-frame compute
    // ---------------------------------------------------------------

    @Inject(
            method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            at = @At("TAIL")
    )
    private void phaze$stampMessageArrival(ChatHudLine line, CallbackInfo ci) {
        phaze$lastMessageNanos = System.nanoTime();
        if (!visibleMessages.isEmpty()) {
            phaze$latestAddedTick = visibleMessages.get(0).addedTime();
        }
        if (phaze$pendingBadgeForNextLine && line != null) {
            phaze$rememberBadgedChatTick(line.creationTick());
        }
        phaze$pendingBadgeForNextLine = false;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$prepareFrame(DrawContext context, int currentTick, int mouseX, int mouseY,
                                    boolean focused, CallbackInfo ci) {
        phaze$frameActive = false;
        phaze$frameDx = 0.0F;
        phaze$frameDy = 0.0F;
        phaze$drawnBadgeTicksThisFrame.clear();

        Animations module = Animations.getInstance();
        if (module == null || !module.isChatSmoothScrollEnabled()) {
            return;
        }
        if (ChatScrollState.suppressSlide) {
            return;
        }
        if (scrolledLines != 0) {
            return;
        }
        if (phaze$lastMessageNanos == 0L) {
            return;
        }

        boolean left = module.isChatMessageSlideLeft();
        float fadeMs = left ? module.chatLeftSlideFadeMs() : module.chatSlideFadeMs();
        if (fadeMs <= 0.0F) {
            return;
        }

        float lifetimeMs = (System.nanoTime() - phaze$lastMessageNanos) / 1_000_000.0F;
        if (lifetimeMs >= fadeMs) {
            return;
        }

        float alpha = lifetimeMs / fadeMs;
        if (alpha < 0.0F) alpha = 0.0F;
        if (alpha > 1.0F) alpha = 1.0F;

        if (left) {
            float maxLeft = getWidth() * LEFT_DISPLACEMENT_SCALE;
            Interpolation interp = module.getChatLeftInterpolation();
            float shaped = (float) interp.interpolate(alpha);
            phaze$frameDx = -maxLeft * (1.0F - shaped);
            phaze$frameDy = 0.0F;
            if (Math.abs(phaze$frameDx) < 1.0F) {
                return;
            }
        } else {
            float maxUp = getLineHeight() * UP_DISPLACEMENT_SCALE;
            phaze$frameDx = 0.0F;
            phaze$frameDy = Math.round(maxUp * (1.0F - alpha));
            if (phaze$frameDy < 1.0F) {
                return;
            }
        }

        phaze$frameActive = true;
    }

    @Unique
    private boolean phaze$shouldShift(ChatHudLine.Visible visible) {
        return phaze$frameActive
                && visible != null
                && visible.addedTime() == phaze$latestAddedTick;
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
            require = 0
    )
    private void phaze$shiftFill(DrawContext ctx, int x1, int y1, int x2, int y2, int color,
                                 Operation<Void> op,
                                 @Local ChatHudLine.Visible visible) {
        if (phaze$shouldShift(visible)) {
            int dx = Math.round(phaze$frameDx);
            int dy = Math.round(phaze$frameDy);
            op.call(ctx, x1 + dx, y1 + dy, x2 + dx, y2 + dy, color);
        } else {
            op.call(ctx, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"),
            require = 0
    )
    private int phaze$shiftText(DrawContext ctx, TextRenderer renderer, OrderedText text,
                                int x, int y, int color,
                                Operation<Integer> op,
                                @Local ChatHudLine.Visible visible) {
        int drawX = x;
        int drawY = y;
        if (phaze$shouldShift(visible)) {
            drawX += Math.round(phaze$frameDx);
            drawY += Math.round(phaze$frameDy);
        }

        if (phaze$shouldDrawChatBadge(visible)) {
            PhazeBadgeUtil.drawChatBadgeAsText(ctx, renderer, drawX - 1.0F, drawY - 1.0F, PhazeBadgeUtil.alphaWhite(color));
        }

        return op.call(ctx, renderer, text, drawX, drawY, color);
    }

    @Unique
    private void phaze$rememberBadgedChatTick(int tick) {
        phaze$badgedChatTicks.add(tick);
        while (phaze$badgedChatTicks.size() > 512) {
            Integer oldest = phaze$badgedChatTicks.iterator().next();
            phaze$badgedChatTicks.remove(oldest);
        }
    }

    @Unique
    private boolean phaze$shouldDrawChatBadge(ChatHudLine.Visible visible) {
        return visible != null
                && phaze$badgedChatTicks.contains(visible.addedTime())
                && phaze$drawnBadgeTicksThisFrame.add(visible.addedTime());
    }
}
