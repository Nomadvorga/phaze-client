package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

@Mixin(ScrollableWidget.class)
public abstract class ScrollableWidgetSmoothScrollMixin {

    @Shadow private double scrollY;

    @Shadow public abstract void setScrollY(double y);

    @Unique private boolean phaze$applyingSmooth;

    @Unique private double phaze$targetScroll;
    @Unique private double phaze$displayScroll;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$initialized = false;

    @Unique private boolean phaze$insideMouseDragged = false;

    @Unique
    private boolean phaze$shouldApply() {
        if (!((Object) this instanceof EntryListWidget)) {
            return false;
        }
        Animations module = Animations.getInstance();
        return module != null && module.isListSmoothScrollEnabled();
    }

    @Inject(method = "setScrollY", at = @At("TAIL"))
    private void phaze$captureTargetAndRestore(double y, CallbackInfo ci) {

        if (phaze$applyingSmooth) {
            return;
        }
        if (!phaze$shouldApply()) {

            phaze$displayScroll = scrollY;
            phaze$targetScroll = scrollY;
            return;
        }

        if (phaze$insideMouseDragged) {
            phaze$displayScroll = scrollY;
            phaze$targetScroll = scrollY;
            phaze$initialized = true;
            return;
        }

        phaze$targetScroll = scrollY;

        if (!phaze$initialized) {
            phaze$displayScroll = scrollY;
            phaze$initialized = true;
            return;
        }

        scrollY = phaze$displayScroll;
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), require = 0)
    private void phaze$dragHead(net.minecraft.client.gui.Click click,
                                double deltaX, double deltaY,
                                CallbackInfoReturnable<Boolean> cir) {
        phaze$insideMouseDragged = true;
    }

    @Inject(method = "mouseDragged", at = @At("RETURN"), require = 0)
    private void phaze$dragTail(net.minecraft.client.gui.Click click,
                                double deltaX, double deltaY,
                                CallbackInfoReturnable<Boolean> cir) {
        phaze$insideMouseDragged = false;
    }

    @Inject(method = "drawScrollbar", at = @At("HEAD"))
    private void phaze$tickDecay(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!phaze$shouldApply()) {
            return;
        }
        if (!phaze$initialized) {
            phaze$displayScroll = scrollY;
            phaze$targetScroll = scrollY;
            phaze$initialized = true;
            return;
        }

        long now = System.nanoTime();
        float dt;
        if (phaze$lastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - phaze$lastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$lastFrameNanos = now;

        Animations module = Animations.getInstance();
        float smoothness = module.smoothnessForSpeed(module.listSpeed.getValue());
        double decay = Math.pow(smoothness, dt);
        phaze$displayScroll = (phaze$displayScroll - phaze$targetScroll) * decay + phaze$targetScroll;
        if (Math.abs(phaze$displayScroll - phaze$targetScroll) < 0.5) {
            phaze$displayScroll = phaze$targetScroll;
        }

        phaze$applyingSmooth = true;
        try {
            setScrollY(phaze$displayScroll);
        } finally {
            phaze$applyingSmooth = false;
        }

        phaze$displayScroll = scrollY;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), require = 0)
    private void phaze$wheelChain(double mouseX, double mouseY, double horizontal, double vertical,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!phaze$shouldApply() || !phaze$initialized) {
            return;
        }

        scrollY = phaze$targetScroll;
    }

    @ModifyArg(
            method = "mouseScrolled",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/ScrollableWidget;setScrollY(D)V"),
            require = 0
    )
    private double phaze$multiplyWheelDelta(double newScrollY) {
        if (!((Object) this instanceof EntryListWidget)) {
            return newScrollY;
        }
        Animations module = Animations.getInstance();
        if (module == null) {
            return newScrollY;
        }
        int lines = module.linesPerScroll();
        if (lines <= 1) {
            return newScrollY;
        }

        double anchor = phaze$initialized ? phaze$targetScroll : scrollY;
        return anchor + (newScrollY - anchor) * lines;
    }
}
