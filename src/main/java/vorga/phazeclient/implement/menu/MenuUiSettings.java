package vorga.phazeclient.implement.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.config.ConfigManager;

public final class MenuUiSettings {
    public static final int PANORAMA_SPEED_SCALE_VERSION = 3;
    public static final double DEFAULT_PANORAMA_SPEED = 10.0D;
    public static final int DEFAULT_GUI_FPS_LIMIT = 60;
    public static final int MIN_GUI_FPS_LIMIT = 10;
    public static final int MAX_GUI_FPS_LIMIT = 260;
    public static final float DEFAULT_GUI_SCALE = 1.0F;
    public static final float MIN_GUI_SCALE = 0.9F;
    public static final float MAX_GUI_SCALE = 2.5F;
    public static final String DEFAULT_PANORAMA_PRESET_ID = "vanilla";
    public static final boolean DEFAULT_CUSTOM_MAIN_MENU_ENABLED = true;

    private static final MenuUiSettings INSTANCE = new MenuUiSettings();

    private double panoramaSpeed = DEFAULT_PANORAMA_SPEED;
    private int guiFpsLimit = DEFAULT_GUI_FPS_LIMIT;
    private float guiScale = DEFAULT_GUI_SCALE;
    private String selectedPanoramaPresetId = DEFAULT_PANORAMA_PRESET_ID;
    private boolean customMainMenuEnabled = DEFAULT_CUSTOM_MAIN_MENU_ENABLED;

    private MenuUiSettings() {
    }

    public static MenuUiSettings getInstance() {
        return INSTANCE;
    }

    public double getPanoramaSpeed() {
        return panoramaSpeed;
    }

    public int getGuiFpsLimit() {
        return guiFpsLimit;
    }

    public float getGuiScale() {
        return guiScale;
    }

    public PanoramaDescriptor getSelectedPanoramaPreset() {
        return MenuPanoramaRegistry.findById(selectedPanoramaPresetId);
    }

    public String getSelectedPanoramaPresetId() {
        PanoramaDescriptor selected = getSelectedPanoramaPreset();
        return selected == null ? DEFAULT_PANORAMA_PRESET_ID : selected.getId();
    }

    public boolean isCustomMainMenuEnabled() {
        return customMainMenuEnabled;
    }

    public void setPanoramaSpeed(double panoramaSpeed) {
        setPanoramaSpeedInternal(panoramaSpeed, true);
    }

    public void setGuiFpsLimit(int guiFpsLimit) {
        setGuiFpsLimitInternal(guiFpsLimit, true);
    }

    public void setGuiScale(float guiScale) {
        this.guiScale = quantizeGuiScale(guiScale);
        ConfigManager.getInstance().markDirty();
    }

    public void setSelectedPanoramaPreset(PanoramaDescriptor preset) {
        setSelectedPanoramaPresetInternal(preset, true);
    }

    public void setSelectedPanoramaPreset(PanoramaPreset preset) {
        setSelectedPanoramaPresetInternal(preset, true);
    }

