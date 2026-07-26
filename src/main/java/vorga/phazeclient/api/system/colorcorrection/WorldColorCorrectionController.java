package vorga.phazeclient.api.system.colorcorrection;

import vorga.phazeclient.implement.features.modules.other.ColorCorrection;

import java.util.ArrayDeque;
import java.util.Deque;

public final class WorldColorCorrectionController {
    private static final ThreadLocal<Deque<Target>> TARGET_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private WorldColorCorrectionController() {
    }

    public static void push(Target target) {
        TARGET_STACK.get().push(target == null ? Target.NONE : target);
    }

    public static void pop() {
        Deque<Target> stack = TARGET_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    public static Target current() {
        Deque<Target> stack = TARGET_STACK.get();
        return stack.isEmpty() ? Target.NONE : stack.peek();
    }

    public static boolean isTargetActive(Target target) {
        ColorCorrection module = module();
        return target != null
                && target != Target.NONE
                && module != null
                && module.isEnabled()
                && module.hasAdvancedCorrection(target);
    }

    public static boolean supportsAlpha(Target target) {
        ColorCorrection module = module();
        return module != null && module.supportsAdvancedAlpha(target);
    }

    public static boolean needsBlend(Target target) {
        return supportsAlpha(target) && isTargetActive(target) && getAlpha(target) < 0.999F;
    }

    public static boolean needsColorTransform(Target target) {
        if (!isTargetActive(target)) {
            return false;
        }
        return getRed(target) != 1.0F
                || getGreen(target) != 1.0F
                || getBlue(target) != 1.0F
                || getBrightness(target) != 0.0F
                || getSaturation(target) != 1.0F;
    }

    public static boolean needsAlphaTransform(Target target) {
        return supportsAlpha(target) && isTargetActive(target) && getAlpha(target) != 1.0F;
    }

    public static float getRed(Target target) {
        ColorCorrection module = module();
        return module == null ? 1.0F : module.getAdvancedRed(target);
    }

    public static float getGreen(Target target) {
        ColorCorrection module = module();
        return module == null ? 1.0F : module.getAdvancedGreen(target);
    }

    public static float getBlue(Target target) {
        ColorCorrection module = module();
        return module == null ? 1.0F : module.getAdvancedBlue(target);
    }

    public static float getAlpha(Target target) {
        ColorCorrection module = module();
        return module == null ? 1.0F : module.getAdvancedAlpha(target);
    }

    public static float getBrightness(Target target) {
        ColorCorrection module = module();
        return module == null ? 0.0F : module.getAdvancedBrightness(target);
    }

    public static float getSaturation(Target target) {
        ColorCorrection module = module();
        return module == null ? 1.0F : module.getAdvancedSaturation(target);
    }

    public static int applyToArgb(int argb) {
        return argb;
    }

    public static int applyToArgb(Target target, int argb) {
        if (!isTargetActive(target)) {
            return argb;
        }

        int alpha = (argb >>> 24) & 255;
        int red = (argb >>> 16) & 255;
        int green = (argb >>> 8) & 255;
        int blue = argb & 255;

        int rgb = applyRgb(red, green, blue, target);
        int correctedAlpha = supportsAlpha(target) ? applyAlphaInt(alpha, target) : alpha;
        return (correctedAlpha << 24) | rgb;
    }

    public static int applyRgb(int red, int green, int blue, Target target) {
        if (!isTargetActive(target)) {
            return ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
        }

        return applyRgbWith(
                red, green, blue,
                getRed(target), getGreen(target), getBlue(target),
                getSaturation(target), getBrightness(target)
        );
    }

    /**
     * Same transform as {@link #applyRgb}, but with the per-target factors
     * supplied by the caller instead of read from the module.
     *
     * <p>This exists because the transform runs per vertex. Resolving the
     * factors inside it meant five {@code ColorCorrection.getInstance()}
     * round-trips - each an enum switch plus a settings read, and each
     * preceded by an {@code isTargetActive} check that itself re-reads all
     * six sliders for the target. On terrain that is millions of settings
     * reads per frame to compute a handful of constants that cannot change
     * mid-pass. Callers on the hot path resolve them once and pass them in.
     *
     * <p>The arithmetic is byte-identical to the original.
     */
    public static int applyRgbWith(
            int red, int green, int blue,
            float redMultiplier, float greenMultiplier, float blueMultiplier,
            float saturation, float brightness
    ) {
        float r = clamp01(red / 255.0F) * redMultiplier;
        float g = clamp01(green / 255.0F) * greenMultiplier;
        float b = clamp01(blue / 255.0F) * blueMultiplier;

        r = clamp01(r);
        g = clamp01(g);
        b = clamp01(b);

        float gray = (r * 0.2126F) + (g * 0.7152F) + (b * 0.0722F);
        r = gray + (r - gray) * saturation;
        g = gray + (g - gray) * saturation;
        b = gray + (b - gray) * saturation;

        r = applyBrightness(r, brightness);
        g = applyBrightness(g, brightness);
        b = applyBrightness(b, brightness);

        int packedRed = clamp255(Math.round(r * 255.0F));
        int packedGreen = clamp255(Math.round(g * 255.0F));
        int packedBlue = clamp255(Math.round(b * 255.0F));
        return (packedRed << 16) | (packedGreen << 8) | packedBlue;
    }

    /** Alpha half of {@link #applyAlphaInt} with the factor pre-resolved. */
    public static int applyAlphaIntWith(int alpha, boolean blend, float alphaMultiplier) {
        if (!blend) {
            return clamp255(alpha);
        }
        return clamp255(Math.round(alpha * alphaMultiplier));
    }

    public static int[] applyChannels(int red, int green, int blue, Target target) {
        int packed = applyRgb(red, green, blue, target);
        return new int[]{
                (packed >>> 16) & 255,
                (packed >>> 8) & 255,
                packed & 255
        };
    }

    public static float applyAlpha(float alpha, Target target) {
        if (!needsBlend(target)) {
            return alpha;
        }
        return clamp01(alpha * getAlpha(target));
    }

    public static int applyAlphaInt(int alpha, Target target) {
        if (!needsBlend(target)) {
            return clamp255(alpha);
        }
        return clamp255(Math.round(alpha * getAlpha(target)));
    }

    private static float applyBrightness(float value, float brightness) {
        value = clamp01(value);
        if (brightness > 0.0F) {
            return clamp01(value + (1.0F - value) * brightness);
        }
        if (brightness < 0.0F) {
            return clamp01(value * (1.0F + brightness));
        }
        return value;
    }

    private static ColorCorrection module() {
        return ColorCorrection.getInstance();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public enum Target {
        NONE,
        BLOCKS,
        FLUIDS,
        SKY,
        CLOUDS,
        PLAYERS,
        ENTITIES
    }
}
