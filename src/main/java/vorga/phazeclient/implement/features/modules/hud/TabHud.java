package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class TabHud extends RectHudModule {
    private static final TabHud INSTANCE = new TabHud();

    public final SectionSetting colorSection2 = new SectionSetting("Tab Colors");
    public final BooleanSetting highlightOwn = new BooleanSetting("Highlight Own Name", "Highlight own name on tab").setValue(true);
    public final BooleanSetting showSelfOnTop = new BooleanSetting("Show Self On Top", "Move your own row to the top of tab list").setValue(false);
    public final BooleanSetting displayPingAsNumber = new BooleanSetting("Display Ping Number", "Show ping as number").setValue(true);
    public final BooleanSetting pingNumberShadow = new BooleanSetting("Ping Number Shadow", "Shadow for ping number").setValue(true)
            .visible(() -> displayPingAsNumber.isValue());
    public final BooleanSetting dynamicPingColor = new BooleanSetting("Dynamic Ping Color", "Color ping by thresholds").setValue(true)
            .visible(() -> displayPingAsNumber.isValue());

    public static TabHud getInstance() {
        return INSTANCE;
    }

    private TabHud() {
        super("tab_hud", "Tab", 22.0f, 374.0f, 1.0f);
        // Remove background-related settings since TabHud renders through vanilla PlayerListHud
        settings().removeIf(s -> {
            String name = s.getNameKey();
            return name.equals("Background") || name.equals("Background Preset") || name.equals("Color Brightness")
                   || name.equals("Background Opacity") || name.equals("Background Blur Radius")
                   || name.equals("Color Settings") || name.equals("Batching")
                   || name.equals("HUD Batching") || name.equals("Force HUD Update") || name.equals("HUD FPS");
        });
        highlightOwn.setFullWidth(true);
        showSelfOnTop.setFullWidth(true);
        displayPingAsNumber.setFullWidth(true);
        pingNumberShadow.setFullWidth(true);
        dynamicPingColor.setFullWidth(true);
        setup(colorSection2, highlightOwn, showSelfOnTop, displayPingAsNumber, pingNumberShadow, dynamicPingColor);
    }

    @Override
    public String getDescription() {
        return "Tab overlay settings HUD";
    }

    @Override
    public String getIcon() {
        return "tab_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}

