package vorga.phazeclient.mixins;

/**
 * Tiny non-mixin helper for cross-mixin coordination flags.
 *
 * <p>Mixin's preprocessor rejects non-private static fields on mixin
 * classes (see Mixin {@code MixinPreProcessorStandard.validateField}),
 * so any flag that needs to be read from another mixin lives here.
 */
public final class ChatScrollState {
    private ChatScrollState() {}

    /**
     * Set by {@link ChatHudSmoothScrollMixin} while it has temporarily
     * decremented {@code scrolledLines} for its back-step animation;
     * {@link ChatHudMessageSlideMixin} reads this so its
     * {@code scrolledLines == 0} guard isn't fooled into sliding.
     */
    public static boolean suppressSlide = false;
}
