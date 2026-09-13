package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.base.util.animation.Interpolations;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkAnimator extends Module {
    private static final ChunkAnimator INSTANCE = new ChunkAnimator();

    private static final int MAX_TRACKED = 100_000;

    private final Map<Long, Long> firstSeenMs = new ConcurrentHashMap<>();

    private final Set<Long> pendingAnimationKeys = ConcurrentHashMap.newKeySet();

    private final Map<Long, Float> playerYAtRegister = new ConcurrentHashMap<>();

    private volatile long lastAnimRegisterMs = 0L;

    public boolean hasActiveAnimations() {
        if (!isEnabled()) {
            return false;
        }
        long last = lastAnimRegisterMs;
        if (last == 0L) {
            return false;
        }
        return System.currentTimeMillis() - last <= (long) duration.getInt();
    }

    public boolean shouldEvaluateSodiumFallback() {
        return isEnabled() && (hasActiveAnimations() || !pendingAnimationKeys.isEmpty());
    }

    public boolean consumeSodiumFallbackRequest() {
        if (pendingAnimationKeys.isEmpty()) {
            return false;
        }
        pendingAnimationKeys.clear();
        return true;
    }

    public boolean isColumnAnimating(BlockPos position) {
        if (!isEnabled() || position == null) return false;
        long key = ChunkPos.toLong(position.getX() >> 4, position.getZ() >> 4);
        if (pendingAnimationKeys.contains(key)) return true;
        long total = Math.max(1L, (long) duration.getInt());
        Long startedAt = firstSeenMs.get(key);
        if (startedAt != null && System.currentTimeMillis() - startedAt < total) return true;

        int chunkX = position.getX() >> 4;
        int sectionY = position.getY() >> 4;
        int chunkZ = position.getZ() >> 4;
        long regionKey = ChunkSectionPos.asLong(
                Math.floorDiv(chunkX, 8) * 8,
                Math.floorDiv(sectionY, 4) * 4,
                Math.floorDiv(chunkZ, 8) * 8
        );
        Long regionStartedAt = regionFirstSeenMs.get(regionKey);
        return regionStartedAt != null && System.currentTimeMillis() - regionStartedAt < total
                || hasActiveAnimations();
    }

    private final Map<Long, Long> regionFirstSeenMs = new ConcurrentHashMap<>();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting duration = new ValueSetting(
            "Duration",
            "Animation length in milliseconds. Lower = snappier."
    ).range(100, 5000).step(50).setValue(600);

    public final SelectSetting animationType = new SelectSetting(
            "Animation Type",
            "How chunks enter view. Top/Bottom/Side slide chunks in from a direction. Scale grows each section out from its centre (Sodium only - shader patch required)."
    ).value("Top", "Bottom", "Side", "Scale").selected("Bottom");
    public final ValueSetting distance = new ValueSetting(
            "Distance",
            "How many blocks chunks travel before reaching their final position."
    ).range(8, 256).step(1).setValue(64)

            .visible(() -> !animationType.isSelected("Scale"));

    public final SelectSetting directionSide = new SelectSetting(
            "Side",
            "Cardinal direction chunks slide in from when Animation Type is Side."
    ).value("North", "South", "East", "West").selected("South")
            .visible(() -> animationType.isSelected("Side"));
    public final SelectSetting easing = new SelectSetting(
            "Easing",
            "Easing curve for the slide. Decelerate gives a soft landing; Bounce / Elastic add an overshoot."
    ).value(Interpolations.getAllNames()).selected("Decelerate")

            .visible(() -> !animationType.isSelected("Scale"));

    private final SelectSetting fadeStyle = new SelectSetting(
            "Fade Style",
            "Fog Mix: smooth blend toward fog-like distance colour. Applies only when Animation Type is Fade."
    ).value("Fog Mix").selected("Fog Mix")
            .visible(() -> false);

    private ChunkAnimator() {
        super("chunk_animator", "World Animator", ModuleCategory.OTHER);

        animationType.onChange(value -> {
            if ("Fade".equalsIgnoreCase(value)) animationType.setSelected("Bottom");
        });
        duration.setFullWidth(true);
        distance.setFullWidth(true);
        animationType.setFullWidth(true);
        directionSide.setFullWidth(true);
        easing.setFullWidth(true);
        fadeStyle.setFullWidth(true);

        setup(generalSection, duration, distance, animationType, directionSide, easing);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!isEnabled()) {
                return;
            }
            long key = ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z);
            firstSeenMs.remove(key);
            playerYAtRegister.remove(key);
            pendingAnimationKeys.add(key);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z);
            firstSeenMs.remove(key);
            playerYAtRegister.remove(key);
            pendingAnimationKeys.remove(key);
        });
    }

    public static ChunkAnimator getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Animates loaded chunks sliding smoothly into place from below or from a chosen direction";
    }

    public void writeAnimationDirection(float[] out) {
        if (out == null || out.length < 3) return;

        if (animationType.isSelected("Fade") || animationType.isSelected("Scale")) {
            out[0] = 0.0F; out[1] = 0.0F; out[2] = 0.0F;
            return;
        }
        if (animationType.isSelected("Top")) {

            out[0] = 0.0F; out[1] = 1.0F; out[2] = 0.0F;
            return;
        }
        if (animationType.isSelected("Side")) {
            String side = directionSide.getSelected();
            if ("North".equalsIgnoreCase(side)) {
                out[0] = 0.0F; out[1] = 0.0F; out[2] = -1.0F;
                return;
            }
            if ("South".equalsIgnoreCase(side)) {
                out[0] = 0.0F; out[1] = 0.0F; out[2] = 1.0F;
                return;
            }
            if ("East".equalsIgnoreCase(side)) {
                out[0] = 1.0F; out[1] = 0.0F; out[2] = 0.0F;
                return;
            }
            if ("West".equalsIgnoreCase(side)) {
                out[0] = -1.0F; out[1] = 0.0F; out[2] = 0.0F;
                return;
            }

        }

        out[0] = 0.0F; out[1] = -1.0F; out[2] = 0.0F;
    }

    public void writeAnimationDirectionPerSection(float[] out) {
        writeAnimationDirection(out);
    }

    @Override
    public String getIcon() {
        return "chunk_animator.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getYOffset(BlockPos origin) {
        if (!isEnabled() || origin == null) {
            return 0.0F;
        }

        if (animationType.isSelected("Fade") || animationType.isSelected("Scale")) {
            return 0.0F;
        }

        long key = ChunkPos.toLong(origin.getX() >> 4, origin.getZ() >> 4);
        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());
        int dist = distance.getInt();

        Long start = firstSeenMs.get(key);
        if (start == null) {

            if (!pendingAnimationKeys.contains(key)) {
                return 0.0F;
            }

            if (firstSeenMs.size() >= MAX_TRACKED) {
                evictExpired(now, total);
            }
            pendingAnimationKeys.remove(key);
            firstSeenMs.put(key, now);
            lastAnimRegisterMs = now;
            return (float) dist;
        }

        long elapsed = now - start;
        if (elapsed >= total) {
            return 0.0F;
        }

        double progress = (double) elapsed / (double) total;
        Interpolation curve = Interpolations.getByName(easing.getSelected());
        double eased = curve.interpolate(progress);

        double offset = dist * (1.0 - eased);

        if (offset < 0.0) {
            offset = 0.0;
        }
        return (float) offset;
    }

    public float getScale(BlockPos origin) {
        if (!isEnabled() || !isScaleMode() || origin == null) {
            return 1.0F;
        }

        long key = ChunkPos.toLong(origin.getX() >> 4, origin.getZ() >> 4);
        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());
        Long start = firstSeenMs.get(key);

        if (start == null) {
            if (!pendingAnimationKeys.remove(key)) {
                return 1.0F;
            }
            if (firstSeenMs.size() >= MAX_TRACKED) {
                evictExpired(now, total);
            }
            firstSeenMs.put(key, now);
            lastAnimRegisterMs = now;
            return 0.0F;
        }

        long elapsed = now - start;
        if (elapsed >= total) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) elapsed / (float) total));
    }

    private void evictExpired(long now, long total) {
        evictExpiredFrom(firstSeenMs, now, total);

        playerYAtRegister.keySet().removeIf(k -> !firstSeenMs.containsKey(k));
    }

    public boolean writeRegionSectionYOffsets(
            int regionOriginBlockX, int regionOriginBlockY, int regionOriginBlockZ,
            int[] drawableSlots, int drawableCount,
            float[] outOffsets) {

        java.util.Arrays.fill(outOffsets, 0, 256, 0.0F);
        if (!isEnabled() || drawableSlots == null || drawableCount <= 0) {
            return false;
        }

        if (animationType.isSelected("Fade") || animationType.isSelected("Scale")) {
            return false;
        }

        int regionChunkX = regionOriginBlockX >> 4;
        int regionChunkY = regionOriginBlockY >> 4;
        int regionChunkZ = regionOriginBlockZ >> 4;

        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());
        int dist = distance.getInt();
        Interpolation curve = Interpolations.getByName(easing.getSelected());

        MinecraftClient mc = MinecraftClient.getInstance();
        float currentPlayerY = (mc != null && mc.player != null)
                ? (float) mc.player.getY()
                : 0.0F;

        float verticalSign;
        if (animationType.isSelected("Top")) {
            verticalSign = 1.0F;
        } else if (animationType.isSelected("Bottom")) {
            verticalSign = -1.0F;
        } else {
            verticalSign = 0.0F;
        }
        boolean verticalMode = verticalSign != 0.0F;

        if (firstSeenMs.size() >= MAX_TRACKED) {
            evictExpired(now, total);
        }

        boolean hasNonZero = false;
        for (int i = 0; i < drawableCount; i++) {
            int slot = drawableSlots[i] & 0xFF;

            int relX = (slot >> 5) & 7;
            int relY = slot & 3;
            int relZ = (slot >> 2) & 7;

            int sx = regionChunkX + relX;
            int sz = regionChunkZ + relZ;

            long key = ChunkPos.toLong(sx, sz);

            int sectionWorldY = regionOriginBlockY + relY * 16;

            Long start = firstSeenMs.get(key);
            float offset;

            if (start == null && !pendingAnimationKeys.contains(key)) {
                continue;
            }
            if (start == null) {
                pendingAnimationKeys.remove(key);
                firstSeenMs.put(key, now);
                lastAnimRegisterMs = now;
                if (verticalMode) {

                    offset = verticalSign * (float) dist;
                } else {

                    offset = (float) dist;
                }
            } else {
                long elapsed = now - start;
                if (elapsed >= total) {

                    continue;
                }
                double progress = (double) elapsed / (double) total;
                double eased = curve.interpolate(progress);
                if (verticalMode) {
                    offset = verticalSign * (float) (dist * (1.0 - eased));

                    if (offset == 0.0F) continue;
                } else {
                    double off = dist * (1.0 - eased);
                    offset = off < 0.0 ? 0.0F : (float) off;
                    if (offset == 0.0F) {

                        continue;
                    }
                }
            }

            outOffsets[slot] = offset;
            hasNonZero = true;
        }
        return hasNonZero;
    }

    public boolean isFadeMode() {
        return animationType.isSelected("Fade");
    }

    public boolean isScaleMode() {
        return animationType.isSelected("Scale");
    }

    public int getAnimationModeIndex() {
        if (!isEnabled()) {
            return 0;
        }
        if (isScaleMode()) {
            return 3;
        }

        return 1;
    }

    public int getFadeStyleIndex() {
        return 1;
    }

    public boolean writeRegionSectionFadeValues(
            int regionOriginBlockX, int regionOriginBlockY, int regionOriginBlockZ,
            int[] drawableSlots, int drawableCount,
            float[] outFade) {

        java.util.Arrays.fill(outFade, 0, 256, 1.0F);
        return writeRegionSectionProgressInternal(
                regionOriginBlockX, regionOriginBlockZ,
                drawableSlots, drawableCount,
                outFade,
                isFadeMode());
    }

    public boolean writeRegionSectionScaleValues(
            int regionOriginBlockX, int regionOriginBlockY, int regionOriginBlockZ,
            int[] drawableSlots, int drawableCount,
            float[] outScale) {
        java.util.Arrays.fill(outScale, 0, 256, 1.0F);
        return writeRegionSectionProgressInternal(
                regionOriginBlockX, regionOriginBlockZ,
                drawableSlots, drawableCount,
                outScale,
                isScaleMode());
    }

    private boolean writeRegionSectionProgressInternal(
            int regionOriginBlockX, int regionOriginBlockZ,
            int[] drawableSlots, int drawableCount,
            float[] out,
            boolean active) {
        if (!isEnabled() || !active || drawableSlots == null || drawableCount <= 0) {
            return false;
        }

        int regionChunkX = regionOriginBlockX >> 4;
        int regionChunkZ = regionOriginBlockZ >> 4;

        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());

        if (firstSeenMs.size() >= MAX_TRACKED) {
            evictExpired(now, total);
        }

        boolean hasNonOne = false;
        for (int i = 0; i < drawableCount; i++) {
            int slot = drawableSlots[i] & 0xFF;

            int relX = (slot >> 5) & 7;
            int relZ = (slot >> 2) & 7;
            int sx = regionChunkX + relX;
            int sz = regionChunkZ + relZ;
            long key = ChunkPos.toLong(sx, sz);

            Long start = firstSeenMs.get(key);
            if (start == null && !pendingAnimationKeys.contains(key)) {
                continue;
            }
            if (start == null) {
                pendingAnimationKeys.remove(key);
                firstSeenMs.put(key, now);
                lastAnimRegisterMs = now;

                out[slot] = 0.0F;
                hasNonOne = true;
                continue;
            }
            long elapsed = now - start;
            if (elapsed >= total) {

                continue;
            }

            float progress = (float) elapsed / (float) total;
            out[slot] = progress;
            hasNonOne = true;
        }
        return hasNonOne;
    }

    public float getRegionMagnitude(int regionOriginBlockX, int regionOriginBlockY, int regionOriginBlockZ,
                                    int[] drawableSlots, int drawableCount) {
        if (!isEnabled() || drawableSlots == null || drawableCount <= 0) {
            return 0.0F;
        }

        int regionChunkX = regionOriginBlockX >> 4;
        int regionChunkY = regionOriginBlockY >> 4;
        int regionChunkZ = regionOriginBlockZ >> 4;
        long regionKey = ChunkSectionPos.asLong(regionChunkX, regionChunkY, regionChunkZ);

        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());
        int dist = distance.getInt();

        if (regionFirstSeenMs.size() >= MAX_TRACKED) {
            evictExpiredFrom(regionFirstSeenMs, now, total);
        }

        Long start = regionFirstSeenMs.get(regionKey);
        if (start == null) {
            regionFirstSeenMs.put(regionKey, now);
            lastAnimRegisterMs = now;
            return (float) dist;
        }

        long elapsed = now - start;
        if (elapsed >= total) {
            return 0.0F;
        }

        Interpolation curve = Interpolations.getByName(easing.getSelected());
        double progress = (double) elapsed / (double) total;
        double eased = curve.interpolate(progress);
        return (float) (dist * (1.0 - eased));
    }

    private static void evictExpiredFrom(Map<Long, Long> map, long now, long total) {
        long cutoff = now - 2L * total;
        Iterator<Map.Entry<Long, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) {
                it.remove();
            }
        }
    }

    public void resetTracker() {

        firstSeenMs.clear();
        regionFirstSeenMs.clear();
        playerYAtRegister.clear();

        pendingAnimationKeys.clear();
    }

    public void onF3AReload() {
        firstSeenMs.clear();
        regionFirstSeenMs.clear();
        playerYAtRegister.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }
        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;

        int viewDistance = mc.options.getViewDistance().getValue() + 1;
        boolean queued = false;
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;

                if (mc.world.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false) != null) {
                    pendingAnimationKeys.add(ChunkPos.toLong(cx, cz));
                    queued = true;
                }
            }
        }
        if (queued) lastAnimRegisterMs = System.currentTimeMillis();
    }
}
