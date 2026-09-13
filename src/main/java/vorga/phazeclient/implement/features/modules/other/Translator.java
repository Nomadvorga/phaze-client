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

public final class Translator extends Module {
    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    private static final Logger LOG = LoggerFactory.getLogger("PhazeTranslator");

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

    private static final String[] LANGUAGE_NAMES = {
            "Russian", "English", "Ukrainian", "Belarusian", "Spanish", "French",
            "German", "Italian", "Portuguese", "Polish", "Turkish", "Chinese",
            "Japanese", "Korean", "Arabic", "Hindi", "Vietnamese", "Indonesian"
    };

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

            Map.entry("mend",    "Mending enchantment")
    );

    private static final Pattern SLANG_PATTERN = Pattern.compile(
            "\\b(" + SLANG_EXPANSIONS.keySet().stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .map(Pattern::quote)
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("") + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PLAYER_CHAT_HEURISTIC = Pattern.compile(
            "^.*?\\p{L}[\\p{L}\\p{N}_\\-]+\\s*[:\u00BB]\\s+.+$",
            Pattern.DOTALL
    );

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(.*?[:>\u00BB])\\s+(.*)$", Pattern.DOTALL);

    private static final int CACHE_CAPACITY = 256;

    private static final int MAX_PENDING = 32;

    public final SectionSetting generalSection = new SectionSetting("General");

    public final SelectSetting targetLanguage = new SelectSetting(
            "Target Language",
            "Language to translate incoming chat into"
    ).value(LANGUAGE_NAMES).selected("Russian");

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

    public final BooleanLike showOriginalOnHover = () -> translationOptions.isSelected("Show Original On Hover");

    public final BooleanLike skipNicknames = () -> translationOptions.isSelected("Skip Nicknames");

    public final BooleanLike skipSameLanguage = () -> translationOptions.isSelected("Skip Same Language");

    public final BooleanLike replaceOriginal = () -> translationOptions.isSelected("Replace Message");

    public final BooleanLike onlyPlayers = () -> translationOptions.isSelected("Only Players");

    public final BooleanLike autoDetectSource = () -> translationOptions.isSelected("Auto-Detect Source");

    public final SelectSetting sourceLanguage = new SelectSetting(
            "Source Language",
            "Source language (used only when Auto-Detect Source is off)"
    ).value(LANGUAGE_NAMES).selected("English")
     .visible(() -> !autoDetectSource.isValue());

    public final ValueSetting minLength = new ValueSetting(
            "Min Length",
            "Skip messages whose translatable body has fewer characters than this"
    ).range(1, 64).step(1).setValue(3);

    public final BooleanLike expandSlang = () -> translationOptions.isSelected("Expand Slang");

    public final SectionSetting providerSection = new SectionSetting("Provider");

    public final SelectSetting provider = new SelectSetting(
            "Provider",
            "Translation backend. Google = direct mobile endpoint (fast, free). Apify = run actor through Apify API (slower, uses your token)."
    ).value("Google", "Apify").selected("Google");

    public final TextSetting apifyToken = new TextSetting(
            "Apify Token",
            "Personal API token for api.apify.com (used only when Provider=Apify)"
    ).setText("")
     .visible(() -> provider.isSelected("Apify"));

    public final TextSetting apifyActor = new TextSetting(
            "Apify Actor",
            "Actor ID in username~actorname form"
    ).setText("web.harvester~google-translator")
     .visible(() -> provider.isSelected("Apify"));

    @SuppressWarnings("serial")
    private final Map<String, String> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_CAPACITY;
        }
    };

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "phaze-translator");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger pending = new AtomicInteger(0);

    private boolean bypass = false;

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

    public boolean isBypassActive() {
        return bypass;
    }

    public void onIncomingChat(Text message, MessageSignatureData signature) {
        if (!isEnabled() || bypass || message == null) {
            return;
        }

        if (isClientSystemMessage(message)) {
            return;
        }

        if (onlyPlayers.isValue() && !isPlayerChat(message, signature)) {
            return;
        }

        String raw = message.getString();
        if (raw == null || raw.isEmpty()) {
            return;
        }

        lastIncomingRaw = raw;

        String header;
        String body;
        if (skipNicknames.isValue()) {
            Matcher matcher = HEADER_PATTERN.matcher(raw);
            if (matcher.matches()) {
                header = matcher.group(1) + " ";
                body = matcher.group(2);
            } else {

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

        if (translationWeight(body) < (int) minLength.getValue()) {
            return;
        }

        if (isMostlySymbolic(body)) {
            return;
        }

        String target = languageNameToCode(targetLanguage.getSelected());
        if (target == null || target.isEmpty()) {
            return;
        }

        String cacheKey = target + "|" + body;
        String cached;
        synchronized (cache) {
            cached = cache.get(cacheKey);
        }
        if (cached != null) {

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

        if (pending.get() >= MAX_PENDING) {
            return;
        }
        pending.incrementAndGet();
        final Text messageF = message;
        final String rawF = raw;
        final String headerF = header;
        final String bodyF = body;
        final String targetF = target;

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

        String apiInput = shouldExpandSlang(body, source) ? applySlangExpansion(body) : body;

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

        if (translation.trim().equalsIgnoreCase(body.trim())) {
            return;
        }

        String displayDetected = autoDetectSource.isValue() ? detected : source;
        postTranslation(original, raw, header, translation, displayDetected, target);
    }

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

            vorga.phazeclient.api.system.hud.ExordiumAnimationBridge
                    .requestImmediateCapture(
                            vorga.phazeclient.api.system.hud.ExordiumAnimationBridge.CHAT);
        });
    }

    private MutableText buildStyledTranslationRow(Text original, String header, String translation, String suffix) {
        if (original == null) {

            MutableText row = Text.empty();
            if (header != null && !header.isEmpty()) {
                row.append(Text.literal(header));
            }
            row.append(Text.literal(translation));
            if (suffix != null) {
                row.append(Text.literal(suffix).formatted(Formatting.GRAY, Formatting.ITALIC));
            }

            return row;
        }

        String raw = original.getString();

        int bodyStart = header == null ? 0 : header.length();

        if (bodyStart < 0 || bodyStart > raw.length()) {
            bodyStart = 0;
        }
        final int bodyStartF = bodyStart;
        final int bodyEndF = raw.length();

        final MutableText result = Text.empty();
        final int[] cursor = {0};
        final boolean[] translationInserted = {false};

        original.visit((StringVisitable.StyledVisitor<Object>) (style, str) -> {
            int segStart = cursor[0];
            int segEnd = segStart + str.length();
            cursor[0] = segEnd;

            int prefixLen = Math.max(0, Math.min(str.length(), bodyStartF - segStart));

            int suffixStart = Math.min(str.length(), Math.max(0, bodyEndF - segStart));

            if (prefixLen > 0) {
                String prefix = str.substring(0, prefixLen);
                result.append(Text.literal(prefix).setStyle(style));
            }

            boolean bodyOverlaps = segStart < bodyEndF && segEnd > bodyStartF;
            if (bodyOverlaps && !translationInserted[0]) {

                result.append(Text.literal(translation).setStyle(style));
                translationInserted[0] = true;
            }

            if (suffixStart < str.length()) {
                String tail = str.substring(suffixStart);
                result.append(Text.literal(tail).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (!translationInserted[0]) {
            result.append(Text.literal(translation));
        }

        if (suffix != null) {

            result.append(Text.literal(suffix).formatted(Formatting.GRAY, Formatting.ITALIC));
        }

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

        return new HoverEvent.ShowText(tooltip);
    }

    private static String languageNameToCode(String name) {
        if (name == null || name.isEmpty()) return "ru";
        String code = LANGUAGE_NAME_TO_CODE.get(name);
        if (code != null) return code;

        return name.trim().toLowerCase();
    }

    private static boolean isPlayerChat(Text message, MessageSignatureData signature) {
        if (signature != null) return true;
        if (message == null) return false;
        String raw = message.getString();
        if (raw == null || raw.isEmpty()) return false;

        if (raw.startsWith("<")) {
            int close = raw.indexOf("> ");
            if (close > 1 && close < raw.length() - 2) {
                String name = raw.substring(1, close);

                if (!name.isEmpty() && name.indexOf('<') < 0 && containsLetter(name)) {
                    return true;
                }
            }
        }

        return PLAYER_CHAT_HEURISTIC.matcher(raw).matches();
    }

    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.codePointAt(i))) return true;
        }
        return false;
    }

    private boolean shouldExpandSlang(String body, String source) {
        if (!expandSlang.isValue()) return false;
        if (body == null || body.isEmpty()) return false;
        if (!isAsciiLettersOnly(body)) return false;
        return "auto".equals(source) || "en".equalsIgnoreCase(source);
    }

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

    private String lastIncomingRaw;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> storedLineTicks =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void captureStoredLine(int creationTick) {
        String raw = lastIncomingRaw;
        lastIncomingRaw = null;
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (storedLineTicks.size() > 32) {
            storedLineTicks.clear();
        }
        storedLineTicks.put(raw, (long) creationTick);
    }

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

            Long tick = storedLineTicks.remove(originalRaw);
            if (tick != null) {
                for (int i = 0; i < messages.size(); i++) {
                    ChatHudLine line = messages.get(i);
                    if (line != null && line.creationTick() == tick) {
                        targetIdx = i;
                        targetLine = line;
                        break;
                    }
                }
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

    private String[] translateGoogleDirect(String text, String source, String target) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);

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

                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(60_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");

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

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[2048];
            int n;
            int total = 0;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
                total += n;

                if (total > 262_144) {
                    break;
                }
            }
        }
        return sb.toString();
    }

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

    private static int translationWeight(String s) {
        int total = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            total += isCjkLike(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return total;
    }

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
