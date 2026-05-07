package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class Animations extends Module {
    private static final Animations INSTANCE = new Animations();

    private static final long TAB_SLIDE_DURATION_MS = 220L;
    private static final long TAB_REOPEN_GAP_MS = 100L;
    private static final float TAB_SLIDE_TRAVEL = 18.0F;
    private static final int CHAT_FADE_IN_TICKS = 4;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting chatFade = new BooleanSetting(
            "Chat Fade",
            "Fade in newly received chat messages over a few ticks"
    ).setValue(true);
    public final BooleanSetting tabSlide = new BooleanSetting(
            "Tab Slide",
            "Slide the player tab list in from the top when opening it"
    ).setValue(true);
    public final BooleanSetting hotbarShift = new BooleanSetting(
            "Hotbar Shift (WIP)",
            "Smoothly slide the hotbar selection highlight when changing slots (work in progress)"
    ).setValue(false);

    private long tabLastFrameMs = 0L;
    private long tabSlideStartMs = 0L;

    private Animations() {
        super("animations", "Animations", ModuleCategory.UTILITIES);
        chatFade.setFullWidth(true);
        tabSlide.setFullWidth(true);
        hotbarShift.setFullWidth(true);
        setup(generalSection, chatFade, tabSlide, hotbarShift);
    }

    public static Animations getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds smooth UI animations: chat message fade-in, tab list slide-in";
    }

    public boolean isChatFadeEnabled() {
        return isEnabled() && chatFade.isValue();
    }

    public boolean isTabSlideEnabled() {
        return isEnabled() && tabSlide.isValue();
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
     * Computes a Y translation (in pixels) that should be applied to the
     * tab list while it slides in from above. Negative values move the
     * list upward. Tracks last-frame time so a brief gap (key released and
     * re-pressed) restarts the animation, while consecutive frames keep
     * progressing.
     */
    public float computeTabSlideOffsetY() {
        if (!isTabSlideEnabled()) {
            tabLastFrameMs = 0L;
            return 0.0F;
        }
        long now = System.currentTimeMillis();
        if (tabLastFrameMs == 0L || now - tabLastFrameMs > TAB_REOPEN_GAP_MS) {
            tabSlideStartMs = now;
        }
        tabLastFrameMs = now;
        long elapsed = now - tabSlideStartMs;
        if (elapsed >= TAB_SLIDE_DURATION_MS) {
            return 0.0F;
        }
        float t = MathHelper.clamp(elapsed / (float) TAB_SLIDE_DURATION_MS, 0.0F, 1.0F);
        // Ease-out cubic: starts fast, decelerates.
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        return -TAB_SLIDE_TRAVEL * (1.0F - eased);
    }
}
