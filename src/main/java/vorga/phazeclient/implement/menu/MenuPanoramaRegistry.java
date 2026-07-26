package vorga.phazeclient.implement.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runtime registry for built-in + user-supplied panorama zip packs.
 *
 * <p>User panoramas live in {@code <gameDir>/Phaze/Panoramas}
 * and follow the same simple archive shape as panorama mods:
 * {@code panorama_0.png .. panorama_5.png} plus optional
 * {@code icon.png} for the preview card.</p>
 */
public final class MenuPanoramaRegistry {
    private static final String ROOT_DIR = "Phaze";
    private static final String PANORAMAS_DIR = "Panoramas";
    private static final long RESCAN_INTERVAL_MS = 1000L;

    private static final Map<String, CustomPanoramaDescriptor> CUSTOM_PANORAMAS = new LinkedHashMap<>();
    private static final Map<String, RemotePanoramaDescriptor> REMOTE_PANORAMAS = new LinkedHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private static volatile boolean loaded = false;
    private static volatile long lastFolderSignature = Long.MIN_VALUE;
    private static volatile long lastRefreshCheckMs = 0L;

    private MenuPanoramaRegistry() {
    }

    static {
        registerRemote(new RemotePanoramaDescriptor(
                "remote:chateau",
                "Chateau",
                "chateau.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v1/chateau.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v1/chateau-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:castle",
                "Castle",
                "castle.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v1/castle.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v1/castle-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:post_soviet_night",
                "Post-Soviet Night",
                "post-soviet-night.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v1/post-soviet-night.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v1/post-soviet-night-preview.png"
        ));
    }

    public static synchronized void ensureDirectoryExists() {
        try {
            Files.createDirectories(getPanoramasDirectory());
        } catch (IOException e) {
            System.err.println("[Phaze] failed to create panoramas directory: " + e);
        }
    }

