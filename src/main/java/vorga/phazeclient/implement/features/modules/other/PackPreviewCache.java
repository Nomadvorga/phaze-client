package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** Render-thread cache with one decoder and one replaceable pending request. */
final class PackPreviewCache {
    static final int MAX_CACHED_PACKS = 8;
    static final long MAX_TEXTURE_BYTES = 32L * 1024 * 1024;
    static final int TEXTURES_PER_FRAME = 8;
    static final long BYTES_PER_FRAME = 1024 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger("PhazePackPreview");

    private final Executor worker;
    private final Function<String, PackPreviewAssets.LoadRequest> requests;
    private final Map<String, PackPreviewAssets.Data> cached = new LinkedHashMap<>(MAX_CACHED_PACKS, 0.75f, true);
    private final Set<String> failed = new HashSet<>();
    private volatile long generation;
    private long cachedBytes;
    private Loading loading;
    private PackPreviewAssets.LoadRequest pending;
    private PackPreviewAssets.Prepared uploading;
    private String uploadingPack;

    PackPreviewCache() {
        this(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Phaze-PackPreview");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }), packId -> PackPreviewAssets.request(MinecraftClient.getInstance(), packId));
    }

    PackPreviewCache(Executor worker, Function<String, PackPreviewAssets.LoadRequest> requests) {
        this.worker = worker;
        this.requests = requests;
    }

    PackPreviewAssets.Data get(String packId) {
        PackPreviewAssets.Data data = cached.get(packId);
        if (data != null || failed.contains(packId) || packId.equals(uploadingPack)
                || loading != null && loading.generation == generation && packId.equals(loading.packId)) {
            pending = null;
            return data;
        }
        if (pending == null || !packId.equals(pending.packId())) {
            pending = requests.apply(packId);
        }
        startPending();
        return null;
    }

    /** Called once before the pack screen draws any rows, including frames without a hover. */
    void beginFrame() {
        if (uploading == null && loading != null && loading.result.isDone()) {
            Loading completed = loading;
            loading = null;
            if (completed.generation == generation) {
                try {
                    uploading = completed.result.getNow(null);
                    uploadingPack = completed.packId;
                    reserve(uploading.textureBytes());
                } catch (CancellationException ignored) {
                    // Invalidated decoders release their own unuploaded images.
                } catch (CompletionException e) {
                    fail(completed.packId, e.getCause());
                }
            }
        }
        if (uploading != null) {
            try {
                if (uploading.upload(TEXTURES_PER_FRAME, BYTES_PER_FRAME)) {
                    PackPreviewAssets.Data data = uploading.takeData();
                    cached.put(data.packId(), data);
                    cachedBytes += data.textureBytes();
                    uploading = null;
                    uploadingPack = null;
                }
            } catch (RuntimeException e) {
                fail(uploadingPack, e);
                uploading.close();
                uploading = null;
                uploadingPack = null;
            }
        }
        startPending();
    }

    void clear() {
        long invalidated = generation++;
        pending = null;
        if (loading != null && loading.generation == invalidated) {
            // Do not cancel the future: a result published during invalidation still needs an owner.
            loading.result.thenAccept(PackPreviewAssets.Prepared::close);
        }
        if (uploading != null) {
            uploading.close();
            uploading = null;
            uploadingPack = null;
        }
        cached.values().forEach(PackPreviewAssets.Data::close);
        cached.clear();
        cachedBytes = 0;
        failed.clear();
    }

    private void startPending() {
        if (loading != null || pending == null) return;
        PackPreviewAssets.LoadRequest request = pending;
        pending = null;
        if (cached.containsKey(request.packId()) || failed.contains(request.packId())
                || request.packId().equals(uploadingPack)) return;
        long currentGeneration = generation;
        loading = new Loading(request.packId(), currentGeneration, CompletableFuture.supplyAsync(
                () -> request.decode(() -> generation != currentGeneration), worker));
    }

    private void reserve(long incomingBytes) {
        var iterator = cached.values().iterator();
        while (iterator.hasNext() && (cached.size() >= MAX_CACHED_PACKS
                || cachedBytes + incomingBytes > MAX_TEXTURE_BYTES)) {
            PackPreviewAssets.Data oldest = iterator.next();
            cachedBytes -= oldest.textureBytes();
            oldest.close();
            iterator.remove();
        }
    }

    private void fail(String packId, Throwable failure) {
        failed.add(packId);
        LOG.warn("Could not prepare preview for {}", packId, failure);
    }

    private record Loading(String packId, long generation, CompletableFuture<PackPreviewAssets.Prepared> result) {
    }
}
