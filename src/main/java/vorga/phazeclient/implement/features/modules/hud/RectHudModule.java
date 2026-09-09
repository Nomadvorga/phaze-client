package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudScaleLimits;
import vorga.phazeclient.implement.menu.MenuPalettes;

public abstract class RectHudModule extends Module {
    private static final float DEFAULT_HUD_X = 22.0f;
    private static final float DEFAULT_HUD_Y = 22.0f;
    private static final float DEFAULT_HUD_SCALE = 1.0f;
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
                    "Ocean"
            )
            .selected("Vanilla")
            .visible(() -> background.isValue());
    public final ValueSetting colorBrightness = new ValueSetting("Color Brightness", "Adjust main color brightness")
            .range(0, 200)
            .setValue(100)
            .visible(() -> background.isValue() && !isVanillaPreset());
    public final ValueSetting backgroundOpacity = new ValueSetting("Background Opacity", "Custom background opacity")
            .range(0, 100)
            .setValue(50)
            .visible(() -> background.isValue() && !isVanillaPreset());
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

    private final HudBuffer hudBuffer = new HudBuffer();

    private float hudX = DEFAULT_HUD_X;
    private float hudY = DEFAULT_HUD_Y;
    private float hudScale = DEFAULT_HUD_SCALE;
    private float hudXRatio = Float.NaN;
    private float hudYRatio = Float.NaN;

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
        backgroundBlurRadius.setFullWidth(true);
        setup(mainSection, textShadow, background, showBrackets, colorSection, backgroundPreset, colorBrightness, backgroundOpacity, backgroundBlurRadius);
    }

    public float getHudX() {
        return hudX;
    }

    public void setHudX(float hudX) {
        this.hudX = hudX;
        syncStoredHudRatios();
    }

    public float getHudY() {
        return hudY;
    }

    public void setHudY(float hudY) {
        this.hudY = hudY;
        syncStoredHudRatios();
    }

    public float getHudScale() {
        return hudScale;
    }

    /**
     * Visual scale shared by every rectangular HUD.  Keeping the stored value
     * independent preserves existing configs: x1.00 is now rendered at the
     * former x2.00 size, while the former x1.00 look is selected with x0.50.
     */
    public float getRenderHudScale() {
        return hudScale * HudScaleLimits.RENDER_MULTIPLIER;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = HudScaleLimits.normalize(hudScale);
        syncStoredHudRatios();
    }

    public float getMinHudScale() {
        return HudScaleLimits.MIN;
    }

    public float getMaxHudScale() {
        return HudScaleLimits.MAX;
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

    public void resetHudTransform() {
        this.hudX = defaultHudX;
        this.hudY = defaultHudY;
        this.hudScale = defaultHudScale;
        syncStoredHudRatios();
    }

    public float getHudXRatio() {
        if (!Float.isNaN(hudXRatio)) {
            return hudXRatio;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0.0f;
        }
        return MathHelper.clamp(hudX / Math.max(1.0f, client.getWindow().getScaledWidth()), 0.0f, 1.0f);
    }

    public float getHudYRatio() {
        if (!Float.isNaN(hudYRatio)) {
            return hudYRatio;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0.0f;
        }
        return MathHelper.clamp(hudY / Math.max(1.0f, client.getWindow().getScaledHeight()), 0.0f, 1.0f);
    }

    public void setHudXRatio(float ratio) {
        MinecraftClient client = MinecraftClient.getInstance();
        this.hudXRatio = MathHelper.clamp(ratio, 0.0f, 1.0f);
        if (client != null && client.getWindow() != null) {
            this.hudX = this.hudXRatio * Math.max(1, client.getWindow().getScaledWidth());
        }
    }

    public void setHudYRatio(float ratio) {
        MinecraftClient client = MinecraftClient.getInstance();
        this.hudYRatio = MathHelper.clamp(ratio, 0.0f, 1.0f);
        if (client != null && client.getWindow() != null) {
            this.hudY = this.hudYRatio * Math.max(1, client.getWindow().getScaledHeight());
        }
    }

    public int getResolvedBackgroundColor(MinecraftClient client) {
        if (isVanillaPreset()) {
            return client.options.getTextBackgroundColor(0.5F);
        }
        var palette = MenuPalettes.byName(backgroundPreset.getSelected());
        int presetColor = adjustBrightness(palette.chipActive(), colorBrightness.getValue() / 100.0f);
        int alpha = MathHelper.clamp(Math.round((backgroundOpacity.getValue() / 100.0f) * 255.0f), 0, 255);
        return (alpha << 24) | (presetColor & 0x00FFFFFF);
    }

    protected boolean isVanillaPreset() {
        return "Vanilla".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    private static int adjustBrightness(int color, float multiplier) {
        int r = MathHelper.clamp(Math.round(((color >>> 16) & 0xFF) * multiplier), 0, 255);
        int g = MathHelper.clamp(Math.round(((color >>> 8) & 0xFF) * multiplier), 0, 255);
        int b = MathHelper.clamp(Math.round((color & 0xFF) * multiplier), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private void syncStoredHudRatios() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        float screenWidth = Math.max(1.0f, client.getWindow().getScaledWidth());
        float screenHeight = Math.max(1.0f, client.getWindow().getScaledHeight());
        this.hudXRatio = MathHelper.clamp(this.hudX / screenWidth, 0.0f, 1.0f);
        this.hudYRatio = MathHelper.clamp(this.hudY / screenHeight, 0.0f, 1.0f);
    }
}