    public void setSelectedPanoramaPreset(String presetId) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), true);
    }

    public void setCustomMainMenuEnabled(boolean enabled) {
        setCustomMainMenuEnabledInternal(enabled, true);
    }

    public void applyConfig(double panoramaSpeed, int guiFpsLimit, float guiScale, String presetId, boolean customMainMenuEnabled) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), false);
        setPanoramaSpeedInternal(panoramaSpeed, false);
        setGuiFpsLimitInternal(guiFpsLimit, false);
        this.guiScale = quantizeGuiScale(guiScale);
        setCustomMainMenuEnabledInternal(customMainMenuEnabled, false);
    }

    public void applyLegacyScaleV2Config(double panoramaSpeed, int guiFpsLimit, String presetId, boolean customMainMenuEnabled) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), false);
        setPanoramaSpeedInternal(Math.min(100.0D, panoramaSpeed * 2.0D), false);
        setGuiFpsLimitInternal(guiFpsLimit, false);
        setCustomMainMenuEnabledInternal(customMainMenuEnabled, false);
    }

    public void applyLegacyScaleV1Config(double panoramaSpeed, int guiFpsLimit, String presetId, boolean customMainMenuEnabled) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), false);
        setPanoramaSpeedInternal(Math.min(100.0D, panoramaSpeed * 10.0D), false);
        setGuiFpsLimitInternal(guiFpsLimit, false);
        setCustomMainMenuEnabledInternal(customMainMenuEnabled, false);
    }

    public void resetToDefaults() {
        applyConfig(DEFAULT_PANORAMA_SPEED, DEFAULT_GUI_FPS_LIMIT, DEFAULT_GUI_SCALE, DEFAULT_PANORAMA_PRESET_ID, DEFAULT_CUSTOM_MAIN_MENU_ENABLED);
    }

    private static float quantizeGuiScale(float value) {
        float clamped = clamp(value, MIN_GUI_SCALE, MAX_GUI_SCALE);
        // Work in tenths as an integer so every advertised value exists,
        // especially 1.1, without float drift skipping to 1.2.
        return Math.round(clamped * 10.0F) / 10.0F;
    }

    private void setPanoramaSpeedInternal(double panoramaSpeed, boolean markDirty) {
        this.panoramaSpeed = clamp(panoramaSpeed, 0.0D, 100.0D);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.getPanoramaSpeed().setValue(this.panoramaSpeed / 200.0D);
        }
        if (markDirty) {
            ConfigManager.getInstance().markDirty();
        }
    }

    private void setGuiFpsLimitInternal(int guiFpsLimit, boolean markDirty) {
        this.guiFpsLimit = clamp(guiFpsLimit, MIN_GUI_FPS_LIMIT, MAX_GUI_FPS_LIMIT);
        if (markDirty) {
            ConfigManager.getInstance().markDirty();
        }
    }

    private void setSelectedPanoramaPresetInternal(PanoramaDescriptor preset, boolean markDirty) {
        PanoramaDescriptor resolved = preset == null ? PanoramaPreset.VANILLA : preset;
        this.selectedPanoramaPresetId = resolved.getId();
        if (markDirty) {
            ConfigManager.getInstance().markDirty();
        }
    }

    private void setCustomMainMenuEnabledInternal(boolean enabled, boolean markDirty) {
        this.customMainMenuEnabled = enabled;
        if (markDirty) {
            ConfigManager.getInstance().markDirty();
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public interface PanoramaDescriptor {
        String getId();

        String displayName();

        Identifier previewTexture();

        int previewTextureSize();

        int previewCropInset();

        int previewCropSize();

        MenuPanoramaRenderer getRenderer();

        default boolean isCustom() {
            return false;
        }

        default boolean isRemote() {
            return false;
        }

        default boolean isDownloading() {
            return false;
        }

        default float downloadProgress() {
            return 0.0F;
        }

        default boolean hasPreviewTexture() {
            return true;
        }
    }

    public enum PanoramaPreset implements PanoramaDescriptor {
        VANILLA(
                "vanilla",
                "Vanilla",
                Identifier.ofVanilla("textures/gui/title/background/panorama")
        );

        private final String id;
        private final String displayName;
        private final Identifier cubeMapBase;
        private MenuPanoramaRenderer renderer;

        PanoramaPreset(String id, String displayName, Identifier cubeMapBase) {
            this.id = id;
            this.displayName = displayName;
            this.cubeMapBase = cubeMapBase;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String displayName() {
            return Lang.translate(displayName);
        }

        @Override
        public Identifier previewTexture() {
            return cubeMapBase.withPath(cubeMapBase.getPath() + "_0.png");
        }

        @Override
        public int previewTextureSize() {
            return this == VANILLA ? 256 : 1080;
        }

        @Override
        public int previewCropInset() {
            return 0;
        }

        @Override
        public int previewCropSize() {
            return previewTextureSize();
        }

        @Override
        public MenuPanoramaRenderer getRenderer() {
            if (renderer == null) {
                renderer = new MenuPanoramaRenderer(cubeMapBase);
            }
            return renderer;
        }

        public static PanoramaPreset byId(String id) {
            if (id != null) {
                for (PanoramaPreset preset : values()) {
                    if (preset.id.equalsIgnoreCase(id)) {
                        return preset;
                    }
                }
            }
            return VANILLA;
        }
    }
}
