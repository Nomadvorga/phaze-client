package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Edits the vanilla player list in place: visibility, colours, names and ping. */
public final class TabEditor extends Module {
    private static final TabEditor INSTANCE = new TabEditor();
    private static final int VANILLA_PANEL_COLOR = 0x80000000;
    private static final int VANILLA_ROW_COLOR = 0x20FFFFFF;
    private static final int PING_ROW_ALPHA = 0x40;
    private static final int PING_HIDE_ABOVE = 500;

    // Half-width settings pair up left/right in declaration order.
    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting toggleTab = new BooleanSetting("Toggle Tab", "Tab opens and closes the list instead of holding it").setValue(false);
    public final BooleanSetting disableHeader = new BooleanSetting("Disable Header", "Hide the server's tab header").setValue(false);
    public final BooleanSetting displayPlayerHead = new BooleanSetting("Display Player Head", "Show player heads where the server sends skins").setValue(true);
    public final BooleanSetting disableFooter = new BooleanSetting("Disable Footer", "Hide the server's tab footer").setValue(false);

    public final SectionSetting colorSection = new SectionSetting("Color");
    public final ColorSetting headerColor = new ColorSetting("Header Color", "Background behind the tab header")
            .value(VANILLA_PANEL_COLOR).visible(() -> !disableHeader.isValue());
    public final ColorSetting footerColor = new ColorSetting("Footer Color", "Background behind the tab footer")
            .value(VANILLA_PANEL_COLOR).visible(() -> !disableFooter.isValue());
    public final ColorSetting backgroundColor = new ColorSetting("Background Color", "Background behind the player list")
            .value(VANILLA_PANEL_COLOR);
    public final BooleanSetting usePingColorForRow = new BooleanSetting("Use Ping Color For Row", "Tint each player's row by their ping").setValue(false);
    public final ColorSetting rowColor = new ColorSetting("Row Color", "Background of each player row")
            .value(VANILLA_ROW_COLOR).visible(() -> !usePingColorForRow.isValue());

    public final SectionSetting nameSection = new SectionSetting("Name");
    public final BooleanSetting highlightOwnName = new BooleanSetting("Highlight Own Name On Tab", "Recolour your own nickname in the list").setValue(true);
    public final ColorSetting tabNameColor = new ColorSetting("Tab Name Color", "Colour of your own nickname")
            .value(0xFFFF5555).noAlpha();
    public final BooleanSetting nameShadow = new BooleanSetting("Add Shadow for Name", "Draw player names with a shadow").setValue(true);
    public final BooleanSetting moveSelfToTop = new BooleanSetting("Move Self To Top", "Put your own row first in the list").setValue(false);

    public final SectionSetting pingSection = new SectionSetting("Ping Options");
    public final BooleanSetting hidePing = new BooleanSetting("Hide Ping", "Hide everyone's ping").setValue(false);
    public final BooleanSetting hidePingOver500 = new BooleanSetting("Hide Ping If Over 500", "Hide ping above 500 ms").setValue(false);
    public final BooleanSetting pingNumberShadow = new BooleanSetting("Ping Number Shadow", "Draw the ping number with a shadow").setValue(true)
            .visible(() -> !hidePing.isValue());
    public final BooleanSetting dynamicPingNumber = new BooleanSetting("Dynamic Ping Number", "Colour the ping number by latency").setValue(true)
            .visible(() -> !hidePing.isValue());
    public final ColorSetting lowPingColor = pingColorSetting("Low Ping Color (< 65)", 0xFF55FF55);
    public final ColorSetting mediumPingColor = pingColorSetting("Medium Ping Color (> 65)", 0xFFFFFF55);
    public final ColorSetting highPingColor = pingColorSetting("High Ping Color (> 120)", 0xFFFF5555);
    public final ColorSetting extremePingColor = pingColorSetting("Extremely High Ping Color (> 250)", 0xFFAA0000);

    private boolean toggledOpen;
    private boolean toggleArmed;

    public static TabEditor getInstance() {
        return INSTANCE;
    }

