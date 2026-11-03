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

/** Fetches official Mojang skins once and exposes render-thread-safe textures. */
public final class SkinAvatarService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessengerSkins");
    private static final int TIMEOUT_MS = 3_000;
    private static final int MAX_IMAGE_BYTES = 1_048_576;
    private static final int MAX_TEXTURES = 256;
    private static final String TEXTURE_HOST = "textures.minecraft.net";
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> OVERLAY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FAILED = new ConcurrentHashMap<>();
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

    /** Returns a loaded skin texture, or null while the background fetch is pending. */
    public Identifier get(String username) {
        String name = normalize(username);
        if (name == null) return null;
        Identifier texture = TEXTURES.get(name.toLowerCase());
        if (texture != null) return texture;
        request(name);
        return null;
    }

    public void preload(Iterable<String> usernames) {
        if (usernames == null) return;
        for (String username : usernames) request(username);
    }

    public boolean hasOverlay(String username) {
        String name = normalize(username);
        return name == null || OVERLAY.getOrDefault(name.toLowerCase(), Boolean.TRUE);
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
            client.execute(() -> {
                if (TEXTURES.size() >= MAX_TEXTURES) return;
                try {
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                    Identifier id = Identifier.of("phaze", "dynamic/chat/avatar_" + Integer.toUnsignedString(username.toLowerCase().hashCode()));
                    OVERLAY.put(username.toLowerCase(), image.getHeight() >= 64);
                    client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                    TEXTURES.put(username.toLowerCase(), id);
                } catch (Throwable error) {
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
