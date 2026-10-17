package vorga.phazeclient.implement.features.modules.other;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One-shot, bounded asset inspection, independent of the enabled resource stack.
 * Call load/close on the render thread. Cache the returned Data in the screen,
 * then close it on screen removal, pack changes, or resource reload. There is no
 * global cache: a Data owns every texture it exposes, including vanilla copies.
 */
public final class PackPreviewAssets {
    public static final long BLOCK_CYCLE_MILLIS = 1500L;
    public static final int MAX_BLOCK_SPRITES = 64;
    public static final int MAX_SPRITE_SIZE = 256;
    private static final int MAX_RESOURCES = 16384;
    private static final int MAX_NAMESPACES = 256;
    private static final int MAX_CANDIDATES = 2048;
    private static final int MAX_PNG_BYTES = 8 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private static final long MAX_IMAGE_PIXELS = 4L * 1024 * 1024;
    private static final long MAX_CUSTOM_PIXELS = 64L * 1024 * 1024;
    private static final long MAX_CUSTOM_BYTES = 128L * 1024 * 1024;
    private static final AtomicLong SERIAL = new AtomicLong();
    private static final ResourceType TYPE = ResourceType.CLIENT_RESOURCES;
    private static final List<String> FIXED_BLOCKS = List.of("dirt", "oak_log", "grass_block_top", "sand");

    private PackPreviewAssets() {
    }

    /** Texture size is the uploaded first frame; original sizes are the uncapped frame dimensions. */
    public record Sprite(Identifier id, int width, int height, int originalWidth, int originalHeight,
                          boolean custom) {
    }

    public record Features(boolean animations, boolean lightmap, boolean sounds) {
    }

    /** Inspect flags without a client, GPU, enabled packs, or texture uploads. */
    public static Features inspectFeatures(ResourcePack pack) {
        Loader loader = new Loader(pack);
        loader.scan();
        loader.detectFeatures();
        return new Features(loader.animations, loader.lightmap, loader.sounds);
    }

    /** All collections are immutable. A Sprite's id is valid only until this handle is closed. */
    public static final class Data implements AutoCloseable {
        private final MinecraftClient client;
        private final String packId;
        private final List<List<Sprite>> blockBatches;
        private final Map<String, Sprite> items;
        private final List<Sprite> hearts;
        private final Map<String, Sprite> particles;
        private final int resolution;
        private final int changedBlockCount;
        private final boolean animations;
        private final boolean lightmap;
        private final boolean sounds;
        private final List<String> diagnostics;
        private final List<Identifier> owned;
        private final long textureBytes;
        private boolean closed;

