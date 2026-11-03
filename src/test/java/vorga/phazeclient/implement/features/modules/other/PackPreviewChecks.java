package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Exercises the production scanner with an isolated ResourcePack; no game window/network required. */
public final class PackPreviewChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        PackPreviewAssetLogicTest.main(new String[0]);
        features();
        search();
        System.out.println("Pack scanner and search checks passed: " + checks);
        PackPreviewCacheChecks.main(new String[0]);
    }

    private static void features() {
        var empty = PackPreviewAssets.inspectFeatures(new MemoryPack());
        check(!empty.sounds() && !empty.animations() && !empty.lightmap(), "empty pack has no custom features");

        var totem = new MemoryPack().put("minecraft:sounds/item/totem/use.ogg", new byte[]{'O', 'g', 'g', 'S', 0});
        check(PackPreviewAssets.inspectFeatures(totem).sounds(), "totem OGG without sounds.json is detected");
        check(totem.scans == 4, "scanner enumerates four supported directories in the real namespace");
        check(PackPreviewAssets.inspectFeatures(new MemoryPack().put("example:sounds/totem.ogg",
                new byte[]{'O', 'g', 'g', 'S'})).sounds(), "custom namespace OGG is detected");
        check(!PackPreviewAssets.inspectFeatures(new MemoryPack().text("minecraft:sounds/totem.ogg", "not ogg")).sounds(),
                "garbage with an OGG extension does not count");

        check(PackPreviewAssets.inspectFeatures(new MemoryPack().text("minecraft:sounds.json",
                "{\"item.totem.use\":{\"sounds\":[\"item/totem/use\"]}}")).sounds(), "totem event JSON is detected");
        check(PackPreviewAssets.inspectFeatures(new MemoryPack().text("custom:sounds.json",
                "{\"my_event\":{\"sounds\":[{\"name\":\"minecraft:item.totem.use\",\"type\":\"event\"}]}}"))
                .sounds(), "event reference to vanilla is still a real custom sound definition");
        for (String bad : new String[]{"{}", "{\"test\":{\"sounds\":[]}}", "not json",
                "{\"test\":{\"sounds\":[42]}}", "{\"test\":{\"sounds\":[\"x\"]}} trailing"}) {
            check(!PackPreviewAssets.inspectFeatures(new MemoryPack().text("minecraft:sounds.json", bad)).sounds(),
                    "invalid or empty sound JSON does not count: " + bad);
        }

        check(PackPreviewAssets.inspectFeatures(new MemoryPack().put("minecraft:textures/block/fire.png", png(16, 32))
                .text("minecraft:textures/block/fire.png.mcmeta", "{\"animation\":{}}"))
                .animations(), "valid custom animation is detected");
        check(!PackPreviewAssets.inspectFeatures(new MemoryPack().text("minecraft:textures/block/fire.png.mcmeta",
                "{\"animation\":{}}")).animations(), "metadata without the texture is not an animation");
        check(!PackPreviewAssets.inspectFeatures(new MemoryPack().put("minecraft:textures/block/fire.png", png(16, 32))
                .text("minecraft:textures/block/fire.png.mcmeta", "{\"texture\":{\"blur\":true}}"))
                .animations(), "ordinary texture metadata is not an animation");
        check(!PackPreviewAssets.inspectFeatures(new MemoryPack().put("minecraft:textures/block/fire.png", png(16, 32))
                .text("minecraft:textures/block/fire.png.mcmeta", "{\"animation\":{\"frames\":[9]}}"))
                .animations(), "out-of-range frame is rejected");
        for (String path : new String[]{"optifine/lightmap/world0.png", "mcpatcher/lightmap/world-1.png",
                "textures/misc/lightmap.png"}) {
            check(PackPreviewAssets.inspectFeatures(new MemoryPack().put("minecraft:" + path, png(16, 32))).lightmap(),
                    "supported lightmap detected: " + path);
        }
        check(!PackPreviewAssets.inspectFeatures(new MemoryPack().text("minecraft:optifine/lightmap/world0.png", "bad"))
                .lightmap(), "invalid lightmap PNG does not count");
    }

    private static void search() {
        check(PackSearch.matches("  JAguar  ", "file/foo.zip", "§aGreen Jaguars", "Blocks"), "name and case matching");
        check(PackSearch.matches("свечение", "file/glow.zip", "Текстуры", "Свечение тотема"), "Russian description matching");
        check(PackSearch.matches("foo.zip", "file/foo.zip", "Other", ""), "pack file ID matching");
        check(PackSearch.matches("", "id", "title", "desc"), "empty query restores all entries");
        check(!PackSearch.matches("missing", "id", "title", "desc"), "nonmatching entry is hidden");
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check(PackSearch.matches("ITEM", "file/item.zip", "", ""), "matching is independent of OS locale");
        } finally {
            Locale.setDefault(saved);
        }
    }

    private static byte[] png(int width, int height) {
        // Feature detection checks PNG header/geometry, not decoding/rendering.
        return ByteBuffer.allocate(24).put(new byte[]{(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10})
                .putInt(13).put(new byte[]{'I', 'H', 'D', 'R'}).putInt(width).putInt(height).array();
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class MemoryPack implements ResourcePack {
        private final Map<Identifier, byte[]> files = new LinkedHashMap<>();
        private int scans;

        MemoryPack put(String id, byte[] bytes) { files.put(Identifier.of(id), bytes); return this; }
        MemoryPack text(String id, String value) { return put(id, value.getBytes(StandardCharsets.UTF_8)); }

        @Override public InputSupplier<InputStream> openRoot(String... segments) { return null; }
        @Override public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
            byte[] bytes = files.get(id);
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }
        @Override public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
            check(type == ResourceType.CLIENT_RESOURCES, "client resources scanned");
            check(getNamespaces(type).contains(namespace), "namespace is not confused with the path or extension");
            check(!prefix.isEmpty(), "folder packs reject an empty path prefix");
            scans++;
            files.keySet().stream().filter(id -> id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix))
                    .forEach(id -> consumer.accept(id, open(type, id)));
        }
        @Override public Set<String> getNamespaces(ResourceType type) {
            return files.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toSet());
        }
        @Override public <T> T parseMetadata(ResourceMetadataSerializer<T> serializer) { return null; }
        @Override public ResourcePackInfo getInfo() { return null; }
        @Override public void close() { }
    }
}
