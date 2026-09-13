package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.base.util.PhazeAnnouncements;

public final class AucHelper extends Module {
    private static final AucHelper INSTANCE = new AucHelper();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BindSetting keybind = new BindSetting("Bind", "Key that triggers /ah search for the item in your main hand");

    private AucHelper() {
        super("auc_helper", "Auc Helper", ModuleCategory.UTILITIES);
        keybind.setFullWidth(true);
        setup(generalSection, keybind);
    }

    public static AucHelper getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Sends /ah search <item in main hand> when the bind is pressed";
    }

    @Override
    public String getIcon() {
        return "auc_helper.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {

        return false;
    }

    public void onBindPressed(int key, int action) {
        if (!isEnabled()) {
            return;
        }
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        int bound = keybind.getKey();
        if (bound == GLFW.GLFW_KEY_UNKNOWN || bound != key) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            mc.player.sendMessage(PhazeAnnouncements.systemMessage(
                    Text.literal("Auc Helper · hand is empty").formatted(Formatting.RED)
            ), true);
            return;
        }

        String rawName = stack.getName().getString();
        String query = rawName.replaceAll("\u00A7.", "").trim();
        if (query.isEmpty()) {
            return;
        }

        mc.getNetworkHandler().sendChatCommand("ah search " + query);
    }
}