        private Data(Loader loader, List<List<Sprite>> blocks, Map<String, Sprite> items,
                     List<Sprite> hearts, Map<String, Sprite> particles, int changedBlockCount) {
            this.client = loader.client;
            this.packId = loader.packId;
            this.blockBatches = blocks.stream().map(List::copyOf).toList();
            this.items = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(items));
            this.hearts = List.copyOf(hearts);
            this.particles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(particles));
            this.resolution = PackPreviewAssetLogic.resolution(loader.widths);
            this.changedBlockCount = changedBlockCount;
            this.animations = loader.animations;
            this.lightmap = loader.lightmap;
            this.sounds = loader.sounds;
            this.diagnostics = List.copyOf(loader.diagnostics);
            this.owned = List.copyOf(loader.owned);
            this.textureBytes = loader.textureBytes;
        }

        public String packId() { return packId; }
        public List<List<Sprite>> blockBatches() { return blockBatches; }
        /** Supply elapsed time since the preview opened, or a monotonic clock in milliseconds. */
        public List<Sprite> blocksAt(long elapsedMillis) {
            return blockBatches.get(PackPreviewAssetLogic.batchIndex(elapsedMillis, blockBatches.size()));
        }
        public Map<String, Sprite> items() { return items; }
        /** In order: bordered full, bordered half, empty container. */
        public List<Sprite> hearts() { return hearts; }
        public Map<String, Sprite> particles() { return particles; }
        /** Modal custom block/item frame width, or 0 when no usable custom block/item PNG was read. */
        public int resolution() { return resolution; }
        /** Verified changed canonical block IDs; may be a lower bound if diagnostics reports a scan limit. */
        public int changedBlockCount() { return changedBlockCount; }
        public boolean animations() { return animations; }
        public boolean lightmap() { return lightmap; }
        public boolean sounds() { return sounds; }
        public List<String> diagnostics() { return diagnostics; }
        /** Uploaded RGBA bytes; NativeImageBackedTexture also retains an equally sized CPU image. */
        public long textureBytes() { return textureBytes; }
        public boolean isClosed() { return closed; }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                for (Identifier id : owned) {
                    client.getTextureManager().destroyTexture(id);
                }
            }
        }
    }

    /** Opens a profile directly even when disabled. Missing/broken profiles still produce vanilla previews. */
    public static Data load(MinecraftClient client, String packId) {
        if (client == null || packId == null) {
            throw new IllegalArgumentException("client and packId are required");
        }
        Loader loader = new Loader(client, packId);
        ResourcePack pack = null;
        try {
            ResourcePackProfile profile = client.getResourcePackManager().getProfile(packId);
            if (profile != null) {
                try {
                    pack = profile.createResourcePack();
                } catch (RuntimeException e) {
                    loader.note("Could not open inspected profile: " + e.getClass().getSimpleName());
                }
            } else {
                loader.note("Profile not found; using vanilla assets");
            }
            loader.pack = pack;
            return loader.load();
        } catch (RuntimeException | Error e) {
            loader.destroy();
            throw e;
        } finally {
            // The shared DefaultResourcePack belongs to MinecraftClient, not this handle.
            if (pack != null && pack != loader.vanilla) {
                try {
                    pack.close();
                } catch (RuntimeException ignored) {
                    // A zip close failure must not discard a successfully created texture owner.
                }
            }
        }
    }

    private static final class Loader {
        private final MinecraftClient client;
        private final String packId;
        private final DefaultResourcePack vanilla;
        private final String texturePrefix = "pack_preview_assets/" + SERIAL.incrementAndGet() + "/";
        private final TreeMap<Identifier, InputSupplier<InputStream>> resources = new TreeMap<>(Comparator.comparing(Identifier::toString));
        private final Map<Integer, Integer> widths = new TreeMap<>();
        private final Set<String> measured = new HashSet<>();
        private final List<String> diagnostics = new ArrayList<>();
        private final List<Identifier> owned = new ArrayList<>();
        private ResourcePack pack;
        private long customPixels;
        private long customBytesRead;
        private long textureBytes;
        private boolean animations;
        private boolean lightmap;
        private boolean sounds;

        private Loader(MinecraftClient client, String packId) {
            this.client = client;
            this.packId = packId;
            this.vanilla = client.getDefaultResourcePack();
        }

        private Loader(ResourcePack pack) {
            this.client = null;
            this.packId = "feature-inspection";
            this.vanilla = null;
            this.pack = pack;
        }

        private Data load() {
            scan();
            // Required icons take priority over the optional block/resolution scan's decode budget.
            Map<String, Sprite> items = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : PackPreviewAssetLogic.itemPaths().entrySet()) {
                try (Image image = preferred(Identifier.ofVanilla(entry.getValue()), true)) {
                    items.put(entry.getKey(), register(image));
                }
            }
            List<Sprite> hearts = loadHearts();
            Map<String, Sprite> particles = new LinkedHashMap<>();
            for (String name : List.of("critical_hit", "enchanted_hit")) {
                try (Image image = preferred(Identifier.ofVanilla("textures/particle/" + name + ".png"), false)) {
                    particles.put(name, register(image));
                }
            }
            detectFeatures();
            List<List<Sprite>> blocks = new ArrayList<>();
            int changed = loadBlocks(blocks);
            measureOtherItems();
            return new Data(this, blocks, items, hearts, particles, changed);
        }

        private void note(String message) {
            if (diagnostics.contains(message)) {
                return;
            }
            if (message.contains("limited to") || message.contains("budget reached") || message.startsWith("Cycling retains")) {
                diagnostics.add(0, message);
                if (diagnostics.size() > 32) {
                    diagnostics.removeLast();
                }
            } else if (diagnostics.size() < 32) {
                diagnostics.add(message);
            }
        }

        private void scan() {
            if (pack == null) {
                return;
            }
            try {
                List<String> namespaces = pack.getNamespaces(TYPE).stream().sorted().toList();
                if (namespaces.size() > MAX_NAMESPACES) {
                    note("Namespace scan limited to " + MAX_NAMESPACES);
                }
                for (String namespace : namespaces.subList(0, Math.min(namespaces.size(), MAX_NAMESPACES))) {
                    // Actual API: the second argument is a namespace, the third is a path prefix.
                    try {
                        ResourcePack.ResultConsumer collect = (id, supplier) -> {
                            if (!relevant(id.getPath())) {
                                return;
                            }
                            if (resources.size() < MAX_RESOURCES || resources.containsKey(id)) {
                                resources.put(id, supplier);
                            } else {
                                note("Resource index limited to " + MAX_RESOURCES + "; feature detection may be partial");
                                // Keep the lexicographically first IDs, independent of ZIP/directory enumeration order.
                                if (resources.comparator().compare(id, resources.lastKey()) < 0) {
                                    resources.pollLastEntry();
                                    resources.put(id, supplier);
                                }
                            }
                        };
                        // PathResourcePack rejects an empty path prefix. Enumerate
                        // supported directories explicitly for folder AND ZIP packs.
                        for (String prefix : List.of("textures", "sounds", "optifine/lightmap", "mcpatcher/lightmap")) {
                            pack.findResources(TYPE, namespace, prefix, collect);
                        }
                        Identifier soundsJson = Identifier.of(namespace, "sounds.json");
                        InputSupplier<InputStream> definitions = pack.open(TYPE, soundsJson);
                        if (definitions != null) {
                            collect.accept(soundsJson, definitions);
                        }
                    } catch (RuntimeException e) {
                        note("Partial namespace scan (" + namespace + "): " + e.getClass().getSimpleName());
                    }
                }
            } catch (RuntimeException e) {
                note("Partial resource scan: " + e.getClass().getSimpleName());
            }
        }

        private boolean relevant(String path) {
            return path.startsWith("textures/") || path.startsWith("sounds/") || path.equals("sounds.json")
                    || path.startsWith("optifine/lightmap/") || path.startsWith("mcpatcher/lightmap/");
        }

        private void destroy() {
            for (Identifier id : owned) {
                client.getTextureManager().destroyTexture(id);
            }
            owned.clear();
        }

        private int loadBlocks(List<List<Sprite>> batches) {
            Map<String, Identifier> candidates = new TreeMap<>();
            for (Identifier id : resources.keySet()) {
                String path = PackPreviewAssetLogic.canonicalPath(id.getPath());
                if (!path.startsWith("textures/block/") || !path.endsWith(".png")) {
                    continue;
                }
                String key = id.getNamespace() + ":" + path;
                candidates.putIfAbsent(key, Identifier.of(key));
            }
            List<Image> selected = new ArrayList<>();
            int changed = 0;
            int inspected = 0;
            try {
                for (String key : PackPreviewAssetLogic.shuffled(new ArrayList<>(candidates.keySet()), packId)) {
                    if (inspected++ == MAX_CANDIDATES) {
                        note("Block comparison limited to " + MAX_CANDIDATES + " candidates");
                        break;
                    }
                    Identifier id = candidates.get(key);
                    try (Raw custom = customRaw(id)) {
                        if (custom == null) {
                            continue;
                        }
                        rememberWidth(custom);
                        boolean different;
                        Identifier canonical = Identifier.of(key);
                        try (Raw base = raw(canonical, false)) {
                            different = base == null || !Arrays.equals(custom.encoded, base.encoded)
                                    && !PackPreviewAssetLogic.samePixels(custom.pixels.getWidth(), custom.pixels.getHeight(),
                                    custom.pixels.copyPixelsArgb(), base.pixels.getWidth(), base.pixels.getHeight(),
                                    base.pixels.copyPixelsArgb());
                        }
                        if (different) {
                            changed++;
                            if (selected.size() < MAX_BLOCK_SPRITES) {
                                selected.add(frame(custom, true));
                            }
                        }
                    }
                }
                if (changed < 4) {
                    // Deliberately vanilla: one to three changed blocks must not leak into this fixed set.
                    List<Sprite> fixed = new ArrayList<>(4);
                    for (String name : FIXED_BLOCKS) {
                        try (Image image = fallback(Identifier.ofVanilla("textures/block/" + name + ".png"))) {
                            fixed.add(register(image));
                        }
                    }
                    batches.add(fixed);
                } else {
                    List<Sprite> sprites = new ArrayList<>();
                    for (Image image : selected) {
                        sprites.add(register(image));
                    }
                    batches.addAll(PackPreviewAssetLogic.blockBatches(sprites));
                    if (changed > MAX_BLOCK_SPRITES) {
                        note("Cycling retains " + MAX_BLOCK_SPRITES + " of " + changed + " verified changed blocks");
                    }
                }
            } finally {
                selected.forEach(Image::close);
            }
            return changed;
        }

        private Image preferred(Identifier canonical, boolean item) {
            try (Raw raw = customRaw(canonical)) {
                if (raw != null) {
                    if (item) {
                        rememberWidth(raw);
                    }
                    return frame(raw, true);
                }
            }
            return fallback(canonical);
        }

        private Raw customRaw(Identifier canonical) {
            for (String path : PackPreviewAssetLogic.aliases(canonical.getPath())) {
                Raw raw = raw(Identifier.of(canonical.getNamespace(), path), true);
                if (raw != null) {
                    return raw;
                }
            }
            return null;
        }

        private Image fallback(Identifier id) {
            try (Raw raw = raw(id, false)) {
                if (raw != null) {
                    return frame(raw, false);
                }
            }
            note("Vanilla asset unavailable: " + id + "; generated placeholder");
            NativeImage image = new NativeImage(16, 16, true);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    image.setColorArgb(x, y, ((x / 4 + y / 4) & 1) == 0 ? 0xFFFF00FF : 0xFF151515);
                }
            }
            return new Image(image, 16, 16, false);
        }

        private void rememberWidth(Raw raw) {
            String canonical = raw.id.getNamespace() + ":" + PackPreviewAssetLogic.canonicalPath(raw.id.getPath());
            if (measured.add(canonical)) {
                widths.merge(raw.frame.width, 1, Integer::sum);
            }
        }

        private void measureOtherItems() {
            int count = 0;
            for (Identifier id : resources.keySet()) {
                String path = PackPreviewAssetLogic.canonicalPath(id.getPath());
                if (path.startsWith("textures/item/") && path.endsWith(".png")
                        && !measured.contains(id.getNamespace() + ":" + path)) {
                    if (count++ == MAX_CANDIDATES) {
                        note("Item resolution scan limited to " + MAX_CANDIDATES + " candidates");
                        break;
                    }
                    // Resolve modern before legacy aliases, independently of enumeration order.
                    for (String alias : PackPreviewAssetLogic.aliases(path)) {
                        try (Raw raw = raw(Identifier.of(id.getNamespace(), alias), true)) {
                            if (raw != null) {
                                rememberWidth(raw);
                                break;
                            }
                        }
                    }
                }
            }
        }

        private Raw raw(Identifier id, boolean custom) {
            if (custom && customPixels >= MAX_CUSTOM_PIXELS) {
                note("Custom PNG decode budget reached (64 million pixels); detection/resolution may be partial");
                return null;
            }
            byte[] bytes = bytes(id, custom, MAX_PNG_BYTES);
            if (bytes == null) {
                return null;
            }
            int[] size = pngSize(bytes);
            if (size == null) {
                note("Invalid or oversized PNG: " + id);
                return null;
            }
            long pixels = (long) size[0] * size[1];
            if (custom && customPixels + pixels > MAX_CUSTOM_PIXELS) {
                note("Custom PNG decode budget reached (64 million pixels); detection/resolution may be partial");
                return null;
            }
            if (custom) {
                customPixels += pixels;
            }
            try {
                NativeImage image = NativeImage.read(bytes);
                try {
                    return new Raw(id, image, bytes, frameInfo(id, custom, image.getWidth(), image.getHeight()));
                } catch (RuntimeException e) {
                    image.close();
                    throw e;
                }
            } catch (IOException | RuntimeException e) {
                note("Unreadable PNG: " + id);
                return null;
            }
        }

        private byte[] bytes(Identifier id, boolean custom, int limit) {
            ResourcePack source = custom ? pack : vanilla;
            if (source == null) {
                return null;
            }
            long remaining = MAX_CUSTOM_BYTES - customBytesRead;
            if (custom && remaining <= 0) {
                note("Custom resource read budget reached (128MiB); detection/resolution may be partial");
                return null;
            }
            try {
                InputSupplier<InputStream> supplier = custom ? resources.get(id) : null;
                if (supplier == null) {
                    supplier = source.open(TYPE, id);
                }
                if (supplier == null) {
                    return null;
                }
                try (InputStream input = supplier.get()) {
                    int readLimit = custom ? (int) Math.min(limit, remaining) : limit;
                    byte[] bytes = input.readNBytes(readLimit + 1);
                    if (custom) {
                        customBytesRead += bytes.length;
                    }
                    if (bytes.length > readLimit) {
                        if (readLimit < limit) {
                            note("Custom resource read budget reached (128MiB); detection/resolution may be partial");
                        } else {
                            note("Resource exceeds byte limit: " + id);
                        }
                        return null;
                    }
                    return bytes;
                }
            } catch (IOException | RuntimeException e) {
                note("Unreadable resource: " + id);
                return null;
            }
        }

        private JsonObject json(Identifier id, boolean custom) {
            byte[] bytes = bytes(id, custom, MAX_JSON_BYTES);
            if (bytes == null) {
                return null;
            }
            try {
                String text = new String(bytes, StandardCharsets.UTF_8);
                // JsonParser itself accepts lenient JSON even with older Gson versions.
                // Validate tokens strictly first, so comments/trailing values/bare names do not count.
                try (JsonReader validation = new JsonReader(new StringReader(text))) {
                    validation.setLenient(false);
                    validation.skipValue();
                    if (validation.peek() != JsonToken.END_DOCUMENT) {
                        note("Invalid JSON: " + id);
                        return null;
                    }
                }
                JsonElement json = JsonParser.parseString(text);
                return json.isJsonObject() ? json.getAsJsonObject() : null;
            } catch (IOException | RuntimeException | StackOverflowError e) {
                note("Invalid JSON: " + id);
                return null;
            }
        }

        private Frame frameInfo(Identifier id, boolean custom, int width, int height) {
            JsonObject metadata = json(Identifier.of(id.getNamespace(), id.getPath() + ".mcmeta"), custom);
            JsonObject animation = animation(metadata);
            int frameWidth = width;
            int frameHeight = height;
            if (animation != null) {
                if (!validFrameGeometry(animation, width, height)) {
                    note("Invalid animation frame geometry/index: " + id);
                    return new Frame(0, 0, width, height);
                }
                if (animation.has("width")) {
                    frameWidth = animation.get("width").getAsInt();
                    frameHeight = animation.has("height") ? animation.get("height").getAsInt() : height;
                } else if (animation.has("height")) {
                    frameHeight = animation.get("height").getAsInt();
                } else {
                    frameWidth = frameHeight = Math.min(width, height);
                }
                if (frameWidth > width || frameHeight > height || width % frameWidth != 0 || height % frameHeight != 0) {
                    note("Invalid animation frame geometry: " + id);
                    return new Frame(0, 0, width, height);
                }
                int first = 0;
                if (animation.has("frames") && !animation.getAsJsonArray("frames").isEmpty()) {
                    JsonElement entry = animation.getAsJsonArray("frames").get(0);
                    first = entry.isJsonObject() ? entry.getAsJsonObject().get("index").getAsInt() : entry.getAsInt();
                }
                int columns = width / frameWidth;
                int frameCount = columns * (height / frameHeight);
                if (first >= frameCount) {
                    note("Animation frame index out of range: " + id);
                    first = 0;
                }
                return new Frame(first % columns * frameWidth, first / columns * frameHeight, frameWidth, frameHeight);
            }
            return new Frame(0, 0, frameWidth, frameHeight);
        }

        private Image frame(Raw raw, boolean custom) {
            Frame f = raw.frame;
            double scale = Math.min(1.0, (double) MAX_SPRITE_SIZE / Math.max(f.width, f.height));
            int width = Math.max(1, (int) Math.round(f.width * scale));
            int height = Math.max(1, (int) Math.round(f.height * scale));
            NativeImage result = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    result.setColorArgb(x, y, raw.pixels.getColorArgb(f.x + x * f.width / width, f.y + y * f.height / height));
                }
            }
            return new Image(result, f.width, f.height, custom);
        }

        private Sprite register(Image image) {
            // Copy ownership: callers can always close their Image, even if upload fails.
            NativeImage copy = new NativeImage(image.pixels.getWidth(), image.pixels.getHeight(), true);
            copy.copyFrom(image.pixels);
            NativeImageBackedTexture texture = null;
            Identifier id = Identifier.of("phaze", texturePrefix + owned.size());
            try {
                texture = new NativeImageBackedTexture(copy);
                client.getTextureManager().registerTexture(id, texture);
                owned.add(id);
                textureBytes += (long) copy.getWidth() * copy.getHeight() * 4;
                return new Sprite(id, copy.getWidth(), copy.getHeight(), image.originalWidth, image.originalHeight, image.custom);
            } catch (RuntimeException | Error e) {
                if (texture != null) {
                    texture.close();
                } else {
                    copy.close();
                }
                throw e;
            }
        }

        private List<Sprite> loadHearts() {
            List<Sprite> result = new ArrayList<>(3);
            try (Image container = heartLayer("container", 16);
                 Image full = heartLayer("full", 52);
                 Image half = heartLayer("half", 61)) {
                try (Image composed = compose(container, full)) {
                    result.add(register(composed));
                }
                try (Image composed = compose(container, half)) {
                    result.add(register(composed));
                }
                result.add(register(container));
            }
            return result;
        }

        private Image heartLayer(String name, int legacyX) {
            Identifier modern = Identifier.ofVanilla("textures/gui/sprites/hud/heart/" + name + ".png");
            try (Raw custom = raw(modern, true)) {
                if (custom != null) {
                    return frame(custom, true);
                }
            }
            // Classic icons.png: empty (16,0), full (52,0), half (61,0), each 9x9 on a 256x256 sheet.
            try (Raw atlas = raw(Identifier.ofVanilla("textures/gui/icons.png"), true)) {
                if (atlas != null && atlas.pixels.getWidth() % 256 == 0 && atlas.pixels.getHeight() % 256 == 0) {
                    int sx = atlas.pixels.getWidth() / 256;
                    int sy = atlas.pixels.getHeight() / 256;
                    Raw crop = new Raw(atlas.id, atlas.pixels, atlas.encoded, new Frame(legacyX * sx, 0, 9 * sx, 9 * sy));
                    return frame(crop, true);
                }
            }
            return fallback(modern);
        }

        private Image compose(Image container, Image fill) {
            int width = Math.max(container.pixels.getWidth(), fill.pixels.getWidth());
            int height = Math.max(container.pixels.getHeight(), fill.pixels.getHeight());
            NativeImage result = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int background = container.pixels.getColorArgb(x * container.pixels.getWidth() / width,
                            y * container.pixels.getHeight() / height);
                    int foreground = fill.pixels.getColorArgb(x * fill.pixels.getWidth() / width,
                            y * fill.pixels.getHeight() / height);
                    result.setColorArgb(x, y, PackPreviewAssetLogic.over(background, foreground));
                }
            }
            return new Image(result, Math.max(container.originalWidth, fill.originalWidth),
                    Math.max(container.originalHeight, fill.originalHeight), container.custom || fill.custom);
        }

        private void detectFeatures() {
            int metadataCount = 0;
            for (Identifier id : resources.keySet()) {
                String path = id.getPath();
                if (!sounds && path.startsWith("sounds/") && path.endsWith(".ogg")) {
                    byte[] header = header(id, 4);
                    sounds = header != null && Arrays.equals(header, new byte[]{'O', 'g', 'g', 'S'});
                }
                if (!sounds && path.equals("sounds.json")) {
                    sounds = soundDefinitions(json(id, true));
                }
                if (!lightmap && path.endsWith(".png") && (path.startsWith("optifine/lightmap/")
                        || path.startsWith("mcpatcher/lightmap/") || path.equals("textures/misc/lightmap.png"))) {
                    lightmap = pngSize(header(id, 24)) != null;
                }
                if (!animations && path.startsWith("textures/") && path.endsWith(".png.mcmeta")) {
                    if (metadataCount++ >= MAX_CANDIDATES) {
                        note("Animation metadata scan limited to " + MAX_CANDIDATES);
                        continue;
                    }
                    Identifier png = Identifier.of(id.getNamespace(), path.substring(0, path.length() - 7));
                    int[] size = pngSize(header(png, 24));
                    if (size != null) {
                        JsonObject anim = animation(json(id, true));
                        animations = anim != null && validFrameGeometry(anim, size[0], size[1]);
                    }
                }
            }
        }

        private byte[] header(Identifier id, int size) {
            if (pack == null) {
                return null;
            }
            try {
                InputSupplier<InputStream> supplier = resources.get(id);
                if (supplier == null) {
                    supplier = pack.open(TYPE, id);
                }
                if (supplier != null) {
                    try (InputStream stream = supplier.get()) {
                        return stream.readNBytes(size);
                    }
                }
            } catch (IOException | RuntimeException e) {
                note("Unreadable resource header: " + id);
            }
            return null;
        }
    }

    private static int[] pngSize(byte[] bytes) {
        if (bytes == null || bytes.length < 24 || bytes[0] != (byte) 137 || bytes[1] != 'P'
                || bytes[2] != 'N' || bytes[3] != 'G' || bytes[4] != 13 || bytes[5] != 10
                || bytes[6] != 26 || bytes[7] != 10 || bytes[12] != 'I' || bytes[13] != 'H'
                || bytes[14] != 'D' || bytes[15] != 'R') {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt(8) != 13) {
            return null;
        }
        int width = buffer.getInt(16);
        int height = buffer.getInt(20);
        return width > 0 && height > 0 && width <= 8192 && height <= 8192
                && (long) width * height <= MAX_IMAGE_PIXELS ? new int[]{width, height} : null;
    }

    private static boolean integer(JsonElement element, int minimum) {
        try {
            return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                    && element.getAsBigDecimal().intValueExact() >= minimum;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** An actual animation object, not merely a .mcmeta filename or unrelated texture metadata. */
    private static JsonObject animation(JsonObject metadata) {
        if (metadata == null || !metadata.has("animation") || !metadata.get("animation").isJsonObject()) {
            return null;
        }
        JsonObject animation = metadata.getAsJsonObject("animation");
        for (String key : List.of("width", "height", "frametime")) {
            if (animation.has(key) && !integer(animation.get(key), 1)) {
                return null;
            }
        }
        if (animation.has("interpolate") && (!animation.get("interpolate").isJsonPrimitive()
                || !animation.getAsJsonPrimitive("interpolate").isBoolean())) {
            return null;
        }
        if (animation.has("frames")) {
            if (!animation.get("frames").isJsonArray()) {
                return null;
            }
            for (JsonElement entry : animation.getAsJsonArray("frames")) {
                if (entry.isJsonObject()) {
                    JsonObject frame = entry.getAsJsonObject();
                    if (!integer(frame.get("index"), 0) || frame.has("time") && !integer(frame.get("time"), 1)) {
                        return null;
                    }
                } else if (!integer(entry, 0)) {
                    return null;
                }
            }
        }
        return animation;
    }

    private static boolean validFrameGeometry(JsonObject animation, int width, int height) {
        int fw = animation.has("width") ? animation.get("width").getAsInt()
                : animation.has("height") ? width : Math.min(width, height);
        int fh = animation.has("height") ? animation.get("height").getAsInt()
                : animation.has("width") ? height : Math.min(width, height);
        if (fw > width || fh > height || width % fw != 0 || height % fh != 0) {
            return false;
        }
        int count = width / fw * (height / fh);
        if (animation.has("frames")) {
            for (JsonElement entry : animation.getAsJsonArray("frames")) {
                int index = entry.isJsonObject() ? entry.getAsJsonObject().get("index").getAsInt() : entry.getAsInt();
                if (index >= count) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Nonempty valid event definitions count even if their files/referenced events live in vanilla. */
    private static boolean soundDefinitions(JsonObject definitions) {
        if (definitions == null) {
            return false;
        }
        for (Map.Entry<String, JsonElement> entry : definitions.entrySet()) {
            if (entry.getKey().isBlank() || Identifier.tryParse(entry.getKey()) == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject event = entry.getValue().getAsJsonObject();
            if (!event.has("sounds") || !event.get("sounds").isJsonArray()) {
                continue;
            }
            if (event.has("replace") && (!event.get("replace").isJsonPrimitive()
                    || !event.getAsJsonPrimitive("replace").isBoolean())) {
                continue;
            }
            if (event.has("subtitle") && (!event.get("subtitle").isJsonPrimitive()
                    || !event.getAsJsonPrimitive("subtitle").isString())) {
                continue;
            }
            JsonArray sounds = event.getAsJsonArray("sounds");
            boolean valid = !sounds.isEmpty();
            for (JsonElement sound : sounds) {
                if (!validSound(sound)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static boolean validSound(JsonElement sound) {
        JsonElement name = sound;
        if (sound.isJsonObject()) {
            JsonObject object = sound.getAsJsonObject();
            name = object.get("name");
            if (object.has("type")) {
                JsonElement type = object.get("type");
                if (!type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()
                        || !List.of("file", "event").contains(type.getAsString())) {
                    return false;
                }
            }
            if (object.has("weight") && !integer(object.get("weight"), 1)) {
                return false;
            }
            if (object.has("attenuation_distance") && !integer(object.get("attenuation_distance"), 0)) {
                return false;
            }
            for (String key : List.of("stream", "preload")) {
                if (object.has(key) && (!object.get(key).isJsonPrimitive()
                        || !object.getAsJsonPrimitive(key).isBoolean())) {
                    return false;
                }
            }
            for (String key : List.of("volume", "pitch")) {
                if (object.has(key)) {
                    JsonElement value = object.get(key);
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                            || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() <= 0) {
                        return false;
                    }
                }
            }
        }
        return name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
                && !name.getAsString().isBlank() && Identifier.tryParse(name.getAsString()) != null;
    }

    private record Frame(int x, int y, int width, int height) {
    }

    private record Raw(Identifier id, NativeImage pixels, byte[] encoded, Frame frame) implements AutoCloseable {
        @Override
        public void close() {
            pixels.close();
        }
    }

    private record Image(NativeImage pixels, int originalWidth, int originalHeight, boolean custom)
            implements AutoCloseable {
        @Override
        public void close() {
            pixels.close();
        }
    }
}
