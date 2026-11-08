package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Small live copy of the camera entity drawn on the HUD. It mirrors the real
 * pose (sneaking, swimming, gliding, riding, swinging) from a fixed three-quarter
 * view; position and size are edited in chat like every other HUD.
 */
public final class PaperDoll extends RectHudModule {
    public static final String HEAD_FLOATY = "Floaty";
    public static final String HEAD_STATIC = "Static";
    public static final String HEAD_LOCKED = "Locked";
    public static final String HEAD_FLOATY_HORIZONTAL = "Floaty Horizontal";
    public static final String HEAD_STATIC_HORIZONTAL = "Static Horizontal";

    /** GUI pixels per block at render scale 1. */
    public static final float BASE_DOLL_SIZE = 25.0F;
    public static final float BASE_WIDTH = 50.0F;
    public static final float BASE_HEIGHT = 60.0F;
    /** Feet position inside the panel at render scale 1. */
    public static final float FEET_X = 25.0F;
    public static final float FEET_Y = 55.0F;

    private static final PaperDoll INSTANCE = new PaperDoll();

    public final SectionSetting dollSection = new SectionSetting("Doll");
    public final SelectSetting headMode = new SelectSetting(
            "Doll Head Locked",
            "How the doll's head reacts to your movement"
    ).value(HEAD_FLOATY, HEAD_STATIC, HEAD_LOCKED, HEAD_FLOATY_HORIZONTAL, HEAD_STATIC_HORIZONTAL).selected(HEAD_FLOATY);
    public final ValueSetting lookingSides = new ValueSetting(
            "Looking Sides",
            "How far the doll is turned to the left/right"
    ).range(-80, 80).step(1).setValue(20);
    public final ValueSetting lookingUpDown = new ValueSetting(
            "Looking Up/Down",
            "How far the doll is tilted up/down"
    ).range(-80, 80).step(1).setValue(-20);

    public final SectionSetting behaviorSection = new SectionSetting("Behavior");
    public final BooleanSetting lockElytraRotation = new BooleanSetting(
            "Lock Elytra Rotation",
            "Do not rotate the doll with your view while flying with an elytra"
    ).setValue(true);
    public final BooleanSetting lockSpinningRotation = new BooleanSetting(
            "Lock Spinning Rotation",
            "Do not rotate the doll with your view while riptide spinning"
    ).setValue(true);

    public final SectionSetting visibilitySection = new SectionSetting("Visibility");
    public final BooleanSetting autoHide = new BooleanSetting(
            "Auto Hide",
            "Only show the doll while you are doing something"
    ).setValue(false);
    public final BooleanSetting hideInF5 = new BooleanSetting(
            "Hide In F5",
            "Hide the doll in third person"
    ).setValue(true);
    public final BooleanSetting hideDuringF3 = new BooleanSetting(
            "Hide During F3",
            "Hide the doll while the debug screen is open"
    ).setValue(true);
    public final BooleanSetting hideDuringScreens = new BooleanSetting(
            "Hide During Screens",
            "Hide the doll while any screen (inventory, menus) is open"
    ).setValue(false);

    private PaperDoll() {
        super("paper_doll", "PaperDoll", 0.0F, 0.0F, 0.5F);
        background.setVisible(() -> false);
        backgroundPreset.setVisible(() -> false);
        colorBrightness.setVisible(() -> false);
        backgroundOpacity.setVisible(() -> false);
        backgroundBlurRadius.setVisible(() -> false);
        textShadow.setVisible(() -> false);
        showBrackets.setVisible(() -> false);
        mainSection.setVisible(() -> false);
        colorSection.setVisible(() -> false);
        headMode.setFullWidth(true);
        lookingSides.setFullWidth(true);
        lookingUpDown.setFullWidth(true);
        setup(dollSection, headMode, lookingSides, lookingUpDown,
                behaviorSection, lockElytraRotation, lockSpinningRotation,
                visibilitySection, autoHide, hideInF5, hideDuringF3, hideDuringScreens);
    }

    public static PaperDoll getInstance() {
        return INSTANCE;
    }

    @Override
    protected boolean usesGuiCoordinates() {
        return true;
    }

    @Override
    public float getMinHudScale() {
        return 0.25F;
    }

    @Override
    public String getDescription() {
        return "Shows a small live preview of your player in a corner of the screen";
    }

    @Override
    public String getIcon() {
        return "player_model_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean headLocked() {
        return HEAD_LOCKED.equals(headMode.getSelected());
    }

    public boolean headFollowsBody() {
        String mode = headMode.getSelected();
        return HEAD_FLOATY.equals(mode) || HEAD_FLOATY_HORIZONTAL.equals(mode);
    }

    public boolean pitchLocked() {
        String mode = headMode.getSelected();
        return HEAD_LOCKED.equals(mode) || HEAD_FLOATY_HORIZONTAL.equals(mode) || HEAD_STATIC_HORIZONTAL.equals(mode);
    }
}
