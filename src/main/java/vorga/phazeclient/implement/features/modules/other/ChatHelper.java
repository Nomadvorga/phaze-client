package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
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
            "Collapse consecutive identical chat messages into one with an (Nx) suffix"
    ).setValue(true);

    private boolean bypass = false;

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
        return "Filters duplicate chat messages by collapsing them into one (Nx)";
    }

    /**
     * Set while we're recursively re-adding a message we modified, so the
     * collapse mixin doesn't reprocess our own output as a duplicate.
     */
    public boolean isBypassActive() {
        return bypass;
    }

    /**
     * Inspects the latest message in the {@link ChatHud}. If it matches
     * the {@code incoming} text (after stripping any prior {@code (Nx)}
     * suffix), removes it from the message lists and returns a new
     * {@link Text} containing the same base text with an incremented count
     * suffix. Returns {@code null} when no collapse should happen and the
     * caller should let vanilla render the message normally.
     */
    public Text tryCollapse(ChatHud hud, Text incoming) {
        if (!isEnabled() || !collapseRepeats.isValue() || incoming == null) {
            return null;
        }

        ChatHudAccessor accessor = (ChatHudAccessor) hud;
        List<ChatHudLine> messages = accessor.phaze$getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        ChatHudLine topLine = messages.get(0);
        if (topLine == null || topLine.content() == null) {
            return null;
        }

        ParsedSuffix incomingParsed = stripSuffix(incoming.getString());
        ParsedSuffix topParsed = stripSuffix(topLine.content().getString());

        if (!incomingParsed.baseRaw.equals(topParsed.baseRaw)) {
            return null;
        }

        int newCount = topParsed.count + 1;

        // Remove the previous occurrence so we can re-add the collapsed one
        // through vanilla's normal flow (which preserves wrapping, scroll
        // index assignment, etc.).
        messages.remove(0);
        List<ChatHudLine.Visible> visible = accessor.phaze$getVisibleMessages();
        if (visible != null && !visible.isEmpty()) {
            // Most chat messages fit on one wrapped line. Long wrapped lines
            // can leave residual entries which is acceptable for v1; vanilla
            // refresh on width change cleans them up eventually.
            visible.remove(0);
        }

        return Text.literal(incomingParsed.baseRaw + " (" + newCount + "x)");
    }

    public void runWithBypass(Runnable runnable) {
        bypass = true;
        try {
            runnable.run();
        } finally {
            bypass = false;
        }
    }

    private ParsedSuffix stripSuffix(String text) {
        if (text == null) {
            return new ParsedSuffix("", 1);
        }
        Matcher matcher = COUNT_SUFFIX.matcher(text);
        if (matcher.find()) {
            int count;
            try {
                count = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                count = 1;
            }
            return new ParsedSuffix(text.substring(0, matcher.start()), count);
        }
        return new ParsedSuffix(text, 1);
    }

    private record ParsedSuffix(String baseRaw, int count) {}
}
