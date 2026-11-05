package vorga.phazeclient.implement.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.BossBarHud;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/** Offline persistence checks. Every config lives in a newly created test directory. */
public final class ConfigPersistenceChecks {
    private static int checks;
    private static final String CONFIG_MANAGER = "vorga/phazeclient/implement/config/ConfigManager";

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("build"), "config-persistence-checks-");
        Object loader = FabricLoader.getInstance();
        Field gameDirectory = loader.getClass().getDeclaredField("gameDir");
        gameDirectory.setAccessible(true);
        Object previousDirectory = gameDirectory.get(loader);
        // The shared serializer resolves panorama IDs through Fabric's
        // directory registry. Keep that incidental scan under build/ too.
        gameDirectory.set(loader, root.toAbsolutePath());
        try {
            shapeAndImportedMarker(root.resolve("shape"));
            hudLayoutRestart(root.resolve("hud-layout"));
            loadAndImportIsolation(root.resolve("load"));
            debounceAndImmediateSave(root.resolve("debounce"));
            concurrentSaves(root.resolve("concurrent"));
            dirtyNotificationDuringSave(root.resolve("dirty"));
            shutdownBarrier(root.resolve("shutdown"));
            failureRetry(root.resolve("retry"));
            backupRecovery(root.resolve("backup"));
            lifecycleWiring();
        } finally {
            Setting.setGlobalChangeListener(null);
            Module.setGlobalStateChangeListener(null);
            gameDirectory.set(loader, previousDirectory);
        }
        System.out.println("Config persistence checks passed: " + checks + " (fixtures: " + root + ")");
    }

    private static void shapeAndImportedMarker(Path root) throws Exception {
        Fixture fixture = new Fixture();
        ConfigManager manager = new ConfigManager(root.toFile(),
                () -> List.of(fixture.module, BossBarHud.getInstance(), ArmorHud.getInstance()));
        Path target = manager.getConfigFile("default").toPath();
        manager.saveCurrentConfig();
        JsonObject saved = read(target);
        check(saved.equals(decode(manager.exportCurrentToString())), "save and export share the exact JSON tree");
        check(saved.keySet().equals(Set.of("modules", "theme", "blurRadius", "menuPanoramaSpeed",
                "menuGuiFpsLimit", "menuGuiScale", "menuPanoramaPreset", "customMainMenuEnabled",
                "menuPanoramaSpeedScaleVersion")), "headless top-level shape retains every menu/theme field");
        JsonObject module = saved.getAsJsonObject("modules").getAsJsonObject("config_test");
        check(module.keySet().equals(Set.of("enabled", "key", "settings")), "module state/key/settings shape unchanged");
        JsonObject settings = module.getAsJsonObject("settings");
        check(settings.keySet().equals(Set.of("Boolean", "Value", "Text", "Bind", "Color", "Select",
                "MultiSelect", "MultiColor", "Group", "Item", "EmptyItem")), "every persisted setting type is retained; sections are omitted");
        check(settings.get("Text").getAsString().equals("hello%%++world"), "text transport encoding retained");
        check(settings.get("MultiSelect").getAsString().equals("A,C"), "multi-select encoding retained");
        check(settings.getAsJsonObject("MultiColor").getAsJsonArray("colors").size() == 2
                && settings.getAsJsonObject("MultiColor").get("selectedColorIndex").getAsInt() == 1,
                "all palette colors and selected index retained");
        check(settings.getAsJsonObject("Group").getAsJsonObject("Nested").get("Child").getAsBoolean(),
                "nested groups serialize recursively");
        check(settings.getAsJsonObject("Item").keySet().equals(Set.of("active", "enabled", "highlightColor",
                "itemId", "displayName", "matchName")) && !settings.get("EmptyItem").getAsBoolean(),
                "active and inactive item-picker formats retained");
        Set<String> hudFields = Set.of("hud_x_ratio", "hud_y_ratio", "hud_x", "hud_y", "hud_ref_w", "hud_ref_h", "hud_scale");
        for (Module hud : List.of(BossBarHud.getInstance(), ArmorHud.getInstance())) {
            check(saved.getAsJsonObject("modules").getAsJsonObject(hud.getName()).keySet().containsAll(hudFields),
                    "all HUD transform/reference fields retained for " + hud.getName());
        }
        check(saved.getAsJsonObject("modules").getAsJsonObject(BossBarHud.getInstance().getName()).has("hud_auto_position"),
                "Boss Bar automatic positioning is persisted");
        saved.addProperty("imported", true);
        Files.writeString(target, saved.toString());
        manager.saveCurrentConfig();
        check(read(target).get("imported").getAsBoolean(), "autosave preserves destination's imported marker");
        check(!decode(manager.exportCurrentToString()).has("imported"), "portable snapshot does not inherit file-local imported marker");
        manager.saveConfig("local-copy");
        check(!read(manager.getConfigFile("local-copy").toPath()).has("imported"), "saving a fresh local copy does not inherit imported marker");
    }

    private static void hudLayoutRestart(Path root) throws Exception {
        var rect = BossBarHud.getInstance();
        var armor = ArmorHud.getInstance();
        rect.setHudPosition(600, 400, 800, 600);
        rect.setHudLayoutSize(200, 200);
        rect.setHudScale(1.5F);
        armor.setHudPosition(350, 200, 800, 600);
        armor.setHudLayoutSize(100, 200);
        armor.setHudScale(1.25F);
        ConfigManager manager = new ConfigManager(root.toFile(), () -> List.of(rect, armor));
        manager.saveCurrentConfig();
        JsonObject saved = read(manager.getConfigFile("default").toPath()).getAsJsonObject("modules");
        check(saved.getAsJsonObject(rect.getName()).get("hud_layout_w").getAsFloat() == 200,
                "widget extent is persisted alongside its reference screen");
        rect.resetHudTransform(); armor.resetHudTransform();
        manager.loadCurrentConfig();
        check(rect.getHudX() == 600 && rect.getHudY() == 400 && rect.getHudRefWidth() == 800
                && rect.getHudLayoutWidth() == 200 && rect.getHudScale() == 1.5F,
                "rectangle restart restores pixels/reference/extent atomically, ignoring fallback ratios");
        check(armor.getHudX() == 350 && armor.getHudY() == 200 && armor.getHudRefWidth() == 800
                && armor.getHudLayoutHeight() == 200 && armor.getHudScale() == 1.25F,
                "armor restart restores its complete screen-space layout");
        for (float fraction : new float[]{0, 0.25F, 0.5F, 0.75F, 1}) {
            float oldX = fraction * 600;
            float fullscreenX = vorga.phazeclient.api.system.hud.HudPositionMath.rescale(oldX, 800, 1920, 200);
            check(Math.abs(fullscreenX - fraction * 1720) < 0.001F,
                    "small-window restart preserves left/center/right fraction of available screen");
            float restoredX = vorga.phazeclient.api.system.hud.HudPositionMath.rescale(fullscreenX, 1920, 800, 200);
            check(Math.abs(restoredX - oldX) < 0.001F, "fullscreen/windowed round trip preserves layout");
        }
        check(vorga.phazeclient.api.system.hud.HudPositionMath.rescale(400, 600, 1080, 200) == 880,
                "bottom anchored HUD remains bottom anchored after restart");
        // GUI-scaled coordinates use the same math, but their own viewport dimensions.
        check(vorga.phazeclient.api.system.hud.HudPositionMath.rescale(300, 400, 640, 100) == 540,
                "player-model GUI coordinate space remains independent of pixel-space HUDs");
        for (Class<?> type : List.of(vorga.phazeclient.implement.features.modules.hud.RectHudModule.class, ArmorHud.class)) {
            ClassNode node = new ClassNode();
            try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                new ClassReader(stream).accept(node, 0);
            }
            var setter = node.methods.stream().filter(m -> m.name.equals("setHudScale")).findFirst().orElseThrow();
            for (AbstractInsnNode instruction : setter.instructions) {
                check(!(instruction instanceof MethodInsnNode call && call.name.equals("markResolvedForCurrentScreen")),
                        "restoring/clamping scale cannot overwrite the saved position reference");
            }
        }
    }

    private static void loadAndImportIsolation(Path root) throws Exception {
        Fixture fixture = new Fixture();
        ConfigManager manager = new ConfigManager(root.toFile(), () -> List.of(fixture.module));
        manager.enableAutoSave();
        Setting.setGlobalChangeListener(setting -> manager.markDirty());
        Module.setGlobalStateChangeListener(module -> manager.flushNow());
        manager.saveCurrentConfig();
        JsonObject original = read(manager.getConfigFile("default").toPath());
        fixture.text.setText("unsaved temporary value");
        fixture.bool.setValue(false);
        manager.loadCurrentConfig();
        check(decode(manager.exportCurrentToString()).equals(original),
                "every setting type round-trips through reset, load and the shared serializer");
        JsonObject incoming = new JsonObject();
        JsonObject modules = new JsonObject();
        JsonObject module = new JsonObject();
        JsonObject settings = new JsonObject();
        settings.addProperty("Text", "incoming%%text");
        module.add("settings", settings);
        module.addProperty("key", 91);
        modules.add("config_test", module);
        incoming.add("modules", modules);
        Path second = manager.getConfigFile("second").toPath();
        Files.writeString(second, incoming.toString());
        manager.loadConfig("second");
        check(fixture.text.getText().equals("incoming text") && fixture.module.getKey() == 91, "new config is applied");
        check(!fixture.bool.isValue(), "missing settings reset to in-code defaults instead of merging");
        check(read(manager.getConfigFile("default").toPath()).equals(original), "load callbacks cannot overwrite outgoing config with defaults");
        check(read(second).equals(incoming) && dirtyAt(manager) == 0, "load callbacks cannot overwrite the incoming config or enqueue an autosave");
        String imported = manager.importFromString(manager.exportCurrentToString(), "shared");
        check("shared".equals(imported) && manager.getCurrentConfigName().equals(imported), "import switches to its fresh destination");
        check(manager.isImportedConfig(imported), "import writes its file-local marker atomically");
        fixture.text.setText("edited import");
        manager.flushNow();
        check(manager.isImportedConfig(imported) && read(second).getAsJsonObject("modules")
                .getAsJsonObject("config_test").getAsJsonObject("settings").get("Text").getAsString().equals("incoming%%text"),
                "editing an import cannot overwrite the previous config");
        manager.deleteConfig(imported);
        check(!manager.configExists(imported) && manager.getCurrentConfigName().equals("default"),
                "deleting the active import does not recreate it through load/save callbacks");
        Setting.setGlobalChangeListener(null);
        Module.setGlobalStateChangeListener(null);
    }

    private static void debounceAndImmediateSave(Path root) throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger snapshots = new AtomicInteger();
        ConfigManager manager = new ConfigManager(root.toFile(), () -> {
            snapshots.incrementAndGet();
            return List.of(fixture.module);
        });
        Path target = manager.getConfigFile("default").toPath();
        manager.enableAutoSave();
        manager.markDirty();
        manager.flushIfDirty();
        check(!Files.exists(target), "ordinary setting changes stay debounced");
        ageDirty(manager);
        manager.flushIfDirty();
        manager.flushIfDirty();
        check(snapshots.get() == 1 && dirtyAt(manager) == 0, "one elapsed debounce publishes once and consumes dirty state");
        Module.setGlobalStateChangeListener(module -> manager.flushNow());
        fixture.module.setKey(77);
        check(read(target).getAsJsonObject("modules").getAsJsonObject("config_test").get("key").getAsInt() == 77,
                "module key change is on disk before its listener returns, without a tick");
        Module.setGlobalStateChangeListener(null);
    }

    private static void concurrentSaves(Path root) throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        ConfigManager manager = new ConfigManager(root.toFile(), () -> {
            int count = active.incrementAndGet();
            maximum.accumulateAndGet(count, Math::max);
            Thread.yield();
            active.decrementAndGet();
            return List.of(fixture.module);
        });
        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 6; worker++) futures.add(executor.submit(() -> {
                await(start);
                for (int iteration = 0; iteration < 12; iteration++) manager.saveConfig("race");
            }));
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }
        Path target = manager.getConfigFile("race").toPath();
        check(maximum.get() == 1, "concurrent save callers cannot overlap snapshots or shared temp/backup publication");
        check(read(target).equals(read(target.resolveSibling("race.Phaze.bak"))), "concurrent target and backup remain complete JSON");
        check(!Files.exists(target.resolveSibling("race.Phaze.tmp")), "concurrent writes leave no stranded temp file");
    }

    private static void dirtyNotificationDuringSave(Path root) throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch snapshotEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConfigManager manager = new ConfigManager(root.toFile(), () -> {
            snapshotEntered.countDown();
            await(release);
            return List.of(fixture.module);
        });
        manager.enableAutoSave();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> save = executor.submit(manager::flushNow);
            check(snapshotEntered.await(5, TimeUnit.SECONDS), "save reaches its snapshot");
            CountDownLatch dirtyStarted = new CountDownLatch(1);
            Future<?> dirty = executor.submit(() -> {
                dirtyStarted.countDown();
                manager.markDirty();
            });
            check(dirtyStarted.await(5, TimeUnit.SECONDS), "concurrent notification is issued");
            release.countDown();
            save.get(10, TimeUnit.SECONDS);
            dirty.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        check(dirtyAt(manager) != 0, "a notification racing a save is not cleared by that older save");
    }

    private static void shutdownBarrier(Path root) throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger snapshots = new AtomicInteger();
        ConfigManager manager = new ConfigManager(root.toFile(), () -> {
            snapshots.incrementAndGet();
            return List.of(fixture.module);
        });
        manager.saveCurrentConfig();
        fixture.module.setKey(99);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) futures.add(executor.submit(manager::saveOnShutdown));
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        }
        manager.saveOnShutdown();
        manager.saveCurrentConfig();
        check(snapshots.get() == 2, "lifecycle and concurrent JVM shutdown callers share exactly one final snapshot");
        Path target = manager.getConfigFile("default").toPath();
        check(read(target).getAsJsonObject("modules").getAsJsonObject("config_test").get("key").getAsInt() == 99,
                "final save captures the most recent change inside the debounce window");
        check(read(target.resolveSibling("default.Phaze.bak")).getAsJsonObject("modules")
                .getAsJsonObject("config_test").get("key").getAsInt() != 99,
                "duplicate shutdown saves do not rotate away the previous-good backup");
    }

    private static void failureRetry(Path root) throws Exception {
        Fixture fixture = new Fixture();
        ConfigManager manager = new ConfigManager(root.toFile(), () -> List.of(fixture.module));
        manager.enableAutoSave();
        Path blockedTemp = manager.getConfigFile("default").toPath().resolveSibling("default.Phaze.tmp");
        Files.createDirectory(blockedTemp);
        Files.writeString(blockedTemp.resolve("lock"), "test-owned blocker");
        manager.flushNow();
        check(dirtyAt(manager) != 0, "failed immediate save leaves the change pending");
        manager.saveOnShutdown();
        Files.delete(blockedTemp.resolve("lock"));
        Files.delete(blockedTemp);
        manager.saveOnShutdown();
        check(Files.isRegularFile(manager.getConfigFile("default").toPath()) && dirtyAt(manager) == 0,
                "failed lifecycle save is retryable by the JVM safety net");
    }

    private static void backupRecovery(Path root) throws Exception {
        Fixture fixture = new Fixture();
        ConfigManager manager = new ConfigManager(root.toFile(), () -> List.of(fixture.module));
        manager.saveCurrentConfig();
        Path target = manager.getConfigFile("default").toPath();
        JsonObject imported = read(target);
        imported.addProperty("imported", true);
        Files.writeString(target, imported.toString());
        manager.saveCurrentConfig();
        Path backup = target.resolveSibling("default.Phaze.bak");
        String previousGood = Files.readString(backup);
        Files.writeString(target, "{broken");
        manager.loadCurrentConfig();
        manager.saveCurrentConfig();
        check(Files.readString(backup).equals(previousGood), "a corrupt primary never overwrites its good backup during recovery save");
        check(manager.isImportedConfig("default"), "backup recovery preserves imported semantics");
        manager.setCurrentConfigName("first");
        manager.setCurrentConfigName("second");
        Files.writeString(root.resolve("Phaze/files/current_config.json"), "{broken");
        ConfigManager reopened = new ConfigManager(root.toFile(), () -> List.of(fixture.module));
        check(reopened.getCurrentConfigName().equals("first"), "active-config metadata recovers from its atomic-write backup");
    }

    private static void lifecycleWiring() throws Exception {
        ClassNode main = bytecode("vorga/phazeclient/core/Main");
        long hooks = main.methods.stream().flatMap(method -> calls(method.instructions.toArray()).stream())
                .filter(call -> call.owner.equals("java/lang/Runtime") && call.name.equals("addShutdownHook")).count();
        check(hooks == 1, "Main registers only one JVM config safety net");
        long finalSaves = main.methods.stream().flatMap(method -> calls(method.instructions.toArray()).stream())
                .filter(call -> call.owner.equals(CONFIG_MANAGER) && call.name.equals("saveOnShutdown")).count();
        check(finalSaves == 2, "CLIENT_STOPPING and the JVM hook use the shared final-save barrier");
        ClassNode manager = bytecode(CONFIG_MANAGER);
        long builders = manager.methods.stream().flatMap(method -> calls(method.instructions.toArray()).stream())
                .filter(call -> call.owner.equals(CONFIG_MANAGER) && call.name.equals("buildCurrentConfigJson")).count();
        check(builders == 2, "saveSnapshot and portable export reuse the sole config serializer");
    }

    private static List<MethodInsnNode> calls(AbstractInsnNode[] instructions) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        return calls;
    }

    private static ClassNode bytecode(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = ConfigPersistenceChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static JsonObject decode(String share) throws Exception {
        check(share != null && share.startsWith("PHAZE1:"), "portable prefix retained");
        byte[] gzip = Base64.getDecoder().decode(share.substring("PHAZE1:".length()));
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static long dirtyAt(ConfigManager manager) throws Exception {
        Field field = ConfigManager.class.getDeclaredField("dirtyAt");
        field.setAccessible(true);
        return field.getLong(manager);
    }

    private static void ageDirty(ConfigManager manager) throws Exception {
        Field field = ConfigManager.class.getDeclaredField("dirtyAt");
        field.setAccessible(true);
        field.setLong(manager, System.currentTimeMillis() - 300L);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class OfflineModule extends Module {
        private OfflineModule() { super("config_test", ModuleCategory.OTHER); }
        @Override public void setState(boolean enabled) { state = enabled; }
    }

    private static final class Fixture {
        private final OfflineModule module = new OfflineModule();
        private final BooleanSetting bool = new BooleanSetting("Boolean", "").setValue(false);
        private final TextSetting text = new TextSetting("Text", "").setText("default");

        private Fixture() {
            bool.setValue(true);
            text.setText("hello /world");
            MultiColorSetting palette = new MultiColorSetting("MultiColor", "").colors("One", "Two")
                    .defaultColors(0xFF123456, 0xFFABCDEF);
            palette.setSelectedColorIndex(1);
            ItemPickerSetting item = new ItemPickerSetting("Item", "", 0x123456);
            item.setSerializedState(true, "minecraft:stone", "Stone Name", "Stone", false, 0x654321);
            module.setup(bool, new ValueSetting("Value", "").range(0, 10).setValue(3.5F), text,
                    new BindSetting("Bind", "").setKey(65), new ColorSetting("Color", "").value(0xFF654321),
                    new SelectSetting("Select", "").value("A", "B").selected("B"),
                    new MultiSelectSetting("MultiSelect", "").value("A", "B", "C").selected("A", "C"), palette,
                    new GroupSetting("Group", "").setValue(true).settings(new GroupSetting("Nested", "").setValue(true)
                            .settings(new BooleanSetting("Child", "").setValue(true))),
                    item, new ItemPickerSetting("EmptyItem", "", 0), new SectionSetting("UI only"));
        }
    }
}