    public static Path getPanoramasDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ROOT_DIR).resolve(PANORAMAS_DIR);
    }

    public static List<MenuUiSettings.PanoramaDescriptor> getAllPresets() {
        maybeRefresh();
        List<MenuUiSettings.PanoramaDescriptor> presets = new ArrayList<>();
        presets.addAll(Arrays.asList(MenuUiSettings.PanoramaPreset.values()));
        for (RemotePanoramaDescriptor remote : REMOTE_PANORAMAS.values()) {
            if (!remote.isDownloaded()) {
                presets.add(remote);
            }
        }
        presets.addAll(CUSTOM_PANORAMAS.values());
        return presets;
    }

    public static synchronized MenuUiSettings.PanoramaDescriptor findById(String id) {
        maybeRefresh();
        if (id != null) {
            for (MenuUiSettings.PanoramaPreset preset : MenuUiSettings.PanoramaPreset.values()) {
                if (preset.getId().equalsIgnoreCase(id)) {
                    return preset;
                }
            }
            CustomPanoramaDescriptor custom = CUSTOM_PANORAMAS.get(id.toLowerCase(Locale.ROOT));
            if (custom != null) {
                return custom;
            }
            RemotePanoramaDescriptor remote = REMOTE_PANORAMAS.get(id.toLowerCase(Locale.ROOT));
            if (remote != null) {
                return remote;
            }
        }
        return MenuUiSettings.PanoramaPreset.VANILLA;
    }

    public static boolean isRemotePanorama(String id) {
        return id != null && REMOTE_PANORAMAS.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public static void activateRemotePanorama(String id) {
        if (id == null) return;
        RemotePanoramaDescriptor remote = REMOTE_PANORAMAS.get(id.toLowerCase(Locale.ROOT));
        if (remote != null) remote.activate();

        // --- Minecraft version panoramas ---------------------------
        // Mirrored from Modrinth under their original licenses; see
        // THIRD_PARTY_LICENSES.md for authors and terms. Ordered
        // oldest-first so the picker reads as a timeline.
        registerRemote(new RemotePanoramaDescriptor(
                "remote:legacy",
                "Legacy",
                "legacy-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/legacy-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/legacy-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:classic",
                "Classic",
                "classic-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/classic-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/classic-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_13",
                "1.13",
                "1-13-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-13-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-13-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_15",
                "1.15",
                "1-15-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-15-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-15-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_16",
                "1.16",
                "1-16-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-16-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-16-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_17",
                "1.17",
                "1-17-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-17-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-17-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_18",
                "1.18",
                "1-18-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-18-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-18-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_19",
                "1.19",
                "1-19-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-19-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-19-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_19_night",
                "1.19 Night",
                "1-19-night-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-19-night-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-19-night-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_20",
                "1.20 Trails & Tales",
                "1-20-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-20-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-20-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_20_night",
                "1.20 Night",
                "1-20-night-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-20-night-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-20-night-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_21",
                "1.21",
                "1-21-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_21_5",
                "1.21.5 Spring to Life",
                "1-21-5-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-5-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-5-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_21_6",
                "1.21.6 Chase the Skies",
                "1-21-6-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-6-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-6-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_21_9",
                "1.21.9",
                "1-21-9-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-9-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-9-panorama-preview.png"
        ));
        registerRemote(new RemotePanoramaDescriptor(
                "remote:mc_1_21_11",
                "1.21.11 Mounts of Mayhem",
                "1-21-11-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-11-panorama.zip",
                "https://github.com/Nomadvorga/phaze-client/releases/download/panorama-assets-v2/1-21-11-panorama-preview.png"
        ));
    }

    private static void registerRemote(RemotePanoramaDescriptor panorama) {
        REMOTE_PANORAMAS.put(panorama.getId().toLowerCase(Locale.ROOT), panorama);
    }

    public static synchronized List<MenuUiSettings.PanoramaDescriptor> importArchives(List<Path> paths) {
        ensureDirectoryExists();
        List<String> importedIds = new ArrayList<>();
        for (Path path : paths) {
            if (path == null || !Files.isRegularFile(path)) continue;
            String fileName = path.getFileName().toString();
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;

            try {
                Path target = resolveImportTarget(path);
                Path sourceAbsolute = path.toAbsolutePath().normalize();
                Path targetAbsolute = target.toAbsolutePath().normalize();
                if (!sourceAbsolute.equals(targetAbsolute)) {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
                importedIds.add(customIdForArchiveName(target.getFileName().toString()));
            } catch (Throwable t) {
                System.err.println("[Phaze] failed to import panorama archive " + path + ": " + t);
            }
        }

        if (importedIds.isEmpty()) {
            return List.of();
        }

        reload();
        List<MenuUiSettings.PanoramaDescriptor> imported = new ArrayList<>();
        for (String id : importedIds) {
            imported.add(findById(id));
        }
        return imported;
    }

    /**
     * Brings the registry in line with what is on disk, touching only
     * what actually changed.
     *
     * <p>This used to close every descriptor and rebuild the whole map
     * from scratch. That is very visible: loading a descriptor decodes
     * its preview PNG - a multi-megabyte image - and it happens on the
     * client thread, so one new archive re-decoded every installed
     * panorama. Downloading a pack made the entire grid of previews
     * blink and stalled the menu for as long as the decode took, and
     * anything still holding a texture id across the teardown logged
     * "Missing resource ... panorama_N.png".
     *
     * <p>Now each archive is keyed by path plus size and timestamp:
     * unchanged entries are left alone, entries whose file changed are
     * reloaded, and only genuinely removed ones are closed.
     */
    public static synchronized void reload() {
        ensureDirectoryExists();
        MinecraftClient client = MinecraftClient.getInstance();

        List<Path> archives = listArchives();
        Map<String, CustomPanoramaDescriptor> next = new LinkedHashMap<>();

        for (Path archive : archives) {
            String key = archive.getFileName().toString().toLowerCase(Locale.ROOT);
            String id = customIdForArchiveName(archive.getFileName().toString()).toLowerCase(Locale.ROOT);
            CustomPanoramaDescriptor existing = CUSTOM_PANORAMAS.get(id);
            long stamp = archiveStamp(archive);

            if (existing != null && existing.archiveStamp == stamp) {
                // Same file, byte for byte as far as the filesystem is
                // concerned - keep the live textures.
                next.put(id, existing);
                continue;
            }

            if (existing != null) {
                existing.close(client);
            }
            try {
                CustomPanoramaDescriptor panorama = CustomPanoramaDescriptor.load(archive, client);
                panorama.archiveStamp = stamp;
                next.put(panorama.getId().toLowerCase(Locale.ROOT), panorama);
            } catch (Throwable t) {
                System.err.println("[Phaze] skipped invalid panorama archive " + key + ": " + t.getMessage());
            }
        }

        // Whatever survived from the previous map but is not in the new
        // one no longer exists on disk.
        for (Map.Entry<String, CustomPanoramaDescriptor> entry : CUSTOM_PANORAMAS.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                entry.getValue().close(client);
            }
        }

        CUSTOM_PANORAMAS.clear();
        CUSTOM_PANORAMAS.putAll(next);

        loaded = true;
        lastFolderSignature = computeFolderSignature(archives);
        lastRefreshCheckMs = System.currentTimeMillis();
    }

    /** Size + last-modified, the same pair the folder signature uses. */
    private static long archiveStamp(Path archive) {
        try {
            return mix(Files.size(archive), Files.getLastModifiedTime(archive).toMillis());
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    public static synchronized boolean deleteCustomPanorama(String id) {
        maybeRefresh();
        if (id == null) {
            return false;
        }

        String normalizedId = id.toLowerCase(Locale.ROOT);
        CustomPanoramaDescriptor panorama = CUSTOM_PANORAMAS.remove(normalizedId);
        if (panorama == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        panorama.close(client);
        try {
            boolean deleted = Files.deleteIfExists(panorama.archivePath);
            if (!deleted) {
                CUSTOM_PANORAMAS.put(normalizedId, panorama);
                panorama.reloadClientTextures(client);
                return false;
            }
            List<Path> archives = listArchives();
            lastFolderSignature = computeFolderSignature(archives);
            lastRefreshCheckMs = System.currentTimeMillis();
            loaded = true;
            return true;
        } catch (IOException e) {
            CUSTOM_PANORAMAS.put(normalizedId, panorama);
            panorama.reloadClientTextures(client);
            System.err.println("[Phaze] failed to delete panorama archive " + panorama.archivePath.getFileName() + ": " + e);
            return false;
        }
    }

    public static synchronized void onResourcesReloaded() {
        if (!loaded) return;
        MinecraftClient client = MinecraftClient.getInstance();
        for (CustomPanoramaDescriptor panorama : CUSTOM_PANORAMAS.values()) {
            panorama.reloadClientTextures(client);
        }
    }

    private static synchronized void maybeRefresh() {
        long now = System.currentTimeMillis();
        if (!loaded) {
            reload();
            return;
        }
        if (now - lastRefreshCheckMs < RESCAN_INTERVAL_MS) {
            return;
        }

        List<Path> archives = listArchives();
        long signature = computeFolderSignature(archives);
        lastRefreshCheckMs = now;
        if (signature != lastFolderSignature) {
            reload();
        }
    }

    private static Path resolveImportTarget(Path source) throws IOException {
        Path directory = getPanoramasDirectory();
        Path sourceAbsolute = source.toAbsolutePath().normalize();
        if (sourceAbsolute.getParent() != null && sourceAbsolute.getParent().equals(directory.toAbsolutePath().normalize())) {
            return sourceAbsolute;
        }

        String originalName = sanitizeArchiveFileName(source.getFileName().toString());
        String baseName = originalName;
        String extension = ".zip";
        int dot = originalName.toLowerCase(Locale.ROOT).lastIndexOf(".zip");
        if (dot >= 0) {
            baseName = originalName.substring(0, dot);
        }

        Path candidate = directory.resolve(originalName);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + " (" + index++ + ")" + extension);
        }
        return candidate;
    }

    private static String sanitizeArchiveFileName(String fileName) {
        String safe = fileName == null ? "panorama.zip" : fileName.trim();
        if (safe.isEmpty()) safe = "panorama.zip";
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            safe = safe + ".zip";
        }
        return safe;
    }

    private static List<Path> listArchives() {
        ensureDirectoryExists();
        List<Path> archives = new ArrayList<>();
        try (var stream = Files.list(getPanoramasDirectory())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(archives::add);
        } catch (IOException e) {
            System.err.println("[Phaze] failed to list panoramas: " + e);
        }
        return archives;
    }

    private static long computeFolderSignature(List<Path> archives) {
        long signature = 1469598103934665603L;
        for (Path archive : archives) {
            try {
                FileTime modified = Files.getLastModifiedTime(archive);
                signature = mix(signature, archive.getFileName().toString().toLowerCase(Locale.ROOT).hashCode());
                signature = mix(signature, Files.size(archive));
                signature = mix(signature, modified.toMillis());
            } catch (IOException e) {
                signature = mix(signature, archive.getFileName().toString().hashCode());
            }
        }
        return signature;
    }

    private static long mix(long current, long value) {
        return (current ^ value) * 1099511628211L;
    }

    private static String customIdForArchiveName(String archiveName) {
        return "custom:" + archiveName.toLowerCase(Locale.ROOT);
    }

    private static String sanitizeTextureToken(String text) {
        String safe = text == null ? "panorama" : text.toLowerCase(Locale.ROOT);
        safe = safe.replaceAll("[^a-z0-9._-]", "_");
        while (safe.contains("__")) {
            safe = safe.replace("__", "_");
        }
        if (safe.isEmpty()) {
            safe = "panorama";
        }
        return safe;
    }

    private static String stripZipExtension(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private static ZipEntry findEntryIgnoreCase(ZipFile zip, String entryName) {
        String target = entryName.toLowerCase(Locale.ROOT);
        return zip.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).equals(target))
                .findFirst()
                .orElse(null);
    }

    private static NativeImage cropToSquare(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int size = Math.max(1, Math.min(width, height));
        if (width == size && height == size) {
            return image;
        }
        int offsetX = Math.max(0, (width - size) / 2);
        int offsetY = Math.max(0, (height - size) / 2);
        NativeImage square = new NativeImage(size, size, false);
        image.copyRect(square, offsetX, offsetY, 0, 0, size, size, false, false);
        image.close();
        return square;
    }

    private static final class CustomPanoramaDescriptor implements MenuUiSettings.PanoramaDescriptor {
        private final String id;
        private final String displayName;
        private final Path archivePath;
        private final Identifier cubeMapBase;
        private final Identifier previewTextureId;
        private final Identifier[] faceTextureIds;

        private MenuPanoramaRenderer renderer;
        private NativeImageBackedTexture previewTexture;
        private NativeImageBackedTexture[] faceTextures;
        private int previewTextureSize;
        private boolean facesLoaded;
        /** Size+mtime when this was loaded; lets a rescan skip it. */
        private long archiveStamp = Long.MIN_VALUE;

        private CustomPanoramaDescriptor(String id,
                                         String displayName,
                                         Path archivePath,
                                         Identifier cubeMapBase,
                                         Identifier previewTextureId,
                                         Identifier[] faceTextureIds) {
            this.id = id;
            this.displayName = displayName;
            this.archivePath = archivePath;
            this.cubeMapBase = cubeMapBase;
            this.previewTextureId = previewTextureId;
            this.faceTextureIds = faceTextureIds;
        }

        static CustomPanoramaDescriptor load(Path archivePath, MinecraftClient client) throws IOException {
            String archiveName = archivePath.getFileName().toString();
            String displayName = stripZipExtension(archiveName);
            String textureToken = sanitizeTextureToken(displayName) + "_" + Integer.toHexString(archiveName.toLowerCase(Locale.ROOT).hashCode());
            Identifier cubeMapBase = Identifier.of("phaze", "dynamic/panoramas/" + textureToken + "/panorama");
            Identifier previewTextureId = Identifier.of("phaze", "dynamic/panoramas/" + textureToken + "/preview");
            Identifier[] faceTextureIds = new Identifier[6];
            for (int i = 0; i < 6; i++) {
                faceTextureIds[i] = cubeMapBase.withPath(cubeMapBase.getPath() + "_" + i + ".png");
            }

            CustomPanoramaDescriptor descriptor = new CustomPanoramaDescriptor(
                    customIdForArchiveName(archiveName),
                    displayName,
                    archivePath,
                    cubeMapBase,
                    previewTextureId,
                    faceTextureIds
            );
            descriptor.validateArchive();
            descriptor.reloadClientTextures(client);
            return descriptor;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public Identifier previewTexture() {
            return previewTextureId;
        }

        @Override
        public int previewTextureSize() {
            return previewTextureSize;
        }

        @Override
        public int previewCropInset() {
            return 0;
        }

        @Override
        public int previewCropSize() {
            return previewTextureSize;
        }

        @Override
        public MenuPanoramaRenderer getRenderer() {
            if (renderer == null) {
                ensureFaceTexturesLoaded(MinecraftClient.getInstance());
                renderer = new MenuPanoramaRenderer(cubeMapBase);
            }
            return renderer;
        }

        @Override
        public boolean isCustom() {
            return true;
        }

        @Override
        public boolean hasPreviewTexture() {
            return previewTexture != null;
        }

        void reloadClientTextures(MinecraftClient client) {
            boolean reloadFaces = facesLoaded || renderer != null;
            close(client);
            loadPreviewTexture(client);
            if (reloadFaces) {
                ensureFaceTexturesLoaded(client);
            }
        }

        void close(MinecraftClient client) {
            TextureManager textureManager = client != null ? client.getTextureManager() : null;
            if (textureManager != null) {
                textureManager.destroyTexture(previewTextureId);
                for (Identifier faceTextureId : faceTextureIds) {
                    textureManager.destroyTexture(faceTextureId);
                }
            }
            if (previewTexture != null) {
                previewTexture.close();
                previewTexture = null;
            }
            if (faceTextures != null) {
                for (NativeImageBackedTexture faceTexture : faceTextures) {
                    if (faceTexture != null) {
                        faceTexture.close();
                    }
                }
                faceTextures = null;
            }
            renderer = null;
            facesLoaded = false;
        }

        private void validateArchive() throws IOException {
            try (ZipFile zip = new ZipFile(archivePath.toFile())) {
                for (int i = 0; i < 6; i++) {
                    if (findEntryIgnoreCase(zip, "panorama_" + i + ".png") == null) {
                        throw new IOException("missing panorama_" + i + ".png");
                    }
                }
            }
        }

        private void loadPreviewTexture(MinecraftClient client) {
            if (client == null || client.getTextureManager() == null) {
                previewTextureSize = 256;
                return;
            }
            try (ZipFile zip = new ZipFile(archivePath.toFile())) {
                ZipEntry previewEntry = findEntryIgnoreCase(zip, "icon.png");
                boolean usePanoramaFace = previewEntry == null;
                if (!usePanoramaFace) {
                    try (InputStream input = zip.getInputStream(previewEntry)) {
                        NativeImage icon = NativeImage.read(input);
                        usePanoramaFace = icon.getWidth() <= 1 || icon.getHeight() <= 1;
                        icon.close();
                    }
                }
                if (usePanoramaFace) previewEntry = findEntryIgnoreCase(zip, "panorama_0.png");
                if (previewEntry == null) {
                    throw new IOException("missing icon.png and panorama_0.png");
                }

                try (InputStream input = zip.getInputStream(previewEntry)) {
                    NativeImage preview = cropToSquare(NativeImage.read(input));
                    previewTextureSize = Math.max(1, preview.getWidth());
                    previewTexture = new NativeImageBackedTexture(preview);
                    client.getTextureManager().registerTexture(previewTextureId, previewTexture);
                }
            } catch (Throwable t) {
                previewTextureSize = 256;
                System.err.println("[Phaze] failed to load panorama preview " + archivePath.getFileName() + ": " + t);
            }
        }

        private void ensureFaceTexturesLoaded(MinecraftClient client) {
            if (facesLoaded || client == null || client.getTextureManager() == null) {
                return;
            }

            try (ZipFile zip = new ZipFile(archivePath.toFile())) {
                NativeImageBackedTexture[] loadedTextures = new NativeImageBackedTexture[6];
                for (int i = 0; i < 6; i++) {
                    ZipEntry faceEntry = findEntryIgnoreCase(zip, "panorama_" + i + ".png");
                    if (faceEntry == null) {
                        throw new IOException("missing panorama_" + i + ".png");
                    }
                    try (InputStream input = zip.getInputStream(faceEntry)) {
                        NativeImage face = NativeImage.read(input);
                        loadedTextures[i] = new NativeImageBackedTexture(face);
                        client.getTextureManager().registerTexture(faceTextureIds[i], loadedTextures[i]);
                    }
                }
                faceTextures = loadedTextures;
                facesLoaded = true;
            } catch (Throwable t) {
                if (faceTextures != null) {
                    for (NativeImageBackedTexture faceTexture : faceTextures) {
                        if (faceTexture != null) {
                            faceTexture.close();
                        }
                    }
                    faceTextures = null;
                }
                TextureManager textureManager = client.getTextureManager();
                if (textureManager != null) {
                    for (Identifier faceTextureId : faceTextureIds) {
                        textureManager.destroyTexture(faceTextureId);
                    }
                }
                System.err.println("[Phaze] failed to load panorama faces " + archivePath.getFileName() + ": " + t);
            }
        }
    }

    private static final class RemotePanoramaDescriptor implements MenuUiSettings.PanoramaDescriptor {
        private final String id;
        private final String displayName;
        private final String archiveName;
        private final URI downloadUri;
        private final URI previewUri;
        private final Identifier previewTextureId;
        private final AtomicBoolean downloading = new AtomicBoolean(false);
        private final AtomicBoolean previewLoading = new AtomicBoolean(false);
        private final AtomicLong downloadedBytes = new AtomicLong();
        private volatile long totalBytes = -1L;
        private NativeImageBackedTexture previewTexture;

        private RemotePanoramaDescriptor(String id, String displayName, String archiveName, String downloadUrl, String previewUrl) {
            this.id = id;
            this.displayName = displayName;
            this.archiveName = archiveName;
            this.downloadUri = URI.create(downloadUrl);
            this.previewUri = URI.create(previewUrl);
            this.previewTextureId = Identifier.of("phaze", "dynamic/remote-panoramas/" + sanitizeTextureToken(id) + "/preview");
        }

        @Override public String getId() { return id; }
        @Override public String displayName() { return displayName; }
        @Override public Identifier previewTexture() { ensurePreview(); return previewTextureId; }
        @Override public int previewTextureSize() { return 1; }
        @Override public int previewCropInset() { return 0; }
        @Override public int previewCropSize() { return 1; }
        @Override public MenuPanoramaRenderer getRenderer() { return MenuUiSettings.PanoramaPreset.VANILLA.getRenderer(); }
        @Override public boolean isRemote() { return true; }
        @Override public boolean isDownloading() { return downloading.get(); }
        @Override public boolean hasPreviewTexture() { return previewTexture != null; }
        @Override public float downloadProgress() {
            long total = totalBytes;
            return total > 0L ? Math.min(1.0F, downloadedBytes.get() / (float) total) : 0.0F;
        }

        private boolean isDownloaded() {
            return Files.isRegularFile(getPanoramasDirectory().resolve(archiveName));
        }

        private void ensurePreview() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (previewTexture != null || client == null || !previewLoading.compareAndSet(false, true)) return;
            HttpRequest request = HttpRequest.newBuilder(previewUri).GET().build();
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            previewLoading.set(false);
                            return;
                        }
                        client.execute(() -> {
                            try {
                                NativeImage image = NativeImage.read(new ByteArrayInputStream(response.body()));
                                previewTexture = new NativeImageBackedTexture(image);
                                client.getTextureManager().registerTexture(previewTextureId, previewTexture);
                            } catch (Throwable error) {
                                System.err.println("[Phaze] remote panorama preview failed: " + error);
                            } finally {
                                previewLoading.set(false);
                            }
                        });
                    })
                    .exceptionally(error -> { previewLoading.set(false); return null; });
        }

        private void activate() {
            Path target = getPanoramasDirectory().resolve(archiveName);
            if (Files.isRegularFile(target)) {
                selectDownloaded();
                return;
            }
            if (!downloading.compareAndSet(false, true)) return;
            ensureDirectoryExists();
            downloadedBytes.set(0L);
            totalBytes = -1L;
            // Download beside the target but under a name the archive
            // scanner ignores, so a half-written file is never picked
            // up as a panorama and an interrupted download leaves at
            // most one stale .part behind.
            Path temporary = target.resolveSibling(archiveName + ".part");
            HttpRequest request = HttpRequest.newBuilder(downloadUri).GET().build();
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAcceptAsync(response -> {
                        try {
                            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                throw new IOException("HTTP " + response.statusCode());
                            }
                            totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temporary)) {
                                byte[] buffer = new byte[64 * 1024];
                                int read;
                                while ((read = input.read(buffer)) >= 0) {
                                    output.write(buffer, 0, read);
                                    downloadedBytes.addAndGet(read);
                                }
                            }
                            try (ZipFile ignored = new ZipFile(temporary.toFile())) {
                                // Validate before making the panorama visible.
                            }
                            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                            selectDownloaded();
                        } catch (Throwable error) {
                            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                            System.err.println("[Phaze] remote panorama download failed: " + error);
                        } finally {
                            downloading.set(false);
                        }
                    })
                    .exceptionally(error -> {
                        try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                        downloading.set(false);
                        System.err.println("[Phaze] remote panorama download failed: " + error);
                        return null;
                    });
        }

        private void selectDownloaded() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            client.execute(() -> {
                reload();
                MenuUiSettings.getInstance().setSelectedPanoramaPreset(customIdForArchiveName(archiveName));
            });
        }
    }
}
