package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operator announcements: a short message the server wants in front of
 * the player now.
 *
 * <h3>Two ways in, one effect</h3>
 * An announcement arrives either pushed down the event stream (for
 * players already in game) or attached to the periodic rules response
 * (for anyone who launched after it was created). Both funnel through
 * {@link #accept}, which keys on the announcement id - so an
 * announcement that arrives by push and again on the next poll rings
 * once, prints to chat once, and shows one banner.
 *
 * <p>{@link #seenIds} is never cleared. An announcement lives at most
 * a week and the set costs a few bytes per entry, so bounding it would
 * risk re-firing an old one for no real saving.
 */
public final class PhazeAnnouncements {

    /** How long one banner stays on screen, independent of the announcement's lifetime. */
    private static final long BANNER_MS = 10_000L;

    /**
     * URLs in the chat line become clickable. Deliberately strict -
     * only http(s), and stopping at whitespace or trailing
     * punctuation, so a sentence-ending period does not end up inside
     * the link.
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+[\\w\\-_~/#\\[\\]@$&*+=]");

    private static final Set<Integer> seenIds = new LinkedHashSet<>();
    private static final List<Banner> banners = new ArrayList<>();

    private PhazeAnnouncements() {
    }

    /** One on-screen announcement with its own deadline. */
    public record Banner(int id, String text, long shownUntilMs) {
        public boolean isExpired(long now) {
            return now >= shownUntilMs;
        }
    }

    /**
     * Banners that should currently be drawn, newest last. Expired
     * ones are dropped as a side effect - this is called every frame,
     * which makes it the natural place to sweep.
     */
    public static synchronized List<Banner> activeBanners() {
        long now = System.currentTimeMillis();
        banners.removeIf(banner -> banner.isExpired(now));
        return List.copyOf(banners);
    }

    /** Drops one immediately - the operator retracted it. */
    public static synchronized void dismiss(int id) {
        banners.removeIf(banner -> banner.id() == id);
    }

    /** Handles a batch from the rules response. */
    public static void acceptAll(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (entry != null && entry.isJsonObject()) {
                accept(entry.getAsJsonObject());
            }
        }
    }

    /**
     * Shows one announcement, unless this id has already been shown.
     * Safe to call from any thread - the effects are hopped onto the
     * client thread.
     */
    public static void accept(JsonObject json) {
        if (json == null || !json.has("id")) {
            return;
        }

        int id;
        try {
            id = json.get("id").getAsInt();
        } catch (Throwable malformed) {
            return;
        }

        synchronized (PhazeAnnouncements.class) {
            if (!seenIds.add(id)) {
                return;
            }
        }

        String text = optionalString(json, "text");
        if (text == null) {
            return;
        }
        String sound = optionalString(json, "sound");
        boolean chatEnabled = json.has("chatEnabled")
                && !json.get("chatEnabled").isJsonNull()
                && json.get("chatEnabled").getAsBoolean();
        String chatText = chatEnabled ? optionalString(json, "chatText") : null;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> show(client, id, text, sound, chatText));
    }

    private static void show(MinecraftClient client, int id, String text,
                             String sound, String chatText) {
        synchronized (PhazeAnnouncements.class) {
            banners.add(new Banner(id, text, System.currentTimeMillis() + BANNER_MS));
        }

        playSound(client, sound);

        if (chatText != null && !chatText.isBlank() && client.player != null) {
            client.player.sendMessage(buildChatMessage(chatText), false);
        }
    }

    /**
     * Maps the server's sound key to a vanilla sound.
     *
     * <p>The server sends a key, never a sound id, so this switch is
     * the whole set of sounds an announcement can ever make. Anything
     * unrecognised is silent rather than falling back to something -
     * a surprise noise is worse than none.
     */
    private static void playSound(MinecraftClient client, String sound) {
        if (sound == null || sound.isBlank() || "none".equals(sound)) {
            return;
        }
        if (client.getSoundManager() == null) {
            return;
        }

        SoundEvent event = switch (sound) {
            case "ping" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "levelup" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case "bell" -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case "chime" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case "xp" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "challenge" -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case "click" -> SoundEvents.UI_BUTTON_CLICK.value();
            default -> null;
        };
        if (event == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.master(event, 1.0F, 1.0F));
    }

    /**
     * Turns the chat line into text with clickable links.
     *
     * <p>Only the URL itself carries the click event and the underline
     * - making the whole line clickable would mean a stray click on
     * the message opened a browser.
     */
    static Text buildChatMessage(String raw) {
        MutableText result = Text.literal("");
        Matcher matcher = URL_PATTERN.matcher(raw);
        int cursor = 0;

        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result.append(Text.literal(raw.substring(cursor, matcher.start())));
            }
            String url = matcher.group();
            result.append(Text.literal(url).setStyle(Style.EMPTY
                    .withColor(Formatting.AQUA)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
            cursor = matcher.end();
        }
        if (cursor < raw.length()) {
            result.append(Text.literal(raw.substring(cursor)));
        }
        return result;
    }

    private static String optionalString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            return null;
        }
        String value = json.get(field).getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
