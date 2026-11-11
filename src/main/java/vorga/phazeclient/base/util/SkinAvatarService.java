package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches official Mojang skins once and draws their heads from one shared atlas.
 *
 * <p>Each skin is reduced, once, to a 64x64 head: face plus hat layer, with
 * anti-aliased rounded corners baked into the alpha channel, written into its
 * own cell of a single texture. An avatar is then one textured quad that joins
 * the open GUI batch, so a chat full of faces costs no extra draw calls and no
 * texture switches. Lookups by name allocate nothing.</p>
 */
public final class SkinAvatarService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessengerSkins");
    private static final int TIMEOUT_MS = 3_000;
    private static final int MAX_IMAGE_BYTES = 1_048_576;
    private static final String TEXTURE_HOST = "textures.minecraft.net";
    private static final int HEAD_SIZE = 64;
    /** A head plus a one pixel gutter that repeats its edge, so linear filtering never reads a neighbour. */
    private static final int CELL = HEAD_SIZE + 2;
    private static final int COLUMNS = 16;
    private static final int ATLAS_SIZE = CELL * COLUMNS;
    private static final int STEVE_CELL = 0;
    private static final int ANONYMOUS_CELL = 1;
    private static final int FIRST_USER_CELL = 2;
    private static final int CELLS = COLUMNS * COLUMNS;
    private static final Identifier ATLAS_ID = Identifier.of("phaze", "dynamic/chat/head_atlas");
    /** Corner radius as a fraction of the avatar edge: a small, size-independent rounding. */
    public static final float HEAD_CORNER = 0.16F;
    private static final Identifier STEVE_SKIN = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    private static final Identifier ANONYMOUS_SKIN = Identifier.of("phaze", "textures/chat/anon_skin.png");
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Boolean> FAILED = new ConcurrentHashMap<>();

    private static final class Holder {
        private static final SkinAvatarService INSTANCE = new SkinAvatarService();
    }

    private final ExecutorService io = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "phaze-skin-avatar");
        thread.setDaemon(true);
        return thread;
    });

    // Render thread only.
    /** Lowercase name to its atlas cell, for loaded heads. */
    private final Map<String, Integer> cells = new HashMap<>();
    /** Names exactly as callers pass them; cleared whenever a head arrives. */
    private final Map<String, Integer> lookups = new HashMap<>();
    private NativeImageBackedTexture atlas;
    private int atlasGlId;
    private int nextCell = FIRST_USER_CELL;

    public static SkinAvatarService getInstance() {
        return Holder.INSTANCE;
    }

    /** Atlas cell of {@code username}'s head; the Steve cell while the fetch is pending. Render thread only. */
    public int head(String username) {
        if (username == null) return STEVE_CELL;
        Integer known = lookups.get(username);
        if (known != null) return known;
        String name = normalize(username);
        int cell = STEVE_CELL;
        if (name != null) {
            String key = name.toLowerCase(Locale.ROOT);
            Integer loaded = cells.get(key);
            if (loaded != null) cell = loaded;
            else request(name);
        }
        if (lookups.size() > CELLS * 4) lookups.clear();
        lookups.put(username, cell);
        return cell;
    }

    /** Shown instead of the real face of a user who blocked the local player. */
    public int anonymousHead() {
        return ANONYMOUS_CELL;
    }

    public void preload(Iterable<String> usernames) {
        if (usernames == null) return;
        for (String username : usernames) request(username);
    }

    /**
     * One head as a square at float coordinates, tinted by {@code color}. Inside a GUI batch it is
     * queued with everything else; outside one it is a single immediate quad. Render thread only.
     */
    public void draw(Matrix4f matrix, int cell, float x, float y, float size, int color) {
        if (!ensureAtlas()) return;
        float u0 = ((cell % COLUMNS) * CELL + 1) / (float) ATLAS_SIZE;
        float v0 = ((cell / COLUMNS) * CELL + 1) / (float) ATLAS_SIZE;
        float u1 = u0 + HEAD_SIZE / (float) ATLAS_SIZE;
        float v1 = v0 + HEAD_SIZE / (float) ATLAS_SIZE;
        if (BatchedRectangle.isBatching()) {
            GuiBatch.texture(atlasGlId).quad(matrix, x, y, size, size, 0.0F, u0, v0, u1, v1, color, 0);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, ATLAS_ID);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(color);
        buffer.vertex(matrix, x, y + size, 0.0F).texture(u0, v1).color(color);
        buffer.vertex(matrix, x + size, y + size, 0.0F).texture(u1, v1).color(color);
        buffer.vertex(matrix, x + size, y, 0.0F).texture(u1, v0).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    /** Creates the atlas with the two bundled heads on first use; false if it can not exist. */
    private boolean ensureAtlas() {
        if (atlas != null) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, true);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.setFilter(true, false);
            client.getTextureManager().registerTexture(ATLAS_ID, texture);
            atlas = texture;
            atlasGlId = texture.getGlId();
            writeBundled(client, STEVE_SKIN, STEVE_CELL);
            writeBundled(client, ANONYMOUS_SKIN, ANONYMOUS_CELL);
            atlas.upload();
            return true;
        } catch (Throwable error) {
            LOG.debug("Could not create the head atlas", error);
            atlas = null;
            return false;
        }
    }

    private void writeBundled(MinecraftClient client, Identifier skin, int cell) {
        try (InputStream stream = client.getResourceManager().open(skin);
             NativeImage image = NativeImage.read(NativeImage.Format.RGBA, stream);
             NativeImage head = composeHead(image)) {
            writeCell(atlas.getImage(), cell, head);
        } catch (Throwable error) {
            LOG.debug("Could not build bundled head {}", skin, error);
        }
    }

    /** Copies {@code head} into its cell and repeats the outer pixels into the gutter. */
    private static void writeCell(NativeImage target, int cell, NativeImage head) {
        int originX = (cell % COLUMNS) * CELL;
        int originY = (cell / COLUMNS) * CELL;
        for (int y = 0; y < CELL; y++) {
            int sy = Math.max(0, Math.min(HEAD_SIZE - 1, y - 1));
            for (int x = 0; x < CELL; x++) {
                int sx = Math.max(0, Math.min(HEAD_SIZE - 1, x - 1));
                target.setColorArgb(originX + x, originY + y, head.getColorArgb(sx, sy));
            }
        }
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
        if (name == null) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (FAILED.containsKey(key) || !REQUESTED.add(key)) return;
        io.execute(() -> load(name, key));
    }

    private void load(String username, String key) {
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
                try (head) {
                    if (nextCell >= CELLS || !ensureAtlas()) return;
                    int cell = nextCell++;
                    writeCell(atlas.getImage(), cell, head);
                    atlas.upload();
                    cells.put(key, cell);
                    lookups.clear();
                } catch (Throwable error) {
                    FAILED.put(key, Boolean.TRUE);
                    LOG.debug("Could not upload skin for {}", username, error);
                }
            });
        } catch (Throwable error) {
            FAILED.put(key, Boolean.TRUE);
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

    /** A Minecraft name (1-16 of A-Z, a-z, 0-9, _) after trimming, else null; no regex, no allocation when valid. */
    private static String normalize(String username) {
        if (username == null) return null;
        String value = username.trim();
        if (value.isEmpty() || value.length() > 16) return null;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return null;
        }
        return value;
    }
}
