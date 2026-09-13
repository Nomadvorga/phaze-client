package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.option.Perspective;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.base.util.animation.Interpolations;

public final class Animations extends Module {
    private static final Animations INSTANCE = new Animations();

    private static final float TAB_SLIDE_TRAVEL = 18.0F;

    private static final float TAB_SMOOTH_BASE = 0.0015F;

    private static final float TAB_SETTLE_EPSILON = 0.15F;

    private static final int CHAT_FADE_IN_TICKS = 4;

    public final SectionSetting tabSection = new SectionSetting("Tab List");
    public final BooleanSetting tabSlide = new BooleanSetting(
            "Tab Slide",
            "Animate the player tab list when opening or closing it"
    ).setValue(true);
    public final SelectSetting tabAnimationType = new SelectSetting(
            "Tab Animation",
            "Which style of animation to use when showing or hiding the tab list"
    ).value("Slide", "Scale", "Slide+Scale").selected("Slide");
    public final BooleanSetting tabFade = new BooleanSetting(
            "Tab Fade",
            "Fade the tab list's opacity in and out together with the animation"
    ).setValue(true);
    public final ValueSetting tabSlideSpeed = new ValueSetting(
            "Tab Animation Speed",
            "How quickly the tab list animates in/out. Higher = snappier."
    ).range(1, 30).step(0.5F).setValue(5);

    public final SelectSetting tabInterpolationOpen = new SelectSetting(
            "Tab Open Interpolation",
            "Easing curve when the tab list opens (Scale / Slide+Scale styles)"
    ).value(Interpolations.getAllNames()).selected("Default");

    public final SelectSetting tabInterpolationClose = new SelectSetting(
            "Tab Close Interpolation",
            "Easing curve when the tab list closes (Scale / Slide+Scale styles)"
    ).value(Interpolations.getAllNames()).selected("Default");

    public final SectionSetting chatSection = new SectionSetting("Chat");
    public final BooleanSetting chatFade = new BooleanSetting(
            "Chat Fade",
            "Fade in newly received chat messages over a few ticks"
    ).setValue(true);
    public final BooleanSetting chatSmoothScroll = new BooleanSetting(
            "Message Animation",
            "Slide newly received chat messages into place instead of popping in"
    ).setValue(true);

    public final SelectSetting chatMessageAnimationType = new SelectSetting(
            "Message Animation Type",
            "Direction the new chat message slides in from"
    ).value("Up", "Left").selected("Up");
    public final ValueSetting chatSmoothSpeed = new ValueSetting(
            "Message Animation Speed",
            "Speed of the new-message slide. Higher = snappier (shorter slide duration)."
    ).range(1, 30).step(0.5F).setValue(5);

    public final SelectSetting chatLeftInterpolation = new SelectSetting(
            "Left Slide Interpolation",
            "Easing curve for the Left-direction chat message slide"
    ).value(Interpolations.getAllNames()).selected("Default");
    public final BooleanSetting smoothInputField = new BooleanSetting(
            "Smooth Input Field",
            "Slide the chat input box up from below when the chat screen opens (fixed speed)"
    ).setValue(true);

    public final SectionSetting hotbarSection = new SectionSetting("Hotbar");
    public final BooleanSetting hotbarSlide = new BooleanSetting(
            "Hotbar Slide",
            "Smoothly slide the hotbar selection highlight when changing slots"
    ).setValue(true);
    public final BooleanSetting hotbarRollover = new BooleanSetting(
            "Hotbar Rollover",
            "When wrapping past slot 8 to 0 (or vice versa), slide across the wrap instead of teleporting"
    ).setValue(true);
    public final ValueSetting hotbarSpeed = new ValueSetting(
            "Hotbar Slide Speed",
            "Smoothness of the hotbar selection slide. Higher = snappier."
    ).range(1, 30).step(0.5F).setValue(5);

    public final SectionSetting cameraSection = new SectionSetting("Camera");
    public final BooleanSetting smoothF5 = new BooleanSetting(
            "Smooth F5",
            "Smoothly slide the camera out behind the player when toggling third person, and back in when returning to first person"
    ).setValue(true);
    public final ValueSetting smoothF5Speed = new ValueSetting(
            "F5 Animation Speed",
            "How quickly the camera zooms in / out on perspective toggle. Higher = snappier."
    ).range(1, 30).step(0.5F).setValue(5);

