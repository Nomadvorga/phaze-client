package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Explicit opt-in test mod; never included in the distributable JAR. */
public final class PackPreviewClientTest implements FabricClientGameTest {
    private CompletableFuture<Void> reload;
    private CompletableFuture<PackPreviewAssets.Prepared> decoding;
    private Map<String, Snapshot> expectedPixels;

    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            runChecked(context);
        } catch (Exception e) {
            throw new RuntimeException("Pack preview client smoke failed", e);
        }
    }

    private void runChecked(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            Path packs = client.getResourcePackDir();
            Path inspected = packs.resolve("preview-fixture");
            Files.createDirectories(inspected);
            Files.writeString(inspected.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Totem sound fixture\"}}");
            for (String block : List.of("stone", "sand", "dirt", "oak_log", "cobblestone")) {
                image(inspected, "block/" + block, 16, 16, 0xFF334455 + block.length());
            }
            image(inspected, "item/golden_apple", 32, 64, 0xFFFFBB22);
            image(inspected, "item/totem_of_undying", 256, 256, 0xA040A0E0);
            Files.writeString(inspected.resolve("assets/minecraft/textures/item/golden_apple.png.mcmeta"), "{\"animation\":{}}");
            Path sound = inspected.resolve("assets/minecraft/sounds/item/totem/use.ogg");
            Files.createDirectories(sound.getParent());
            Files.write(sound, new byte[]{'O', 'g', 'g', 'S', 0}); // Detection fixture, not played.
            try (var archive = new ZipOutputStream(Files.newOutputStream(packs.resolve("zip-fixture.zip")));
                 var files = Files.walk(inspected)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    archive.putNextEntry(new ZipEntry(inspected.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, archive);
                    archive.closeEntry();
                }
            }

            Path few = packs.resolve("few-blocks");
            Files.createDirectories(few);
            Files.writeString(few.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Only three changed blocks\"}}");
            for (String block : List.of("stone", "dirt", "sand")) {
                image(few, "block/" + block, 16, 16, 0xFF123456);
            }

            Path contaminant = packs.resolve("enabled-overrides");
            Files.createDirectories(contaminant);
            Files.writeString(contaminant.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Overrides vanilla bucket\"}}");
            image(contaminant, "item/bucket", 16, 16, 0xFF00FF00);
            client.getResourcePackManager().scanPacks();
            client.getResourcePackManager().enable("file/enabled-overrides");
            reload = client.reloadResources();
        });
        context.waitFor(client -> reload.isDone());
        reload.join();

        context.runOnClient(client -> {
            check(!client.getResourcePackManager().getEnabledIds().contains("file/preview-fixture"), "inspected pack stays disabled");
            try (var zipped = PackPreviewAssets.load(client, "file/zip-fixture.zip")) {
                check(zipped.sounds() && zipped.animations() && zipped.changedBlockCount() == 5, "ZIP scanning matches folder scanning");
                expectedPixels = snapshots(client, zipped);
            }
            try (var few = PackPreviewAssets.load(client, "file/few-blocks")) {
                check(few.changedBlockCount() == 3, "three changes detected");
                check(few.blockBatches().size() == 1 && few.blocksAt(0).stream().noneMatch(PackPreviewAssets.Sprite::custom),
                        "fewer than four changes yields fixed vanilla blocks");
            }
            try (var overridden = client.getResourceManager().getResource(Identifier.ofVanilla("textures/item/bucket.png")).orElseThrow().getInputStream();
                 var active = NativeImage.read(overridden);
                 var base = client.getDefaultResourcePack().open(ResourceType.CLIENT_RESOURCES,
                         Identifier.ofVanilla("textures/item/bucket.png")).get();
                 var vanilla = NativeImage.read(base);
                 var data = PackPreviewAssets.load(client, "file/preview-fixture")) {
                check(active.getColorArgb(0, 0) == 0xFF00FF00, "contaminating pack really is active");
                check(data.sounds() && data.animations() && !data.lightmap(), "real pack discovery flags");
                check(data.changedBlockCount() == 5, "actual changed block PNGs compared with vanilla");
                check(!data.blocksAt(0).equals(data.blocksAt(1500)), "blocks rotate");
                check(data.hearts().size() == 3, "one full, one half, one empty heart");
                var bucket = data.items().get("bucket");
                check(!bucket.custom(), "missing item uses vanilla fallback");
                var decoded = ((NativeImageBackedTexture) client.getTextureManager().getTexture(bucket.id())).getImage();
                check(decoded.getColorArgb(0, 0) == vanilla.getColorArgb(0, 0), "fallback ignores enabled override");
                check(data.items().get("golden_apple").originalHeight() == 32, "animated PNG uses one frame");
                check(data.diagnostics().isEmpty(), "fixture has no fallback/read errors: " + data.diagnostics());
            }
            PackPreviewAssets.LoadRequest request = PackPreviewAssets.request(client, "file/zip-fixture.zip");
            decoding = CompletableFuture.supplyAsync(() -> request.decode(() -> false));
            PackOrganizer.getInstance().setStateSilent(true);
            PackOrganizer.getInstance().packPreviews.setValue(true);
            client.setScreen(new PackScreen(client.getResourcePackManager(), manager -> {}, client.getResourcePackDir(), Text.literal("Pack Preview Test")));
            PackScreen screen = (PackScreen) client.currentScreen;
            var field = search(screen);
            int original = entries(screen);
            check(original >= 3, "screen has fixture packs and vanilla");
            field.setText("no-such-pack-13579");
            check(entries(screen) == 0, "search filters both lists");
            check(screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Done")).allMatch(button -> button.active), "Done stays enabled with no matches");
            field.setText("");
            check(entries(screen) == original, "clearing search restores list");
            field.setText("PREVIEW-FIXTURE");
            check(entries(screen) == 1, "case-insensitive file matching");
        });
        checkIncrementalUploads(context);
        context.waitFor(client -> client.getOverlay() == null);
        context.waitTicks(3);
        double[] cursor = context.computeOnClient(client -> {
            PackScreen screen = (PackScreen) client.currentScreen;
            PackListWidget list = screen.children().stream().filter(PackListWidget.class::isInstance)
                    .map(PackListWidget.class::cast).filter(widget -> !widget.children().isEmpty()).findFirst().orElseThrow();
            double factor = client.getWindow().getScaleFactor();
            return new double[]{(list.getRowLeft() + 60) * factor, (list.getRowTop(0) + 15) * factor};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitFor(client -> cachedPreview("file/preview-fixture") != null);
        context.runOnClient(client -> {
            PackPreviewAssets.Data preview = cachedPreview("file/preview-fixture");
            check(preview != null && !preview.isClosed() && preview.packId().equals("file/preview-fixture"),
                    "real mouse hovering completes the asynchronous screen pipeline");
            check(preview.items().get("totem_of_undying").width() == 256,
                    "the hover cache retains full preview image quality");
        });
        System.out.println("Pack preview screenshot: " + context.takeScreenshot("pack-preview-async-hover"));
        context.waitTicks(33);
        System.out.println("Pack preview cycled screenshot: " + context.takeScreenshot("pack-preview-async-cycled"));
        context.runOnClient(client -> {
            PackPreviewAssets.Data preview = cachedPreview("file/preview-fixture");
            client.setScreen(new TitleScreen());
            check(preview.isClosed(), "leaving the screen releases the actual cached textures");
        });
        System.out.println("Pack preview client smoke PASSED");
    }

    private void checkIncrementalUploads(ClientGameTestContext context) {
        context.waitFor(client -> decoding.isDone());
        PackPreviewAssets.Prepared prepared = decoding.join();
        context.runOnClient(client -> {
            check(!uploadBatch(prepared, 1), "one upload does not prematurely publish a complete preview");
            try {
                prepared.takeData();
                throw new AssertionError("An incomplete preview became visible");
            } catch (IllegalStateException expected) {
                // Text must stay vanilla until all preview textures exist.
            }
        });
        context.waitFor(client -> uploadBatch(prepared, PackPreviewCache.TEXTURES_PER_FRAME));
        context.runOnClient(client -> {
            try (PackPreviewAssets.Data data = prepared.takeData()) {
                Map<String, Snapshot> uploaded = snapshots(client, data);
                check(uploaded.keySet().equals(expectedPixels.keySet()), "asynchronous preparation keeps every sprite");
                for (Map.Entry<String, Snapshot> entry : uploaded.entrySet()) {
                    Snapshot expected = expectedPixels.get(entry.getKey());
                    Snapshot actual = entry.getValue();
                    check(actual.width == expected.width && actual.height == expected.height
                                    && actual.originalWidth == expected.originalWidth && actual.originalHeight == expected.originalHeight
                                    && actual.custom == expected.custom && Arrays.equals(actual.pixels, expected.pixels),
                            "incremental uploads preserve every pixel and dimension: " + entry.getKey());
                }
            }
            prepared.close();
            PackPreviewAssets.LoadRequest request = PackPreviewAssets.request(client, "file/zip-fixture.zip");
            decoding = CompletableFuture.supplyAsync(() -> request.decode(() -> false));
        });
        context.waitFor(client -> decoding.isDone());
        context.runOnClient(client -> {
            PackPreviewAssets.Prepared abandoned = decoding.join();
            List<NativeImage> images = new ArrayList<>(preparedImages(abandoned));
            check(!uploadBatch(abandoned, 1), "partial-upload cleanup has both uploaded and CPU-only images");
            abandoned.close();
            for (NativeImage image : images) {
                try {
                    image.getColorArgb(0, 0);
                    throw new AssertionError("Abandoned preview retained an allocated image");
                } catch (IllegalStateException expected) {
                    // Both the texture-owned image and pending CPU images were released.
                }
            }
        });
    }

    private static boolean uploadBatch(PackPreviewAssets.Prepared prepared, int maxTextures) {
        try {
            Field uploaded = PackPreviewAssets.Prepared.class.getDeclaredField("uploaded");
            uploaded.setAccessible(true);
            int before = uploaded.getInt(prepared);
            List<NativeImage> images = preparedImages(prepared);
            long[] sizes = new long[images.size()];
            for (int i = before; i < images.size(); i++) sizes[i] = (long) images.get(i).getWidth() * images.get(i).getHeight() * 4;
            boolean ready = prepared.upload(maxTextures, PackPreviewCache.BYTES_PER_FRAME);
            int after = uploaded.getInt(prepared);
            long bytes = 0;
            for (int i = before; i < after; i++) bytes += sizes[i];
            check(after > before && after - before <= maxTextures && bytes <= PackPreviewCache.BYTES_PER_FRAME,
                    "actual GPU uploads obey the count and byte budgets");
            return ready;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<NativeImage> preparedImages(PackPreviewAssets.Prepared prepared) {
        try {
            Field images = PackPreviewAssets.Prepared.class.getDeclaredField("images");
            images.setAccessible(true);
            return (List<NativeImage>) images.get(prepared);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static PackPreviewAssets.Data cachedPreview(String packId) {
        try {
            Field owner = PackPreviewRenderer.class.getDeclaredField("CACHE");
            owner.setAccessible(true);
            Field cached = PackPreviewCache.class.getDeclaredField("cached");
            cached.setAccessible(true);
            return ((Map<String, PackPreviewAssets.Data>) cached.get(owner.get(null))).get(packId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Snapshot> snapshots(MinecraftClient client, PackPreviewAssets.Data data) {
        Map<String, PackPreviewAssets.Sprite> sprites = new LinkedHashMap<>();
        for (int batch = 0; batch < data.blockBatches().size(); batch++) {
            List<PackPreviewAssets.Sprite> blocks = data.blockBatches().get(batch);
            for (int i = 0; i < blocks.size(); i++) sprites.put("block-" + batch + "-" + i, blocks.get(i));
        }
        data.items().forEach((name, sprite) -> sprites.put("item-" + name, sprite));
        for (int i = 0; i < data.hearts().size(); i++) sprites.put("heart-" + i, data.hearts().get(i));
        data.particles().forEach((name, sprite) -> sprites.put("particle-" + name, sprite));
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        sprites.forEach((name, sprite) -> {
            NativeImage image = ((NativeImageBackedTexture) client.getTextureManager().getTexture(sprite.id())).getImage();
            snapshots.put(name, new Snapshot(sprite.width(), sprite.height(), sprite.originalWidth(), sprite.originalHeight(),
                    sprite.custom(), image.copyPixelsArgb()));
        });
        return snapshots;
    }

    private record Snapshot(int width, int height, int originalWidth, int originalHeight, boolean custom, int[] pixels) {
    }

    private static TextFieldWidget search(PackScreen screen) {
        return screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
    }

    private static int entries(PackScreen screen) {
        return screen.children().stream().filter(PackListWidget.class::isInstance).map(PackListWidget.class::cast)
                .mapToInt(list -> list.children().size()).sum();
    }

    private static void image(Path pack, String path, int width, int height, int color) throws Exception {
        Path file = pack.resolve("assets/minecraft/textures/" + path + ".png");
        Files.createDirectories(file.getParent());
        try (NativeImage image = new NativeImage(width, height, true)) {
            image.fillRect(0, 0, width, height, color);
            image.writeTo(file);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
