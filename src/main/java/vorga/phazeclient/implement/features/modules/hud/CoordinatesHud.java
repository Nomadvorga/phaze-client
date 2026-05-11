package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class CoordinatesHud extends RectHudModule {
    private static final CoordinatesHud INSTANCE = new CoordinatesHud();

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting showX = new BooleanSetting("Show X", "Display X coordinate").setValue(true);
    public final BooleanSetting showY = new BooleanSetting("Show Y", "Display Y coordinate").setValue(true);
    public final BooleanSetting showZ = new BooleanSetting("Show Z", "Display Z coordinate").setValue(true);
    public final BooleanSetting showChunk = new BooleanSetting("Show Chunk", "Display local chunk coordinates").setValue(true);
    public final BooleanSetting showBiome = new BooleanSetting("Show Biome", "Display current biome").setValue(true);
    public final BooleanSetting showDirection = new BooleanSetting("Show Direction", "Display cardinal direction").setValue(true);
    public final BooleanSetting showAxisSigns = new BooleanSetting("Show Axis Signs", "Display X/Z plus-minus signs").setValue(true)
            .visible(() -> showDirection.isValue());

    public static CoordinatesHud getInstance() {
        return INSTANCE;
    }

    private CoordinatesHud() {
        super("coordinates_hud", "Coordinates", 22.0f, 124.0f, 1.0f);
        showX.setFullWidth(true);
        showY.setFullWidth(true);
        showZ.setFullWidth(true);
        showChunk.setFullWidth(true);
        showBiome.setFullWidth(true);
        showDirection.setFullWidth(true);
        showAxisSigns.setFullWidth(true);
        setup(otherSection, showX, showY, showZ, showChunk, showBiome, showDirection, showAxisSigns);
        // Coordinates lines are emitted as multi-line text and have
        // never round-tripped through the [] wrap path, so the parent-
        // registered Show Brackets toggle is meaningless here. Hide it
        // from the panel so the user isn't presented with a no-op.
        showBrackets.visible(() -> false);
    }

    @Override
    public String getDescription() {
        return "Shows position, chunk, biome, and direction";
    }

    @Override
    public String getIcon() {
        return "coordinates_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
