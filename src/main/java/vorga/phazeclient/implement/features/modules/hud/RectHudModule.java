package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.implement.menu.MenuPalettes;

public abstract class RectHudModule extends Module {
    private static final float DEFAULT_HUD_X = 22.0f;
    private static final float DEFAULT_HUD_Y = 22.0f;
    private static final float DEFAULT_HUD_SCALE = 1.0f;
    private static final float MIN_HUD_SCALE = 0.5f;
    private static final float MAX_HUD_SCALE = 8.0f;
    private final float defaultHudX;
    private final float defaultHudY;
    private final float defaultHudScale;

    public final SectionSetting mainSection = new SectionSetting("General");
    public final BooleanSetting textShadow = new BooleanSetting("Text Shadow", "Draw text with vanilla shadow").setValue(true);
    public final BooleanSetting background = new BooleanSetting("Background", "Draw scoreboard-style background").setValue(true);
    public final BooleanSetting showBrackets = new BooleanSetting("Show Brackets", "Show brackets around text when background is disabled").setValue(false).visible(() -> !background.isValue());
    public final SelectSetting backgroundPreset = new SelectSetting("Background Preset", "Choose preset background style")
            .value(
                    "Vanilla",
                    "Lunar Blue",
                    "Mocha Gold",
                    "Rose Quartz",
                    "Emerald Frost",
                    "Arctic Mint",
                    "Crimson Silk",
                    "Solar Ember",
                    "Midnight Bloom",
                    "Desert Mirage",
                    "Sapphire Steel",
                    "Velvet Plum",
                    "Frosted Peach",
                    "Moss Smoke",
                    "Polar Night",
                    "Snow",
                    "Obsidian",
                    "Nebula",
                    "Coral",
                    "Jade",
                    "Sunset",
                    "Violet",
                    "Ocean",
                    "Custom Color",
                    "Gradient"
            )
            .selected("Vanilla")
            .visible(() -> background.isValue());
    public final ValueSetting colorBrightness = new ValueSetting("Color Brightness", "Adjust main color brightness")
            .range(0, 200)
            .setValue(100)
            .visible(() -> background.isValue() && !isVanillaPreset() && !isCustomColorPreset() && !isGradientPreset());
    public final ValueSetting backgroundOpacity = new ValueSetting("Background Opacity", "Custom background opacity")
            .range(0, 100)
            .setValue(50)
            .visible(() -> background.isValue() && !isVanillaPreset());
    public final ColorSetting customBackgroundColor = new ColorSetting("Custom Background Color", "Color used by the custom HUD background")
            .value(0xFF203447)
            .noAlpha()
            .popupRow()
            .visible(() -> background.isValue() && isCustomColorPreset());
    public final ColorSetting gradientStartColor = new ColorSetting("Gradient Start Color", "First color of the HUD gradient")
            .value(0xFF1B4965)
            .noAlpha()
            .popupRow()
            .visible(() -> background.isValue() && isGradientPreset());
    public final ColorSetting gradientEndColor = new ColorSetting("Gradient End Color", "Second color of the HUD gradient")
            .value(0xFF5FA8D3)
            .noAlpha()
            .popupRow()
            .visible(() -> background.isValue() && isGradientPreset());
    public final ValueSetting gradientSpeed = new ValueSetting("Gradient Speed", "How quickly the gradient flows between its colors")
            .range(0.0f, 4.0f)
            .step(0.05f)
            .setValue(0.75f)
            .visible(() -> background.isValue() && isGradientPreset());
    public final SelectSetting gradientDirection = new SelectSetting("Gradient Direction", "Direction of the animated HUD gradient")
            .value("Left to Right", "Right to Left", "Top to Bottom", "Bottom to Top", "Diagonal Down", "Diagonal Up", "Pulse")
            .selected("Left to Right")
            .visible(() -> background.isValue() && isGradientPreset());
    public final ValueSetting backgroundBlurRadius = new ValueSetting("Background Blur Radius", "Blur radius for HUD background")
            .range(0.0f, 32.0f)
            .step(0.25f)
            .setValue(0)
            .visible(() -> background.isValue());
    // The Color Settings divider is meaningful only when at least one
    // of its children is visible, and every child is gated by
    // {@code background.isValue()}. So when Background is off the whole
    // section collapses, otherwise we'd render an empty header floating
    // above nothing.
    public final SectionSetting colorSection = new SectionSetting("Color Settings")
            .visible(() -> background.isValue());
    public final SectionSetting otherSection = new SectionSetting("Other");
    public final ValueSetting cornerRounding = new ValueSetting("Corner Rounding", "Round this HUD background's corners")
            .range(0.0F, 10.0F)
            .step(0.5F)
            .setValue(0.0F)
            .visible(() -> background.isValue());

