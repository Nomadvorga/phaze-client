package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MentionHighlight extends Module {
    private static final MentionHighlight INSTANCE = new MentionHighlight();

    public final SectionSetting matchSection = new SectionSetting("Match");
    public final BooleanSetting matchUsername = new BooleanSetting(
            "Match Your Username",
            "Highlight when someone says your in-game name"
    ).setValue(true);
    public final TextSetting extraTriggers = new TextSetting(
            "Extra Triggers",
            "Comma-separated list of extra words that count as a mention (case-insensitive)"
    ).setText("");

    public final SectionSetting visualSection = new SectionSetting("Visual");
    public final BooleanSetting recolor = new BooleanSetting(
            "Recolor Match",
            "Tint the matched word with the highlight color"
    ).setValue(true);
    public final ColorSetting highlightColor = new ColorSetting(
            "Highlight Color",
            "Color applied to matched substrings"
    ).setColor(0xFFFFAA00).popupRow();
    public final BooleanSetting bold = new BooleanSetting(
            "Bold",
            "Render the matched substring in bold"
    ).setValue(true);
    public final BooleanSetting underline = new BooleanSetting(
            "Underline",
            "Underline the matched substring"
    ).setValue(false);

    public final SectionSetting soundSection = new SectionSetting("Sound");
    public final BooleanSetting playSound = new BooleanSetting(
            "Play Sound",
            "Ping sound when a mention is received"
    ).setValue(true);
    public final ValueSetting volume = new ValueSetting(
            "Volume",
            "Sound volume (0.0 = mute, 1.0 = full)"
    ).range(0.0F, 1.0F).step(0.05F).setValue(0.6F);
    public final ValueSetting pitch = new ValueSetting(
            "Pitch",
            "Sound pitch multiplier"
    ).range(0.5F, 2.0F).step(0.05F).setValue(1.4F);

    private static final long SOUND_COOLDOWN_MS = 350L;

    private static final long ECHO_WINDOW_MS = 2000L;

    private volatile String lastOutgoing = null;
    private volatile long lastOutgoingAtMs = 0L;

    private long lastSoundAtMs = 0L;
    private List<Pattern> compiledTriggers = null;
    private String compiledTriggersFromText = null;

    private MentionHighlight() {
        super("mention_highlight", "Mention Highlight", ModuleCategory.UTILITIES);

        matchUsername.setFullWidth(true);
        extraTriggers.setFullWidth(true);
        recolor.setFullWidth(true);
        highlightColor.setFullWidth(true);
        bold.setFullWidth(true);
        underline.setFullWidth(true);
        playSound.setFullWidth(true);
        volume.setFullWidth(true);
        pitch.setFullWidth(true);

        setup(
                matchSection, matchUsername, extraTriggers,
                visualSection, recolor, highlightColor, bold, underline,
                soundSection, playSound, volume, pitch
        );
    }

    public static MentionHighlight getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Highlights mentions of your name in chat and plays a ping sound";
    }

    @Override
    public String getIcon() {
        return "mention_highlight.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public void markOutgoing(String content) {
        if (!isEnabled() || content == null) {
            return;
        }
        lastOutgoing = normalizeForEcho(content);
        lastOutgoingAtMs = System.currentTimeMillis();
    }

    public void markOutgoing() {
        markOutgoing("");
    }

    public Text processIncoming(Text original) {
        if (!isEnabled() || original == null) {
            return original;
        }

        String flat = original.getString();
        if (flat == null || flat.isEmpty()) {
            return original;
        }
        String normalizedFlat = normalizeForEcho(flat);

        MinecraftClient mcc = MinecraftClient.getInstance();

        String selfName = (mcc != null && mcc.player != null)
                ? mcc.player.getGameProfile().name()
                : null;
        if (selfName != null && !selfName.isEmpty() && isSelfAuthored(flat, selfName)) {
            return original;
        }

        List<Pattern> triggers = resolveTriggers();
        boolean hit = false;
        for (Pattern p : triggers) {
            if (p.matcher(flat).find()) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            return original;
        }

        long now = System.currentTimeMillis();
        String pending = lastOutgoing;
        if (pending != null && !pending.isEmpty()) {
            long age = now - lastOutgoingAtMs;
            if (age > ECHO_WINDOW_MS) {

                lastOutgoing = null;
            } else if (matchesOutgoingEcho(normalizedFlat, pending)) {
                lastOutgoing = null;
                return original;
            }
        }

        if (playSound.isValue() && now - lastSoundAtMs >= SOUND_COOLDOWN_MS) {
            playPingSound();
            lastSoundAtMs = now;
        }

        if (!recolor.isValue() && !bold.isValue() && !underline.isValue()) {

            return original;
        }

        return rebuildWithHighlights(original, triggers);
    }

    private List<Pattern> resolveTriggers() {

        MinecraftClient mc = MinecraftClient.getInstance();

        String username = (mc != null && mc.player != null && matchUsername.isValue())
                ? mc.player.getGameProfile().name()
                : null;
        String extras = extraTriggers.getText();
        String key = (username == null ? "" : username) + "\u0000" + (extras == null ? "" : extras);

        if (compiledTriggers != null && key.equals(compiledTriggersFromText)) {
            return compiledTriggers;
        }

        List<Pattern> built = new ArrayList<>();
        if (username != null && !username.isEmpty()) {

            built.add(Pattern.compile(Pattern.quote(username), Pattern.CASE_INSENSITIVE));
        }
        if (extras != null) {
            for (String raw : extras.split(",")) {
                String t = raw.trim();
                if (t.isEmpty()) continue;

                built.add(Pattern.compile("\\b" + Pattern.quote(t) + "\\b", Pattern.CASE_INSENSITIVE));
            }
        }

        compiledTriggers = built;
        compiledTriggersFromText = key;
        return built;
    }

    private Text rebuildWithHighlights(Text original, List<Pattern> triggers) {
        Style highlightStyle = buildHighlightStyle();
        return walk(original, triggers, highlightStyle);
    }

    private Text walk(Text node, List<Pattern> triggers, Style highlight) {
        Text rewritten = node.getContent() instanceof PlainTextContent ptc
                ? rewriteSegment(ptc.string(), node.getStyle(), triggers, highlight)
                : null;

        MutableText acc;
        if (rewritten != null) {
            acc = rewritten.copy();
        } else {
            acc = node.copyContentOnly().setStyle(node.getStyle());
        }
        for (Text sibling : node.getSiblings()) {
            acc.append(walk(sibling, triggers, highlight));
        }
        return acc;
    }

    private Text rewriteSegment(String segment, Style baseStyle, List<Pattern> triggers, Style highlight) {
        if (segment == null || segment.isEmpty()) {
            return Text.empty().setStyle(baseStyle);
        }

        Pattern combined = combine(triggers);
        java.util.regex.Matcher m = combined.matcher(segment);
        if (!m.find()) {
            return Text.literal(segment).setStyle(baseStyle);
        }
        m.reset();

        MutableText out = Text.empty().setStyle(baseStyle);
        int last = 0;
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (start > last) {
                out.append(Text.literal(segment.substring(last, start)).setStyle(baseStyle));
            }

            Style merged = mergeStyles(baseStyle, highlight);
            out.append(Text.literal(segment.substring(start, end)).setStyle(merged));
            last = end;
        }
        if (last < segment.length()) {
            out.append(Text.literal(segment.substring(last)).setStyle(baseStyle));
        }
        return out;
    }

    private Pattern combine(List<Pattern> triggers) {
        if (triggers.size() == 1) return triggers.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < triggers.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append("(?:").append(triggers.get(i).pattern()).append(')');
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private Style mergeStyles(Style base, Style highlight) {
        Style s = base;
        TextColor color = highlight.getColor();
        if (color != null) {
            s = s.withColor(color);
        }
        if (highlight.isBold()) {
            s = s.withBold(true);
        }
        if (highlight.isUnderlined()) {
            s = s.withUnderline(true);
        }
        return s;
    }

    private Style buildHighlightStyle() {

        Style s = Style.EMPTY;
        if (recolor.isValue()) {

            int rgb = highlightColor.getColor() & 0x00FFFFFF;
            s = s.withColor(TextColor.fromRgb(rgb));
        }
        if (bold.isValue()) {
            s = s.withBold(true);
        }
        if (underline.isValue()) {
            s = s.withUnderline(true);
        }
        return s;
    }

    private void playPingSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        SoundEvent event = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        float v = clamp(volume.getValue(), 0.0F, 1.0F);
        float p = clamp(pitch.getValue(), 0.5F, 2.0F);

        mc.getSoundManager().play(PositionedSoundInstance.ui(event, p, v));
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static String normalizeForEcho(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        boolean prevSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                prevSpace = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!prevSpace) {
                    out.append(' ');
                    prevSpace = true;
                }
            }
        }
        int start = 0;
        int end = out.length();
        while (start < end && out.charAt(start) == ' ') start++;
        while (end > start && out.charAt(end - 1) == ' ') end--;
        return out.substring(start, end);
    }

    private static boolean matchesOutgoingEcho(String incoming, String pendingOutgoing) {
        if (incoming == null || pendingOutgoing == null
                || incoming.isEmpty() || pendingOutgoing.isEmpty()) {
            return false;
        }
        if (incoming.contains(pendingOutgoing) || pendingOutgoing.contains(incoming)) {
            return true;
        }

        if (pendingOutgoing.length() < 6) {
            return false;
        }
        String[] tokens = pendingOutgoing.split(" ");
        int matched = 0;
        for (String t : tokens) {
            if (t.length() < 3) continue;
            if (incoming.contains(t)) matched++;
        }
        return matched >= 2;
    }

    private static boolean isSelfAuthored(String flat, String selfName) {

        int max = Math.min(flat.length(), 80);
        int sep = -1;
        for (int i = 0; i < max; i++) {
            char c = flat.charAt(i);
            if (c == ':' || c == '»' || c == '>' || c == '\u203A') {
                sep = i;
                break;
            }
        }
        if (sep < 0) {

            return false;
        }
        String header = flat.substring(0, sep);
        String lowerHeader = header.toLowerCase();
        String lowerName = selfName.toLowerCase();

        int idx = lowerHeader.indexOf(lowerName);
        while (idx >= 0) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(lowerHeader.charAt(idx - 1));
            int after = idx + lowerName.length();
            boolean rightOk = after >= lowerHeader.length()
                    || !Character.isLetterOrDigit(lowerHeader.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
            idx = lowerHeader.indexOf(lowerName, idx + 1);
        }
        return false;
    }
}
