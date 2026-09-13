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
import net.minecraft.text.TextColor;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;

public final class PhazeAnnouncements {

    private static final long BANNER_MS = 10_000L;

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+[\\w\\-_~/#\\[\\]@$&*+=]");

    private static final int BRAND_FROM = 0x6EF7A5;
    private static final int BRAND_TO = 0x0FA968;

    private static final Set<String> seenIds = new LinkedHashSet<>();
    private static final List<Banner> banners = new ArrayList<>();

    private PhazeAnnouncements() {
    }

    private static int resolveColor(String key) {
        if (key == null) {
            return 0xFFFFFFFF;
        }
        return switch (key) {
            case "gray" -> 0xFF9CA3AF;
            case "red" -> 0xFFFF5555;
            case "orange" -> 0xFFFFA33F;
            case "yellow" -> 0xFFFFD93D;
            case "green" -> 0xFF4ADE80;
            case "aqua" -> 0xFF3FE0D0;
            case "blue" -> 0xFF5B9DFF;
            case "purple" -> 0xFFB57BFF;
            case "pink" -> 0xFFFF7BC2;

            default -> 0xFFFFFFFF;
        };
    }

    public record Banner(int id, String text, int color, long shownUntilMs) {
        public boolean isExpired(long now) {
            return now >= shownUntilMs;
        }
    }

    public static synchronized List<Banner> activeBanners() {
        long now = System.currentTimeMillis();
        banners.removeIf(banner -> banner.isExpired(now));
        return List.copyOf(banners);
    }

    public static synchronized void dismiss(int id) {
        banners.removeIf(banner -> banner.id() == id);
    }

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

    public static void accept(JsonObject json) {
        if (json == null || !json.has("id")) {
            return;
        }

        int id;
        int repeatCount;
        try {
            id = json.get("id").getAsInt();
            repeatCount = json.has("repeatCount") && !json.get("repeatCount").isJsonNull()
                    ? json.get("repeatCount").getAsInt()
                    : 0;
        } catch (Throwable malformed) {
            return;
        }

        String key = id + ":" + repeatCount;
        synchronized (PhazeAnnouncements.class) {
            if (!seenIds.add(key)) {
                return;
            }
        }

        String text = optionalString(json, "text");
        if (text == null) {
            return;
        }
        String sound = optionalString(json, "sound");
        int color = resolveColor(optionalString(json, "color"));
        boolean chatEnabled = json.has("chatEnabled")
                && !json.get("chatEnabled").isJsonNull()
                && json.get("chatEnabled").getAsBoolean();
        String chatText = chatEnabled ? optionalString(json, "chatText") : null;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> show(client, id, text, color, sound, chatText));
    }

    private static void show(MinecraftClient client, int id, String text, int color,
                             String sound, String chatText) {
        synchronized (PhazeAnnouncements.class) {
            banners.add(new Banner(id, text, color, System.currentTimeMillis() + BANNER_MS));
        }

        playSound(client, sound);

        if (chatText != null && !chatText.isBlank() && client.player != null) {
            client.player.sendMessage(buildChatMessage(chatText), false);
        }
    }

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
        client.getSoundManager().play(PositionedSoundInstance.ui(event, 1.0F, 1.0F));
    }

    static Text buildChatMessage(String raw) {
        MutableText result = brandPrefix();
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
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
            cursor = matcher.end();
        }
        if (cursor < raw.length()) {
            result.append(Text.literal(raw.substring(cursor)));
        }
        return result;
    }

    public static MutableText systemMessage(Text body) {
        MutableText result = brandPrefix();
        if (body != null) {
            result.append(body.copy());
        }
        return result;
    }

    public static MutableText systemMessage(String body) {
        return systemMessage(Text.literal(body == null ? "" : body));
    }

    private static MutableText brandPrefix() {
        final String brand = "Phaze Client";
        MutableText prefix = Text.literal("");

        for (int i = 0; i < brand.length(); i++) {
            float t = brand.length() == 1 ? 0.0F : i / (float) (brand.length() - 1);
            prefix.append(Text.literal(String.valueOf(brand.charAt(i)))
                    .setStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(lerpColor(BRAND_FROM, BRAND_TO, t)))
                            .withBold(true)));
        }

        prefix.append(Text.literal(" · ")
                .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)));
        return prefix;
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static String optionalString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            return null;
        }
        String value = json.get(field).getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
