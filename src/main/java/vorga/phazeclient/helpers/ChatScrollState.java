package vorga.phazeclient.helpers;

/**
 * Cross-mixin coordination flag for chat-render hooks.
 *
 * <p>{@link vorga.phazeclient.mixins.ChatHudSmoothScrollMixin} back-steps
 * {@code ChatHud.scrolledLines} during render so vanilla draws an extra
 * row to fill the slide-in gap. While that back-step is active,
 * {@link vorga.phazeclient.mixins.ChatHudMessageSlideMixin}'s
 * {@code scrolledLines == 0} guard would mis-fire (the field reads as
 * non-zero only because the smooth-scroll mixin moved it) and the two
 * matrix translates would stack into a double-slide. The smooth-scroll
 * mixin sets {@link #suppressSlide} to {@code true} for the duration
 * of its back-stepped section so the slide-in mixin knows to no-op,
 * and clears it once {@code scrolledLines} is restored.
 *
 * <p>Single-threaded by construction (Minecraft's render thread is the
 * only consumer), so no synchronisation is needed.
 */
public final class ChatScrollState {
    /**
     * {@code true} while {@link vorga.phazeclient.mixins.ChatHudSmoothScrollMixin}
     * has temporarily back-stepped {@code ChatHud.scrolledLines}.
     * Other chat-render mixins must skip {@code scrolledLines}-based
     * gating while this flag is set.
     */
    public static boolean suppressSlide = false;

    private ChatScrollState() {
    }
}
