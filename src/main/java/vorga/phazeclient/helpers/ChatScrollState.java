package vorga.phazeclient.helpers;

/**
 * Tiny non-mixin helper for cross-mixin coordination flags.
 *
 * <p>Lives outside {@code vorga.phazeclient.mixins} on purpose: that
 * package is owned by {@code phaze.mixins.json} and Mixin's transformer
 * raises {@code IllegalClassLoadError} if anything other than declared
 * mixin classes is referenced from there. Mixin's preprocessor also
 * rejects non-private static fields on mixin classes themselves
 * ({@code MixinPreProcessorStandard.validateField}), so any flag that
 * needs to be read from a different mixin lives here.
 */
public final class ChatScrollState {
    private ChatScrollState() {}

    /**
     * Set by ChatHudSmoothScrollMixin while it has temporarily
     * decremented {@code scrolledLines} for its back-step animation;
     * ChatHudMessageSlideMixin reads this so its
     * {@code scrolledLines == 0} guard isn't fooled into sliding.
     */
    public static boolean suppressSlide = false;
}