    private final HudBuffer hudBuffer = new HudBuffer();
    private long gradientLastUpdateNanos = -1L;
    private float gradientAnimationOffset = 0.0F;

    private float hudX = DEFAULT_HUD_X;
    private float hudY = DEFAULT_HUD_Y;
    private float hudScale = DEFAULT_HUD_SCALE;
    /**
     * Screen the absolute coordinates above were measured on. The
     * coordinates are the single source of truth; this only says what
     * they are relative to, so they can be rescaled when the window
     * changes size.
     */
    private int hudRefWidth = -1;
    private int hudRefHeight = -1;

    protected RectHudModule(String name, String visibleName) {
        this(name, visibleName, DEFAULT_HUD_X, DEFAULT_HUD_Y, DEFAULT_HUD_SCALE);
    }

    protected RectHudModule(String name, String visibleName, float defaultHudX, float defaultHudY, float defaultHudScale) {
        this(name, visibleName, ModuleCategory.HUD, defaultHudX, defaultHudY, defaultHudScale);
    }

    /**
     * Category-customisable variant of the standard {@code RectHudModule}
     * constructor. Modules that want to inherit the full drag / resize /
     * background / blur pipeline but show up in a non-HUD tab of the
     * client menu (e.g. {@code OTHER}) should call this overload and pass
     * the desired {@link ModuleCategory}. All other behaviour matches the
     * 5-arg constructor exactly - the shared body is delegated, so any
     * future change to the inherited setting wiring only needs to be
     * applied here.
     */
    protected RectHudModule(String name, String visibleName, ModuleCategory category, float defaultHudX, float defaultHudY, float defaultHudScale) {
        super(name, visibleName, category, true, false);
        this.defaultHudX = defaultHudX;
        this.defaultHudY = defaultHudY;
        this.defaultHudScale = defaultHudScale;
        this.hudX = defaultHudX;
        this.hudY = defaultHudY;
        this.hudScale = defaultHudScale;
        mainSection.setFullWidth(true);
        textShadow.setFullWidth(true);
        background.setFullWidth(true);
        showBrackets.setFullWidth(true);
        backgroundPreset.setFullWidth(true);
        colorBrightness.setFullWidth(true);
        backgroundOpacity.setFullWidth(true);
        customBackgroundColor.setFullWidth(true);
        gradientStartColor.setFullWidth(true);
        gradientEndColor.setFullWidth(true);
        gradientSpeed.setFullWidth(true);
        gradientDirection.setFullWidth(true);
        backgroundBlurRadius.setFullWidth(true);
        otherSection.setFullWidth(true);
        cornerRounding.setFullWidth(true);
        setup(mainSection, textShadow, background, showBrackets, colorSection, backgroundPreset, colorBrightness, backgroundOpacity,
                customBackgroundColor, gradientStartColor, gradientEndColor, gradientSpeed, gradientDirection, backgroundBlurRadius,
                otherSection, cornerRounding);
    }

    public float getHudX() {
        resolveForCurrentScreen();
        return hudX;
    }

    public void setHudX(float hudX) {
        resolveForCurrentScreen();
        this.hudX = hudX;
        markResolvedForCurrentScreen();
    }

    public float getHudY() {
        resolveForCurrentScreen();
        return hudY;
    }

    public void setHudY(float hudY) {
        resolveForCurrentScreen();
        this.hudY = hudY;
        markResolvedForCurrentScreen();
    }

    public float getHudScale() {
        return hudScale;
    }

    /**
     * Runtime scale used by the renderer. HUD scale 1.0 is the new visual
     * baseline and intentionally matches the old 2.0 size, while configs
     * and the editor continue to store and display the intuitive 1.0 value.
     */
    public float getRenderHudScale() {
        return hudScale * 2.0F;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = MathHelper.clamp(hudScale, getMinHudScale(), getMaxHudScale());
        markResolvedForCurrentScreen();
    }

    public float getMinHudScale() {
        return MIN_HUD_SCALE;
    }

    public float getMaxHudScale() {
        return MAX_HUD_SCALE;
    }

    public HudBuffer getHudBuffer() {
        return hudBuffer;
    }

