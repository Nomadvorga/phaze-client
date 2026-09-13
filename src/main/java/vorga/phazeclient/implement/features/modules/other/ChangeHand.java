package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Arm;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class ChangeHand extends Module {
    private static final ChangeHand INSTANCE = new ChangeHand();

    public final SectionSetting mainHandSection = new SectionSetting("Main Hand");
    public final ValueSetting mainHandX = new ValueSetting("Main X", "Horizontal offset of the main hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting mainHandY = new ValueSetting("Main Y", "Vertical offset of the main hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting mainHandZ = new ValueSetting("Main Z", "Depth (forward/back) offset of the main hand item")
            .range(-2.5f, 2.5f).step(0.01f).setValue(0.0f);
    public final ValueSetting mainHandScale = new ValueSetting("Main Scale", "Size multiplier of the main hand item")
            .range(0.1f, 2.0f).step(0.01f).setValue(1.0f);

    public final SectionSetting offHandSection = new SectionSetting("Off Hand");
    public final ValueSetting offHandX = new ValueSetting("Off X", "Horizontal offset of the off hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting offHandY = new ValueSetting("Off Y", "Vertical offset of the off hand item")
            .range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting offHandZ = new ValueSetting("Off Z", "Depth (forward/back) offset of the off hand item")
            .range(-2.5f, 2.5f).step(0.01f).setValue(0.0f);
    public final ValueSetting offHandScale = new ValueSetting("Off Scale", "Size multiplier of the off hand item")
            .range(0.1f, 2.0f).step(0.01f).setValue(1.0f);

    public final SectionSetting switchSection = new SectionSetting("Switch Side");
    public final BooleanSetting uponImpact = new BooleanSetting(
            "Upon Impact",
            "Flip the main arm every time you successfully hit an entity"
    ).setValue(false);
    public final BindSetting keybind = new BindSetting(
            "Bind",
            "Key to manually flip the main arm. Only used when Upon Impact is OFF."
    );

    public final SectionSetting swaySection = new SectionSetting("Hand Sway");
    public final BooleanSetting noHandSway = new BooleanSetting(
            "No Hand Sway",
            "Stop the camera-following yaw/pitch sway on your held item, keeping it rotationally fixed relative to the camera"
    ).setValue(false);

    private ChangeHand() {
        super("changehand", "Change Hand", ModuleCategory.OTHER);

        mainHandX.setFullWidth(true);
        mainHandY.setFullWidth(true);
        mainHandZ.setFullWidth(true);
        mainHandScale.setFullWidth(true);
        offHandX.setFullWidth(true);
        offHandY.setFullWidth(true);
        offHandZ.setFullWidth(true);
        offHandScale.setFullWidth(true);
        uponImpact.setFullWidth(true);
        keybind.setFullWidth(true);
        noHandSway.setFullWidth(true);

        keybind.visible(() -> !uponImpact.isValue());
        setup(
                mainHandSection, mainHandX, mainHandY, mainHandZ, mainHandScale,
                offHandSection, offHandX, offHandY, offHandZ, offHandScale,
                switchSection, uponImpact, keybind,
                swaySection, noHandSway
        );
    }

    public static ChangeHand getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Changes the visual properties of your hands - position, scale, and active side";
    }

    @Override
    public String getIcon() {
        return "hand.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean hasMainHandTransform() {
        return mainHandX.getValue() != 0.0f
                || mainHandY.getValue() != 0.0f
                || mainHandZ.getValue() != 0.0f
                || mainHandScale.getValue() != 1.0f;
    }

    public boolean hasOffHandTransform() {
        return offHandX.getValue() != 0.0f
                || offHandY.getValue() != 0.0f
                || offHandZ.getValue() != 0.0f
                || offHandScale.getValue() != 1.0f;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    public void flipMainArm() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        Arm current = client.player.getMainArm();
        Arm flipped = (current == Arm.LEFT) ? Arm.RIGHT : Arm.LEFT;
        client.player.setMainArm(flipped);
    }

    public void onAttackEntity() {
        if (!isEnabled() || !uponImpact.isValue()) {
            return;
        }
        flipMainArm();
    }

    public void onBindStateChanged(int code, int action) {
        if (!isEnabled() || uponImpact.isValue()) {
            return;
        }
        int bound = keybind.getKey();
        if (bound == GLFW.GLFW_KEY_UNKNOWN || code != bound) {
            return;
        }
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        flipMainArm();
    }
}