    public final SelectSetting smoothF5Interpolation = new SelectSetting(
            "F5 Interpolation",
            "Easing curve for the camera zoom-out animation"
    ).value(Interpolations.getAllNames()).selected("Default");

    public final SectionSetting listsSection = new SectionSetting("Lists");
    public final BooleanSetting listSmoothScroll = new BooleanSetting(
            "List Smooth Scroll",
            "Smooth scrolling for option lists, server lists, multiplayer lists, etc."
    ).setValue(true);
    public final ValueSetting listSpeed = new ValueSetting(
            "List Scroll Speed",
            "Smoothness of widget-list scrolling. Higher = snappier."
    ).range(1, 30).step(0.5F).setValue(5);
    public final ValueSetting listLinesPerScroll = new ValueSetting(
            "List Lines Per Scroll",
            "Number of entries advanced per mouse-wheel tick in option lists."
    ).range(1, 10).step(1.0F).setValue(1);

    private float tabCurrentOffset = -TAB_SLIDE_TRAVEL;

    private float tabTargetOffset = -TAB_SLIDE_TRAVEL;
    private long tabLastFrameNanos = 0L;

    private float tabLinearProgress = 0.0F;

    private float tabAnimationStartProgress = 0.0F;

    private long tabAnimationStartNanos = 0L;

    private boolean tabAnimationTargetOpen = false;

    private static final float F5_FULL_DISTANCE = 4.0F;

    private static final float F5_SETTLE_EPSILON = 0.02F;

    private Perspective f5LastPerspective = null;

    private float f5CurrentDistance = 0.0F;

    private float f5TargetDistance = 0.0F;
    private long f5LastFrameNanos = 0L;

    private float f5AnimationStartDistance = 0.0F;

    private long f5AnimationStartNanos = 0L;

    private Animations() {
        super("animations", "Animations", ModuleCategory.HUD);

        tabSlide.setFullWidth(true);
        tabAnimationType.setFullWidth(true);
        tabAnimationType.visible(tabSlide::isValue);
        tabFade.setFullWidth(true);

        tabFade.visible(() -> tabSlide.isValue() && !isTabSlideStyle());
        tabSlideSpeed.setFullWidth(true);
        tabSlideSpeed.visible(tabSlide::isValue);
        tabInterpolationOpen.setFullWidth(true);
        tabInterpolationClose.setFullWidth(true);

        tabInterpolationOpen.visible(() -> tabSlide.isValue() && !isTabSlideStyle());
        tabInterpolationClose.visible(() -> tabSlide.isValue() && !isTabSlideStyle());

        chatFade.setFullWidth(true);
        chatSmoothScroll.setFullWidth(true);
        chatMessageAnimationType.setFullWidth(true);
        chatMessageAnimationType.visible(chatSmoothScroll::isValue);
        chatSmoothSpeed.setFullWidth(true);
        chatSmoothSpeed.visible(chatSmoothScroll::isValue);
        chatLeftInterpolation.setFullWidth(true);

        chatLeftInterpolation.visible(() -> chatSmoothScroll.isValue()
                && "Left".equalsIgnoreCase(chatMessageAnimationType.getSelected()));
        smoothInputField.setFullWidth(true);

        hotbarSlide.setFullWidth(true);
        hotbarRollover.setFullWidth(true);
        hotbarRollover.visible(hotbarSlide::isValue);
        hotbarSpeed.setFullWidth(true);
        hotbarSpeed.visible(hotbarSlide::isValue);

        listSmoothScroll.setFullWidth(true);
        listSpeed.setFullWidth(true);
        listSpeed.visible(listSmoothScroll::isValue);
        listLinesPerScroll.setFullWidth(true);

        smoothF5.setFullWidth(true);
        smoothF5Speed.setFullWidth(true);
        smoothF5Speed.visible(smoothF5::isValue);
        smoothF5Interpolation.setFullWidth(true);
        smoothF5Interpolation.visible(smoothF5::isValue);

        setup(
                tabSection, tabSlide, tabAnimationType, tabFade, tabSlideSpeed, tabInterpolationOpen, tabInterpolationClose,
                chatSection, chatFade, chatSmoothScroll, chatMessageAnimationType, chatSmoothSpeed, chatLeftInterpolation, smoothInputField,
                hotbarSection, hotbarSlide, hotbarRollover, hotbarSpeed,
                cameraSection, smoothF5, smoothF5Speed, smoothF5Interpolation,
                listsSection, listSmoothScroll, listSpeed, listLinesPerScroll
        );
    }

