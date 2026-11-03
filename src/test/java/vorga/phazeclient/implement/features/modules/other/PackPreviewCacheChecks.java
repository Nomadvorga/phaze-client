package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Exercises the real scheduler with controlled completions; no game window or GPU required. */
public final class PackPreviewCacheChecks {
    private static final String PACKAGE = "vorga/phazeclient/implement/features/modules/other/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        checks = 0;
        renderThreadContracts();
        coalescedRequests();
        invalidation();
        cacheLimits();
        transferOwnership();
        backgroundCancellation();
        System.out.println("Pack preview loading checks passed: " + checks);
    }

    private static void renderThreadContracts() throws Exception {
        ClassNode cache = readClass(PACKAGE + "PackPreviewCache");
        for (MethodNode method : cache.methods) {
            check(calls(method).stream().noneMatch(call -> call.owner.startsWith("java/util/concurrent/")
                            && (call.name.equals("get") || call.name.equals("join"))
                            || call.owner.equals("java/lang/Thread") && call.name.equals("sleep")),
                    "preview scheduling never waits for a decoder on the render thread");
        }
        check((cache.fields.stream().filter(field -> field.name.equals("generation")).findFirst().orElseThrow().access
                & Opcodes.ACC_VOLATILE) != 0, "the worker sees invalidation without locks or polling");
        MethodNode frame = method(cache, "beginFrame");
        List<MethodInsnNode> frameCalls = calls(frame);
        check(callIndex(frameCalls, "isDone") >= 0 && callIndex(frameCalls, "getNow") > callIndex(frameCalls, "isDone"),
                "a completed future is consumed without waiting");
        check(frameCalls.stream().filter(call -> call.name.equals("upload")).count() == 1,
                "there is a single upload budget per screen frame, not per row");
        List<MethodInsnNode> clearCalls = calls(method(cache, "clear"));
        check(clearCalls.stream().anyMatch(call -> call.name.equals("thenAccept"))
                        && clearCalls.stream().noneMatch(call -> call.name.equals("cancel")),
                "invalidation retains ownership of results arriving after screen removal");

        ClassNode loader = readClass(PACKAGE + "PackPreviewAssets$Loader");
        for (MethodNode method : loader.methods) {
            check(calls(method).stream().noneMatch(call -> call.owner.contains("TextureManager")
                            || call.owner.contains("NativeImageBackedTexture") || call.owner.contains("RenderSystem")
                            || call.owner.equals("net/minecraft/client/MinecraftClient")),
                    "background inspection only reads and prepares CPU images");
        }
        ClassNode renderer = readClass(PACKAGE + "PackPreviewRenderer");
        check(calls(method(renderer, "render")).stream().noneMatch(call -> call.name.equals("load")
                        || call.name.equals("decode") || call.name.equals("upload")),
                "hover rendering cannot scan ZIPs, decode PNGs or upload textures");
        ClassNode screen = readClass("vorga/phazeclient/mixins/PackScreenMixin");
        MethodNode screenRender = method(screen, "render");
        check(calls(screenRender).stream().filter(call -> call.owner.equals(
                        PACKAGE + "PackPreviewRenderer") && call.name.equals("beginFrame")).count() == 1,
                "the pack screen advances the cache even when no row is hovered");
        check(readClass("net/minecraft/client/gui/screen/pack/PackScreen").methods.stream().noneMatch(method -> method.name.equals("render"))
                        && calls(screenRender).stream().anyMatch(call -> call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.owner.equals("net/minecraft/client/gui/screen/Screen") && call.name.equals("render")),
                "the frame pump overrides the inherited render method and preserves vanilla screen drawing");
        MethodNode row = method(readClass("vorga/phazeclient/mixins/ResourcePackEntryPreviewMixin"), "phaze$renderPackPreview");
        MethodInsnNode preview = calls(row).stream().filter(call -> call.owner.equals(PACKAGE + "PackPreviewRenderer")
                && call.name.equals("render")).findFirst().orElseThrow();
        MethodInsnNode cancel = calls(row).stream().filter(call -> call.name.equals("cancel")).findFirst().orElseThrow();
        AbstractInsnNode branch = preview.getNext();
        while (branch != null && branch.getOpcode() < 0) branch = branch.getNext();
        check(preview.desc.endsWith(")Z") && branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ
                        && row.instructions.indexOf(jump.label) > row.instructions.indexOf(cancel),
                "vanilla pack text remains visible until the requested preview is ready");

        check(PackPreviewAssets.MAX_SPRITE_SIZE == 256 && PackPreviewAssets.MAX_BLOCK_SPRITES == 64
                        && PackPreviewAssets.BLOCK_CYCLE_MILLIS == 1500,
                "image quality, block diversity and cycling speed are unchanged");
        long largestPreview = (PackPreviewAssets.MAX_BLOCK_SPRITES + PackPreviewAssetLogic.itemPaths().size() + 3L + 2L)
                * PackPreviewAssets.MAX_SPRITE_SIZE * PackPreviewAssets.MAX_SPRITE_SIZE * 4;
        check(largestPreview <= PackPreviewCache.MAX_TEXTURE_BYTES,
                "even the largest full-quality preview fits the texture-cache budget");
        check(PackPreviewCache.TEXTURES_PER_FRAME == 8 && PackPreviewCache.BYTES_PER_FRAME == 1024 * 1024,
                "uploads are bounded by both texture count and bytes per frame");
    }

    private static void coalescedRequests() throws Exception {
        ManualWorker worker = new ManualWorker();
        List<String> requested = new ArrayList<>();
        PackPreviewCache cache = cache(worker, requested);
        try {
            check(cache.get("first") == null && worker.tasks.size() == 1, "a cache miss schedules rather than blocks");
            CompletableFuture<PackPreviewAssets.Prepared> first = result(cache);
            for (int i = 0; i < 1000; i++) {
                check(cache.get("hover-" + i) == null, "rapid hovering does not return another pack's preview");
                cache.beginFrame();
                check(worker.tasks.size() == 1, "rapid hovering never builds an executor backlog");
            }
            check(((PackPreviewAssets.LoadRequest) field(cache, "pending").get(cache)).packId().equals("hover-999"),
                    "only the latest hover is pending");
            Fixture loaded = prepared("first", 1024);
            first.complete(loaded.prepared);
            worker.runNext();
            cache.beginFrame();
            check(cache.get("first") == loaded.data, "a completed preview is reused without decoding again");
            check(worker.tasks.size() == 1 && requested.getLast().equals("hover-999"),
                    "the decoder skips every superseded pending hover");
            for (int i = 0; i < 1000; i++) {
                check(cache.get("hover-999") == null && worker.tasks.size() == 1,
                        "repeated hover frames share the same in-flight request");
            }
        } finally {
            cache.clear();
            worker.runNext();
        }
    }

    private static void invalidation() throws Exception {
        for (boolean completedBeforeClear : new boolean[]{false, true}) {
            ManualWorker worker = new ManualWorker();
            PackPreviewCache cache = cache(worker, new ArrayList<>());
            cache.get("same-id");
            CompletableFuture<PackPreviewAssets.Prepared> obsolete = result(cache);
            Fixture stale = prepared("same-id", 4096);
            if (completedBeforeClear) obsolete.complete(stale.prepared);
            cache.clear();
            for (int i = 0; i < 20; i++) {
                cache.clear();
                check(cache.get("same-id") == null && worker.tasks.size() == 1,
                        "closing and reopening screens cannot queue decoders behind an obsolete task");
            }
            if (!completedBeforeClear) obsolete.complete(stale.prepared);
            check(stale.data.isClosed(), "ready and late obsolete results both release their images");
            worker.runNext();
            cache.beginFrame();
            check(cache.get("same-id") == null, "a previous generation never reappears under the same profile ID");
            Fixture fresh = prepared("same-id", 4096);
            result(cache).complete(fresh.prepared);
            worker.runNext();
            cache.beginFrame();
            check(cache.get("same-id") == fresh.data && !fresh.data.isClosed(), "a fresh generation remains usable");
            cache.clear();
            check(fresh.data.isClosed(), "screen removal also releases cached preview textures");
        }
    }

    private static void cacheLimits() throws Exception {
        for (long bytes : new long[]{1024, 8L * 1024 * 1024}) {
            ManualWorker worker = new ManualWorker();
            List<String> requested = new ArrayList<>();
            PackPreviewCache cache = cache(worker, requested);
            List<Fixture> fixtures = new ArrayList<>();
            int capacity = (int) Math.min(PackPreviewCache.MAX_CACHED_PACKS, PackPreviewCache.MAX_TEXTURE_BYTES / bytes);
            for (int i = 0; i <= capacity; i++) {
                String id = "pack-" + i;
                if (i == capacity) check(cache.get("pack-0") == fixtures.getFirst().data, "cache hits update recency");
                cache.get(id);
                Fixture fixture = prepared(id, bytes);
                fixtures.add(fixture);
                result(cache).complete(fixture.prepared);
                worker.runNext();
                cache.beginFrame();
                check(((Map<?, ?>) field(cache, "cached").get(cache)).size() <= PackPreviewCache.MAX_CACHED_PACKS,
                        "pack count stays bounded");
                check(field(cache, "cachedBytes").getLong(cache) <= PackPreviewCache.MAX_TEXTURE_BYTES,
                        "texture bytes stay bounded without lowering sprite quality");
            }
            check(fixtures.get(1).data.isClosed() && !fixtures.getFirst().data.isClosed(),
                    "the least recently used pack is released, not the recently revisited pack");
            int loads = requested.size();
            check(cache.get("pack-0") == fixtures.getFirst().data && requested.size() == loads && worker.tasks.isEmpty(),
                    "revisiting a cached pack performs no disk or executor work");
            cache.clear();
            check(fixtures.stream().allMatch(fixture -> fixture.data.isClosed()), "cache cleanup releases every owner");
        }
    }

    private static void transferOwnership() throws Exception {
        Fixture fixture = prepared("transfer", 0);
        check(fixture.prepared.upload(0, 0), "an empty prepared result needs no upload calls");
        PackPreviewAssets.Data data = fixture.prepared.takeData();
        fixture.prepared.close();
        check(data == fixture.data && !data.isClosed(), "taking data transfers ownership out of Prepared");
        try {
            fixture.prepared.takeData();
            throw new AssertionError("Preview ownership was transferred twice");
        } catch (IllegalStateException expected) {
            checks++;
        }
        data.close();
        data.close();
        check(data.isClosed(), "the transferred owner can be closed idempotently");
        try {
            new PackPreviewAssets.LoadRequest(null, "cancelled", null, null).decode(() -> true);
            throw new AssertionError("Invalidated decoding continued");
        } catch (CancellationException expected) {
            checks++;
        }
    }

    private static void backgroundCancellation() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> decodingThread = new AtomicReference<>();
        EmptyPack pack = new EmptyPack();
        ResourcePackProfile profile = new ResourcePackProfile(null, null, null, null) {
            @Override public ResourcePack createResourcePack() {
                decodingThread.set(Thread.currentThread());
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Decoder was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException();
                }
                return pack;
            }
        };
        PackPreviewCache cache = new PackPreviewCache(worker,
                id -> new PackPreviewAssets.LoadRequest(null, id, profile, null));
        try {
            check(cache.get("slow-pack") == null, "opening a slow pack does not block the caller");
            check(entered.await(5, TimeUnit.SECONDS) && decodingThread.get() != Thread.currentThread(),
                    "the actual pack factory runs on the decoder thread");
            for (int i = 0; i < 100; i++) cache.beginFrame();
            cache.clear();
            release.countDown();
            worker.shutdown();
            check(worker.awaitTermination(5, TimeUnit.SECONDS), "invalidation terminates the decoder cooperatively");
            cache.beginFrame();
            check(pack.closed && !pack.scanned, "invalidating an opened pack closes it before scanning or decoding");
        } finally {
            cache.clear();
            release.countDown();
            worker.shutdownNow();
        }
    }

    private static PackPreviewCache cache(ManualWorker worker, List<String> requested) {
        return new PackPreviewCache(worker, id -> {
            requested.add(id);
            return new PackPreviewAssets.LoadRequest(null, id, null, null);
        });
    }

    /** Empty image lists stand in for completed decodes; Data and ownership transitions are production code. */
    private static Fixture prepared(String packId, long textureBytes) throws Exception {
        Class<?> loaderClass = Class.forName(PackPreviewAssets.class.getName() + "$Loader");
        Constructor<?> loaderConstructor = loaderClass.getDeclaredConstructor(MinecraftClient.class, String.class,
                DefaultResourcePack.class, BooleanSupplier.class);
        loaderConstructor.setAccessible(true);
        Object loader = loaderConstructor.newInstance(null, packId, null, (BooleanSupplier) () -> false);
        field(loader, "textureBytes").setLong(loader, textureBytes);
        Constructor<PackPreviewAssets.Data> dataConstructor = PackPreviewAssets.Data.class.getDeclaredConstructor(
                loaderClass, List.class, Map.class, List.class, Map.class, int.class);
        dataConstructor.setAccessible(true);
        PackPreviewAssets.Data data = dataConstructor.newInstance(loader, List.of(List.of()), Map.of(), List.of(), Map.of(), 0);
        Constructor<PackPreviewAssets.Prepared> preparedConstructor = PackPreviewAssets.Prepared.class.getDeclaredConstructor(
                PackPreviewAssets.Data.class, List.class);
        preparedConstructor.setAccessible(true);
        return new Fixture(preparedConstructor.newInstance(data, new ArrayList<>()), data);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<PackPreviewAssets.Prepared> result(PackPreviewCache cache) throws Exception {
        Object loading = field(cache, "loading").get(cache);
        Method result = loading.getClass().getDeclaredMethod("result");
        result.setAccessible(true);
        return (CompletableFuture<PackPreviewAssets.Prepared>) result.invoke(loading);
    }

    private static Field field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static ClassNode readClass(String name) throws Exception {
        try (InputStream input = PackPreviewCacheChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class: " + name);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static int callIndex(List<MethodInsnNode> calls, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i;
        return -1;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private record Fixture(PackPreviewAssets.Prepared prepared, PackPreviewAssets.Data data) {
    }

    private static final class ManualWorker implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void runNext() { tasks.remove().run(); }
    }

    private static final class EmptyPack implements ResourcePack {
        private boolean scanned;
        private boolean closed;
        @Override public InputSupplier<InputStream> openRoot(String... segments) { return null; }
        @Override public InputSupplier<InputStream> open(ResourceType type, Identifier id) { return null; }
        @Override public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) { }
        @Override public Set<String> getNamespaces(ResourceType type) { scanned = true; return Set.of(); }
        @Override public <T> T parseMetadata(ResourceMetadataSerializer<T> serializer) { return null; }
        @Override public ResourcePackInfo getInfo() { return null; }
        @Override public void close() { closed = true; }
    }
}
