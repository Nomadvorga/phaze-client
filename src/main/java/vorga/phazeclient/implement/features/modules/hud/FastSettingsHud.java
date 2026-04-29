package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class FastSettingsHud extends Module {
    private static final FastSettingsHud INSTANCE = new FastSettingsHud();
    private boolean applying = false;
    private long lastAppliedSignature = Long.MIN_VALUE;

    public final SectionSetting colorSection = new SectionSetting("Color Settings");
    public final BooleanSetting textShadow = new BooleanSetting("Text Shadow", "Apply to all HUD modules").setValue(true);
    public final BooleanSetting background = new BooleanSetting("Background", "Apply to all HUD modules").setValue(true);
    public final SelectSetting backgroundPreset = new SelectSetting("Background Preset", "Apply to all HUD modules")
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
                    "Polar Night"
            )
            .selected("Vanilla")
            .visible(() -> background.isValue());
    public final ValueSetting colorBrightness = new ValueSetting("Color Brightness", "Apply to all HUD modules")
            .range(0, 200)
            .setValue(100)
            .visible(() -> background.isValue() && !isVanillaPreset());
    public final ValueSetting backgroundOpacity = new ValueSetting("Background Opacity", "Apply to all HUD modules")
            .range(0, 100)
            .setValue(30)
            .visible(() -> background.isValue() && !isVanillaPreset());
    public final ValueSetting backgroundBlurRadius = new ValueSetting("Background Blur Radius", "Apply to all HUD modules")
            .range(0, 32)
            .setValue(0)
            .visible(() -> background.isValue());

    public final SectionSetting batchingSection = new SectionSetting("Batching");
    public final BooleanSetting hudBatching = new BooleanSetting("HUD Batching", "Apply to all HUD modules").setValue(false);
    public final BooleanSetting forceHudUpdate = new BooleanSetting("Force HUD Update", "Apply to all HUD modules")
            .setValue(false)
            .visible(() -> hudBatching.isValue());
    public final ValueSetting hudFps = new ValueSetting("HUD FPS", "Apply to all HUD modules")
            .range(5, 120)
            .setValue(60)
            .visible(() -> hudBatching.isValue() && !forceHudUpdate.isValue());

    public static FastSettingsHud getInstance() {
        return INSTANCE;
    }

    private FastSettingsHud() {
        super("fast_settings_hud", "Fast Settings", ModuleCategory.HUD, true, false);

        textShadow.setFullWidth(true);
        background.setFullWidth(true);
        backgroundPreset.setFullWidth(true);
        colorBrightness.setFullWidth(true);
        backgroundOpacity.setFullWidth(true);
        backgroundBlurRadius.setFullWidth(true);
        hudBatching.setFullWidth(true);
        forceHudUpdate.setFullWidth(true);
        hudFps.setFullWidth(true);

        setup(
                colorSection,
                textShadow,
                background,
                backgroundPreset,
                colorBrightness,
                backgroundOpacity,
                backgroundBlurRadius,
                batchingSection,
                hudBatching,
                forceHudUpdate,
                hudFps
        );
    }

    @Override
    public String getDescription() {
        return "Global HUD settings sync";
    }

    @Override
    public String getIcon() {
        return "settings.png";
    }

    public void applyToAllHudModules() {
        if (applying) {
            return;
        }
        applying = true;
        try {
            applyToRect(FpsHud.getInstance());
            applyToRect(CpsHud.getInstance());
            applyToRect(ReachHud.getInstance());
            applyToRect(SprintHud.getInstance());
            applyToRect(CoordinatesHud.getInstance());
            applyToRect(PingHud.getInstance());
            applyToRect(KeystrokesHud.getInstance());
            applyToRect(PotionHud.getInstance());
            applyToRect(DayCounterHud.getInstance());
            applyToRect(StatsHud.getInstance());
            applyToRect(DirectionHud.getInstance());
            applyToRect(TabHud.getInstance());
            applyToRect(NametagHud.getInstance());
            applyToRect(TimeHud.getInstance());
            applyToRect(SessionTimeHud.getInstance());
            applyToArmor(ArmorHud.getInstance());
        } finally {
            applying = false;
        }
    }

    public void applyToAllHudModulesIfDirty() {
        long signature = computeSignature();
        if (signature == lastAppliedSignature) {
            return;
        }
        applyToAllHudModules();
        lastAppliedSignature = signature;
    }

    private void applyToRect(RectHudModule module) {
        module.textShadow.setValue(textShadow.isValue());
        module.background.setValue(background.isValue());
        module.backgroundPreset.setSelected(backgroundPreset.getSelected());
        module.colorBrightness.setValue(clampValue(colorBrightness, module.colorBrightness));
        module.backgroundOpacity.setValue(clampValue(backgroundOpacity, module.backgroundOpacity));
        module.backgroundBlurRadius.setValue(clampValue(backgroundBlurRadius, module.backgroundBlurRadius));
        module.hudBatching.setValue(hudBatching.isValue());
        module.forceHudUpdate.setValue(forceHudUpdate.isValue());
        module.hudFps.setValue(clampValue(hudFps, module.hudFps));
    }

    private void applyToArmor(ArmorHud module) {
        module.textShadow.setValue(textShadow.isValue());
        module.background.setValue(background.isValue());
        module.backgroundPreset.setSelected(backgroundPreset.getSelected());
        module.colorBrightness.setValue(clampValue(colorBrightness, module.colorBrightness));
        module.backgroundOpacity.setValue(clampValue(backgroundOpacity, module.backgroundOpacity));
        module.backgroundBlurRadius.setValue(clampValue(backgroundBlurRadius, module.backgroundBlurRadius));
        module.hudBatching.setValue(hudBatching.isValue());
        module.forceHudUpdate.setValue(forceHudUpdate.isValue());
        module.hudFps.setValue(clampValue(hudFps, module.hudFps));
    }

    private static float clampValue(ValueSetting source, ValueSetting target) {
        return MathHelper.clamp(source.getValue(), target.getMin(), target.getMax());
    }

    private boolean isVanillaPreset() {
        return "Vanilla".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    private long computeSignature() {
        long h = 0x9E3779B97F4A7C15L;
        h = (h * 0x100000001B3L) ^ (textShadow.isValue() ? 1 : 0);
        h = (h * 0x100000001B3L) ^ (background.isValue() ? 1 : 0);
        h = (h * 0x100000001B3L) ^ safeHash(backgroundPreset.getSelected());
        h = (h * 0x100000001B3L) ^ Math.round(colorBrightness.getValue() * 100.0f);
        h = (h * 0x100000001B3L) ^ Math.round(backgroundOpacity.getValue() * 100.0f);
        h = (h * 0x100000001B3L) ^ Math.round(backgroundBlurRadius.getValue() * 100.0f);
        h = (h * 0x100000001B3L) ^ (hudBatching.isValue() ? 1 : 0);
        h = (h * 0x100000001B3L) ^ (forceHudUpdate.isValue() ? 1 : 0);
        h = (h * 0x100000001B3L) ^ Math.round(hudFps.getValue() * 100.0f);
        return h;
    }

    private static int safeHash(String value) {
        return value == null ? 0 : value.hashCode();
    }
}