    /**
     * Whether this HUD currently renders a live blur backdrop. Used by the
     * HUD batching to force unlimited refresh, since a throttled blur would
     * freeze the world behind it between refreshes.
     */
    public boolean hasActiveBackgroundBlur() {
        // Float comparison instead of getInt(): the renderRectHud blur
        // gate uses backgroundBlurRadius.getValue() > 0.0f, and the two
        // paths MUST agree on whether a HUD is blur-active. A truncated
        // getInt() would round 0 < r < 1 down to 0 and falsely report
        // "no blur" here while the renderer still spawns the blur pass
        // - which would then land the HUD in BOTH the cached FBO (Pass 1
        // thinks no blur) AND the live blur pass (Pass 2 still draws),
        // producing the imprinted-HUD ghost.
        return background.isValue() && backgroundBlurRadius.getValue() > 0.0f;
    }

    /**
     * Animated gradients ask the shared HUD cache for a smooth 60 FPS
     * refresh. This keeps the effect fluid without forcing an uncapped draw
     * pass or disabling the optimizer for every other HUD.
     */
    public boolean hasActiveAnimatedBackground() {
        return background.isValue() && isGradientPreset() && gradientSpeed.getValue() > 0.0F;
    }

    public void resetHudTransform() {
        this.hudX = defaultHudX;
        this.hudY = defaultHudY;
        this.hudScale = defaultHudScale;
        markResolvedForCurrentScreen();
    }

