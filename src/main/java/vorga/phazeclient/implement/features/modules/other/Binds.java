package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.base.util.PhazeAnnouncements;

import java.util.ArrayList;
import java.util.List;

public final class Binds extends Module {
    private static final Binds INSTANCE = new Binds();

    private static final int SLOT_COUNT = 8;

    public final SectionSetting generalSection = new SectionSetting("Binds");
    private final BindSlot[] slots;

    private Binds() {
        super("binds", "Binds", ModuleCategory.UTILITIES);

        slots = new BindSlot[SLOT_COUNT];
        List<vorga.phazeclient.api.feature.module.setting.Setting> setupArgs = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = new BindSlot(i + 1);

            setupArgs.add(slots[i].section);
            setupArgs.add(slots[i].bind);
            setupArgs.add(slots[i].text);
        }
        setup(setupArgs.toArray(new vorga.phazeclient.api.feature.module.setting.Setting[0]));
    }

    public static Binds getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "N keybinds that send a chat message or slash command";
    }

    @Override
    public String getIcon() {
        return "binds.png";
    }

    @Override
    public float getIconSize() {
        return 27.4F;
    }

    @Override
    public float getIconOffsetY() {
        return -2.0F;
    }

    public void onKey(int key, int action) {
        if (!isEnabled() || action != GLFW.GLFW_PRESS) {
            return;
        }
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }
        for (BindSlot slot : slots) {
            if (slot.bind.getKey() == key) {
                slot.fire(mc);
            }
        }
    }

    private static final class BindSlot {
        final SectionSetting section;
        final BindSetting bind;
        final TextSetting text;

        BindSlot(int index) {
            String label = "Bind " + index;
            this.section = new SectionSetting(label);
            this.bind = new BindSetting(label + " Key", "Key that triggers " + label);
            this.text = new TextSetting(label + " Message", "Chat text or /command. Empty = slot is disabled.")
                    .setText("");
            this.bind.setFullWidth(true);
            this.text.setFullWidth(true);
        }

        void fire(MinecraftClient mc) {
            String payload = text.getText();
            if (payload == null) {
                return;
            }
            payload = payload.trim();
            if (payload.isEmpty()) {
                return;
            }
            try {
                if (payload.startsWith("/")) {
                    String command = payload.substring(1).trim();
                    if (!command.isEmpty()) {
                        mc.getNetworkHandler().sendChatCommand(command);
                    }
                } else {
                    mc.getNetworkHandler().sendChatMessage(payload);
                }
            } catch (Throwable t) {

                if (mc.player != null) {
                    mc.player.sendMessage(
                            PhazeAnnouncements.systemMessage(
                                    Text.literal("Binds failed: " + t.getMessage()).formatted(Formatting.RED)
                            ),
                            false
                    );
                }
            }
        }
    }
}
