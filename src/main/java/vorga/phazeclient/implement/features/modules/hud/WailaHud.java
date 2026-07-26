package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class WailaHud extends RectHudModule {
    private static final WailaHud INSTANCE = new WailaHud();

    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    public static WailaHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting infoSection = new SectionSetting("Info Display");
    public final MultiSelectSetting infoItems = new MultiSelectSetting(
            "Info Items",
            "Pick which WAILA info blocks should appear"
    ).value(
            "Show Icon",
            "Show Correct Tool",
            "Show Coordinates",
            "Show Break Time",
            "Always Show",
            "Show Entities"
    ).selected(
            "Show Icon",
            "Show Correct Tool",
            "Show Coordinates",
            "Show Break Time",
            "Always Show",
            "Show Entities"
    );
    public final BooleanLike showIcon = () -> infoItems.isSelected("Show Icon");
    public final BooleanLike showCorrectTool = () -> infoItems.isSelected("Show Correct Tool");
    public final BooleanLike showCoordinates = () -> infoItems.isSelected("Show Coordinates");
    public final BooleanLike showBreakTime = () -> infoItems.isSelected("Show Break Time");
    public final BooleanLike alwaysShow = () -> infoItems.isSelected("Always Show");
    public final BooleanLike showEntities = () -> infoItems.isSelected("Show Entities");

    private WailaHud() {
        super("waila_hud", "Waila", 22.0f, 544.0f, 1.0f);
        infoItems.setFullWidth(true);
        // {@code showBrackets} is already wired into the Main section by
        // {@code RectHudModule}'s constructor (right after Background).
        // Re-listing it here would have appended a duplicate entry that
        // surfaced inside the Color Settings region of the panel; the
        // child setup() call therefore covers ONLY the WAILA-specific
        // info-display toggles.
        setup(infoSection, infoItems);
    }

    @Override
    public String getDescription() {
        return "Shows block/entity info when targeting";
    }

    @Override
    public String getIcon() {
        return "waila.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
