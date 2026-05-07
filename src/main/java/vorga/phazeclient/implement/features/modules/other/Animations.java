package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class Animations extends Module {
    private static final Animations INSTANCE = new Animations();

    /** Total slide travel in GUI pixels. */
    private static final float TAB_SLIDE_TRAVEL = 18.0F;

    /**
     * Reference per-second decay factor at slider value 5. Inspired by the
     * smooth-scrolling mod's {@code pow(smoothness, dt)} trick:
     * {@code current = (current - target) * pow(s, dt) + target}. The Slide
     * Speed slider exponentiates this so each unit of change keeps the same
     * perceived ratio of speed-up / slow-down regardless of where on the
     * slider you are.
     */
    private static final float TAB_SMOOTH_BASE = 0.0015F;

    /**
     * Distance in pixels at which we consider the slide "settled" - once
     * we're closer than this AND the target matches the current direction,
     * we stop forcing extra render frames during a close.
     */
    private static final float TAB_SETTLE_EPSILON = 0.15F;

    private static final int CHAT_FADE_IN_TICKS = 4;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting chatFade = new BooleanSetting(
            "Chat Fade",
            "Fade in newly received chat messages over a few ticks"
    ).setValue(true);
    public final BooleanSetting tabSlide = new BooleanSetting(
            "Tab Slide",
            "Slide the player tab list in from the top when opening or closing it"
    ).setValue(true);
    public final BooleanSetting tabFade = new BooleanSetting(
            "Tab Fade",
            "Fade the tab list's opacity in and out together with the slide"
    ).setValue(true);
    public final ValueSetting slideSpeed = new ValueSetting(
            "Slide Speed",
            "How quickly the tab list slides in/out. Higher = snappier."
    ).range(1, 10).step(0.5F).setValue(5);
    public final BooleanSetting hotbarShift = new BooleanSetting(
            "Hotbar Shift (WIP)",
            "Smoothly slide the hotbar selection highlight when changing slots (work in progress)"
    ).setValue(false);

    /** Current interpolated offset; -TAB_SLIDE_TRAVEL = fully hidden. */
    private float tabCurrentOffset = -TAB_SLIDE_TRAVEL;
    /** Target offset: 0 when open, -TAB_SLIDE_TRAVEL when closed. */
    private float tabTargetOffset = -TAB_SLIDE_TRAVEL;
    private long tabLastFrameNanos = 0L;

    private Animations() {
        super("animations", "Animations", ModuleCategory.UTILITIES);
        chatFade.setFullWidth(true);
        tabSlide.setFullWidth(true);
        tabFade.setFullWidth(true);
        tabFade.visible(tabSlide::isValue);
        slideSpeed.setFullWidth(true);
        slideSpeed.visible(tabSlide::isValue);
        hotbarShift.setFullWidth(true);
        setup(generalSection, chatFade, tabSlide, tabFade, slideSpeed, hotbarShift);
    }

    public static Animations getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds smooth UI animations: chat message fade-in, tab list slide-in/out";
    }

    public boolean isChatFadeEnabled() {
        return isEnabled() && chatFade.isValue();
    }

    public boolean isTabSlideEnabled() {
        return isEnabled() && tabSlide.isValue();
    }

    public boolean isTabFadeEnabled() {
        return isTabSlideEnabled() && tabFade.isValue();
    }

    /**
     * Returns a fade-in multiplier (0..1) based on a chat message's age in
     * ticks. Returns 1.0F for any message older than {@link #CHAT_FADE_IN_TICKS}
     * so vanilla's existing fade-out logic remains untouched.
     */
    public float computeChatFadeInMultiplier(int messageAgeTicks) {
        if (!isChatFadeEnabled()) {
            return 1.0F;
        }
        if (messageAgeTicks < 0 || messageAgeTicks >= CHAT_FADE_IN_TICKS) {
            return 1.0F;
        }
        return (messageAgeTicks + 1) / (float) CHAT_FADE_IN_TICKS;
    }

    /**
     * Updates the tab slide target based on whether the player-list key is
     * currently held. Should be called once per frame from the InGameHud
     * mixin BEFORE either branch (open or closing) renders. Returns the
     * current interpolated Y offset (in GUI pixels, negative = moved up).
     */
    public float tickTabSlide(boolean keyPressed) {
        if (!isTabSlideEnabled()) {
            // Reset to "open at zero" so a future toggle of the setting
            // doesn't fire a stale slide.
            tabCurrentOffset = 0.0F;
            tabTargetOffset = 0.0F;
            tabLastFrameNanos = 0L;
            return 0.0F;
        }

        tabTargetOffset = keyPressed ? 0.0F : -TAB_SLIDE_TRAVEL;

        long now = System.nanoTime();
        float dt;
        if (tabLastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - tabLastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F; // cap after long pauses (alt-tab etc.)
        }
        tabLastFrameNanos = now;

        // Slider value 5 reproduces the old hardcoded 0.0015 base; values
        // above 5 push the exponent past 1 so each per-second decay step
        // squeezes a smaller fraction (snappier), and below 5 raises the
        // base toward 1 (slower). pow(0.0015, 5/5) = 0.0015 exactly.
        float smoothness = (float) Math.pow(TAB_SMOOTH_BASE, slideSpeed.getValue() / 5.0F);

        // Frame-rate independent exponential decay: identical settle time
        // regardless of FPS. Math.pow(s, dt) returns 1 when dt=0, smaller as
        // dt grows.
        float decay = (float) Math.pow(smoothness, dt);
        tabCurrentOffset = (tabCurrentOffset - tabTargetOffset) * decay + tabTargetOffset;

        // Snap to target once we're within epsilon to avoid endless tiny
        // updates that prevent the closing forced-render path from giving up.
        if (Math.abs(tabCurrentOffset - tabTargetOffset) < TAB_SETTLE_EPSILON) {
            tabCurrentOffset = tabTargetOffset;
        }
        return tabCurrentOffset;
    }

    /**
     * True while the tab list is currently being shown via the slide
     * (either fully open, mid-open, or mid-close). The InGameHud mixin
     * uses this to keep rendering the tab list one extra frame past the
     * key release, until the closing slide finishes.
     */
    public boolean isTabSlideRendering(boolean keyPressed) {
        if (!isTabSlideEnabled()) {
            return keyPressed;
        }
        if (keyPressed) return true;
        // Closing animation in progress while we're not yet at -TAB_SLIDE_TRAVEL.
        return tabCurrentOffset > -TAB_SLIDE_TRAVEL + TAB_SETTLE_EPSILON;
    }

    /**
     * Returns the most recently computed slide offset without ticking. Used
     * by the {@link vorga.phazeclient.mixins.PlayerListHudMixin} when it
     * pushes the matrix translate inside {@code render()}; the actual tick
     * happens in the InGameHud wrapper so we get a single per-frame update
     * shared between the open and close branches.
     */
    public float currentTabSlideOffset() {
        return tabCurrentOffset;
    }

    /**
     * Alpha multiplier (0..1) that should be applied to the tab list while
     * the slide is in progress. Maps the offset linearly: fully closed (-h)
     * = 0 alpha, fully open (0) = 1 alpha. Returns 1 when fade is disabled
     * or the module is off so the mixin can skip the shader-color dance.
     */
    public float currentTabAlpha() {
        if (!isTabFadeEnabled()) {
            return 1.0F;
        }
        float alpha = 1.0F + tabCurrentOffset / TAB_SLIDE_TRAVEL;
        if (alpha < 0.0F) return 0.0F;
        if (alpha > 1.0F) return 1.0F;
        return alpha;
    }
}