    private TabEditor() {
        // Keep the identifier so existing configs and server rules still apply.
        super("tab_hud", "Tab Editor", ModuleCategory.HUD);
        for (var setting : List.of(headerColor, footerColor, backgroundColor, usePingColorForRow, rowColor,
                lowPingColor, mediumPingColor, highPingColor, extremePingColor)) {
            setting.setFullWidth(true);
        }
        setup(generalSection, toggleTab, disableHeader, displayPlayerHead, disableFooter,
                colorSection, headerColor, footerColor, backgroundColor, usePingColorForRow, rowColor,
                nameSection, highlightOwnName, tabNameColor, nameShadow, moveSelfToTop,
                pingSection, hidePing, hidePingOver500, pingNumberShadow, dynamicPingNumber,
                lowPingColor, mediumPingColor, highPingColor, extremePingColor);
    }

    private ColorSetting pingColorSetting(String name, int color) {
        return new ColorSetting(name, "Ping number colour in this range").value(color).noAlpha()
                .visible(() -> (!hidePing.isValue() && dynamicPingNumber.isValue()) || usePingColorForRow.isValue());
    }

    @Override
    public String getDescription() {
        return "Customize the tab player list";
    }

    @Override
    public String getIcon() {
        return "tab_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Once per frame, before anything asks whether the list is open. Toggle mode counts
     * key presses rather than sampling the key, so a tap shorter than a frame still counts.
     */
    public void pollToggle(MinecraftClient client) {
        if (!isEnabled() || !toggleTab.isValue() || client == null || client.options == null || client.player == null) {
            toggledOpen = false;
            toggleArmed = false;
            return;
        }
        KeyBinding key = client.options.playerListKey;
        int presses = 0;
        while (key.wasPressed()) {
            presses++;
        }
        // Presses counted before toggle mode was on are stale: the vanilla list never reads them.
        if (!toggleArmed) {
            toggleArmed = true;
            return;
        }
        if ((presses & 1) == 1) {
            toggledOpen = !toggledOpen;
        }
    }

    /** Whether the player wants the list shown: the held key, or the toggled state. */
    public static boolean isListKeyDown(MinecraftClient client) {
        TabEditor editor = INSTANCE;
        if (editor.isEnabled() && editor.toggleTab.isValue()) {
            return editor.toggledOpen;
        }
        return client != null && client.options != null && client.options.playerListKey.isPressed();
    }

    public boolean showsPlayerHeads() {
        return !isEnabled() || displayPlayerHead.isValue();
    }

    public boolean hidesHeader() {
        return isEnabled() && disableHeader.isValue();
    }

    public boolean hidesFooter() {
        return isEnabled() && disableFooter.isValue();
    }

    public int headerColor(int vanilla) {
        return isEnabled() ? headerColor.getColor() : vanilla;
    }

    public int footerColor(int vanilla) {
        return isEnabled() ? footerColor.getColor() : vanilla;
    }

    public int backgroundColor(int vanilla) {
        return isEnabled() ? backgroundColor.getColor() : vanilla;
    }

    public int rowColor(int vanilla, PlayerListEntry entry) {
        if (!isEnabled()) {
            return vanilla;
        }
        if (usePingColorForRow.isValue()) {
            if (entry == null) {
                return vanilla;
            }
            return (PING_ROW_ALPHA << 24) | (pingColor(entry.getLatency()) & 0x00FFFFFF);
        }
        return rowColor.getColor();
    }

    /** Space the vanilla layout reserves for the ping, sized for a three-digit number. */
    public int pingSlotWidth(int vanilla, TextRenderer renderer) {
        if (!isEnabled()) {
            return vanilla;
        }
        if (hidePing.isValue()) {
            return 2;
        }
        return Math.max(vanilla, renderer.getWidth("000") + 3);
    }

    public boolean hidesPing(int latency) {
        return hidePing.isValue() || latency < 0 || (hidePingOver500.isValue() && latency > PING_HIDE_ABOVE);
    }

    public int pingTextColor(int latency) {
        return dynamicPingNumber.isValue() ? pingColor(latency) : 0xFFFFFFFF;
    }

    public int pingColor(int latency) {
        if (latency < 65) return 0xFF000000 | lowPingColor.getColor();
        if (latency <= 120) return 0xFF000000 | mediumPingColor.getColor();
        if (latency <= 250) return 0xFF000000 | highPingColor.getColor();
        return 0xFF000000 | extremePingColor.getColor();
    }

    public boolean drawsNameShadow() {
        return !isEnabled() || nameShadow.isValue();
    }

    /** Recolours just the player's own nickname, leaving server prefixes and suffixes as sent. */
    public Text styleOwnName(PlayerListEntry entry, Text name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !highlightOwnName.isValue() || name == null || client == null || client.player == null) {
            return name;
        }
        String nickname = client.player.getGameProfile().getName();
        if (!isSelf(entry) && !containsWord(name.getString(), nickname)) {
            return name;
        }
        return recolor(name, nickname, tabNameColor.getColor() & 0x00FFFFFF);
    }

