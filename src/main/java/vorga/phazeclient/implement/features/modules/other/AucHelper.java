package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Auc Helper. On the configured bind, sends
 * {@code /ah search <name of item in main hand>} to chat. Designed
 * around FunTime-style auction houses where {@code /ah search <q>}
 * pre-fills the search field for the current marketplace.
 *
 * <p>The module deliberately does not gate on {@link #isServerAllowed()};
 * users on other servers running a similarly-named command should
 * still be able to bind a one-shot search trigger. If a server doesn't
 * recognise {@code /ah search} it'll surface its own error in chat,
 * which is preferable to the module silently no-op'ing because of an
 * overly strict whitelist.
 *
 * <p>The bind dispatch lives in {@link
 * vorga.phazeclient.mixins.KeyboardAucHelperMixin}; the mixin
 * forwards each {@code Keyboard.onKey} event to
 * {@link #onBindPressed(int, int)} so the module itself is purely
 * data + chat send, no GLFW state polling.
 */
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
        // The module's purpose is its bind action - there's no
        // useful enable/disable toggle distinct from "is the bind
        // assigned?". Returning false keeps the canBind chip out of
        // the module card so the user is steered toward the inner
        // Bind setting instead.
        return false;
    }

    /**
     * Forwarded from {@link
     * vorga.phazeclient.mixins.KeyboardAucHelperMixin#phaze$onKeyAucHelper}
     * for every key event the GLFW window receives. We dispatch on
     * PRESS only and bail out for any non-matching key so the mixin
     * stays a thin pass-through.
     */
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
        // Don't fire while the user is typing in a chat / sign / book
        // GUI - that would inject a spurious slash command in the
        // middle of whatever they're authoring.
        if (mc.currentScreen != null) {
            return;
        }

        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            mc.player.sendMessage(Text.literal("§c[Auc Helper] hand is empty"), true);
            return;
        }

        // getName() returns the display name (custom or vanilla
        // translated). Stripping section-code formatting keeps the
        // search field clean - some servers' tooltips have colour
        // codes baked into custom item names, which would otherwise
        // leak into the auction query as literal '§'-codes.
        String rawName = stack.getName().getString();
        String query = rawName.replaceAll("\u00A7.", "").trim();
        if (query.isEmpty()) {
            return;
        }

        mc.getNetworkHandler().sendChatCommand("ah search " + query);
    }
}
