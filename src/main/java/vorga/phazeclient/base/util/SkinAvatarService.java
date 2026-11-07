package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches official Mojang skins once and exposes render-thread-safe head textures.
 *
 * <p>Each skin is reduced, once, to a 64x64 head: face plus hat layer, with
 * anti-aliased rounded corners baked into the alpha channel. An avatar is then
 * a single ordinary textured quad, at any size, with no masking at draw time.
 */
public final class SkinAvatarService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessengerSkins");
    private static final int TIMEOUT_MS = 3_000;
    private static final int MAX_IMAGE_BYTES = 1_048_576;
    private static final int MAX_TEXTURES = 256;
    private static final String TEXTURE_HOST = "textures.minecraft.net";
    private static final int HEAD_SIZE = 64;
    /** Corner radius as a fraction of the avatar edge: a small, size-independent rounding. */
    public static final float HEAD_CORNER = 0.16F;
    private static final Identifier STEVE_SKIN = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    private static final Identifier ANONYMOUS_SKIN = Identifier.of("phaze", "textures/chat/anon_skin.png");
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FAILED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, Identifier> BUNDLED_HEADS = new ConcurrentHashMap<>();
    private static final class Holder {
        private static final SkinAvatarService INSTANCE = new SkinAvatarService();
    }

    private final ExecutorService io = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "phaze-skin-avatar");
        thread.setDaemon(true);
        return thread;
    });

    public static SkinAvatarService getInstance() {
        return Holder.INSTANCE;
    }

    /** Rounded head for {@code username}; the default Steve head while the fetch is pending. Render thread only. */
    public Identifier head(String username) {
        String name = normalize(username);
        if (name != null) {
            Identifier texture = TEXTURES.get(name.toLowerCase());
            if (texture != null) return texture;
            request(name);
        }
        return bundledHead(STEVE_SKIN);
    }

    /** Shown instead of the real face of a user who blocked the local player. Render thread only. */
    public Identifier anonymousHead() {
        return bundledHead(ANONYMOUS_SKIN);
    }

    public void preload(Iterable<String> usernames) {
        if (usernames == null) return;
        for (String username : usernames) request(username);
    }

    private static Identifier bundledHead(Identifier skin) {
        Identifier cached = BUNDLED_HEADS.get(skin);
        if (cached != null) return cached;
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier id = Identifier.of("phaze", "dynamic/chat/head_" + skin.getPath().replaceAll("[^a-z0-9_]", "_"));
        try (InputStream stream = client.getResourceManager().open(skin);
             NativeImage image = NativeImage.read(NativeImage.Format.RGBA, stream)) {
            registerHead(client, id, composeHead(image));
        } catch (Throwable error) {
            LOG.debug("Could not build bundled head {}", skin, error);
            return id;
        }
        BUNDLED_HEADS.put(skin, id);
        return id;
    }

    /** Takes ownership of {@code head}; must run on the render thread (GPU upload). */
    private static void registerHead(MinecraftClient client, Identifier id, NativeImage head) {
        NativeImageBackedTexture texture = new NativeImageBackedTexture(head);
        texture.setFilter(true, false);
        client.getTextureManager().registerTexture(id, texture);
    }

    /**
     * Face (8,8) with the hat layer (40,8) on top, scaled to {@value #HEAD_SIZE}px. Legacy
     * 64x32 skins often fill the hat area with opaque colour; like vanilla, a hat without any
     * transparent pixel is ignored there.
     */
    static NativeImage composeHead(NativeImage skin) {
        if (skin.getWidth() < 16 || skin.getHeight() < 16) throw new IllegalArgumentException("skin too small");
        int scale = HEAD_SIZE / 8;
        boolean hat = skin.getWidth() >= 48 && skin.getHeight() >= 16
                && (skin.getHeight() >= 64 || hatHasTransparency(skin));
        NativeImage head = new NativeImage(NativeImage.Format.RGBA, HEAD_SIZE, HEAD_SIZE, false);
        float radius = HEAD_SIZE * HEAD_CORNER;
        for (int y = 0; y < HEAD_SIZE; y++) {
            for (int x = 0; x < HEAD_SIZE; x++) {
                int sx = x / scale, sy = y / scale;
                int argb = skin.getColorArgb(8 + sx, 8 + sy) | 0xFF000000;
                if (hat) argb = over(skin.getColorArgb(40 + sx, 8 + sy), argb);
                float coverage = cornerCoverage(x + 0.5F, y + 0.5F, radius);
                int alpha = Math.round(255.0F * coverage);
                head.setColorArgb(x, y, (alpha << 24) | (argb & 0x00FFFFFF));
            }
        }
        return head;
    }

    private static boolean hatHasTransparency(NativeImage skin) {
        for (int y = 8; y < 16; y++) {
            for (int x = 40; x < 48; x++) {
                if ((skin.getColorArgb(x, y) >>> 24) < 255) return true;
            }
        }
        return false;
    }

    private static int over(int top, int bottom) {
        int alpha = top >>> 24;
        if (alpha == 0) return bottom;
        if (alpha == 255) return top;
        int r = (((top >> 16) & 0xFF) * alpha + ((bottom >> 16) & 0xFF) * (255 - alpha)) / 255;
        int g = (((top >> 8) & 0xFF) * alpha + ((bottom >> 8) & 0xFF) * (255 - alpha)) / 255;
        int b = ((top & 0xFF) * alpha + (bottom & 0xFF) * (255 - alpha)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Fraction of the pixel centred at (x, y) inside a rounded square of the given radius. */
    private static float cornerCoverage(float x, float y, float radius) {
        float dx = Math.max(Math.max(radius - x, x - (HEAD_SIZE - radius)), 0.0F);
        float dy = Math.max(Math.max(radius - y, y - (HEAD_SIZE - radius)), 0.0F);
        if (dx <= 0.0F || dy <= 0.0F) return 1.0F;
        float outside = (float) Math.sqrt(dx * dx + dy * dy) - radius;
        return Math.max(0.0F, Math.min(1.0F, 0.5F - outside));
    }

    private void request(String username) {
        String name = normalize(username);
        if (name == null || FAILED.containsKey(name.toLowerCase()) || !REQUESTED.add(name.toLowerCase())) return;
        io.execute(() -> load(name));
    }

    private void load(String username) {
        try {
            String uuid = readProfileUuid(username);
            if (uuid == null) throw new IllegalStateException("profile not found");
            String skinUrl = readSkinUrl(uuid);
            if (skinUrl == null) throw new IllegalStateException("profile has no skin");
            URI uri = URI.create(skinUrl);
            if (!TEXTURE_HOST.equalsIgnoreCase(uri.getHost())) throw new SecurityException("untrusted skin host");
            byte[] bytes = readBytes(uri);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) throw new IllegalStateException("client unavailable");
            NativeImage head;
            try (NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes))) {
                head = composeHead(image);
            }
            client.execute(() -> {
                if (TEXTURES.size() >= MAX_TEXTURES) {
                    head.close();
                    return;
                }
                try {
                    Identifier id = Identifier.of("phaze", "dynamic/chat/avatar_" + Integer.toUnsignedString(username.toLowerCase().hashCode()));
                    registerHead(client, id, head);
                    TEXTURES.put(username.toLowerCase(), id);
                } catch (Throwable error) {
                    head.close();
                    FAILED.put(username.toLowerCase(), Boolean.TRUE);
                    LOG.debug("Could not upload skin for {}", username, error);
                }
            });
        } catch (Throwable error) {
            FAILED.put(username.toLowerCase(), Boolean.TRUE);
            LOG.debug("Could not load Mojang skin for {}", username, error);
        }
    }

    private static String readProfileUuid(String username) throws Exception {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(readText(URI.create(
                "https://api.mojang.com/users/profiles/minecraft/" + encoded))).getAsJsonObject();
        return root.has("id") ? root.get("id").getAsString() : null;
    }

    private static String readSkinUrl(String uuid) throws Exception {
        JsonObject root = JsonParser.parseString(readText(URI.create(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))).getAsJsonObject();
        JsonArray properties = root.getAsJsonArray("properties");
        if (properties == null) return null;
        for (var property : properties) {
            JsonObject object = property.getAsJsonObject();
            if (!"textures".equals(object.get("name").getAsString())) continue;
            JsonObject decoded = JsonParser.parseString(new String(Base64.getDecoder().decode(
                    object.get("value").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject skin = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN");
            return skin == null ? null : skin.get("url").getAsString();
        }
        return null;
    }

    private static String readText(URI uri) throws Exception {
        return new String(readBytes(uri), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(URI uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json,image/png");
            connection.setRequestProperty("User-Agent", "PhazeClient-SkinCache/1");
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            if (connection.getContentLengthLong() > MAX_IMAGE_BYTES) throw new IllegalStateException("skin too large");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_IMAGE_BYTES) throw new IllegalStateException("response too large");
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String normalize(String username) {
        if (username == null) return null;
        String value = username.trim();
        if (value.length() < 1 || value.length() > 16 || !value.matches("[A-Za-z0-9_]+")) return null;
        return value;
    }
}
