package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class NametagHud extends RectHudModule {
    private static final NametagHud INSTANCE = new NametagHud();

    public final SectionSetting nametagSection = new SectionSetting("Nametag");
    public final BooleanSetting nametagTextShadow = new BooleanSetting("Nametag Text Shadow", "Shadow for nametag text").setValue(true);
    public final BooleanSetting thirdPersonNametag = new BooleanSetting("Third Person Nametag", "Render nametag in third person").setValue(true);
    public final BooleanSetting toggleMessage = new BooleanSetting("Toggle Message", "Display toggle message for nametags").setValue(true);
    public final BooleanSetting hideInF1 = new BooleanSetting("Hide in F1", "Hide nametags in F1 mode").setValue(false);
    public final ValueSetting nametagOpacity = new ValueSetting("Nametag Opacity", "Opacity multiplier for nametag")
            .range(0, 100)
            .setValue(100);
    public final BooleanSetting replaceOwnNameColor = new BooleanSetting("Replace Own Name Color", "Replace own nametag text color").setValue(true);

    public static NametagHud getInstance() {
        return INSTANCE;
    }

    private NametagHud() {
        super("nametag_hud", "Nametag", 22.0f, 408.0f, 1.0f);
        nametagTextShadow.setFullWidth(true);
        thirdPersonNametag.setFullWidth(true);
        toggleMessage.setFullWidth(true);
        hideInF1.setFullWidth(true);
        nametagOpacity.setFullWidth(true);
        replaceOwnNameColor.setFullWidth(true);
        setup(nametagSection, nametagTextShadow, thirdPersonNametag, toggleMessage, hideInF1, nametagOpacity, replaceOwnNameColor);
    }

    @Override
    public String getDescription() {
        return "Nametag options HUD";
    }
}

