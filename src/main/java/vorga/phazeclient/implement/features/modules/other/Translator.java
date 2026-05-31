package vorga.phazeclient.implement.features.modules.other;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.mixins.ChatHudAccessor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-translates incoming chat messages and posts the translation as a
 * new chat row directly under the original. The translation row preserves
 * the speaker's prefix (rank brackets / nickname) verbatim and tags the
 * translated body with a {@code (srcLang2dstLang)} marker the user can
 * eyeball at a glance.
 *
 * <h3>Why not Apify by default</h3>
 * The user originally provided an Apify API token. Apify translation
 * actors (e.g. {@code web.harvester/google-translator}) work by spawning
 * a containerised browser that drives {@code translate.google.com} via
 * Puppeteer; cold-start latency is 5-30 seconds per request, which is
 * unusable for chat where messages arrive every few seconds. The
 * <strong>same Google data</strong> is available through the public
 * mobile endpoint at {@code translate.googleapis.com/translate_a/single}
 * with sub-second latency, no token required, and no monthly
 * usage-credit cost. Default {@link #provider} is therefore
 * {@code "Google"}; the Apify path is preserved as an opt-in fallback
 * (using the user's token) for cases where the public endpoint is
 * blocked at the network level.
 *
 * <h3>Threading model</h3>
 * Two-thread daemon executor; chat handler hits {@link #onIncomingChat}
 * on the main client thread, the executor performs the HTTP work, and
 * the resulting {@link Text} is posted back via {@code MinecraftClient
 * .execute()} so {@link ChatHud#addMessage(Text)} only ever runs on the
 * main thread (vanilla {@code addMessage} mutates the same message list
 * the renderer iterates - off-thread writes would race the HUD).
 *
 * <h3>Recursion guard</h3>
 * Our own translation row also flows through {@code ChatHud.addMessage},
 * so the {@link vorga.phazeclient.mixins.ChatHudTranslatorMixin} would
 * see the translated text and queue a translation OF the translation,
 * ad infinitum. {@link #bypass} is set while we re-add to suppress this
 * - mirrors the pattern used by {@link ChatHelper#runWithBypass}.
 *
 * <h3>Cache</h3>
 * 256-entry LRU on {@code targetLang|sourceText} prevents the executor
 * from spamming the same string when a server repeatedly broadcasts
 * identical lines (login messages, kits, etc.).
 */
public final class Translator extends Module {
    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    private static final Logger LOG = LoggerFactory.getLogger("PhazeTranslator");

    /**
     * Translation keys whose presence at the root of an incoming
     * {@link Text} marks it as a Minecraft client-internal message
     * (the user explicitly asked us to leave those alone: "чтобы
     * системные сообщения от майна не трогало, типо скрины, ф3+б и т
     * д"). These are emitted by client code via {@code
     * Text.translatable("<key>")} and routed through the same
     * {@code ChatHud.addMessage(Text)} pipeline as real chat, so we
     * have to recognise them by key prefix:
     * <ul>
     *   <li>{@code debug.*}        - F3+B hitboxes, F3+P pause, F3+G
     *       chunk borders, etc.</li>
     *   <li>{@code screenshot.*}   - F2 screenshot save confirmations</li>
     *   <li>{@code narrator.*}     - accessibility narrator toggles</li>
     *   <li>{@code commands.*}     - client-side command output (some
     *       servers also emit these, but the volume of useful
     *       translations there is low and false positives from
     *       client-side {@code /help} are common)</li>
     *   <li>{@code key.*} / {@code options.*} - keybinding /
     *       options change confirmations</li>
     *   <li>{@code chat.cannotSend} / {@code chat.disabled.*} -
     *       client-side chat-state notices</li>
     * </ul>
     * Server-emitted translation keys like {@code death.*},
     * {@code multiplayer.player.joined}, {@code chat.type.text}, etc.
     * are <em>not</em> in this list because they originate from
     * gameplay and should be translatable by request (some users want
     * "Player1 was slain by Player2" auto-translated).
     */
    private static final Set<String> CLIENT_SYSTEM_KEY_PREFIXES = Set.of(
            "debug.",
            "screenshot.",
            "narrator.",
            "commands.",
            "key.",
            "options.",
            "chat.cannotSend",
            "chat.disabled"
    );

    /**
     * Display name -> ISO 639-1 code map used by both target and
     * source language pickers. The picker UI shows the full English
     * name ("Russian", "Chinese", ...) per user request, but every
     * downstream consumer (Google API {@code sl}/{@code tl} params,
     * Apify actor input, cache key, suffix marker, same-language
     * comparison) needs the ISO code. {@link #languageNameToCode}
     * is the single conversion site.
     *
     * <p><strong>Chinese deserves a callout.</strong> The Google
     * mobile endpoint at {@code translate.googleapis.com/translate_a/single}
     * is strict about Chinese codes - bare {@code zh} returns an
     * empty translation array for short inputs and silently 200s
     * (looks like "translation succeeded but produced nothing" to our
     * code, which then drops the result). {@code zh-CN} (Simplified)
     * works reliably for both directions. Users wanting Traditional
     * specifically can edit the map to add a separate entry, but for
     * the 99% case Simplified is the right pick.
     */
    private static final Map<String, String> LANGUAGE_NAME_TO_CODE = Map.ofEntries(
            Map.entry("Russian",     "ru"),
            Map.entry("English",     "en"),
            Map.entry("Ukrainian",   "uk"),
            Map.entry("Belarusian",  "be"),
            Map.entry("Spanish",     "es"),
            Map.entry("French",      "fr"),
            Map.entry("German",      "de"),
            Map.entry("Italian",     "it"),
            Map.entry("Portuguese",  "pt"),
            Map.entry("Polish",      "pl"),
            Map.entry("Turkish",     "tr"),
            Map.entry("Chinese",     "zh-CN"),
            Map.entry("Japanese",    "ja"),
            Map.entry("Korean",      "ko"),
            Map.entry("Arabic",      "ar"),
            Map.entry("Hindi",       "hi"),
            Map.entry("Vietnamese",  "vi"),
            Map.entry("Indonesian",  "id")
    );

    /**
     * Display name list in canonical order - drives the picker
     * dropdown. Russian first (default target) and English first in
     * the source picker are placed in their respective {@code
     * .selected(...)} calls; this list is shared so both pickers see
     * the same options.
     */
    private static final String[] LANGUAGE_NAMES = {
            "Russian", "English", "Ukrainian", "Belarusian", "Spanish", "French",
            "German", "Italian", "Portuguese", "Polish", "Turkish", "Chinese",
            "Japanese", "Korean", "Arabic", "Hindi", "Vietnamese", "Indonesian"
    };

    /**
     * English chat slang / abbreviations rewritten to their full
     * forms before the body reaches Google's mobile endpoint. The
     * reason for the pre-pass is purely translation quality:
     * Google translates {@code "be right back" -> "сейчас вернусь"}
     * but bare {@code "brb"} round-trips as {@code "БРБ"} - a
     * meaningless transliteration. Expanding here gives the user
     * the "more modern, more human" translation they asked for
     * ("сокращения на английском ещё переводило ... человечнее").
     *
     * <h4>Selection criteria</h4>
     * <ul>
     *   <li><strong>Two characters minimum.</strong> Single-letter
     *       abbreviations like {@code u -> you} / {@code r -> are}
     *       are intentionally excluded: they collide with real
     *       words in other languages (Spanish {@code y} = "and",
     *       {@code u} = "or", French {@code y}, etc.) AND with
     *       proper-noun fragments (R&amp;B, U-turn). The cost of a
     *       missed expansion is one less polished translation; the
     *       cost of a false positive is mangled foreign text.</li>
     *   <li><strong>Gaming + general-chat slang covered.</strong>
     *       Coverage was tuned for Minecraft / multiplayer use:
     *       {@code gg}, {@code ggwp}, {@code glhf}, {@code nt},
     *       {@code op}, {@code noob}, {@code ez}, {@code rip},
     *       plus the universal {@code lol}/{@code brb}/{@code afk}
     *       cohort.</li>
     *   <li><strong>No profanity escalation.</strong> {@code wtf}
     *       expands to "what the heck" not "what the fuck" so we
     *       don't make the translated line MORE crude than the
     *       original abbreviation - Google translates the heck
     *       version into a less aggressive Russian phrasing.</li>
     * </ul>
     */
    private static final Map<String, String> SLANG_EXPANSIONS = Map.ofEntries(
            Map.entry("lol",     "haha"),
            Map.entry("lmao",    "haha"),
            Map.entry("lmfao",   "haha"),
            Map.entry("rofl",    "haha"),
            Map.entry("xd",      "haha"),
            Map.entry("kek",     "haha"),
            Map.entry("brb",     "be right back"),
            Map.entry("bbl",     "be back later"),
            Map.entry("afk",     "away from keyboard"),
            Map.entry("wtf",     "what the heck"),
            Map.entry("wth",     "what the heck"),
            Map.entry("omg",     "oh my god"),
            Map.entry("omw",     "on my way"),
            Map.entry("idk",     "I don't know"),
            Map.entry("dunno",   "I don't know"),
            Map.entry("idc",     "I don't care"),
            Map.entry("imo",     "in my opinion"),
            Map.entry("imho",    "in my opinion"),
            Map.entry("tbh",     "to be honest"),
            Map.entry("tbf",     "to be fair"),
            Map.entry("ngl",     "not gonna lie"),
            Map.entry("ikr",     "I know right"),
            Map.entry("afaik",   "as far as I know"),
            Map.entry("iirc",    "if I recall correctly"),
            Map.entry("smh",     "shaking my head"),
            Map.entry("fyi",     "for your information"),
            Map.entry("tldr",    "too long didn't read"),
            Map.entry("btw",     "by the way"),
            Map.entry("nvm",     "never mind"),
            Map.entry("asap",    "as soon as possible"),
            Map.entry("thx",     "thanks"),
            Map.entry("ty",      "thank you"),
            Map.entry("tysm",    "thank you so much"),
            Map.entry("np",      "no problem"),
            Map.entry("yw",      "you're welcome"),
            Map.entry("pls",     "please"),
            Map.entry("plz",     "please"),
            Map.entry("rly",     "really"),
            Map.entry("srsly",   "seriously"),
            Map.entry("ofc",     "of course"),
            Map.entry("bc",      "because"),
            Map.entry("cuz",     "because"),
            Map.entry("coz",     "because"),
            Map.entry("rn",      "right now"),
            Map.entry("fr",      "for real"),
            Map.entry("ig",      "I guess"),
            Map.entry("irl",     "in real life"),
            Map.entry("dm",      "direct message"),
            Map.entry("ily",     "I love you"),
            Map.entry("wbu",     "what about you"),
            Map.entry("hbu",     "how about you"),
            Map.entry("ur",      "your"),
            Map.entry("sup",     "what's up"),
            Map.entry("ppl",     "people"),
            Map.entry("smth",    "something"),
            Map.entry("smthn",   "something"),
            Map.entry("smb",     "somebody"),
            Map.entry("gonna",   "going to"),
            Map.entry("wanna",   "want to"),
            Map.entry("gotta",   "got to"),
            Map.entry("gimme",   "give me"),
            Map.entry("lemme",   "let me"),
            Map.entry("kinda",   "kind of"),
            Map.entry("sorta",   "sort of"),
            Map.entry("gg",      "good game"),
            Map.entry("ggs",     "good games"),
            Map.entry("ggwp",    "good game well played"),
            Map.entry("wp",      "well played"),
            Map.entry("ez",      "easy"),
            Map.entry("gl",      "good luck"),
            Map.entry("hf",      "have fun"),
            Map.entry("glhf",    "good luck have fun"),
            Map.entry("nt",      "nice try"),
            Map.entry("rip",     "rest in peace"),
            Map.entry("sus",     "suspicious"),
            Map.entry("bff",     "best friend"),
            Map.entry("bf",      "boyfriend"),
            Map.entry("gf",      "girlfriend"),
            Map.entry("op",      "overpowered"),
            Map.entry("noob",    "newbie"),
            Map.entry("tho",     "though"),
            Map.entry("thru",    "through"),
            Map.entry("yk",      "you know"),
            Map.entry("kk",      "okay"),
            Map.entry("tmrw",    "tomorrow"),
            Map.entry("jk",      "just kidding"),
            Map.entry("ffs",     "for fucks sake"),
            Map.entry("goat",    "greatest of all time"),
            Map.entry("hbd",     "happy birthday"),
            // Minecraft-jargon: in PvP/SMP context players abbreviate the
            // "Mending" enchantment as "mend" ("can you mend me my axe").
            // Without expansion this slips through as the verb "to mend"
            // and the result reads as "to repair" instead of the proper
            // Russian "Починка" (the official localised name of the
            // enchantment). User explicitly requested this so the
            // translator stops producing non-idiomatic output for the
            // single most common Minecraft enchant abbreviation.
            Map.entry("mend",    "Mending enchantment")
    );

    /**
     * Pre-compiled regex matching ANY entry in {@link #SLANG_EXPANSIONS}
     * as a whole word, case-insensitive. Alternatives are listed
     * longest-first to keep Java's leftmost-first regex matcher from
     * grabbing a shorter prefix when a longer entry would also fit
     * (the trailing {@code \b} would still reject a bad short match
     * but the longest-first ordering avoids the backtrack entirely).
     */
    private static final Pattern SLANG_PATTERN = Pattern.compile(
            "\\b(" + SLANG_EXPANSIONS.keySet().stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .map(Pattern::quote)
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("") + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Format heuristic that distinguishes vanilla / server-decorated
     * chat from system messages when no cryptographic signature is
     * available (singleplayer, LAN, offline-mode servers, plugins
     * that strip signatures, etc.). Matches:
     * <ul>
     *   <li>Vanilla {@code <name> body} - the {@code <}/{@code >}
     *       wrapping is applied by
     *       {@code MessageType.Parameters.applyChatDecoration} for
     *       both signed AND profileless player chat, so it's a
     *       reliable marker even without a signature.</li>
     *   <li>{@code <prefix> name<sep> body} variants where
     *       {@code <sep>} is {@code :} or {@code »} - covers most
     *       server formats like {@code [VIP] Player: hi},
     *       {@code [Lobby] [Owner] Steve » hello}, etc. The name
     *       token requires at least one letter to keep noise like
     *       "12:34:56" timestamps from triggering it.</li>
     * </ul>
     * Designed to <em>reject</em>:
     * <ul>
     *   <li>F2 "Screenshot saved as 2026-05-16_12.34.56.png" - no
     *       separator after a name token</li>
     *   <li>F3+B "Hitboxes shown" - no separator</li>
     *   <li>"Player1 joined the game" - no {@code :}/{@code »}</li>
     *   <li>{@code [Server] Welcome!} - {@code [Server]} ends with
     *       {@code ]} not whitespace+separator</li>
     * </ul>
     */
    private static final Pattern PLAYER_CHAT_HEURISTIC = Pattern.compile(
            "^.*?\\p{L}[\\p{L}\\p{N}_\\-]+\\s*[:\u00BB]\\s+.+$",
            Pattern.DOTALL
    );

    /**
     * Pattern that splits a chat line into "header" (rank/nickname
     * decoration) and "body" (the part to translate).
     *
     * <p>Matches the FIRST occurrence of a separator character ({@code :},
     * {@code >}, or {@code »}) followed by whitespace - covers:
     * <ul>
     *   <li>Vanilla {@code <nick> body}</li>
     *   <li>Most servers' {@code [Rank] nick: body}</li>
     *   <li>Anarchy/PvP servers' {@code [Rank] nick » body}</li>
     * </ul>
     * Group 1 captures the header (incl. the trailing separator); group 2
     * captures the body to translate. Non-matches (server broadcasts /me
     * actions / system messages without a recognisable separator) fall
     * through to whole-line translation.
     */
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(.*?[:>\u00BB])\\s+(.*)$", Pattern.DOTALL);

    /** Soft cap on cache size. LinkedHashMap drops the eldest beyond this. */
    private static final int CACHE_CAPACITY = 256;

    /** Rejection threshold for the executor queue; if we'd block more than this many translations behind one another, drop the new request. */
    private static final int MAX_PENDING = 32;

    public final SectionSetting generalSection = new SectionSetting("General");

    /**
     * Two-letter ISO 639-1 codes Google Translate accepts as
     * {@code tl} (target language). Order is curated so the most common
     * picks for our user base sit at the top of the dropdown - "ru"
     * default because the user's UI requests have all been Russian.
     */
    public final SelectSetting targetLanguage = new SelectSetting(
            "Target Language",
            "Language to translate incoming chat into"
    ).value(LANGUAGE_NAMES).selected("Russian");

    /**
     * Whether to append a {@code (srcLang2dstLang)} marker at the end of
     * each translation row. The user explicitly requested this format
     * ("чтобы в конце писалось (en2ru) типо с английского на русский").
     * Keeping it as a toggle for users who want a cleaner look.
     */
    public final MultiSelectSetting translationOptions = new MultiSelectSetting(
            "Translation Options",
            "Choose which translator behaviors stay enabled"
    ).value(
            "Auto-Detect Source",
            "Show Direction",
            "Show Original On Hover",
            "Skip Nicknames",
            "Skip Same Language",
            "Replace Message",
            "Only Players",
            "Expand Slang"
    ).selected(
            "Auto-Detect Source",
            "Show Direction",
            "Show Original On Hover",
            "Skip Nicknames",
            "Skip Same Language",
            "Expand Slang"
    );
    public final BooleanLike showSuffix = () -> translationOptions.isSelected("Show Direction");

    /**
     * When ON, the translation row carries a vanilla {@link HoverEvent}
     * that pops up the original message text when the user mouses over
     * it in the chat hud (the user explicitly asked for this:
     * "сделай чтобы при наведении на сообщение в чате было видно
     * исходное сообщение, подпиши это как-то"). The tooltip contains
     * a gray italic "Original message:" label on the first line
     * followed by the original {@link Text} so colours / formatting in
     * the source line are preserved verbatim. Implemented by wrapping
     * the rebuilt translation row in a parent {@code Text} whose style
     * carries the {@code HoverEvent}; child segments inherit the hover
     * for any field they don't override (i.e. vanilla nickname-info
     * hovers on the speaker's name still work and override ours, which
     * is the desired behaviour - hover on the body shows the original,
     * hover on the rank/nick shows whatever vanilla / server attached).
     */
    public final BooleanLike showOriginalOnHover = () -> translationOptions.isSelected("Show Original On Hover");

    /**
     * When ON, only the part of the message AFTER the rank/nickname
     * decoration is translated; the speaker's prefix passes through
     * verbatim so player names don't get mangled (the user explicitly
     * asked for this: "никнеймы не переводить, только сообщения").
     * When OFF, the entire raw chat line is translated as-is - useful
     * for servers whose chat format isn't covered by
     * {@link #HEADER_PATTERN}.
     */
    public final BooleanLike skipNicknames = () -> translationOptions.isSelected("Skip Nicknames");

    /**
     * When ON, suppresses the translation row when Google returns a
     * detected source language equal to the configured target. Avoids
     * a useless duplicate row when somebody types in your language.
     */
    public final BooleanLike skipSameLanguage = () -> translationOptions.isSelected("Skip Same Language");

    /**
     * When ON, the translated message <em>replaces</em> the original
     * chat row instead of being appended below it. Implementation:
     * <ol>
     *   <li>Original message is added to the chat hud normally
     *       (the {@code @Inject HEAD} mixin doesn't cancel it).</li>
     *   <li>Async translation worker hits the upstream API.</li>
     *   <li>When the result arrives we scan {@link ChatHudAccessor#phaze$getMessages()}
     *       from the most-recent end backwards for a {@code ChatHudLine}
     *       whose plain-text content matches the original raw message,
     *       remove it (plus its wrapped {@code Visible} entries that
     *       share its {@code creationTick}, mirroring the cleanup
     *       {@link ChatHelper#tryCollapse} does for the collapse path),
     *       then re-add the translated row through the bypass-flag
     *       guarded path so the mixin doesn't loop.</li>
     * </ol>
     *
     * <p><strong>Caveat - position fidelity.</strong> Vanilla
     * {@code ChatHud.addMessage} always inserts at index 0 (newest)
     * and there's no public API to insert at an arbitrary index, so
     * the replacement row lands at the top of the chat regardless of
     * the original's original position. For the typical case (latest
     * message gets translated and replaced before any further messages
     * arrive) this is invisible. For the rare case where multiple
     * translations are in flight and arrive out-of-order, the older
     * translation will jump above the newer one - acceptable trade-off
     * versus the substantial complexity of replicating vanilla's
     * private {@code addVisibleMessage} wrapping at an arbitrary index.
     *
     * <p><strong>Visual flicker.</strong> Translation typically takes
     * 200-500 ms via Google direct, during which the original is
     * visible. This is intentional - fully hiding the original until
     * the translation arrives would delay chat by the same window and
     * dropped translations would leave the message invisible. The
     * flicker is the lesser evil.
     */
    public final BooleanLike replaceOriginal = () -> translationOptions.isSelected("Replace Message");

    /**
     * When ON, only translate cryptographically-signed player chat
     * (i.e. the {@code MessageSignatureData} passed to
     * {@code ChatHud.addMessage} is non-null). Skips ALL server
     * broadcasts, death messages, join/leave notices, command output,
     * and client-internal messages.
     *
     * <p>Off by default because most users want server broadcasts
     * translated too. Turn ON for a strict "only translate what real
     * players type" mode. Note that the always-on
     * {@link #CLIENT_SYSTEM_KEY_PREFIXES} filter still applies
     * independently - client-internal messages are skipped regardless
     * of this toggle, because the user explicitly asked for screenshot
     * / F3+B confirmations to be left alone.
     */
    public final BooleanLike onlyPlayers = () -> translationOptions.isSelected("Only Players");

    /**
     * When ON (default), the source language is auto-detected by the
     * upstream translation API (Google's mobile endpoint returns it as
     * the third element of the response array; Apify's actor returns
     * it in the {@code sourceLanguage} field). When OFF, the user
     * picks an explicit source language from {@link #sourceLanguage}
     * which is forwarded to the API as {@code sl=<code>} - useful when
     * the auto-detector mistakes one similar language for another
     * (the classic Polish/Czech, Norwegian/Danish, Indonesian/Malay
     * mix-ups Google has been known to make on short strings).
     */
    public final BooleanLike autoDetectSource = () -> translationOptions.isSelected("Auto-Detect Source");

    /**
     * Explicit source language used when {@link #autoDetectSource} is
     * OFF. Same list of ISO 639-1 codes as {@link #targetLanguage}
     * (with "en" first because that's the most common source language
     * for users translating into Russian, our default target).
     * Visibility-gated so it doesn't clutter the panel when auto-
     * detection is on - mirrors the visibility pattern used by the
     * Apify-only token / actor fields.
     */
    public final SelectSetting sourceLanguage = new SelectSetting(
            "Source Language",
            "Source language (used only when Auto-Detect Source is off)"
    ).value(LANGUAGE_NAMES).selected("English")
     .visible(() -> !autoDetectSource.isValue());

    /**
     * Minimum body length that triggers a translation request - filters
     * out short noise like "gg", ":)", "+1", numerals, etc. that aren't
     * worth a network round-trip.
     */
    public final ValueSetting minLength = new ValueSetting(
            "Min Length",
            "Skip messages whose translatable body has fewer characters than this"
    ).range(1, 64).step(1).setValue(3);

    /**
     * Toggle for the English-slang pre-expansion pass. When ON,
     * common abbreviations ({@code brb}, {@code wtf}, {@code gonna},
     * {@code ggwp}, ...) get rewritten to their full forms before
     * the body is sent to the translation backend, which produces
     * dramatically more natural target-language output - the user's
     * request was "чтобы оно сокращения на английском ещё
     * переводило ... человечнее".
     *
     * <p>Safe to leave ON because the expansion only runs when
     * {@link #shouldExpandSlang} confirms the message is
     * ASCII-letter-only AND the source is either auto-detected or
     * explicitly English - guarantees we never mangle Russian /
     * Chinese / Spanish / Arabic / etc. text that might happen to
     * share short ASCII fragments with our slang keys.
     */
    public final BooleanLike expandSlang = () -> translationOptions.isSelected("Expand Slang");

    public final SectionSetting providerSection = new SectionSetting("Provider");

    /**
     * Translation backend selection. {@code Google} hits the public
     * mobile-app endpoint directly (sub-second, free, no token). {@code
     * Apify} routes through {@link #apifyActor} run-sync API using
     * {@link #apifyToken} - retained per the user's original request
     * but flagged as significantly slower in the option description.
     */
    public final SelectSetting provider = new SelectSetting(
            "Provider",
            "Translation backend. Google = direct mobile endpoint (fast, free). Apify = run actor through Apify API (slower, uses your token)."
    ).value("Google", "Apify").selected("Google");

    /**
     * Apify personal API token - default value is the one the user
     * pasted in chat ({@code apify_api_AWCI...}). Stored as a TextSetting
     * so the user can rotate it from the GUI without rebuilding the mod.
     * Visibility-gated to the Apify provider so it doesn't clutter the
     * default settings panel.
     */
    public final TextSetting apifyToken = new TextSetting(
            "Apify Token",
            "Personal API token for api.apify.com (used only when Provider=Apify)"
    ).setText("apify_api_AWCI0hEcYpdGYUL3TJQSNj9sj6hvW50Nqgkm")
     .visible(() -> provider.isSelected("Apify"));

    /**
     * Apify actor identifier in {@code username~actorname} form. Default
     * is {@code web.harvester/google-translator} (URL-form
     * {@code web.harvester~google-translator}) - chosen because its input
     * schema {@code {text, sourceLanguage, targetLanguage}} maps cleanly
     * onto our {@link #translateApify} call site. Other Google-Translate
     * actors on Apify use different input shapes; users picking a
     * different actor will likely need to fork the JSON body in
     * {@link #translateApify} as well.
     */
    public final TextSetting apifyActor = new TextSetting(
            "Apify Actor",
            "Actor ID in username~actorname form"
    ).setText("web.harvester~google-translator")
     .visible(() -> provider.isSelected("Apify"));

    /**
     * LRU cache of translations keyed by {@code targetLang|sourceText}.
     * {@link LinkedHashMap} access-order with eldest-eviction caps the
     * footprint while keeping repeated identical broadcasts (kit cooldowns,
     * server-wide tips, etc.) cheap.
     */
    @SuppressWarnings("serial")
    private final Map<String, String> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_CAPACITY;
        }
    };

    /**
     * Daemon-pool of two HTTP workers. Two threads is enough for chat
     * (messages arrive at most a few per second; even on the chattiest
     * RP server the second slot is rarely contested), and capping at
     * two prevents an Apify cold-start storm from monopolising network
     * I/O if the user mass-toggles the backend mid-conversation.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "phaze-translator");
        t.setDaemon(true);
        return t;
    });

    /**
     * Tracks how many requests are currently queued or running. When
     * this exceeds {@link #MAX_PENDING}, new translation requests are
     * dropped on the floor (the original message still appears in chat
     * normally - we just skip its translation row). Prevents an
     * unbounded backlog if the upstream API hangs or the player gets
     * raid-spammed.
     */
    private final AtomicInteger pending = new AtomicInteger(0);

    /**
     * Recursion guard. Set to {@code true} for the duration of our own
     * {@code chatHud.addMessage(translatedText)} call so the chat-mixin
     * sees we're re-entering and skips the {@link #onIncomingChat} hook.
     * Single-threaded by virtue of always running on the main client
     * thread (the {@code MinecraftClient.execute()} hop in
     * {@link #postTranslation} ensures this).
     */
    private boolean bypass = false;

    /**
     * Singleton instance.
     *
     * <p><strong>Declaration position matters.</strong> Java
     * initialises {@code static final} fields in lexical (source)
     * order, and the {@link Translator} constructor reads several
     * other static finals (notably {@link #LANGUAGE_NAMES} which is
     * fed to the {@code SelectSetting.value(...)} calls for
     * {@link #targetLanguage} / {@link #sourceLanguage}). If this
     * singleton sat at the top of the class body it would run
     * {@code new Translator()} <em>before</em> those constants got
     * their values, causing {@code SelectSetting.value(null)} to
     * NPE inside {@code Arrays.asList()} at game launch (this
     * actually crashed the user once - see the
     * {@code java.util.Objects.requireNonNull} stack trace from
     * {@code crash-2026-05-16_14.30.50-client.txt}). Keeping the
     * declaration here, after all the other static finals AND after
     * the instance-field declarations, guarantees the constructor
     * sees fully-initialised dependencies.
     */
    private static final Translator INSTANCE = new Translator();

    public static Translator getInstance() {
        return INSTANCE;
    }

    private Translator() {
        super("translator", "Translator", ModuleCategory.OTHER);
        targetLanguage.setFullWidth(true);
        translationOptions.setFullWidth(true);
        sourceLanguage.setFullWidth(true);
        minLength.setFullWidth(true);
        provider.setFullWidth(true);
        apifyToken.setFullWidth(true);
        apifyActor.setFullWidth(true);
        setup(generalSection, targetLanguage, translationOptions, sourceLanguage, minLength,
                providerSection, provider, apifyToken, apifyActor);
    }

    @Override
    public String getDescription() {
        return "Auto-translate incoming chat messages and post the translation as a new row below the original";
    }

    @Override
    public String getIcon() {
        return "translator.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /** True while we're re-adding our own translated message - read by the chat mixin to break the recursion cycle. */
    public boolean isBypassActive() {
        return bypass;
    }

    /**
     * Entry point called from {@link vorga.phazeclient.mixins.ChatHudTranslatorMixin}
     * for every incoming chat row. Cheap fast-path filters first
     * (module disabled, bypass active, message empty), then a worker is
     * dispatched to do the HTTP work - this method NEVER blocks the
     * main thread.
     */
    public void onIncomingChat(Text message, MessageSignatureData signature) {
        if (!isEnabled() || bypass || message == null) {
            return;
        }

        // Always-on filter: skip Minecraft's own client-internal
        // status messages (F2 save confirmation, F3+B hitbox toggle,
        // narrator toggles, etc.). The user explicitly asked for
        // these to be left alone, regardless of the Only Players
        // toggle - it'd be weird to translate "Screenshot saved as
        // 2026-05-16_12.34.56.png".
        if (isClientSystemMessage(message)) {
            return;
        }

        // "Only Players" mode: skip anything that doesn't look like
        // chat from a real player. Was previously a naive
        // signature-only check, but vanilla 1.21.4 routes ALL
        // singleplayer/LAN/offline-mode chat through
        // {@code addMessage(text, null, MessageIndicator.singlePlayer())}
        // - signature is always null when the underlying server
        // doesn't issue session keys, which would have caused
        // legitimate chat like "<Nomadvorga> hello" to be filtered
        // out alongside actual system messages. The heuristic-based
        // {@link #isPlayerChat} check correctly identifies vanilla /
        // server-decorated chat formats regardless of signing state
        // while still rejecting screenshot saves, F3+B toggles,
        // server announcements, etc.
        if (onlyPlayers.isValue() && !isPlayerChat(message, signature)) {
            return;
        }

        String raw = message.getString();
        if (raw == null || raw.isEmpty()) {
            return;
        }

        // Determine which slice of the line we're translating: either
        // the part after the rank/nick decoration, or the whole line.
        String header;
        String body;
        if (skipNicknames.isValue()) {
            Matcher matcher = HEADER_PATTERN.matcher(raw);
            if (matcher.matches()) {
                header = matcher.group(1) + " ";
                body = matcher.group(2);
            } else {
                // Pattern miss = no recognised separator. Fall back to
                // translating the whole line so server-broadcast / /me-style
                // messages still get translated.
                header = "";
                body = raw;
            }
        } else {
            header = "";
            body = raw;
        }

        if (body == null) {
            return;
        }
        body = body.trim();
        // Use the script-aware weight (CJK ideographs and Hangul
        // syllables count double) instead of raw character count so
        // that real Chinese/Japanese/Korean phrases like "你好" -
        // length=2 in Java, but a complete sentence carrying about
        // as much meaning as four English letters - aren't rejected
        // by the default {@code minLength=3} gate. See
        // {@link #translationWeight} for the rationale.
        if (translationWeight(body) < (int) minLength.getValue()) {
            return;
        }

        // Drop common URL-only / chat-spam patterns early so we don't
        // burn an HTTP round-trip translating someone's discord link.
        // Cheap heuristic - any line whose stripped body is purely
        // whitespace + ASCII punctuation gets skipped.
        if (isMostlySymbolic(body)) {
            return;
        }

        // The dropdown shows full English names ("Russian",
        // "Chinese", ...) but every downstream consumer needs the
        // ISO code. Convert once here and propagate the code through
        // the pipeline.
        String target = languageNameToCode(targetLanguage.getSelected());
        if (target == null || target.isEmpty()) {
            return;
        }

        // Cache check - keyed on (target, body) only because source is
        // auto-detected and including the detected source in the key
        // would defeat the purpose for the very common "same speaker
        // repeats themselves" case.
        String cacheKey = target + "|" + body;
        String cached;
        synchronized (cache) {
            cached = cache.get(cacheKey);
        }
        if (cached != null) {
            // Cached entry stores "detectedSrc|translatedText" so we can
            // rebuild the suffix without re-detecting. Skip the network
            // hop entirely.
            int sep = cached.indexOf('|');
            if (sep >= 0) {
                String detected = cached.substring(0, sep);
                String translation = cached.substring(sep + 1);
                if (skipSameLanguage.isValue() && detected.equalsIgnoreCase(target)) {
                    return;
                }
                postTranslation(message, raw, header, translation, detected, target);
                return;
            }
        }

        // Backpressure: drop the request if the executor queue is
        // already full of pending translations. Prevents an unbounded
        // backlog when the upstream API is slow or the player gets
        // chat-flooded.
        if (pending.get() >= MAX_PENDING) {
            return;
        }
        pending.incrementAndGet();
        final Text messageF = message;
        final String rawF = raw;
        final String headerF = header;
        final String bodyF = body;
        final String targetF = target;
        // Source language is captured here at queue time so a mid-flight
        // toggle of Auto-Detect Source doesn't switch the API call
        // partway through. The string "auto" is the magic value both
        // backends understand to mean "detect for me". When the user
        // has picked an explicit source, convert the display name to
        // its ISO code (e.g. "Chinese" -> "zh-CN", which is critical
        // because the Google mobile endpoint silently returns empty
        // strings for bare "zh" on short Chinese inputs).
        final String sourceF = autoDetectSource.isValue()
                ? "auto"
                : languageNameToCode(sourceLanguage.getSelected());
        executor.execute(() -> {
            try {
                translateAndPost(messageF, rawF, headerF, bodyF, sourceF, targetF, cacheKey);
            } catch (Throwable t) {
                LOG.warn("translation failed for body='{}': {}", bodyF, t.toString());
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    private void translateAndPost(Text original, String raw, String header, String body, String source, String target, String cacheKey) {
        // Slang pre-expansion. The expanded form is what we send to
        // the backend; the original body is what stays in the cache
        // key and in the user-visible "<player> <body>" line. Google
        // translates "be right back" into idiomatic Russian but
        // mangles "brb" into "БРБ", so this is the single biggest
        // quality lever short of swapping translation providers.
        String apiInput = shouldExpandSlang(body, source) ? applySlangExpansion(body) : body;

        // Dispatch to the configured backend. Each branch returns a
        // String[]{detectedSrc, translatedText} on success or null on
        // failure - we don't post anything on failure so the user just
        // sees the original line and no error spam.
        String[] result;
        if (provider.isSelected("Apify")) {
            result = translateApify(apiInput, source, target);
        } else {
            result = translateGoogleDirect(apiInput, source, target);
        }
        if (result == null) {
            return;
        }
        String detected = result[0];
        String translation = result[1];
        if (translation == null || translation.isEmpty()) {
            return;
        }

        synchronized (cache) {
            cache.put(cacheKey, detected + "|" + translation);
        }

        if (skipSameLanguage.isValue() && detected.equalsIgnoreCase(target)) {
            return;
        }
        // Also skip if the translation came back identical to the
        // input - this happens for proper nouns, technical terms, and
        // sometimes when the API can't determine the source language.
        // Posting a duplicate row would just be noise.
        if (translation.trim().equalsIgnoreCase(body.trim())) {
            return;
        }

        // If the user picked an explicit source language, prefer it
        // over whatever the API echoed back - this keeps the suffix
        // consistent with the user's UI choice. Auto-detect mode uses
        // the API's detected value as-is.
        String displayDetected = autoDetectSource.isValue() ? detected : source;
        postTranslation(original, raw, header, translation, displayDetected, target);
    }

    /**
     * Posts the assembled translation row onto the chat hud, on the
     * main thread, with the {@link #bypass} guard active so our own
     * {@code addMessage} call doesn't loop back through the mixin.
     *
     * <p>When {@link #replaceOriginal} is ON the original chat row is
     * first removed (located by content match on {@code originalRaw});
     * when OFF the translation is simply appended below the original.
     */
    private void postTranslation(Text original, String originalRaw, String header, String translation, String detectedSrc, String target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            ChatHud chat = client.inGameHud != null ? client.inGameHud.getChatHud() : null;
            if (chat == null) {
                return;
            }
            // Build the row by walking the ORIGINAL styled text tree -
            // see {@link #buildStyledTranslationRow} for the full rationale.
            // Short version: we want the translated row to look like a
            // normal chat message (preserving the speaker's brackets /
            // rank colours / nickname style) with ONLY the (srcLang2dstLang)
            // marker rendered as subordinate gray italic at the end.
            String suffix = showSuffix.isValue()
                    ? " (" + (detectedSrc == null || detectedSrc.isEmpty() ? "auto" : detectedSrc) + "2" + target + ")"
                    : null;
            MutableText row = buildStyledTranslationRow(original, header, translation, suffix);

            bypass = true;
            try {
                if (replaceOriginal.isValue() && originalRaw != null) {
                    removeOriginal(chat, originalRaw);
                }
                chat.addMessage(row);
            } finally {
                bypass = false;
            }
        });
    }

    /**
     * Walks the original styled {@link Text} segment-by-segment and
     * builds a new {@link MutableText} whose visual presentation
     * mirrors the original everywhere except the body, which is
     * replaced with the translated string. The body's original
     * {@link Style} (colour, bold, etc.) is captured at the first
     * body-overlapping segment and applied to the translated text so
     * the row reads as "original-but-translated" rather than as a
     * second-class annotation.
     *
     * <h3>Algorithm</h3>
     * For each styled segment {@code (style, str)} with raw-string
     * range {@code [segStart, segEnd)}:
     * <ol>
     *   <li>Compute its overlap with the body range
     *       {@code [bodyStart, bodyEnd)} (where {@code bodyEnd} is
     *       always {@code raw.length} because
     *       {@link #HEADER_PATTERN}'s second group is greedy DOTALL).</li>
     *   <li>Emit the pre-body prefix portion verbatim with the
     *       segment's own style.</li>
     *   <li>The FIRST time we see a body-overlapping segment, emit
     *       the translated string with that segment's style and set a
     *       sentinel so subsequent body-overlapping segments don't
     *       re-emit the translation. Their content is dropped because
     *       the translated string already replaces all of them.</li>
     * </ol>
     *
     * <h3>Fallbacks</h3>
     * If {@code originalRaw.lastIndexOf(bodyText) &lt; 0} (which
     * shouldn't happen because {@code header + body} is constructed
     * by slicing the same {@code raw}, but defensive programming),
     * we fall through to a flat literal layout: plain header +
     * default-styled translation + grey suffix. Same shape as the
     * pre-stylised implementation, preserves end-to-end behaviour.
     *
     * @param suffix the {@code " (srcLang2dstLang)"} marker, or {@code null}
     *               when {@link #showSuffix} is OFF
     */
    private MutableText buildStyledTranslationRow(Text original, String header, String translation, String suffix) {
        if (original == null) {
            // Defensive: original Text was never captured. Plain layout.
            MutableText row = Text.empty();
            if (header != null && !header.isEmpty()) {
                row.append(Text.literal(header));
            }
            row.append(Text.literal(translation));
            if (suffix != null) {
                row.append(Text.literal(suffix).formatted(Formatting.GRAY, Formatting.ITALIC));
            }
            // Without an original Text there is nothing meaningful to
            // show in a tooltip, so the fallback path skips the hover
            // attachment entirely.
            return row;
        }

        String raw = original.getString();
        // Recover the body slice from the raw string. We know the
        // pipeline computed header + body from this same raw string
        // earlier; rather than thread bodyStart through every layer
        // we recover it by simple subtraction: bodyStart = header.length
        // (header was constructed as match.group(1) + " "). When
        // skipNicknames is OFF, header is the empty string and
        // bodyStart is 0 - i.e. the whole message is the body.
        int bodyStart = header == null ? 0 : header.length();
        // Clamp to the raw length so a malformed header (longer than
        // raw, which shouldn't happen but defensive) doesn't blow up
        // substring math below.
        if (bodyStart < 0 || bodyStart > raw.length()) {
            bodyStart = 0;
        }
        final int bodyStartF = bodyStart;
        final int bodyEndF = raw.length();

        final MutableText result = Text.empty();
        final int[] cursor = {0};
        final boolean[] translationInserted = {false};

        // StringVisitable#visit walks every styled run with its
        // resolved cumulative style. We use Optional.empty() returns
        // so the walk never short-circuits.
        original.visit((StringVisitable.StyledVisitor<Object>) (style, str) -> {
            int segStart = cursor[0];
            int segEnd = segStart + str.length();
            cursor[0] = segEnd;

            // How much of this segment falls strictly before the body.
            int prefixLen = Math.max(0, Math.min(str.length(), bodyStartF - segStart));
            // Where the post-body suffix portion starts within this segment.
            // For our pipeline this is always >= str.length() (body extends
            // to end-of-raw), so this branch is dead today but kept for
            // safety in case the regex changes later.
            int suffixStart = Math.min(str.length(), Math.max(0, bodyEndF - segStart));

            if (prefixLen > 0) {
                String prefix = str.substring(0, prefixLen);
                result.append(Text.literal(prefix).setStyle(style));
            }

            // Does this segment overlap the body range at all?
            boolean bodyOverlaps = segStart < bodyEndF && segEnd > bodyStartF;
            if (bodyOverlaps && !translationInserted[0]) {
                // Translation inherits the body's resolved style - this is
                // the key bit the user asked for: "чтобы переведённое
                // сообщение сохраняло стиль обычного (цвет и тд)".
                result.append(Text.literal(translation).setStyle(style));
                translationInserted[0] = true;
            }

            if (suffixStart < str.length()) {
                String tail = str.substring(suffixStart);
                result.append(Text.literal(tail).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        // Belt-and-braces: if for any reason the visit never
        // encountered a body-overlapping segment (e.g. an empty Text
        // tree slipped through), append the translation with default
        // styling so the user at least sees something.
        if (!translationInserted[0]) {
            result.append(Text.literal(translation));
        }

        if (suffix != null) {
            // The marker stays in gray italic regardless of the
            // message's own colour - this is what the user asked for
            // ("но в конце серым писало перевод типо ... (en2ru)").
            result.append(Text.literal(suffix).formatted(Formatting.GRAY, Formatting.ITALIC));
        }

        // Hover-over tooltip with the original source message. The
        // wrapper carries the {@link HoverEvent} as a parent style and
        // appends {@code result} as its only child; vanilla Text
        // resolution merges the parent style into each child's effective
        // style so every segment that doesn't override the hover field
        // inherits it. Vanilla nickname-info hovers in {@code header}
        // (rare but possible on some servers) keep their own hover
        // because the merge rule is "child wins per-field" - which is
        // exactly what we want: hover on the body shows the original,
        // hover on the nick shows whatever was already there.
        if (showOriginalOnHover.isValue()) {
            HoverEvent hover = buildOriginalHover(original);
            if (hover != null) {
                MutableText wrapped = Text.empty().setStyle(Style.EMPTY.withHoverEvent(hover));
                wrapped.append(result);
                return wrapped;
            }
        }
        return result;
    }

    /**
     * Build the tooltip content for the "Show Original On Hover" toggle.
     * Layout is a fixed two-row card:
     * <pre>
     *   Original message:        &lt;- gray italic label
     *   &lt;original Text&gt;          &lt;- the source line with its
     *                               vanilla styling preserved
     * </pre>
     * Returns {@code null} when the original Text is missing or has
     * empty content; the caller falls through to a tooltip-less row.
     */
    private static HoverEvent buildOriginalHover(Text original) {
        if (original == null) {
            return null;
        }
        String raw = original.getString();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        MutableText tooltip = Text.empty()
                .append(Text.literal("Original message:").formatted(Formatting.GRAY, Formatting.ITALIC))
                .append(Text.literal("\n"))
                .append(original);
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip);
    }

    /**
     * Convert a display-name selection ("Russian", "Chinese", ...)
     * to its ISO 639-1 (or BCP-47 in the Chinese case) code. Falls
     * back to a lower-cased trim if the name is unknown so an
     * out-of-band change to {@link #LANGUAGE_NAMES} doesn't crash
     * the translation pipeline; falls through to {@code "ru"} if
     * the input is empty/null because Russian is the configured
     * default target.
     */
    private static String languageNameToCode(String name) {
        if (name == null || name.isEmpty()) return "ru";
        String code = LANGUAGE_NAME_TO_CODE.get(name);
        if (code != null) return code;
        // Defensive fallback: lower-cased trimmed name. Won't usually
        // produce a working code but at least ensures something
        // non-null reaches the API for diagnostic logging.
        return name.trim().toLowerCase();
    }

    /**
     * True when the incoming message looks like player chat (signed
     * or unsigned) rather than a system / client-internal notice.
     * Used by the Only Players toggle.
     *
     * <p><strong>Why heuristic and not pure signature.</strong>
     * Vanilla 1.21.4 only attaches a {@link MessageSignatureData} to
     * messages from official Mojang-authenticated online servers.
     * Singleplayer, LAN, offline-mode servers, and many community
     * server plugins all forward player chat through
     * {@code addMessage(text, null, ...)} - signature is null even
     * for legitimate chat like {@code <Nomadvorga> hello}. The
     * earlier signature-only gate filtered all of those out, which
     * is what the user reported was broken ("only players не
     * работает, сделай норм систему").
     *
     * <p>The fix is a two-path check:
     * <ol>
     *   <li>{@code signature != null} -&gt; cryptographically signed,
     *       definitely a player.</li>
     *   <li>Otherwise check the message string against
     *       {@link #PLAYER_CHAT_HEURISTIC} which matches the vanilla
     *       {@code <name> body} decoration and standard server
     *       chat formats with {@code :} / {@code »} separators.</li>
     * </ol>
     */
    private static boolean isPlayerChat(Text message, MessageSignatureData signature) {
        if (signature != null) return true;
        if (message == null) return false;
        String raw = message.getString();
        if (raw == null || raw.isEmpty()) return false;

        // Vanilla `<name> body` decoration - applied identically to
        // signed and profileless player chat by
        // MessageType.Parameters.applyChatDecoration. Robust marker
        // even when offline mode strips signatures.
        if (raw.startsWith("<")) {
            int close = raw.indexOf("> ");
            if (close > 1 && close < raw.length() - 2) {
                String name = raw.substring(1, close);
                // Exclude pathological cases like "<<" or "<>" - real
                // names contain at least one letter and never embed an
                // unmatched '<'. Server-broadcast formats sometimes use
                // angle brackets too (rare); the letter check rules
                // those out cheaply.
                if (!name.isEmpty() && name.indexOf('<') < 0 && containsLetter(name)) {
                    return true;
                }
            }
        }

        // Server-decorated chat with rank prefix and `:` / `»` body
        // separator.
        return PLAYER_CHAT_HEURISTIC.matcher(raw).matches();
    }

    /** True if the string contains at least one letter (any script). */
    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.codePointAt(i))) return true;
        }
        return false;
    }

    /**
     * Decide whether the slang-expansion pre-pass should run on this
     * particular body. Three gates have to clear:
     * <ol>
     *   <li>The {@link #expandSlang} toggle is on - user opt-out.</li>
     *   <li>The body contains no non-ASCII letters - rejects Russian,
     *       Chinese, Arabic, etc. cheaply by code-point comparison.
     *       A body like {@code "привет brb"} would still be rejected
     *       here (good - we'd risk corrupting the Russian half) and
     *       the slang stays untranslated, which is a fine fallback.</li>
     *   <li>Source language is either {@code "auto"} (let the backend
     *       guess) or explicitly {@code "en"} - if the user has
     *       deliberately set source = Spanish / French / etc. we
     *       respect that and skip expansion to avoid mangling words
     *       like Spanish {@code "y"} or {@code "cuz"} that overlap
     *       our keys.</li>
     * </ol>
     */
    private boolean shouldExpandSlang(String body, String source) {
        if (!expandSlang.isValue()) return false;
        if (body == null || body.isEmpty()) return false;
        if (!isAsciiLettersOnly(body)) return false;
        return "auto".equals(source) || "en".equalsIgnoreCase(source);
    }

    /**
     * True when every letter in the string is an ASCII A-Z / a-z
     * codepoint - i.e. no Cyrillic, no CJK, no Greek, no accented
     * Latin (é, ñ, ü, ...). Non-letter characters (digits,
     * punctuation, whitespace, emoji) don't disqualify the string.
     * Used by {@link #shouldExpandSlang} as a cheap "probably
     * English" sieve.
     */
    private static boolean isAsciiLettersOnly(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) {
                boolean asciiLetter = (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z');
                if (!asciiLetter) return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    /**
     * Rewrite every {@link #SLANG_EXPANSIONS} key (matched as a whole
     * word, case-insensitive) into its full-phrase value. Caller is
     * responsible for the {@link #shouldExpandSlang} gating - this
     * method assumes the input is appropriate for expansion and just
     * does the substitution.
     *
     * <p>The matched original token's case is intentionally NOT
     * preserved ("LOL" -&gt; "haha", not "HAHA"). Reasoning: the
     * downstream translation engine treats the expanded phrase as
     * normal prose and produces output capitalised per the target
     * language's rules, so faithful case preservation on the
     * intermediate would just create awkward all-caps Russian.
     */
    private static String applySlangExpansion(String input) {
        Matcher m = SLANG_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 16);
        while (m.find()) {
            String key = m.group(1).toLowerCase(java.util.Locale.ROOT);
            String replacement = SLANG_EXPANSIONS.getOrDefault(key, m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * True if the message is a Minecraft client-internal status
     * notice (F2 screenshot save, F3+B hitboxes toggle, F3+P pause,
     * etc.) that the user explicitly does NOT want translated. Match
     * is by the root content's translation key against the prefix
     * whitelist in {@link #CLIENT_SYSTEM_KEY_PREFIXES} - server-
     * emitted translation keys (death, multiplayer.player.joined,
     * etc.) are deliberately not in that list because they're real
     * gameplay messages users may want translated.
     *
     * <p>The check runs only against the root content - we don't
     * descend into siblings, because the client-side messages we
     * care about (screenshot saved, debug toggles, narrator state)
     * all have the translation key at the root. Walking siblings
     * would risk false-positive matches against gameplay messages
     * that happen to embed a debug-keyed sibling.
     */
    private static boolean isClientSystemMessage(Text message) {
        if (message == null) {
            return false;
        }
        TextContent content = message.getContent();
        if (!(content instanceof TranslatableTextContent translatable)) {
            return false;
        }
        String key = translatable.getKey();
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (String prefix : CLIENT_SYSTEM_KEY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Locates the most-recent {@link ChatHudLine} whose plain-text
     * content matches {@code originalRaw} and removes it from both the
     * authoritative message list AND its wrapped
     * {@link ChatHudLine.Visible} children (matched by
     * {@code creationTick}). Modelled directly on the cleanup
     * {@link ChatHelper#tryCollapse} does, with the same rationale -
     * forgetting to drop the visible children leaves orphan blank rows
     * above the chat because vanilla's age-based skip never triggers
     * for fresh entries.
     *
     * <p>Searches from index 0 (newest) so when the user's chat is
     * being actively translated, the latest message is found first.
     * If no match is found (user cleared chat, message scrolled out,
     * other mod stripped formatting differently, etc.) the method is
     * a quiet no-op and the translation just appends as a normal new
     * row, matching the non-replace behaviour.
     */
    private void removeOriginal(ChatHud chat, String originalRaw) {
        ChatHudAccessor accessor = (ChatHudAccessor) chat;
        List<ChatHudLine> messages = accessor.phaze$getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        int targetIdx = -1;
        ChatHudLine targetLine = null;
        for (int i = 0; i < messages.size(); i++) {
            ChatHudLine line = messages.get(i);
            if (line != null && line.content() != null && originalRaw.equals(line.content().getString())) {
                targetIdx = i;
                targetLine = line;
                break;
            }
        }
        if (targetIdx < 0 || targetLine == null) {
            return;
        }

        messages.remove(targetIdx);
        List<ChatHudLine.Visible> visible = accessor.phaze$getVisibleMessages();
        if (visible != null && !visible.isEmpty()) {
            int removedTick = targetLine.creationTick();
            visible.removeIf(v -> v != null && v.addedTime() == removedTick);
        }
    }

    /**
     * Hits the public Google Translate mobile endpoint at
     * {@code translate.googleapis.com/translate_a/single}. Returns
     * {@code [detectedSrcLang, translatedText]} on success, {@code null}
     * on any failure.
     *
     * <p>The endpoint is the same one the Google Translate Android app
     * uses - parameters {@code client=gtx&dt=t&sl=auto&tl=<target>&q=<text>}
     * give back a JSON tree shaped roughly like:
     * <pre>
     * [[["Привет","Hello",null,null,1]],null,"en"]
     * </pre>
     * - {@code root[0][i][0]} = translated chunk
     * - {@code root[0][i][1]} = original chunk (sometimes useful, ignored here)
     * - {@code root[2]}       = detected source language code
     *
     * <p>The API is undocumented and Google has occasionally rate-limited
     * IPs that hammer it, but for chat-volume traffic (a few requests
     * per minute per user) it's been stable for years. Connect and read
     * timeouts are tight (5s/8s) because the endpoint is normally
     * sub-second; if the round-trip is going to fail we want to know
     * fast and free up the worker for the next message.
     */
    private String[] translateGoogleDirect(String text, String source, String target) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            // sl=auto requests detection; sl=<code> forces the API to
            // treat the input as that language. Either way Google
            // still returns the detected language in root[2], so the
            // suffix is right; the user just gets more accurate
            // results when they know the source ahead of time.
            String slParam = (source == null || source.isEmpty()) ? "auto" : source;
            String urlStr = "https://translate.googleapis.com/translate_a/single"
                    + "?client=gtx"
                    + "&sl=" + URLEncoder.encode(slParam, StandardCharsets.UTF_8)
                    + "&tl=" + URLEncoder.encode(target, StandardCharsets.UTF_8)
                    + "&dt=t"
                    + "&q=" + encoded;
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(8_000);
                conn.setRequestProperty("Accept", "application/json");
                // Browser-shaped UA - Google's bot mitigation has been
                // observed rejecting "Java/..." UAs on this endpoint.
                conn.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0.0.0 Safari/537.36");
                conn.setInstanceFollowRedirects(true);

                int status = conn.getResponseCode();
                if (status != 200) {
                    LOG.warn("Google Translate returned HTTP {}", status);
                    return null;
                }
                String body = readAll(conn.getInputStream());
                JsonElement parsed = JsonParser.parseString(body);
                if (!parsed.isJsonArray()) {
                    return null;
                }
                JsonArray root = parsed.getAsJsonArray();

                // root[0] is an array of translated chunks - concatenate
                // their first elements to get the full translated text.
                StringBuilder sb = new StringBuilder();
                if (root.size() > 0 && root.get(0).isJsonArray()) {
                    JsonArray chunks = root.get(0).getAsJsonArray();
                    for (JsonElement chunkEl : chunks) {
                        if (!chunkEl.isJsonArray()) continue;
                        JsonArray chunk = chunkEl.getAsJsonArray();
                        if (chunk.size() == 0) continue;
                        JsonElement piece = chunk.get(0);
                        if (piece != null && piece.isJsonPrimitive()) {
                            sb.append(piece.getAsString());
                        }
                    }
                }
                String translated = sb.toString();

                // root[2] is the detected source language code as a
                // bare string primitive ("en", "de", etc.). Default to
                // "auto" when the API doesn't include it.
                String detected = "auto";
                if (root.size() >= 3 && root.get(2).isJsonPrimitive()) {
                    detected = root.get(2).getAsString();
                }
                return new String[] { detected, translated };
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            LOG.warn("Google Translate request failed: {}", t.toString());
            return null;
        }
    }

    /**
     * Hits Apify's {@code run-sync-get-dataset-items} endpoint for the
     * configured actor. Default actor is {@code
     * web.harvester/google-translator}, whose input schema matches our
     * payload shape. Returns {@code [detectedSrcLang, translatedText]}
     * on success, {@code null} otherwise.
     *
     * <p>WARNING: Apify actors run as containerised browsers and have
     * 5-30 second cold-start latencies. This path is included for
     * completeness (the user originally provided an Apify token) but is
     * NOT recommended for chat-volume traffic - use
     * {@link #translateGoogleDirect} unless the public endpoint is
     * blocked at the network level.
     */
    private String[] translateApify(String text, String source, String target) {
        try {
            String token = apifyToken.getText();
            String actor = apifyActor.getText();
            if (token == null || token.isEmpty() || actor == null || actor.isEmpty()) {
                return null;
            }

            String urlStr = "https://api.apify.com/v2/acts/" + actor
                    + "/run-sync-get-dataset-items?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                // 60-second cap for both connect AND read because the
                // run-sync endpoint blocks until the actor finishes -
                // typical first-call latency is 10-20s for cold-start
                // browsers, sometimes longer.
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(60_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");

                // web.harvester/google-translator input schema:
                //   { "text": "...", "sourceLanguage": "en", "targetLanguage": "es" }
                // sourceLanguage "auto" asks the actor to detect; a
                // specific ISO code forces it (e.g. when the user has
                // turned off Auto-Detect Source in the GUI).
                String slField = (source == null || source.isEmpty()) ? "auto" : source;
                String payload = "{"
                        + "\"text\":" + jsonString(text) + ","
                        + "\"sourceLanguage\":" + jsonString(slField) + ","
                        + "\"targetLanguage\":" + jsonString(target)
                        + "}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status / 100 != 2) {
                    LOG.warn("Apify returned HTTP {}", status);
                    return null;
                }
                String body = readAll(conn.getInputStream());
                JsonElement parsed = JsonParser.parseString(body);
                if (!parsed.isJsonArray()) {
                    return null;
                }
                JsonArray items = parsed.getAsJsonArray();
                if (items.size() == 0 || !items.get(0).isJsonObject()) {
                    return null;
                }

                // Output schema (per the actor's README):
                //   { "originalText", "sourceLanguage" (full name), "targetLanguage",
                //     "translatedText", "autoDetected" }
                // The "sourceLanguage" comes back as a full English name
                // ("English", "German", ...) rather than an ISO code; we
                // map back to the lower-case code via the small lookup
                // table in {@link #shortenLanguageName}.
                JsonObject item = items.get(0).getAsJsonObject();
                String translated = item.has("translatedText") && item.get("translatedText").isJsonPrimitive()
                        ? item.get("translatedText").getAsString()
                        : null;
                String detectedFull = item.has("sourceLanguage") && item.get("sourceLanguage").isJsonPrimitive()
                        ? item.get("sourceLanguage").getAsString()
                        : "auto";
                if (translated == null) {
                    return null;
                }
                return new String[] { shortenLanguageName(detectedFull), translated };
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            LOG.warn("Apify translation failed: {}", t.toString());
            return null;
        }
    }

    /** Quick-and-dirty JSON string encoder for the small payload shape we send to Apify - avoids pulling Gson's writer for two fields. */
    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Maps a full-English language name (Apify output) back to its
     * lower-case ISO 639-1 code so the suffix stays compact. Anything
     * unrecognised falls back to the lower-cased first three characters,
     * which gives sensible-ish marker text even for unexpected outputs.
     */
    private static String shortenLanguageName(String full) {
        if (full == null) return "auto";
        String f = full.trim().toLowerCase();
        switch (f) {
            case "english":            return "en";
            case "russian":            return "ru";
            case "ukrainian":          return "uk";
            case "belarusian":         return "be";
            case "spanish":            return "es";
            case "french":             return "fr";
            case "german":             return "de";
            case "italian":            return "it";
            case "portuguese":         return "pt";
            case "polish":             return "pl";
            case "turkish":            return "tr";
            // Apify's actor returns the full English language name -
            // map it to the same BCP-47 code we use elsewhere so the
            // suffix marker (zh-CN2ru) stays consistent with what
            // Google direct returns natively.
            case "chinese":
            case "chinese (simplified)":
            case "chinese (traditional)": return "zh-CN";
            case "japanese":           return "ja";
            case "korean":             return "ko";
            case "arabic":             return "ar";
            case "hindi":              return "hi";
            case "vietnamese":         return "vi";
            case "indonesian":         return "id";
            case "auto":
            case "":                   return "auto";
            default:
                return f.length() <= 3 ? f : f.substring(0, 3);
        }
    }

    /** Read an InputStream fully into a UTF-8 string, capped at a sane chat-payload size to avoid OOMing on a hostile response. */
    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[2048];
            int n;
            int total = 0;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
                total += n;
                // 256 KiB cap - the largest legitimate translation
                // response we've seen is a few KiB. If we're past 256
                // KiB the upstream is misbehaving and we should bail.
                if (total > 262_144) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    /**
     * True when the body is mostly punctuation / whitespace /
     * non-alphabetic characters and therefore not worth translating.
     * Used as a cheap pre-filter so we don't burn an HTTP request on
     * "..." or ":)" or "+1".
     *
     * <p>The threshold is "weight ≥ 2" (see
     * {@link #translationWeight}) so a single CJK ideograph like
     * "好" already qualifies (each ideograph contributes 2 to the
     * weight); for Latin text the gate still requires at least two
     * letters, matching the previous behaviour.
     */
    private static boolean isMostlySymbolic(String text) {
        if (text == null || text.isEmpty()) return true;
        int letterWeight = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp)) {
                letterWeight += isCjkLike(cp) ? 2 : 1;
                if (letterWeight >= 2) {
                    return false;
                }
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    /**
     * "Translation weight" of a string - the same as Java's
     * {@link String#length()} for Latin / Cyrillic / Greek / etc.,
     * but with CJK ideographs and Hangul syllables counted as 2.
     * Reflects the rough semantic density: a single Chinese
     * character like {@code 好} ("good") is a complete word
     * carrying about as much information as a 4-letter English
     * word, whereas an individual Latin letter is only a fraction
     * of one. Without this weighting the {@link #minLength} gate
     * (default 3) silently dropped legitimate short-but-meaningful
     * Asian-language messages like {@code 你好} ("hello", 2 chars
     * in Java) - exactly the bug the user reported as "китайские
     * иероглифы не переводит".
     *
     * <p>Phonetic kana (Hiragana / Katakana) is also weighted 2x
     * even though individual kana are syllables not whole words -
     * Japanese chat is just typically short enough that the leniency
     * is welcome and false positives (translating very short non-
     * messages) are rare in practice.
     */
    private static int translationWeight(String s) {
        int total = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            total += isCjkLike(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return total;
    }

    /**
     * True if the code point belongs to a CJK ideograph block,
     * Hangul syllables, or Japanese kana. Used to decide which
     * characters should be weighted double in
     * {@link #translationWeight}.
     *
     * <p>The selection is deliberately narrow - we don't include
     * scripts like Arabic, Hebrew, Devanagari, etc. even though
     * they're also non-Latin, because their characters represent
     * phonemes (like Latin) rather than morphemes (like CJK), so
     * weighting them double would over-promote short non-content
     * messages.
     */
    private static boolean isCjkLike(int cp) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA
                || b == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