    public float getHudXRatio() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0.0f;
        }
        resolveForCurrentScreen();
        return MathHelper.clamp(hudX / Math.max(1.0f, client.getWindow().getScaledWidth()), 0.0f, 1.0f);
    }

    public float getHudYRatio() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0.0f;
        }
        resolveForCurrentScreen();
        return MathHelper.clamp(hudY / Math.max(1.0f, client.getWindow().getScaledHeight()), 0.0f, 1.0f);
    }

    public void setHudXRatio(float ratio) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        this.hudX = MathHelper.clamp(ratio, 0.0f, 1.0f)
                * Math.max(1, client.getWindow().getScaledWidth());
        markResolvedForCurrentScreen();
    }

    public void setHudYRatio(float ratio) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        this.hudY = MathHelper.clamp(ratio, 0.0f, 1.0f)
                * Math.max(1, client.getWindow().getScaledHeight());
        markResolvedForCurrentScreen();
    }

    public int getResolvedBackgroundColor(MinecraftClient client) {
        if (isVanillaPreset()) {
            return client.options.getTextBackgroundColor(0.5F);
        }
        if (isCustomColorPreset()) {
            return applyColorOptions(customBackgroundColor.getColor());
        }
        if (isGradientPreset()) {
            return getResolvedGradientStartColor();
        }
        var palette = MenuPalettes.byName(backgroundPreset.getSelected());
        int presetColor = adjustBrightness(palette.chipActive(), colorBrightness.getValue() / 100.0f);
        int alpha = MathHelper.clamp(Math.round((backgroundOpacity.getValue() / 100.0f) * 255.0f), 0, 255);
        return (alpha << 24) | (presetColor & 0x00FFFFFF);
    }

    protected boolean isVanillaPreset() {
        return "Vanilla".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    public boolean isCustomColorPreset() {
        return "Custom Color".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    public boolean isGradientPreset() {
        return "Gradient".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    public int getResolvedGradientStartColor() {
        return applyColorOptions(gradientStartColor.getColor());
    }

    public int getResolvedGradientEndColor() {
        return applyColorOptions(gradientEndColor.getColor());
    }

    public String getGradientDirection() {
        return gradientDirection.getSelected();
    }

    /**
     * Distance travelled by the gradient pattern in normalized HUD space.
     * One full unit is one complete start -> end -> start wave.
     */
    public float getGradientAnimationOffset(long nowMs) {
        long nowNanos = System.nanoTime();
        if (gradientLastUpdateNanos < 0L) {
            gradientLastUpdateNanos = nowNanos;
            return gradientAnimationOffset;
        }
        float deltaSeconds = MathHelper.clamp((nowNanos - gradientLastUpdateNanos) / 1_000_000_000.0F, 0.0F, 0.10F);
        gradientLastUpdateNanos = nowNanos;
        float speed = Math.max(0.0F, gradientSpeed.getValue());
        gradientAnimationOffset = (gradientAnimationOffset + deltaSeconds * speed * 0.5F) % 1.0F;
        return gradientAnimationOffset;
    }

    /**
     * Writes top-left, bottom-left, top-right and bottom-right colors into
     * the caller-provided array. The renderer reuses this small array for
     * every frame, so animated backgrounds do not allocate while rendering.
     */
    public void writeGradientColors(int[] colors, long nowMs) {
        if (colors == null || colors.length < 4) {
            return;
        }
        if (!isGradientPreset()) {
            int color = getResolvedBackgroundColor(MinecraftClient.getInstance());
            colors[0] = color;
            colors[1] = color;
            colors[2] = color;
            colors[3] = color;
            return;
        }

        int first = getResolvedGradientStartColor();
        int second = getResolvedGradientEndColor();
        int midpoint = blend(first, second, 0.5F);
        switch (gradientDirection.getSelected()) {
            case "Right to Left" -> setGradientCorners(colors, second, second, first, first);
            case "Top to Bottom" -> setGradientCorners(colors, first, second, first, second);
            case "Bottom to Top" -> setGradientCorners(colors, second, first, second, first);
            case "Diagonal Down" -> setGradientCorners(colors, first, midpoint, midpoint, second);
            case "Diagonal Up" -> setGradientCorners(colors, midpoint, second, first, midpoint);
            case "Pulse" -> setGradientCorners(colors, first, first, first, first);
            default -> setGradientCorners(colors, first, first, second, second);
        }
    }

    private int applyColorOptions(int color) {
        int alpha = MathHelper.clamp(Math.round(((color >>> 24) & 0xFF) * (backgroundOpacity.getValue() / 100.0F)), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static void setGradientCorners(int[] colors, int topLeft, int bottomLeft, int topRight, int bottomRight) {
        colors[0] = topLeft;
        colors[1] = bottomLeft;
        colors[2] = topRight;
        colors[3] = bottomRight;
    }

    private static int blend(int first, int second, float progress) {
        float t = MathHelper.clamp(progress, 0.0F, 1.0F);
        int a = Math.round(((first >>> 24) & 0xFF) + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * t);
        int r = Math.round(((first >>> 16) & 0xFF) + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * t);
        int g = Math.round(((first >>> 8) & 0xFF) + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * t);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustBrightness(int color, float multiplier) {
        int r = MathHelper.clamp(Math.round(((color >>> 16) & 0xFF) * multiplier), 0, 255);
        int g = MathHelper.clamp(Math.round(((color >>> 8) & 0xFF) * multiplier), 0, 255);
        int b = MathHelper.clamp(Math.round((color & 0xFF) * multiplier), 0, 255);
        return (r << 16) | (g << 8) | b;
    }


    /**
     * Re-scales the position when the window has changed size since
     * these coordinates were set.
     *
     * <p>Single source of truth on purpose. An earlier version kept a
     * stored ratio field alongside the absolute coordinates; the two
     * could drift, and a stale ratio silently overwrote a correct
     * position. Here the absolute values are the truth and
     * {@code hudRefWidth/Height} only records which screen they were
     * measured on, so there is nothing to fall out of sync with.
     */
    private void resolveForCurrentScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }
        if (hudRefWidth <= 0 || hudRefHeight <= 0) {
            // No reference yet - treat the current coordinates as
            // belonging to this screen.
            hudRefWidth = screenWidth;
            hudRefHeight = screenHeight;
            return;
        }
        if (screenWidth == hudRefWidth && screenHeight == hudRefHeight) {
            return;
        }
        this.hudX = this.hudX * (screenWidth / (float) hudRefWidth);
        this.hudY = this.hudY * (screenHeight / (float) hudRefHeight);
        this.hudRefWidth = screenWidth;
        this.hudRefHeight = screenHeight;
    }


    /**
     * Restores a saved position together with the screen it was
     * measured on, in one step.
     *
     * <p>Setting x and y separately does not work: each setter first
     * rescales the current coordinates to the live screen, so the
     * second call would rescale the value the first one just wrote.
     * Applying both plus the reference atomically avoids that, and the
     * next read rescales once, correctly.
     */
    public void setHudPosition(float x, float y, int refWidth, int refHeight) {
        this.hudX = x;
        this.hudY = y;
        if (refWidth > 0 && refHeight > 0) {
            this.hudRefWidth = refWidth;
            this.hudRefHeight = refHeight;
        } else {
            markResolvedForCurrentScreen();
        }
    }

    /** Marks the current coordinates as belonging to the current screen. */
    private void markResolvedForCurrentScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        this.hudRefWidth = client.getWindow().getScaledWidth();
        this.hudRefHeight = client.getWindow().getScaledHeight();
    }

    /** Screen size these coordinates were measured on; -1 = unknown. */
    public int getHudRefWidth() {
        return hudRefWidth;
    }

    public int getHudRefHeight() {
        return hudRefHeight;
    }

    public void setHudReference(int width, int height) {
        this.hudRefWidth = width;
        this.hudRefHeight = height;
    }
}
