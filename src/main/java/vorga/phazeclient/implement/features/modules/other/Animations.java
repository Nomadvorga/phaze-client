package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.option.Perspective;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
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

    public final SectionSetting chatSection = new SectionSetting("Chat");
    public final BooleanSetting chatFade = new BooleanSetting(
            "Chat Fade",
            "Fade in newly received chat messages over a few ticks"
    ).setValue(true);
    public final BooleanSetting chatSmoothScroll = new BooleanSetting(
            "Message Animation",
            "Slide newly received chat messages into place instead of popping in"
    ).setValue(true);
    /**
     * Choice of slide direction for the new-message animation.
     * <ul>
     *   <li>{@code Up} - the original ChatAnimation-style behaviour: the
     *       whole chat stack is translated down by ~20% of a line height
     *       at message arrival and eases up to its rest position. Subtle,
     *       no horizontal motion.</li>
     *   <li>{@code Left} - the chat slides in from beyond the left edge
     *       of the chat box on each new message. Larger, more theatrical
     *       motion; runs without scale (matches the user request).</li>
     * </ul>
     */
    public final SelectSetting chatMessageAnimationType = new SelectSetting(
            "Message Animation Type",
            "Direction the new chat message slides in from"
    ).value("Up", "Left").selected("Up");
    public final ValueSetting chatSmoothSpeed = new ValueSetting(
            "Message Animation Speed",
            "Speed of the new-message slide. Higher = snappier (shorter slide duration)."
    ).range(1, 30).step(0.5F).setValue(5);
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

    /** Current interpolated offset; -TAB_SLIDE_TRAVEL = fully hidden. */
    private float tabCurrentOffset = -TAB_SLIDE_TRAVEL;
    /** Target offset: 0 when open, -TAB_SLIDE_TRAVEL when closed. */
    private float tabTargetOffset = -TAB_SLIDE_TRAVEL;
    private long tabLastFrameNanos = 0L;

    /** Vanilla's default third-person camera distance. Matches the literal
     *  {@code 4.0F} passed to {@code Camera#clipToSpace} in the third-person
     *  branch of {@code Camera#update}. Kept as a named constant so the
     *  intent ("this is the full zoom-out distance") is obvious. */
    private static final float F5_FULL_DISTANCE = 4.0F;
    /** Distance below which the camera-distance interpolator snaps to its
     *  target. 0.02 blocks is well under a single pixel of camera travel
     *  at any practical FOV; smaller and the exponential decay would
     *  asymptote forever. */
    private static final float F5_SETTLE_EPSILON = 0.02F;
    /** Last polled perspective. Used by {@link #tickSmoothF5(Perspective)}
     *  to detect FIRST↔THIRD transitions and arm the zoom animation. */
    private Perspective f5LastPerspective = null;
    /** Interpolated camera distance, in blocks. 0 = at the player's eye
     *  (first-person), {@link #F5_FULL_DISTANCE} = full third-person
     *  retracted. The Camera mixin feeds this to {@code clipToSpace} so
     *  the camera physically slides instead of snapping. */
    private float f5CurrentDistance = 0.0F;
    /** Animation target distance; matches the vanilla distance for the
     *  current perspective (0 in first-person, {@link #F5_FULL_DISTANCE}
     *  in third-person). */
    private float f5TargetDistance = 0.0F;
    /**
     * Two-phase BACK ↔ FRONT transition flag. The straight rotation flip
     * vanilla performs has no distance change to animate, so we synthesise
     * one: target=0 (camera retracts into the player) and once we settle
     * at zero we re-arm target={@link #F5_FULL_DISTANCE} so the camera
     * emerges on the opposite side. Without this the second F5 press would
     * snap instantly (BACK and FRONT both sit at distance 4), which
     * defeated the user-visible point of "Smooth F5" on press #2.
     */
    private boolean f5PendingZoomOut = false;
    private long f5LastFrameNanos = 0L;

    private Animations() {
        super("animations", "Animations", ModuleCategory.HUD);

        tabSlide.setFullWidth(true);
        tabAnimationType.setFullWidth(true);
        tabAnimationType.visible(tabSlide::isValue);
        tabFade.setFullWidth(true);
        // Fade is exposed for every animation style EXCEPT plain
        // Slide. Slide always runs with fade because a fade-less slide
        // either pops out at the 18px stop (ugly) or has to stretch
        // off-screen (which the user explicitly didn't want), so hiding
        // the toggle keeps Slide on its single coherent path. Both
        // scale-based styles let the user pick because the matrix scale
        // already collapses the tab into a sub-pixel speck so a hard
        // cutoff without fade still looks clean.
        tabFade.visible(() -> tabSlide.isValue() && !isTabSlideStyle());
        tabSlideSpeed.setFullWidth(true);
        tabSlideSpeed.visible(tabSlide::isValue);

        chatFade.setFullWidth(true);
        chatSmoothScroll.setFullWidth(true);
        chatMessageAnimationType.setFullWidth(true);
        chatMessageAnimationType.visible(chatSmoothScroll::isValue);
        chatSmoothSpeed.setFullWidth(true);
        chatSmoothSpeed.visible(chatSmoothScroll::isValue);
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

        setup(
                tabSection, tabSlide, tabAnimationType, tabFade, tabSlideSpeed,
                chatSection, chatFade, chatSmoothScroll, chatMessageAnimationType, chatSmoothSpeed, smoothInputField,
                hotbarSection, hotbarSlide, hotbarRollover, hotbarSpeed,
                cameraSection, smoothF5, smoothF5Speed,
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
        // Slide style: fade is always on (toggle is hidden in the UI),
        // so the alpha-driven dissolve carries the final stretch the
        // 18px translate can't reach on its own.
        // Scale and Slide+Scale: respect the user's toggle.
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

    /**
     * True when the message-arrival animation should slide horizontally
     * from the left edge of the chat box rather than vertically from
     * below. Read by {@link
     * vorga.phazeclient.mixins.ChatHudMessageSlideMixin} on every render
     * frame to pick the translate axis. Returns false (= use the
     * default "Up" slide) whenever the smooth-scroll feature is off,
     * so callers don't have to gate twice.
     */
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

    /**
     * Number of entries the widget-list scroll wheel advances per tick.
     * Independent of {@link #isListSmoothScrollEnabled()} - the multiplier
     * applies to vanilla's discrete jump as well as our smoothed slide,
     * so users can crank the per-click distance without having to enable
     * the smoothing animation. Falls back to vanilla's 1-line behaviour
     * when the module is fully disabled so a turned-off module never
     * silently changes the wheel feel.
     */
    public int linesPerScroll() {
        if (!isEnabled()) {
            return 1;
        }
        int v = (int) listLinesPerScroll.getValue();
        if (v < 1) v = 1;
        if (v > 10) v = 10;
        return v;
    }

    /**
     * Maps the {@code Chat Scroll Speed} slider [1..20] to a fade-time in
     * milliseconds for the new-message slide animation. Slider value 5
     * reproduces the ChatAnimation reference's 150 ms default; smaller
     * values stretch the fade out, larger values snap quickly.
     *
     * <p>Curve is a simple inverse so each unit of slider change roughly
     * halves/doubles the duration:
     * <pre>{@code fadeMs = 750 / slider}</pre>
     * Slider 1 = 750 ms (very slow), 5 = 150 ms (default), 20 = 37.5 ms.
     */
    public float chatSlideFadeMs() {
        float v = chatSmoothSpeed.getValue();
        if (v < 1.0F) v = 1.0F;
        return 750.0F / v;
    }

    /**
     * Fade-time for the {@code Left} slide style. Travel distance is
     * an order of magnitude larger than the Up style (full chat width
     * vs. ~2 px), so reusing the same {@link #chatSlideFadeMs()} curve
     * makes the slide visually whip across the screen at high speed
     * settings - at slider 30 the message would cover several hundred
     * pixels in 25 ms, which the eye can't track and reads as "popped
     * in instantly".
     *
     * <p>The 4x multiplier on the base curve gives the user roughly
     * the same perceived snappiness across both styles: at slider 5
     * Up takes 150 ms (≈2 px / 150 ms ≈ 13 px/s) and Left takes 600 ms
     * (≈300 px / 600 ms ≈ 500 px/s). Because pixel travel is ~150x
     * larger we'd need a 150x longer duration to literally match speed
     * but that's far too sluggish; 4x is the empirical sweet spot
     * where the slide is unambiguously visible at every slider value
     * without dragging on at the slow end.
     *
     * <ul>
     *   <li>slider  1: 3000 ms (very slow, theatrical)</li>
     *   <li>slider  5: 600 ms (matches default expectation)</li>
     *   <li>slider 30: 100 ms (still readable as a slide, not a pop)</li>
     * </ul>
     */
    public float chatLeftSlideFadeMs() {
        return chatSlideFadeMs() * 4.0F;
    }

    /**
     * Maps a 1..10 speed slider to an exponential-decay smoothness factor
     * such that {@code value=5} reproduces the reference {@link #TAB_SMOOTH_BASE}
     * baseline, lower values get exponentially smoother and higher exponentially
     * snappier. Shared by every sliding/scrolling animation.
     */
    public float smoothnessForSpeed(float speed) {
        return (float) Math.pow(TAB_SMOOTH_BASE, speed / 5.0F);
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
            // Park the offsets at the CLOSED position so a future toggle
            // of the setting doesn't kick off a phantom slide-out from a
            // stale "open" offset. The previous code reset to 0 (open),
            // which caused a visible close animation the first frame the
            // user re-enabled the module - particularly noticeable in
            // singleplayer where vanilla normally never opens the tab on
            // its own, so the phantom animation looked like the mod was
            // forcing the tab list to appear.
            tabCurrentOffset = -TAB_SLIDE_TRAVEL;
            tabTargetOffset = -TAB_SLIDE_TRAVEL;
            tabLastFrameNanos = 0L;
            return -TAB_SLIDE_TRAVEL;
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
        float smoothness = smoothnessForSpeed(tabSlideSpeed.getValue());

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
     * Normalised open-progress in the range 0..1 derived from the same
     * underlying offset that {@link #currentTabSlideOffset()} exposes:
     * 0 when the tab list is fully hidden, 1 when fully open. The Scale
     * animation style uses this to drive the matrix scale factor, and
     * alpha fading consumes the same curve so the two styles stay in
     * sync with whatever speed slider value the user picks.
     */
    public float currentTabProgress() {
        float progress = 1.0F + tabCurrentOffset / TAB_SLIDE_TRAVEL;
        if (progress < 0.0F) return 0.0F;
        if (progress > 1.0F) return 1.0F;
        return progress;
    }

    /**
     * True when the user has picked the plain "Slide" animation style.
     * Slide hides the Fade toggle and always runs with fade.
     */
    public boolean isTabSlideStyle() {
        return "Slide".equals(tabAnimationType.getSelected());
    }

    /**
     * True when the user has picked the center-pivot "Scale" style.
     * The {@link vorga.phazeclient.mixins.PlayerListHudMixin} pivots
     * the matrix scale around scaledHeight/4 (approximate tab center)
     * for this style.
     */
    public boolean isTabScaleStyle() {
        return "Scale".equals(tabAnimationType.getSelected());
    }

    /**
     * True when the user has picked the top-pivot "Slide+Scale" style.
     * Same matrix-scale machinery as {@link #isTabScaleStyle()} but
     * pivoted at the top of the tab list (around y=10) so the close
     * animation visually retracts UP into the tab's header instead of
     * collapsing toward the middle. "Slide+Scale" because the upward
     * pivot makes the shrink read as a slide-up combined with a
     * scale-down.
     */
    public boolean isTabSlideScaleStyle() {
        return "Slide+Scale".equals(tabAnimationType.getSelected());
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

    /**
     * True when the Smooth F5 feature is currently active. Gates both the
     * mixin's {@code thirdPerson} override and its {@code clipToSpace}
     * arg modification - a disabled feature must leave vanilla behaviour
     * untouched.
     */
    public boolean isSmoothF5Enabled() {
        return isEnabled() && smoothF5.isValue();
    }

    /**
     * Advances the camera-distance interpolator one frame and returns the
     * new distance. Called from
     * {@link vorga.phazeclient.mixins.CameraSmoothF5Mixin} at the HEAD of
     * every {@code Camera#update} so the value the same call site later
     * reads back via {@link #currentF5Distance()} is fresh for THIS frame.
     *
     * <p>Transition detection:
     * <ul>
     *   <li>FIRST → THIRD: snap current distance to 0, target {@link #F5_FULL_DISTANCE}.
     *       The camera was at the player's eye, animate it backwards.</li>
     *   <li>THIRD → FIRST: leave current distance alone (probably already
     *       at full retract), target 0. The camera slides forward into
     *       the player's head; the mixin keeps {@code thirdPerson=true}
     *       throughout so vanilla actually moves the camera instead of
     *       snapping to the eye position.</li>
     *   <li>BACK ↔ FRONT: no distance change, no animation - both modes
     *       sit at {@link #F5_FULL_DISTANCE}. Vanilla's view-flip is
     *       instant by design and we mirror that.</li>
     * </ul>
     *
     * <p>The frame-rate-independent decay uses the same
     * {@link #smoothnessForSpeed(float)} curve as the tab slide so the
     * speed slider behaves identically across animations.
     */
    public float tickSmoothF5(Perspective currentPerspective) {
        if (!isSmoothF5Enabled()) {
            // Park the animation at whichever distance vanilla would
            // render with right now. This prevents a phantom slide on the
            // very first frame after the feature is re-enabled (we don't
            // want a stale 4→0 anim to play just because the user
            // toggled the setting off in third-person and on in first-).
            float vanillaDistance = currentPerspective != null && currentPerspective.isFirstPerson()
                    ? 0.0F : F5_FULL_DISTANCE;
            f5CurrentDistance = vanillaDistance;
            f5TargetDistance = vanillaDistance;
            f5LastPerspective = currentPerspective;
            f5LastFrameNanos = 0L;
            return f5CurrentDistance;
        }

        // Cold-start: seed state to the vanilla position so a player who
        // launches the game already in third-person doesn't see a phantom
        // 0→4 slide on the first camera update.
        if (f5LastPerspective == null) {
            float seed = currentPerspective != null && currentPerspective.isFirstPerson()
                    ? 0.0F : F5_FULL_DISTANCE;
            f5CurrentDistance = seed;
            f5TargetDistance = seed;
            f5LastPerspective = currentPerspective;
            f5LastFrameNanos = 0L;
            return f5CurrentDistance;
        }

        if (f5LastPerspective != currentPerspective) {
            boolean prevFirst = f5LastPerspective.isFirstPerson();
            boolean curFirst = currentPerspective != null && currentPerspective.isFirstPerson();
            if (prevFirst && !curFirst) {
                // FIRST → THIRD_*: start zoom-out from the eye.
                f5CurrentDistance = 0.0F;
                f5TargetDistance = F5_FULL_DISTANCE;
                f5PendingZoomOut = false;
            } else if (!prevFirst && curFirst) {
                // THIRD_* → FIRST: keep the current distance and slide back
                // in. Mid-animation the mixin forces thirdPerson=true so the
                // camera physically moves rather than vanilla snapping.
                f5TargetDistance = 0.0F;
                f5PendingZoomOut = false;
            } else if (!prevFirst && !curFirst) {
                // BACK ↔ FRONT (press #2 in the F5 cycle): both sides sit
                // at full distance, so we synthesise a zoom-through. Pull
                // the camera in to the player first; once we settle at 0
                // the pending flag re-arms a zoom-out to F5_FULL_DISTANCE
                // and vanilla's already-flipped perspective makes the
                // camera emerge on the opposite side. Snap current to the
                // full distance so a re-entry mid-slide (e.g. user mashing
                // F5) doesn't start the new arc from a stale intermediate
                // value.
                f5CurrentDistance = F5_FULL_DISTANCE;
                f5TargetDistance = 0.0F;
                f5PendingZoomOut = true;
            }
            f5LastPerspective = currentPerspective;
        }

        long now = System.nanoTime();
        float dt;
        if (f5LastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - f5LastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F; // cap after long pauses (alt-tab etc.)
        }
        f5LastFrameNanos = now;

        float smoothness = smoothnessForSpeed(smoothF5Speed.getValue());
        float decay = (float) Math.pow(smoothness, dt);
        f5CurrentDistance = (f5CurrentDistance - f5TargetDistance) * decay + f5TargetDistance;

        if (Math.abs(f5CurrentDistance - f5TargetDistance) < F5_SETTLE_EPSILON) {
            f5CurrentDistance = f5TargetDistance;
            // Second half of the BACK ↔ FRONT zoom-through: now that we
            // bottomed out at the player we re-arm a zoom-out so the
            // camera emerges behind/in front of the player (whichever
            // perspective vanilla flipped to on the F5 press).
            if (f5PendingZoomOut && f5TargetDistance == 0.0F) {
                f5TargetDistance = F5_FULL_DISTANCE;
                f5PendingZoomOut = false;
            }
        }
        return f5CurrentDistance;
    }

    /**
     * Most recently computed camera distance from {@link #tickSmoothF5}.
     * Read by {@link vorga.phazeclient.mixins.CameraSmoothF5Mixin} when it
     * substitutes the argument of {@code Camera#clipToSpace(float)}.
     */
    public float currentF5Distance() {
        return f5CurrentDistance;
    }

    /**
     * True while the interpolator has work left to do AND the feature is
     * enabled. The Camera mixin uses this to force
     * {@code Camera#update}'s {@code thirdPerson} parameter to true during
     * a THIRD→FIRST slide - vanilla would otherwise short-circuit to the
     * first-person path (no camera offset) the moment the perspective
     * flips, snapping the camera into the player's head and skipping our
     * animation entirely. The {@code > epsilon} check matches the settle
     * test in {@link #tickSmoothF5} so the forcing stops at the exact
     * frame the animation reaches first-person rest.
     */
    public boolean isF5AnimationActive() {
        return isSmoothF5Enabled() && f5CurrentDistance > F5_SETTLE_EPSILON;
    }
}
