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

/**
 * Watches incoming chat for mentions (player's own username plus a
 * comma-separated list of extra trigger words) and:
 * <ul>
 *   <li>recolors / underlines / bolds the matched substring inside
 *       the chat row so it visually pops;</li>
 *   <li>plays a configurable ping sound at a configurable volume;</li>
 * </ul>
 *
 * <h3>Self-mention skip</h3>
 * The user's own outgoing messages obviously match their own
 * username - the whole point of the module is that <em>other</em>
 * people pinged you, not that you pinged yourself. The
 * {@link #suppressNextSelf} latch is set inside
 * {@link vorga.phazeclient.mixins.ClientPlayNetworkHandlerMentionSelfSkipMixin}
 * just before the message goes out; the next incoming chat row that
 * matches the player's own username is consumed (not pinged, not
 * highlighted) and the latch resets, so subsequent <em>other</em>
 * mentions in the same chat session ping again. The latch is a
 * single boolean rather than a queue because the typical flow is
 * "I send X, server echoes X back, latch clears" - the loopback is
 * always exactly one message ahead. If a server batches replies
 * (rare) the worst case is one missed self-ping, never a stuck
 * latch.
 *
 * <h3>Match strategy</h3>
 * Plain {@code String.toLowerCase().contains(...)} for the username
 * (so "vorga" matches "Vorga," and "vorga's"), substring with word
 * boundaries for trigger words. Regex compiled lazily and cached
 * because chat throughput on busy servers is hundreds of messages
 * per minute and rebuilding patterns per row would burn allocation
 * for no reason.
 *
 * <h3>Why post-process the {@link Text}, not pre-process the string</h3>
 * Vanilla chat messages are {@code Text} trees with embedded styles
 * (player nametag colour, click events, hover-tooltips for kicked-
 * player tags, etc). Flattening to string and rebuilding from
 * scratch would lose all of that. Instead we walk the tree and only
 * recolor the literal text segments where the match lands, leaving
 * sibling formatting intact.
 */
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
    ).setColor(0xFFFFAA00);
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

    /** Cooldown between consecutive ping sounds, ms. Prevents
     *  ear-shattering spam when an angry user types your name three
     *  times in a row. Tuned to roughly match Discord's pivot of
     *  ~300 ms between same-channel mention pings. */
    private static final long SOUND_COOLDOWN_MS = 350L;

    /** Set by the outgoing-chat mixin right before an outgoing
     *  message hits the wire. Cleared the next time an incoming
     *  message would have pinged - that incoming message is assumed
     *  to be the server echo of our own send and is silently
     *  consumed. See class javadoc for why this is a single bool
     *  rather than a queue. */
    private volatile boolean suppressNextSelf = false;

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

    /**
     * Flag the next incoming chat row as "this is the server echo
     * of my own outgoing message". The mixin on
     * {@code ClientPlayNetworkHandler.sendChatMessage} calls this
     * right before the network send; the next
     * {@link #processIncoming(Text)} clears the latch and skips the
     * ping/highlight pass.
     */
    public void markOutgoing() {
        if (!isEnabled()) {
            return;
        }
        suppressNextSelf = true;
    }

    /**
     * Hook from the chat-receive mixin. Returns the (possibly
     * highlighted) text to display, and side-effects the ping sound
     * if a new mention was detected.
     */
    public Text processIncoming(Text original) {
        if (!isEnabled() || original == null) {
            return original;
        }

        String flat = original.getString();
        if (flat == null || flat.isEmpty()) {
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

        // Self-echo skip: the player's own send loops back from the
        // server within ~50-100 ms; consume the latch and don't
        // ping. Highlight is also skipped because the message is
        // visually identifiable as the user's own (server prefix /
        // tag colour) and ringing yourself out is noise.
        if (suppressNextSelf) {
            suppressNextSelf = false;
            return original;
        }

        // Sound first - cheaper to short-circuit on cooldown than
        // to build a Text rewrite we'd then throw away. Cooldown
        // also protects the audio channel from rapid-fire spam.
        long now = System.currentTimeMillis();
        if (playSound.isValue() && now - lastSoundAtMs >= SOUND_COOLDOWN_MS) {
            playPingSound();
            lastSoundAtMs = now;
        }

        if (!recolor.isValue() && !bold.isValue() && !underline.isValue()) {
            // No visual change requested; sound-only mode. Return
            // the original Text so the chat-mixin's @ModifyVariable
            // doesn't mutate the row at all.
            return original;
        }

        return rebuildWithHighlights(original, triggers);
    }

    private List<Pattern> resolveTriggers() {
        // Cache key: combined username + extras text. Recompile when
        // either changes; cheap because chat doesn't churn settings.
        MinecraftClient mc = MinecraftClient.getInstance();
        String username = (mc != null && mc.player != null && matchUsername.isValue())
                ? mc.player.getGameProfile().getName()
                : null;
        String extras = extraTriggers.getText();
        String key = (username == null ? "" : username) + "\u0000" + (extras == null ? "" : extras);

        if (compiledTriggers != null && key.equals(compiledTriggersFromText)) {
            return compiledTriggers;
        }

        List<Pattern> built = new ArrayList<>();
        if (username != null && !username.isEmpty()) {
            // No word boundary for the username - chat clients often
            // suffix it with punctuation ("Vorga,", "Vorga:", "Vorga's"
            // etc). Quote the literal so a player named "Mr.X" doesn't
            // turn into a regex catastrophe.
            built.add(Pattern.compile(Pattern.quote(username), Pattern.CASE_INSENSITIVE));
        }
        if (extras != null) {
            for (String raw : extras.split(",")) {
                String t = raw.trim();
                if (t.isEmpty()) continue;
                // Word boundaries on extras prevent a trigger like
                // "ru" from firing on "trust" / "Russia". Username
                // is treated more permissively above because the
                // user explicitly opts into seeing their own name.
                built.add(Pattern.compile("\\b" + Pattern.quote(t) + "\\b", Pattern.CASE_INSENSITIVE));
            }
        }

        compiledTriggers = built;
        compiledTriggersFromText = key;
        return built;
    }

    /**
     * Walks the {@link Text} tree and produces a new tree where
     * every literal-text segment containing a trigger match has its
     * matched substring(s) wrapped in a styled child while the rest
     * of the segment keeps its original style. Sibling segments
     * with formatting (player nametag colour, hover events, etc.)
     * pass through unchanged because we only descend through
     * plain-text content nodes.
     */
    private Text rebuildWithHighlights(Text original, List<Pattern> triggers) {
        Style highlightStyle = buildHighlightStyle();
        return walk(original, triggers, highlightStyle);
    }

    private Text walk(Text node, List<Pattern> triggers, Style highlight) {
        Text rewritten = node.getContent() instanceof PlainTextContent ptc
                ? rewriteSegment(ptc.string(), node.getStyle(), triggers, highlight)
                : null;

        // Always rebuild siblings so a deeper match in a child gets
        // the highlight too. We can't just return `node` if the top-
        // level segment didn't match because a trigger might live
        // inside a nested sibling.
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
        // Find the union of all matches across triggers. Pattern
        // overlaps are rare (a player named "pin" plus trigger
        // "ping") and resolving them precisely would balloon the
        // code; instead we OR the patterns into one alt and let the
        // longest-match-first nature of regex pick a sane span.
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
            // Layer the highlight style ON TOP of the base so we
            // keep the player-tag colour/ click events of the
            // original segment as fallbacks.
            Style merged = baseStyle.withParent(highlight);
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

    private Style buildHighlightStyle() {
        Style s = Style.EMPTY;
        if (recolor.isValue()) {
            // ARGB -> RGB: the chat renderer ignores the alpha
            // channel of vanilla TextColor (it derives alpha from
            // the message-fade pipeline). Strip it explicitly so
            // the colour reads as the user intended.
            int rgb = highlightColor.getColor() & 0x00FFFFFF;
            s = s.withColor(TextColor.fromRgb(rgb));
        }
        if (bold.isValue()) {
            s = s.withFormatting(Formatting.BOLD);
        }
        if (underline.isValue()) {
            s = s.withFormatting(Formatting.UNDERLINE);
        }
        return s;
    }

    private void playPingSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        SoundEvent event = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        float v = clamp(volume.getValue(), 0.0F, 1.0F);
        float p = clamp(pitch.getValue(), 0.5F, 2.0F);
        mc.getSoundManager().play(PositionedSoundInstance.master(event, p, v));
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
