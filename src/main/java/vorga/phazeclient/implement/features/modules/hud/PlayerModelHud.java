package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * On-screen 3D miniature of the local player. Reuses vanilla's
 * {@code InventoryScreen.drawEntity} static helper for the actual
 * rendering so animations (walking / sneaking / item poses /
 * cape / ear / armor / glint) all work without re-implementing the
 * model layer pipeline.
 *
 * <h3>Mode</h3>
 * <ul>
 *   <li><b>Follow Mouse</b> - the model rotates to track the mouse
 *       cursor like the inventory portrait, so the user can see
 *       any side of their skin by hovering over the panel.</li>
 *   <li><b>Auto Rotate</b> - the model spins on its body axis
 *       continuously at a fixed speed - good for showcase /
 *       streamer overlays where there's no mouse input.</li>
 *   <li><b>Static</b> - locked facing the camera; least
 *       distracting if the user just wants a "this is my skin"
 *       indicator without motion.</li>
 * </ul>
 *
 * <h3>Why a HUD module not a screen overlay</h3>
 * Putting it in the HUD pipeline gets us drag/resize/scale for
 * free and keeps the player model visible in-world (not just in
 * inventory). The renderer mixin paints at TAIL of InGameHud
 * render, so nothing the user does to the inventory affects the
 * HUD copy.
 */
public final class PlayerModelHud extends RectHudModule {
    private static final PlayerModelHud INSTANCE = new PlayerModelHud();

    public final SectionSetting otherSection = new SectionSetting("General");
    public final SelectSetting mode = new SelectSetting(
            "Mode",
            "How the model should orient itself"
    ).value("Follow Mouse", "Auto Rotate", "Static").selected("Follow Mouse");
    public final ValueSetting modelSize = new ValueSetting(
            "Model Size",
            "Visual size of the player model in pixels (vanilla uses 30)"
    ).range(20, 80).step(1).setValue(30);
    public final ValueSetting rotationSpeed = new ValueSetting(
            "Rotation Speed",
            "Degrees per second when Auto Rotate is selected"
    ).range(10, 360).step(5).setValue(60)
            .visible(() -> "Auto Rotate".equalsIgnoreCase(mode.getSelected()));
    private PlayerModelHud() {
        // Footprint roughly 60x100 px (model's drawEntity bounds at
        // size=30); start at the top-left so the user can drag.
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
        modelSize.setFullWidth(true);
        rotationSpeed.setFullWidth(true);
        setup(otherSection, mode, modelSize, rotationSpeed);
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
}
