package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.*;

public final class FreeLook extends Module {
    private static final FreeLook INSTANCE = new FreeLook();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BindSetting keybind = new BindSetting("Keybind", "Key to activate freelook");
    public final BooleanSetting hold = new BooleanSetting("Hold", "Hold the key to keep freelook active. When off, the key toggles freelook on/off.")
            .setValue(false);

    private boolean active = false;
    private float anchorYaw;
    private float anchorPitch;
    private float lookYaw;
    private float lookPitch;
    private float prevLookYaw;
    private float prevLookPitch;
    private Perspective originalPerspective = Perspective.FIRST_PERSON;

    private FreeLook() {
        super("freelook", "Free Look", ModuleCategory.UTILITIES);

        keybind.setFullWidth(true);
        hold.setFullWidth(true);
        setup(generalSection, keybind, hold);
    }

    public static FreeLook getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Look around without moving your player";
    }

    @Override
    public String getIcon() {
        return "freelook.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!active) {
            return;
        }

        if (!isEnabled() || mc.player == null) {
            deactivate();
            return;
        }

        mc.player.setYaw(anchorYaw);
        mc.player.setPitch(anchorPitch);
    }

    public boolean onMouseLook(double cursorDeltaX, double cursorDeltaY) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!active || mc.player == null) {
            return false;
        }

        double yawDelta = cursorDeltaX * 0.15D;
        double pitchDelta = cursorDeltaY * 0.15D;

        prevLookYaw = lookYaw;
        prevLookPitch = lookPitch;
        lookYaw += (float) yawDelta;
        lookPitch = MathHelper.clamp(lookPitch + (float) pitchDelta, -90.0f, 90.0f);
        return true;
    }

    public void activate() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isEnabled() || active || mc.player == null || mc.currentScreen != null) {
            return;
        }

        anchorYaw = mc.player.getYaw();
        anchorPitch = mc.player.getPitch();
        lookYaw = anchorYaw;
        lookPitch = anchorPitch;
        prevLookYaw = lookYaw;
        prevLookPitch = lookPitch;
        originalPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        active = true;
    }

    public void deactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!active) {
            return;
        }

        if (mc.player != null) {
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        }

        mc.options.setPerspective(originalPerspective);
        active = false;
    }

    public float getCameraYaw(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, prevLookYaw, lookYaw);
    }

    public float getCameraPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevLookPitch, lookPitch);
    }

    public boolean isActive() {
        return active;
    }

    public void onBindStateChanged(int code, int action) {

        if (code == GLFW.GLFW_KEY_F5 && action == GLFW.GLFW_PRESS && active) {
            deactivate();
            return;
        }

        if (code != keybind.getKey()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null) {
            long handle = mc.getWindow().getHandle();
            if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS) {
                return;
            }
        }

        if (hold.isValue()) {

            if (action == 1) {
                activate();
            } else if (action == 0) {
                deactivate();
            }
        } else {

            if (action == 1) {
                if (active) {
                    deactivate();
                } else {
                    activate();
                }
            }
        }
    }

    protected void onDisable() {
        deactivate();
    }
}
