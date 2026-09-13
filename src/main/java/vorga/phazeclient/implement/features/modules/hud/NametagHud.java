package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class NametagHud extends RectHudModule {
    private static final NametagHud INSTANCE = new NametagHud();

    private static final float CUSTOM_PRESET_MAX_ALPHA = 0.30F;

    public final SectionSetting nametagSection = new SectionSetting("Nametag");
    public final BooleanSetting nametagTextShadow = new BooleanSetting("Nametag Text Shadow", "Shadow for nametag text").setValue(true);
    public final BooleanSetting thirdPersonNametag = new BooleanSetting("Third Person Nametag", "Render nametag in third person").setValue(true);
    public final BooleanSetting toggleMessage = new BooleanSetting("Toggle Message", "Display toggle message for nametags").setValue(true);
    public final BooleanSetting hideInF1 = new BooleanSetting("Hide in F1", "Hide nametags in F1 mode").setValue(false);
    public final BooleanSetting replaceOwnNameColor = new BooleanSetting("Replace Own Name Color", "Replace own nametag text color").setValue(true);

    public static NametagHud getInstance() {
        return INSTANCE;
    }

    private NametagHud() {
        super("nametag_hud", "Nametag", 22.0f, 408.0f, 1.0f);

        backgroundOpacity.setValue(100);

        textShadow.visible(() -> false);
        nametagTextShadow.setFullWidth(true);
        thirdPersonNametag.setFullWidth(true);
        toggleMessage.setFullWidth(true);
        hideInF1.setFullWidth(true);
        replaceOwnNameColor.setFullWidth(true);
        setup(nametagSection, nametagTextShadow, thirdPersonNametag, toggleMessage, hideInF1, replaceOwnNameColor, otherSection, cornerRounding);
    }

    @Override
    public int getResolvedBackgroundColor(MinecraftClient client) {
        if (isVanillaPreset()) {

            int alpha = MathHelper.clamp(Math.round(255.0F * CUSTOM_PRESET_MAX_ALPHA), 0, 255);
            return alpha << 24;
        }

        int parentColor = super.getResolvedBackgroundColor(client);
        int parentAlpha = (parentColor >>> 24) & 0xFF;
        int scaledAlpha = MathHelper.clamp(Math.round(parentAlpha * CUSTOM_PRESET_MAX_ALPHA), 0, 255);
        return (scaledAlpha << 24) | (parentColor & 0x00FFFFFF);
    }

    @Override
    public String getDescription() {
        return "Nametag options HUD";
    }

    @Override
    public String getIcon() {
        return "nametag_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
