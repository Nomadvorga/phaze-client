package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom keybinds for canned chat messages and slash commands. Each
 * slot is a {bind, text} pair: when the bind fires, the configured
 * text is sent through the player's chat as if they typed it
 * themselves. Lines starting with {@code /} are routed through the
 * command channel, plain text goes through chat.
 *
 * <h3>Why a fixed-size slot list</h3>
 * {@link vorga.phazeclient.api.feature.module.setting.SettingRepository}
 * is a static list - it doesn't expose an "add row" UI gesture and
 * the auto-save pipeline is keyed by setting identity, not order.
 * Rather than reinvent dynamic settings just for this module, we
 * preallocate {@link #SLOT_COUNT} pairs and the user fills in as
 * many as they need; empty rows do nothing on press, so unused
 * slots are free.
 *
 * <h3>Trigger semantics</h3>
 * The dispatch lives in {@code KeyboardBindsMixin}; that mixin
 * forwards every {@code Keyboard.onKey} press into {@link #onKey(int, int)}.
 * We act on {@code GLFW_PRESS} only, skip if the player is in any
 * GUI ({@code currentScreen != null}) so a bind doesn't fire while
 * the user is typing in chat or browsing inventory, and skip if the
 * module is disabled.
 *
 * <h3>Command vs chat routing</h3>
 * If the trimmed payload starts with {@code /}, we strip the slash
 * and call {@code sendChatCommand}. Otherwise we call
 * {@code sendChatMessage}. Two reasons the slash needs stripping:
 * <ul>
 *   <li>{@code sendChatCommand} expects the bare command name
 *       ({@code "home 1"} not {@code "/home 1"}). Passing the slash
 *       creates a command starting with literal {@code /home} which
 *       no server registers.</li>
 *   <li>Vanilla's chat input does the same split internally - we
 *       just replicate the path the player would have walked if
 *       they typed it.</li>
 * </ul>
 *
 * <h3>Empty / whitespace handling</h3>
 * Trimming first lets the user paste a config with leading spaces
 * and not have it silently no-op'd by the empty check, while the
 * empty-after-trim guard skips the network call entirely so a slot
 * with only a bind but no text doesn't accidentally send a blank
 * message every time the key is pressed.
 */
public final class Binds extends Module {
    private static final Binds INSTANCE = new Binds();

    /** Number of preallocated bind slots. Keep enough that the
     *  module is useful for a typical hotbar-style cycle ({@code
     *  /duel}, {@code /home 1..5}, {@code /spawn}, etc.) without
     *  cluttering the settings panel for users who only need one
     *  or two. Eight is the sweet spot from looking at how players
     *  use macro mods on FunTime/HolyWorld. */
    private static final int SLOT_COUNT = 8;

    public final SectionSetting generalSection = new SectionSetting("Binds");
    private final BindSlot[] slots;

    private Binds() {
        super("binds", "Binds", ModuleCategory.UTILITIES);

        slots = new BindSlot[SLOT_COUNT];
        List<vorga.phazeclient.api.feature.module.setting.Setting> setupArgs = new ArrayList<>();
        setupArgs.add(generalSection);
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = new BindSlot(i + 1);
            setupArgs.add(slots[i].asGroup());
        }
        // Module uses the default constructor (showEnable=true) so
        // the per-module toggle in the menu actually gates the
        // dispatch path. Flipping the toggle off should silence
        // every bind without the user needing to clear individual
        // slots.
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
        return 21.0F;
    }

    /**
     * Forwarded from {@code KeyboardBindsMixin}. Walks every slot
     * and fires the matching one(s) on {@code GLFW_PRESS}. Multiple
     * slots sharing a bind is allowed (cheap to support, and useful
     * for "send pair of messages on one key" macros).
     */
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
        // Don't fire while the user is in any GUI (chat, inventory,
        // sign edit, our own menu, etc). A bind firing while the
        // player is typing in chat would interrupt their draft and
        // is the most common "why did my bind misbehave" footgun
        // for macro mods that don't gate this.
        if (mc.currentScreen != null) {
            return;
        }
        for (BindSlot slot : slots) {
            if (slot.bind.getKey() == key) {
                slot.fire(mc);
            }
        }
    }

    /**
     * One {bind, text} row in the settings panel. Each slot owns a
     * {@link GroupSetting} so the bind and the message render
     * together as a labelled card; the group's expand/collapse
     * state is wired automatically by the menu.
     */
    private static final class BindSlot {
        final BindSetting bind;
        final TextSetting text;
        private final GroupSetting group;

        BindSlot(int index) {
            // Localised slot label. Keep it short (the menu's group
            // header truncates anything longer than ~14 chars on a
            // narrow card) and human-1-indexed because the user
            // doesn't think in arrays.
            String label = "Bind " + index;
            this.bind = new BindSetting("Key", "Key that triggers this bind");
            this.text = new TextSetting("Message", "Chat text or /command. Empty = slot is disabled.")
                    .setText("");
            this.bind.setFullWidth(true);
            this.text.setFullWidth(true);
            this.group = new GroupSetting(label, "Bind " + index)
                    .settings(bind, text);
            this.group.setFullWidth(true);
            this.group.setCheckbox(false);
        }

        GroupSetting asGroup() {
            return group;
        }

        /**
         * Send the configured payload through the player's chat /
         * command channel. Requires {@code mc.getNetworkHandler() != null}
         * and {@code mc.player != null} (caller verifies).
         */
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
                // Defensive: a malformed payload (control codes,
                // 200+ char message, etc.) shouldn't crash the
                // client. Log to chat so the user sees why their
                // bind didn't fire instead of silent failure.
                if (mc.player != null) {
                    mc.player.sendMessage(
                            Text.literal("[Phaze Binds] failed: " + t.getMessage())
                                    .formatted(Formatting.RED),
                            false
                    );
                }
            }
        }
    }
}