    public static Animations getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds smooth UI animations: chat message fade-in, tab list slide-in/out";
    }

    @Override
    public String getIcon() {
        return "animations.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean isChatFadeEnabled() {
        return isEnabled() && chatFade.isValue();
    }

    public boolean isTabSlideEnabled() {
        return isEnabled() && tabSlide.isValue();
    }

    public boolean isTabFadeEnabled() {
        if (!isTabSlideEnabled()) {
            return false;
        }

        if (isTabSlideStyle()) {
            return true;
        }
        return tabFade.isValue();
    }

    public boolean isHotbarSlideEnabled() {
        return isEnabled() && hotbarSlide.isValue();
    }

    public boolean isHotbarRolloverEnabled() {
        return isHotbarSlideEnabled() && hotbarRollover.isValue();
    }

    public boolean isChatSmoothScrollEnabled() {
        return isEnabled() && chatSmoothScroll.isValue();
    }

    public boolean isChatMessageSlideLeft() {
        if (!isChatSmoothScrollEnabled()) {
            return false;
        }
        return "Left".equalsIgnoreCase(chatMessageAnimationType.getSelected());
    }

    public boolean isSmoothInputFieldEnabled() {
        return isEnabled() && smoothInputField.isValue();
    }

    public boolean isListSmoothScrollEnabled() {
        return isEnabled() && listSmoothScroll.isValue();
    }

    public int linesPerScroll() {
        if (!isEnabled()) {
            return 1;
        }
        int v = (int) listLinesPerScroll.getValue();
        if (v < 1) v = 1;
        if (v > 10) v = 10;
        return v;
    }

    public float chatSlideFadeMs() {
        float v = chatSmoothSpeed.getValue();
        if (v < 1.0F) v = 1.0F;
        return 750.0F / v;
    }

    public float chatLeftSlideFadeMs() {
        return chatSlideFadeMs() * 4.0F;
    }

    public float smoothnessForSpeed(float speed) {
        return (float) Math.pow(TAB_SMOOTH_BASE, speed / 5.0F);
    }

    public float tabAnimationDurationMs() {
        float v = tabSlideSpeed.getValue();
        if (v < 1.0F) v = 1.0F;
        return 3500.0F / v;
    }

    public float f5AnimationDurationMs() {
        float v = smoothF5Speed.getValue();
        if (v < 1.0F) v = 1.0F;
        return 3500.0F / v;
    }

    public Interpolation getTabInterpolationOpen() {
        return Interpolations.getByName(tabInterpolationOpen.getSelected());
    }

    public Interpolation getTabInterpolationClose() {
        return Interpolations.getByName(tabInterpolationClose.getSelected());
    }

    public Interpolation getChatLeftInterpolation() {
        return Interpolations.getByName(chatLeftInterpolation.getSelected());
    }

    public Interpolation getSmoothF5Interpolation() {
        return Interpolations.getByName(smoothF5Interpolation.getSelected());
    }

    public float computeChatFadeInMultiplier(int messageAgeTicks) {
        if (!isChatFadeEnabled()) {
            return 1.0F;
        }
        if (messageAgeTicks < 0 || messageAgeTicks >= CHAT_FADE_IN_TICKS) {
            return 1.0F;
        }
        return (messageAgeTicks + 1) / (float) CHAT_FADE_IN_TICKS;
    }

    public float tickTabSlide(boolean keyPressed) {
        if (!isTabSlideEnabled()) {

            tabCurrentOffset = -TAB_SLIDE_TRAVEL;
            tabTargetOffset = -TAB_SLIDE_TRAVEL;
            tabLastFrameNanos = 0L;
            tabLinearProgress = 0.0F;
            tabAnimationStartProgress = 0.0F;
            tabAnimationStartNanos = 0L;
            tabAnimationTargetOpen = false;
            return -TAB_SLIDE_TRAVEL;
        }

        tabTargetOffset = keyPressed ? 0.0F : -TAB_SLIDE_TRAVEL;

        long now = System.nanoTime();
        float dt;
        if (tabLastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - tabLastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F;
        }
        tabLastFrameNanos = now;

        float smoothness = smoothnessForSpeed(tabSlideSpeed.getValue());

        float decay = (float) Math.pow(smoothness, dt);
        tabCurrentOffset = (tabCurrentOffset - tabTargetOffset) * decay + tabTargetOffset;

        if (Math.abs(tabCurrentOffset - tabTargetOffset) < TAB_SETTLE_EPSILON) {
            tabCurrentOffset = tabTargetOffset;
        }

        if (keyPressed != tabAnimationTargetOpen || tabAnimationStartNanos == 0L) {
            tabAnimationStartProgress = tabLinearProgress;
            tabAnimationStartNanos = now;
            tabAnimationTargetOpen = keyPressed;
        }
        float endProgress = keyPressed ? 1.0F : 0.0F;
        float duration = tabAnimationDurationMs();
        if (duration < 1.0F) duration = 1.0F;
        float linearT = (now - tabAnimationStartNanos) / 1_000_000.0F / duration;
        if (linearT < 0.0F) linearT = 0.0F;
        if (linearT > 1.0F) linearT = 1.0F;

        String interpName = keyPressed
                ? tabInterpolationOpen.getSelected()
                : tabInterpolationClose.getSelected();
        if (Interpolations.DEFAULT_NAME.equals(interpName)) {

            float decayProgress = 1.0F + tabCurrentOffset / TAB_SLIDE_TRAVEL;
            if (decayProgress < 0.0F) decayProgress = 0.0F;
            if (decayProgress > 1.0F) decayProgress = 1.0F;
            tabLinearProgress = decayProgress;
        } else {
            Interpolation interp = Interpolations.getByName(interpName);
            float shapedT = (float) interp.interpolate(linearT);
            tabLinearProgress = tabAnimationStartProgress + (endProgress - tabAnimationStartProgress) * shapedT;
            if (tabLinearProgress < 0.0F) tabLinearProgress = 0.0F;
            if (tabLinearProgress > 1.0F) tabLinearProgress = 1.0F;
        }

        return tabCurrentOffset;
    }

    public boolean isTabSlideRendering(boolean keyPressed) {
        if (!isTabSlideEnabled()) {
            return keyPressed;
        }
        if (keyPressed) return true;

        return tabCurrentOffset > -TAB_SLIDE_TRAVEL + TAB_SETTLE_EPSILON;
    }

    public void snapTabClosed() {
        tabCurrentOffset = -TAB_SLIDE_TRAVEL;
        tabTargetOffset = -TAB_SLIDE_TRAVEL;
        tabLastFrameNanos = 0L;
        tabLinearProgress = 0.0F;
        tabAnimationStartProgress = 0.0F;
        tabAnimationStartNanos = 0L;
        tabAnimationTargetOpen = false;
    }

    public float currentTabSlideOffset() {
        return tabCurrentOffset;
    }

    public float currentTabProgress() {
        float p = tabLinearProgress;
        if (p < 0.0F) return 0.0F;
        if (p > 1.0F) return 1.0F;
        return p;
    }

    public boolean isTabSlideStyle() {
        return "Slide".equals(tabAnimationType.getSelected());
    }

    public boolean isTabScaleStyle() {
        return "Scale".equals(tabAnimationType.getSelected());
    }

    public boolean isTabSlideScaleStyle() {
        return "Slide+Scale".equals(tabAnimationType.getSelected());
    }

    private static final float TAB_TAIL_FADE_THRESHOLD = 0.15F;

    public float currentTabAlpha() {
        if (!isTabSlideEnabled()) {
            return 1.0F;
        }
        if (isTabSlideStyle()) {

            float alpha = 1.0F + tabCurrentOffset / TAB_SLIDE_TRAVEL;
            return smoothTabFade(alpha);
        }

        float progress = currentTabProgress();
        if (tabFade.isValue()) {

            return smoothTabFade(progress);
        }

        if (progress < TAB_TAIL_FADE_THRESHOLD) {
            return smoothTabFade(progress / TAB_TAIL_FADE_THRESHOLD);
        }
        return 1.0F;
    }

    private static float smoothTabFade(float alpha) {
        float clamped = alpha;
        if (clamped < 0.0F) clamped = 0.0F;
        if (clamped > 1.0F) clamped = 1.0F;
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    public boolean isSmoothF5Enabled() {
        return isEnabled() && smoothF5.isValue();
    }

    public float tickSmoothF5(Perspective currentPerspective) {
        if (!isSmoothF5Enabled()) {

            float vanillaDistance = currentPerspective != null && currentPerspective.isFirstPerson()
                    ? 0.0F : F5_FULL_DISTANCE;
            f5CurrentDistance = vanillaDistance;
            f5TargetDistance = vanillaDistance;
            f5LastPerspective = currentPerspective;
            f5LastFrameNanos = 0L;
            f5AnimationStartDistance = vanillaDistance;
            f5AnimationStartNanos = 0L;
            return f5CurrentDistance;
        }

        if (f5LastPerspective == null) {
            float seed = currentPerspective != null && currentPerspective.isFirstPerson()
                    ? 0.0F : F5_FULL_DISTANCE;
            f5CurrentDistance = seed;
            f5TargetDistance = seed;
            f5LastPerspective = currentPerspective;
            f5LastFrameNanos = 0L;
            f5AnimationStartDistance = seed;
            f5AnimationStartNanos = 0L;
            return f5CurrentDistance;
        }

        if (f5LastPerspective != currentPerspective) {
            boolean curFirst = currentPerspective != null && currentPerspective.isFirstPerson();
            if (curFirst) {

                f5CurrentDistance = 0.0F;
                f5TargetDistance = 0.0F;
                f5AnimationStartDistance = 0.0F;
                f5AnimationStartNanos = 0L;
            } else {

                f5CurrentDistance = 0.0F;
                f5TargetDistance = F5_FULL_DISTANCE;
                f5AnimationStartDistance = 0.0F;
                f5AnimationStartNanos = System.nanoTime();
            }
            f5LastPerspective = currentPerspective;
        }

        String interpName = smoothF5Interpolation.getSelected();
        long now = System.nanoTime();

        if (Interpolations.DEFAULT_NAME.equals(interpName)) {

            float dt;
            if (f5LastFrameNanos == 0L) {
                dt = 1.0F / 60.0F;
            } else {
                dt = (now - f5LastFrameNanos) / 1_000_000_000.0F;
                if (dt > 0.25F) dt = 0.25F;
            }
            f5LastFrameNanos = now;
            float smoothness = smoothnessForSpeed(smoothF5Speed.getValue());
            float decay = (float) Math.pow(smoothness, dt);
            f5CurrentDistance = (f5CurrentDistance - f5TargetDistance) * decay + f5TargetDistance;
            if (Math.abs(f5CurrentDistance - f5TargetDistance) < F5_SETTLE_EPSILON) {
                f5CurrentDistance = f5TargetDistance;
                f5AnimationStartNanos = 0L;
            }
            return f5CurrentDistance;
        }

        if (f5AnimationStartNanos == 0L) {
            f5LastFrameNanos = 0L;
            return f5CurrentDistance;
        }

        f5LastFrameNanos = now;
        float duration = f5AnimationDurationMs();
        if (duration < 1.0F) duration = 1.0F;
        float linearT = (now - f5AnimationStartNanos) / 1_000_000.0F / duration;
        if (linearT < 0.0F) linearT = 0.0F;
        if (linearT > 1.0F) linearT = 1.0F;

        Interpolation interp = Interpolations.getByName(interpName);
        float shapedT = (float) interp.interpolate(linearT);
        f5CurrentDistance = f5AnimationStartDistance + (f5TargetDistance - f5AnimationStartDistance) * shapedT;

        if (linearT >= 1.0F) {
            f5CurrentDistance = f5TargetDistance;
            f5AnimationStartNanos = 0L;
        } else if (Math.abs(f5CurrentDistance - f5TargetDistance) < F5_SETTLE_EPSILON
                && shapedT >= 1.0F) {
            f5CurrentDistance = f5TargetDistance;
            f5AnimationStartNanos = 0L;
        }
        return f5CurrentDistance;
    }

    public float currentF5Distance() {
        return f5CurrentDistance;
    }

    public boolean isF5AnimationActive() {
        return isSmoothF5Enabled() && f5CurrentDistance > F5_SETTLE_EPSILON;
    }
}
