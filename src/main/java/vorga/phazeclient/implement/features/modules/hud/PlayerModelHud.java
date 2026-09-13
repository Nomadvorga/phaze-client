package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class PlayerModelHud extends RectHudModule {
    private static final PlayerModelHud INSTANCE = new PlayerModelHud();
    private static final float BASE_MODEL_SIZE = 30.0F;

    public final SectionSetting otherSection = new SectionSetting("General");
    public final SelectSetting mode = new SelectSetting(
            "Mode",
            "How the model should orient itself"
    ).value("Follow Mouse", "Auto Rotate", "Static").selected("Follow Mouse");
    public final ValueSetting modelSize = new ValueSetting(
            "Model Size",
            "Legacy slider kept hidden; size is now changed via HUD edit / chat like every other HUD"
    ).range(20, 80).step(1).setValue(BASE_MODEL_SIZE).visible(() -> false);
    public final ValueSetting rotationSpeed = new ValueSetting(
            "Rotation Speed",
            "Degrees per second when Auto Rotate is selected"
    ).range(10, 360).step(5).setValue(60)
            .visible(() -> "Auto Rotate".equalsIgnoreCase(mode.getSelected()));
    private PlayerModelHud() {

        super("player_model_hud", "Player Model", 22.0F, 22.0F, 1.5F);
        background.setVisible(() -> false);
        backgroundPreset.setVisible(() -> false);
        colorBrightness.setVisible(() -> false);
        backgroundOpacity.setVisible(() -> false);
        backgroundBlurRadius.setVisible(() -> false);
        textShadow.setVisible(() -> false);
        showBrackets.setVisible(() -> false);
        mainSection.setVisible(() -> false);
        colorSection.setVisible(() -> false);
        mode.setFullWidth(true);
        rotationSpeed.setFullWidth(true);
        setup(otherSection, mode, modelSize, rotationSpeed, cornerRounding);
    }

    public static PlayerModelHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Renders a 3D mini-version of your skin in the corner";
    }

    @Override
    public String getIcon() {
        return "player_model_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getBaseModelSize() {
        return BASE_MODEL_SIZE;
    }
}
