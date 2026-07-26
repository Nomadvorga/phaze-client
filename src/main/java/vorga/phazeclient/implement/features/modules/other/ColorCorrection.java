package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.system.colorcorrection.ColorCorrectionShader;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.implement.menu.MenuScreen;

public final class ColorCorrection extends Module {
    private static final ColorCorrection INSTANCE = new ColorCorrection();

    public final SectionSetting mainSection = new SectionSetting("General");
    public final ValueSetting brightness = new ValueSetting("Brightness", "Adjust brightness").range(-1.0f, 1.0f).setValue(0.0f);
    public final ValueSetting contrast = new ValueSetting("Contrast", "Adjust contrast").range(0.0f, 3.0f).setValue(1.0f);
    public final ValueSetting saturation = new ValueSetting("Saturation", "Adjust saturation").range(0.0f, 3.0f).setValue(1.0f);
    public final ValueSetting hue = new ValueSetting("Hue", "Rotate colors around the hue wheel").range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting gamma = new ValueSetting("Gamma", "Adjust gamma").range(0.2f, 3.0f).setValue(1.0f);
    public final ValueSetting temperature = new ValueSetting("Temperature", "Warm/cool tint").range(-1.0f, 1.0f).setValue(0.0f);
    public final ValueSetting vibrance = new ValueSetting("Vibrance", "Boost muted colors").range(0.0f, 2.0f).setValue(0.0f);
    public final SectionSetting advancedSection = new SectionSetting("Advanced");

    private final WorldTargetSettings blocks = new WorldTargetSettings("Blocks", "World color correction for blocks", false);
    private final WorldTargetSettings fluids = new WorldTargetSettings("Fluids", "World color correction for fluids", false);
    private final WorldTargetSettings sky = new WorldTargetSettings("Sky", "World color correction for empty sky", true);
    private final WorldTargetSettings clouds = new WorldTargetSettings("Clouds", "World color correction for clouds", true);
    private final WorldTargetSettings players = new WorldTargetSettings("Players", "World color correction for players", false);
    private final WorldTargetSettings entities = new WorldTargetSettings("Entities", "World color correction for entities", false);

    public final ColorCorrectionShader shader;
    private ColorCorrection() {
        super("color_correction", "Color Correction", ModuleCategory.OTHER, true, false);

        brightness.setFullWidth(true);
        contrast.setFullWidth(true);
        saturation.setFullWidth(true);
        hue.setFullWidth(true);
        gamma.setFullWidth(true);
        temperature.setFullWidth(true);
        vibrance.setFullWidth(true);
        advancedSection.setFullWidth(true);

        setup(
                mainSection,
                brightness, contrast, saturation, hue, gamma, temperature, vibrance,
                advancedSection,
                blocks.group, fluids.group, sky.group, clouds.group, players.group, entities.group
        );

        shader = new ColorCorrectionShader(this);
    }

    public static ColorCorrection getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Color correction post-process effect";
    }

    @Override
    public String getIcon() {
        return "color_correction.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getBrightness() { return brightness.getValue(); }
    public float getContrast() { return contrast.getValue(); }
    public float getSaturation() { return saturation.getValue(); }
    public float getHue() { return hue.getValue(); }
    public float getGamma() { return gamma.getValue(); }
    public float getTemperature() { return temperature.getValue(); }
    public float getVibrance() { return vibrance.getValue(); }

    public void tick() {
    }

    public static void openWorldColorMenu() {
        MenuScreen.INSTANCE.openGui();
        MenuScreen.INSTANCE.setCategory(ModuleCategory.OTHER);
        MenuScreen.INSTANCE.openModuleDetail(getInstance());
    }

    public boolean hasAdvancedCorrection(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings != null && settings.isActive();
    }

    public float getAdvancedRed(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings == null ? 1.0F : settings.red.getValue();
    }

    public float getAdvancedGreen(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings == null ? 1.0F : settings.green.getValue();
    }

    public float getAdvancedBlue(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings == null ? 1.0F : settings.blue.getValue();
    }

    public float getAdvancedAlpha(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings == null || !settings.supportsAlpha ? 1.0F : settings.alpha.getValue();
    }

    public float getAdvancedBrightness(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings == null ? 0.0F : settings.brightness.getValue();
    }

    public float getAdvancedSaturation(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings == null ? 1.0F : settings.saturation.getValue();
    }

    public boolean supportsAdvancedAlpha(WorldColorCorrectionController.Target target) {
        WorldTargetSettings settings = getTargetSettings(target);
        return settings != null && settings.supportsAlpha;
    }

    private WorldTargetSettings getTargetSettings(WorldColorCorrectionController.Target target) {
        return switch (target) {
            case BLOCKS -> blocks;
            case FLUIDS -> fluids;
            case SKY -> sky;
            case CLOUDS -> clouds;
            case PLAYERS -> players;
            case ENTITIES -> entities;
            default -> null;
        };
    }

    private final class WorldTargetSettings {
        private final boolean supportsAlpha;
        private final GroupSetting group;
        private final ValueSetting red;
        private final ValueSetting green;
        private final ValueSetting blue;
        private final ValueSetting alpha;
        private final ValueSetting brightness;
        private final ValueSetting saturation;

        private WorldTargetSettings(String name, String description, boolean supportsAlpha) {
            this.supportsAlpha = supportsAlpha;
            this.red = slider("Red", "Red multiplier", 0.0F, 2.0F, 1.0F);
            this.green = slider("Green", "Green multiplier", 0.0F, 2.0F, 1.0F);
            this.blue = slider("Blue", "Blue multiplier", 0.0F, 2.0F, 1.0F);
            this.alpha = slider("Alpha", "Alpha multiplier", 0.0F, 1.0F, 1.0F);
            this.alpha.visible(() -> this.supportsAlpha);
            this.brightness = slider("Brightness", "Brightness offset", -1.0F, 1.0F, 0.0F);
            this.saturation = slider("Saturation", "Saturation multiplier", 0.0F, 2.0F, 1.0F);

            this.group = new GroupSetting(name, description, false)
                    .settings(buildSettings())
                    .colorPickerStyleWindow(true)
                    .popupWidth(228.0F)
                    .popupMaxHeight(232.0F);
            this.group.setFullWidth(true);
        }

        private Setting[] buildSettings() {
            if (supportsAlpha) {
                return new Setting[]{red, green, blue, alpha, brightness, saturation};
            }
            return new Setting[]{red, green, blue, brightness, saturation};
        }

        private boolean isActive() {
            if (red.getValue() != 1.0F || green.getValue() != 1.0F || blue.getValue() != 1.0F) {
                return true;
            }
            if (brightness.getValue() != 0.0F || saturation.getValue() != 1.0F) {
                return true;
            }
            return supportsAlpha && alpha.getValue() != 1.0F;
        }
    }

    private static ValueSetting slider(String name, String description, float min, float max, float defaultValue) {
        ValueSetting setting = new ValueSetting(name, description)
                .range(min, max)
                .step(0.01F)
                .setValue(defaultValue);
        setting.setFullWidth(true);
        return setting;
    }
}