    static Text recolor(Text text, String nickname, int rgb) {
        String flat = text.getString();
        int start = nickname == null || nickname.isEmpty() ? -1
                : flat.toLowerCase(Locale.ROOT).indexOf(nickname.toLowerCase(Locale.ROOT));
        if (start < 0) {
            MutableText whole = text.copy();
            return whole.setStyle(whole.getStyle().withColor(rgb));
        }
        int end = start + nickname.length();
        MutableText out = Text.empty();
        int[] offset = {0};
        text.visit((style, part) -> {
            int from = offset[0];
            int to = from + part.length();
            offset[0] = to;
            int cutStart = Math.max(start, from) - from;
            int cutEnd = Math.min(end, to) - from;
            if (cutStart >= cutEnd) {
                if (!part.isEmpty()) out.append(Text.literal(part).setStyle(style));
                return Optional.empty();
            }
            if (cutStart > 0) out.append(Text.literal(part.substring(0, cutStart)).setStyle(style));
            out.append(Text.literal(part.substring(cutStart, cutEnd)).setStyle(style.withColor(rgb)));
            if (cutEnd < part.length()) out.append(Text.literal(part.substring(cutEnd)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /**
     * Puts the player's row first. Vanilla keeps only the first 80 sorted rows, so on big
     * servers the player is often not among them, and layout plugins replace the real
     * entry with fake ones that only carry the name: both are looked for here.
     */
    public List<PlayerListEntry> moveSelfFirst(List<PlayerListEntry> shown, Collection<PlayerListEntry> listed) {
        if (!isEnabled() || !moveSelfToTop.isValue() || shown == null) {
            return shown;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return shown;
        }
        int index = indexOfSelf(shown, client.player.getUuid(), client.player.getGameProfile().getName());
        if (index == 0) {
            return shown;
        }
        List<PlayerListEntry> reordered = new ArrayList<>(shown);
        if (index > 0) {
            reordered.add(0, reordered.remove(index));
            return reordered;
        }
        PlayerListEntry self = listed == null ? null : findByUuid(listed, client.player.getUuid());
        if (self == null) {
            return shown;
        }
        reordered.add(0, self);
        if (reordered.size() > 80) {
            reordered.remove(reordered.size() - 1);
        }
        return reordered;
    }

    private static int indexOfSelf(List<PlayerListEntry> entries, UUID uuid, String nickname) {
        for (int i = 0; i < entries.size(); i++) {
            if (uuid.equals(entries.get(i).getProfile().getId())) return i;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (nickname.equalsIgnoreCase(entries.get(i).getProfile().getName())) return i;
        }
        for (int i = 0; i < entries.size(); i++) {
            Text display = entries.get(i).getDisplayName();
            if (display != null && containsWord(display.getString(), nickname)) return i;
        }
        return -1;
    }

    private static PlayerListEntry findByUuid(Collection<PlayerListEntry> entries, UUID uuid) {
        for (PlayerListEntry entry : entries) {
            if (uuid.equals(entry.getProfile().getId())) return entry;
        }
        return null;
    }

    private boolean isSelf(PlayerListEntry entry) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entry == null || client == null || client.player == null) {
            return false;
        }
        return client.player.getUuid().equals(entry.getProfile().getId())
                || client.player.getGameProfile().getName().equalsIgnoreCase(entry.getProfile().getName());
    }

    static boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String needle = word.toLowerCase(Locale.ROOT);
        for (int at = lower.indexOf(needle); at >= 0; at = lower.indexOf(needle, at + 1)) {
            int end = at + needle.length();
            boolean leftEdge = at == 0 || !isNameChar(lower.charAt(at - 1));
            boolean rightEdge = end == lower.length() || !isNameChar(lower.charAt(end));
            if (leftEdge && rightEdge) return true;
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }
}
