package vorga.phazeclient.implement.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
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

    private static volatile boolean loaded = false;
    private static volatile long lastFolderSignature = Long.MIN_VALUE;
    private static volatile long lastRefreshCheckMs = 0L;

    private MenuPanoramaRegistry() {
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
        }
        return MenuUiSettings.PanoramaPreset.VANILLA;
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

    public static synchronized void reload() {
        ensureDirectoryExists();
        MinecraftClient client = MinecraftClient.getInstance();

        for (CustomPanoramaDescriptor panorama : CUSTOM_PANORAMAS.values()) {
            panorama.close(client);
        }
        CUSTOM_PANORAMAS.clear();

        List<Path> archives = listArchives();
        for (Path archive : archives) {
            try {
                CustomPanoramaDescriptor panorama = CustomPanoramaDescriptor.load(archive, client);
                CUSTOM_PANORAMAS.put(panorama.getId().toLowerCase(Locale.ROOT), panorama);
            } catch (Throwable t) {
                System.err.println("[Phaze] skipped invalid panorama archive " + archive.getFileName() + ": " + t.getMessage());
            }
        }

        loaded = true;
        lastFolderSignature = computeFolderSignature(archives);
        lastRefreshCheckMs = System.currentTimeMillis();
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
                if (previewEntry == null) {
                    previewEntry = findEntryIgnoreCase(zip, "panorama_0.png");
                }
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
}
