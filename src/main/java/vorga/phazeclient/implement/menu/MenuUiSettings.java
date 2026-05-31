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
    public static final String DEFAULT_PANORAMA_PRESET_ID = "vanilla";

    private static final MenuUiSettings INSTANCE = new MenuUiSettings();

    private double panoramaSpeed = DEFAULT_PANORAMA_SPEED;
    private int guiFpsLimit = DEFAULT_GUI_FPS_LIMIT;
    private String selectedPanoramaPresetId = DEFAULT_PANORAMA_PRESET_ID;

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

    public PanoramaDescriptor getSelectedPanoramaPreset() {
        return MenuPanoramaRegistry.findById(selectedPanoramaPresetId);
    }

    public String getSelectedPanoramaPresetId() {
        PanoramaDescriptor selected = getSelectedPanoramaPreset();
        return selected == null ? DEFAULT_PANORAMA_PRESET_ID : selected.getId();
    }

    public void setPanoramaSpeed(double panoramaSpeed) {
        setPanoramaSpeedInternal(panoramaSpeed, true);
    }

    public void setGuiFpsLimit(int guiFpsLimit) {
        setGuiFpsLimitInternal(guiFpsLimit, true);
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

    public void applyConfig(double panoramaSpeed, int guiFpsLimit, String presetId) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), false);
        setPanoramaSpeedInternal(panoramaSpeed, false);
        setGuiFpsLimitInternal(guiFpsLimit, false);
    }

    public void applyLegacyScaleV2Config(double panoramaSpeed, int guiFpsLimit, String presetId) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), false);
        setPanoramaSpeedInternal(Math.min(100.0D, panoramaSpeed * 2.0D), false);
        setGuiFpsLimitInternal(guiFpsLimit, false);
    }

    public void applyLegacyScaleV1Config(double panoramaSpeed, int guiFpsLimit, String presetId) {
        setSelectedPanoramaPresetInternal(MenuPanoramaRegistry.findById(presetId), false);
        setPanoramaSpeedInternal(Math.min(100.0D, panoramaSpeed * 10.0D), false);
        setGuiFpsLimitInternal(guiFpsLimit, false);
    }

    public void resetToDefaults() {
        applyConfig(DEFAULT_PANORAMA_SPEED, DEFAULT_GUI_FPS_LIMIT, DEFAULT_PANORAMA_PRESET_ID);
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
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
    }

    public enum PanoramaPreset implements PanoramaDescriptor {
        VANILLA(
                "vanilla",
                "Vanilla",
                Identifier.ofVanilla("textures/gui/title/background/panorama")
        ),
        CHATEAU(
                "chateau",
                "Chateau",
                Identifier.of("phaze", "textures/menu/panoramas/chateau/panorama")
        ),
        POST_SOVIET_NIGHT(
                "post_soviet_night",
                "Post-Soviet Night",
                Identifier.of("phaze", "textures/menu/panoramas/post_soviet_night/panorama")
        ),
        CASTLE(
                "castle",
                "Castle",
                Identifier.of("phaze", "textures/menu/panoramas/castle/panorama")
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
