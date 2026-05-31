package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class CoordinatesHud extends RectHudModule {
    private static final CoordinatesHud INSTANCE = new CoordinatesHud();

    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final MultiSelectSetting displayItems = new MultiSelectSetting(
            "Display Items",
            "Pick which coordinate rows the HUD should show"
    ).value(
            "Show X",
            "Show Y",
            "Show Z",
            "Show Chunk",
            "Show Biome",
            "Show Direction",
            "Show Axis Signs"
    ).selected(
            "Show X",
            "Show Y",
            "Show Z",
            "Show Chunk",
            "Show Biome",
            "Show Direction",
            "Show Axis Signs"
    );
    public final BooleanLike showX = () -> displayItems.isSelected("Show X");
    public final BooleanLike showY = () -> displayItems.isSelected("Show Y");
    public final BooleanLike showZ = () -> displayItems.isSelected("Show Z");
    public final BooleanLike showChunk = () -> displayItems.isSelected("Show Chunk");
    public final BooleanLike showBiome = () -> displayItems.isSelected("Show Biome");
    public final BooleanLike showDirection = () -> displayItems.isSelected("Show Direction");
    public final BooleanLike showAxisSigns = () -> displayItems.isSelected("Show Axis Signs");

    public static CoordinatesHud getInstance() {
        return INSTANCE;
    }

    private CoordinatesHud() {
        super("coordinates_hud", "Coordinates", 22.0f, 124.0f, 1.0f);
        displayItems.setFullWidth(true);
        setup(otherSection, displayItems);
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
