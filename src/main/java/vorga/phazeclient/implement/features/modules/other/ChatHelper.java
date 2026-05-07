package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.mixins.ChatHudAccessor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatHelper extends Module {
    private static final ChatHelper INSTANCE = new ChatHelper();
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s*\\((\\d+)x\\)\\s*$");

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting collapseRepeats = new BooleanSetting(
            "Collapse Repeats",
            "Collapse consecutive identical chat messages into one with a red (Nx) suffix"
    ).setValue(true);

    private boolean bypass = false;

    /**
     * The original styled text of the most recent unique message. Stored so
     * collapsed re-renders can keep the original colors / formatting and
     * append a separately-colored {@code (Nx)} suffix instead of flattening
     * everything to plain white.
     */
    private Text lastBaseStyled;
    private String lastBaseRaw;
    private int lastCount;

    private ChatHelper() {
        super("chat_helper", "Chat Helper", ModuleCategory.UTILITIES);
        collapseRepeats.setFullWidth(true);
        setup(generalSection, collapseRepeats);
    }

    public static ChatHelper getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Collapses repeated chat messages, preserving original colors with a red (Nx) suffix";
    }

    @Override
    public String getIcon() {
        return "chat_helper.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Set while we're recursively re-adding a message we modified, so the
     * collapse mixin doesn't reprocess our own output as a duplicate.
     */
    public boolean isBypassActive() {
        return bypass;
    }

    /**
     * Inspects the latest message in the {@link ChatHud}. If the new message
     * matches the most recent unique base (ignoring any prior {@code (Nx)}
     * suffix), removes the previous occurrence and returns a styled
     * replacement: the ORIGINAL incoming text (with full color/formatting)
     * followed by a red {@code (Nx)} sibling. Returns {@code null} when no
     * collapse should happen.
     */
    public Text tryCollapse(ChatHud hud, Text incoming) {
        if (!isEnabled() || !collapseRepeats.isValue() || incoming == null) {
            return null;
        }

        String incomingRaw = stripSuffixRaw(incoming.getString());

        // Different message arrived: reset the collapse state so the next
        // duplicate is counted against THIS one (whose styled form vanilla
        // is about to render normally).
        if (lastBaseRaw == null || !lastBaseRaw.equals(incomingRaw)) {
            lastBaseStyled = incoming;
            lastBaseRaw = incomingRaw;
            lastCount = 1;
            return null;
        }

        // Duplicate of the previous message - we need to remove whatever is
        // currently sitting at the top of the chat and re-add a collapsed
        // version.
        ChatHudAccessor accessor = (ChatHudAccessor) hud;
        List<ChatHudLine> messages = accessor.phaze$getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        messages.remove(0);
        List<ChatHudLine.Visible> visible = accessor.phaze$getVisibleMessages();
        if (visible != null && !visible.isEmpty()) {
            // Long wrapped lines can leave residual visible entries; vanilla
            // refresh on width change cleans them up.
            visible.remove(0);
        }

        lastCount++;
        MutableText replacement = lastBaseStyled.copy()
                .append(Text.literal(" (" + lastCount + "x)").formatted(Formatting.RED));
        return replacement;
    }

    public void runWithBypass(Runnable runnable) {
        bypass = true;
        try {
            runnable.run();
        } finally {
            bypass = false;
        }
    }

    /**
     * Strips a trailing {@code " (Nx)"} suffix from a raw message string so
     * comparisons treat "hello" and "hello (3x)" as the same base. The numeric
     * count itself is irrelevant when matching - we keep our own counter.
     */
    private String stripSuffixRaw(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = COUNT_SUFFIX.matcher(text);
        if (matcher.find()) {
            return text.substring(0, matcher.start());
        }
        return text;
    }
}
