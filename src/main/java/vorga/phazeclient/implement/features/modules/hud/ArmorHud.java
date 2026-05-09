package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.implement.menu.MenuPalettes;

public final class ArmorHud extends Module {
    private static final ArmorHud INSTANCE = new ArmorHud();
    private static final float DEFAULT_HUD_X = 22.0f;
    private static final float DEFAULT_HUD_Y = 60.0f;
    private static final float DEFAULT_HUD_SCALE = 1.0f;
    private static final float MIN_HUD_SCALE = 0.5f;
    private static final float MAX_HUD_SCALE = 5.0f;

    public final SectionSetting mainSection = new SectionSetting("Main");
    public final BooleanSetting textShadow = new BooleanSetting("Text Shadow", "Draw text with vanilla shadow").setValue(true);
    public final BooleanSetting background = new BooleanSetting("Background", "Draw scoreboard-style background").setValue(false);
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
            .setValue(30)
            .visible(() -> background.isValue() && !isVanillaPreset());
    public final ValueSetting backgroundBlurRadius = new ValueSetting("Background Blur Radius", "Blur radius for HUD background")
            .range(0, 32)
            .setValue(0)
            .visible(() -> background.isValue());
    public final SectionSetting colorSection = new SectionSetting("Color Settings");
    public final SectionSetting otherSection = new SectionSetting("Other");
    public final SelectSetting durabilityMode = new SelectSetting("Durability Mode", "How to display armor durability")
            .value("Units", "Percent")
            .selected("Units");

    private final HudBuffer hudBuffer = new HudBuffer();

    private float hudX = DEFAULT_HUD_X;
    private float hudY = DEFAULT_HUD_Y;
    private float hudScale = DEFAULT_HUD_SCALE;

    public static ArmorHud getInstance() {
        return INSTANCE;
    }

    private ArmorHud() {
        super("armor_hud", "Armor", ModuleCategory.HUD, true, false);
        mainSection.setFullWidth(true);
        textShadow.setFullWidth(true);
        background.setFullWidth(true);
        backgroundPreset.setFullWidth(true);
        colorBrightness.setFullWidth(true);
        backgroundOpacity.setFullWidth(true);
        backgroundBlurRadius.setFullWidth(true);
        durabilityMode.setFullWidth(true);
        setup(mainSection, textShadow, background, colorSection, backgroundPreset, colorBrightness, backgroundOpacity, backgroundBlurRadius, otherSection, durabilityMode);
    }

    @Override
    public String getDescription() {
        return "Shows armor and durability on HUD";
    }

    @Override
    public String getIcon() {
        return "armor_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
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
        return background.isValue() && backgroundBlurRadius.getInt() > 0;
    }

    public float getHudX() {
        return hudX;
    }

    public void setHudX(float hudX) {
        this.hudX = hudX;
    }

    public float getHudY() {
        return hudY;
    }

    public void setHudY(float hudY) {
        this.hudY = hudY;
    }

    public void resetHudTransform() {
        this.hudX = DEFAULT_HUD_X;
        this.hudY = DEFAULT_HUD_Y;
        this.hudScale = DEFAULT_HUD_SCALE;
    }

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = MathHelper.clamp(hudScale, MIN_HUD_SCALE, MAX_HUD_SCALE);
    }

    public float getMinHudScale() {
        return MIN_HUD_SCALE;
    }

    public float getMaxHudScale() {
        return MAX_HUD_SCALE;
    }

    public String formatDurability(int remaining, int max) {
        if ("Percent".equalsIgnoreCase(durabilityMode.getSelected())) {
            int percent = max <= 0 ? 0 : MathHelper.clamp(Math.round((remaining * 100.0f) / max), 0, 100);
            return percent + "%";
        }
        return String.valueOf(remaining);
    }

    public int getResolvedBackgroundColor(net.minecraft.client.MinecraftClient client) {
        if (isVanillaPreset()) {
            return client.options.getTextBackgroundColor(0.3F);
        }
        var palette = MenuPalettes.byName(backgroundPreset.getSelected());
        int presetColor = adjustBrightness(palette.chipActive(), colorBrightness.getValue() / 100.0f);
        int alpha = MathHelper.clamp(Math.round((backgroundOpacity.getValue() / 100.0f) * 255.0f), 0, 255);
        return (alpha << 24) | (presetColor & 0x00FFFFFF);
    }

    private boolean isVanillaPreset() {
        return "Vanilla".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    private static int adjustBrightness(int color, float multiplier) {
        int r = MathHelper.clamp(Math.round(((color >>> 16) & 0xFF) * multiplier), 0, 255);
        int g = MathHelper.clamp(Math.round(((color >>> 8) & 0xFF) * multiplier), 0, 255);
        int b = MathHelper.clamp(Math.round((color & 0xFF) * multiplier), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
