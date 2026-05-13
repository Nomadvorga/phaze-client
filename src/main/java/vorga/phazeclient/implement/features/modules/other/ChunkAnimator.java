package vorga.phazeclient.implement.features.modules.other;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Animates freshly-uploaded chunk sections falling into place from
 * above. Hooks the per-section model-offset uniform that
 * {@code WorldRenderer.renderLayer} sets right before each
 * {@code VertexBuffer.draw()} (see
 * {@link vorga.phazeclient.mixins.WorldRendererChunkAnimatorMixin}),
 * so the offset is applied on the GPU side without touching any
 * chunk geometry, builder thread, or render-section state.
 *
 * <p>Per-section animation state is keyed by {@link
 * ChunkSectionPos#asLong(int, int, int)} (origin block pos shifted
 * by 4) so re-entering a previously animated section reuses its
 * timestamp and the chunk doesn't pop back up. Map growth is
 * bounded by lazy eviction inside {@link #getYOffset(BlockPos)} -
 * entries older than {@code 2 * duration} are dropped on the next
 * read of any section, which keeps the map at ~ render-distance^2
 * live entries in steady state.
 *
 * <p>The easing dropdown reuses the shared {@link Interpolations}
 * curves so the dropdown stays consistent with the other Phaze
 * animation modules. {@code Decelerate} is the default - it lands
 * the chunk with a soft stop, which reads more like vanilla chunk
 * fade than the original Forge Chunk Animator's bouncy
 * back-easing.
 */
public final class ChunkAnimator extends Module {
    private static final ChunkAnimator INSTANCE = new ChunkAnimator();

    /**
     * Hard cap on tracked sections. With Render Distance 32 a player
     * can see ~33k sections; in practice the map stabilises far
     * lower because animations finish and lazy eviction removes
     * them. The cap is a guard against pathological cases (rapid
     * teleporting across thousands of sections) where eviction
     * hasn't kicked in yet.
     */
    private static final int MAX_TRACKED = 100_000;

    private final Map<Long, Long> firstSeenMs = new ConcurrentHashMap<>();

    /**
     * Wall-clock timestamp of the most recently registered first-seen
     * entry in EITHER {@link #firstSeenMs} or {@link #regionFirstSeenMs}.
     * Used as the steady-state fast path: once {@code now - lastRegisterMs > duration},
     * every animation we've ever started has finished, so the mixin
     * inject sites can return immediately without iterating drawable
     * slots, querying GL state, or doing any uniform uploads.
     *
     * <p>Why this is correct: new entries can only be added (a) when
     * a section first becomes drawable in {@link #writeRegionSectionYOffsets},
     * (b) when a region first appears in {@link #getRegionMagnitude},
     * or (c) when {@link #getYOffset} is called for a fresh column.
     * Each of those updates this field. While the player is stationary
     * and no new chunks load, nothing updates the field; once
     * {@code duration} ms have passed since the last update, the math
     * in those same methods would produce zero offset for every entry.
     * Skipping the per-region work entirely is therefore observationally
     * indistinguishable from doing it.
     *
     * <p>Declared {@code volatile} because the writers (render thread
     * via {@code writeRegionSectionYOffsets} / {@code getRegionMagnitude}
     * and worker threads via {@code getYOffset}) and the readers
     * (mixin inject sites on the render thread) need a happens-before
     * relationship without paying for full synchronization. A coarse
     * timestamp is intrinsically tolerant of being slightly stale.
     */
    private volatile long lastAnimRegisterMs = 0L;

    /**
     * Steady-state fast-path for the Sodium mixin inject sites.
     * Returns {@code true} only if at least one animation could
     * conceivably still be in progress; returns {@code false} once
     * {@code duration} ms have passed since the last animation
     * registration, allowing the per-region setModelMatrixUniforms
     * hook to bail before any work. Avoids the ~25-30% steady-state
     * FPS hit we'd otherwise pay from glGetInteger + iterator
     * allocation + map lookup per region per program per frame
     * (Render Distance 12 = ~30 regions * 4 passes = 120 calls/frame
     * even with no chunks loading).
     */
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

    /**
     * Per-region first-seen tracker for the shadered fallback path.
     * Keyed by the region's chunk-space origin packed via
     * {@link ChunkSectionPos#asLong}. Separate from
     * {@link #firstSeenMs} because the two paths animate at
     * different granularities:
     *
     * <ul>
     *   <li>{@link #firstSeenMs} - per (sx,sy,sz) section, used by
     *       the per-section uniform path. New sections appearing
     *       inside an already-seen region animate independently.</li>
     *   <li>{@link #regionFirstSeenMs} - per region, used by
     *       {@link #getRegionMagnitude}. The region animates exactly
     *       once on its FIRST appearance; new sections later joining
     *       the same region piggy-back onto the (now zero) region
     *       offset instead of re-snapping the whole 8x4x8 block.</li>
     * </ul>
     *
     * <p>The previous implementation reused {@link #firstSeenMs} for
     * the region path and bumped the whole region to full distance
     * the moment ANY new section was first seen. Under elytra flight
     * a single region routinely accumulates 10+ new sections before
     * the player flies past it, which manifested as the region
     * "snapping back to full distance" once per new section -
     * exactly the looping symptom under shaders.
     */
    private final Map<Long, Long> regionFirstSeenMs = new ConcurrentHashMap<>();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting duration = new ValueSetting(
            "Duration",
            "Animation length in milliseconds. Lower = snappier."
    ).range(100, 3000).step(50).setValue(600);
    public final ValueSetting distance = new ValueSetting(
            "Distance",
            "How many blocks chunks travel before reaching their final position."
    ).range(8, 256).step(1).setValue(64);
    /**
     * Direction the animation enters from. {@code Bottom} reproduces
     * the classic "chunks rise up out of the ground" effect: each
     * section first appears {@code distance} blocks below its real
     * position and slides up to land. {@code Side} routes the entry
     * along the chosen cardinal direction instead - chunks slide in
     * horizontally from {@link #directionSide}.
     */
    public final SelectSetting animationType = new SelectSetting(
            "Animation Type",
            "Direction chunks enter from. Top = fall from above, Bottom = rise from below, Side = slide in from a chosen cardinal direction."
    ).value("Top", "Bottom", "Side").selected("Bottom");
    /**
     * Cardinal direction for {@link #animationType} = {@code Side}.
     * Hidden when the type is {@code Bottom} via the visibility
     * supplier so the operator only sees the side picker when it
     * actually applies. Mapping to world axes follows Minecraft
     * conventions: North = -Z, South = +Z, East = +X, West = -X;
     * the offset places each chunk on that side of its final
     * position so the slide-in lands at the home cell.
     */
    public final SelectSetting directionSide = new SelectSetting(
            "Side",
            "Cardinal direction chunks slide in from when Animation Type is Side."
    ).value("North", "South", "East", "West").selected("South")
            .visible(() -> animationType.isSelected("Side"));
    public final SelectSetting easing = new SelectSetting(
            "Easing",
            "Easing curve for the slide. Decelerate gives a soft landing; Bounce / Elastic add an overshoot."
    ).value(Interpolations.getAllNames()).selected("Decelerate");

    private ChunkAnimator() {
        super("chunk_animator", "Chunk Animator", ModuleCategory.UTILITIES);
        duration.setFullWidth(true);
        distance.setFullWidth(true);
        animationType.setFullWidth(true);
        directionSide.setFullWidth(true);
        easing.setFullWidth(true);
        // Order matches the requested UI layout: Animation Type and
        // its dependent Side picker sit between Distance and Easing
        // so the operator picks WHERE chunks enter from before
        // tweaking HOW they ease in.
        setup(generalSection, duration, distance, animationType, directionSide, easing);
    }

    public static ChunkAnimator getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Animates loaded chunks sliding smoothly into place from below or from a chosen direction";
    }

    /**
     * Fills {@code out[0..2]} with the unit-length axis the animation
     * enters along. The vector points from the section's <em>final</em>
     * world position toward where it visually <em>starts</em>; in other
     * words, multiplying the per-section offset magnitude by this
     * vector and adding to the position gives the section's apparent
     * location at that frame.
     *
     * <ul>
     *   <li>{@code Top}:    {@code (0, +1, 0)} - sections start
     *       above their final position and fall down (the original
     *       "chunks dropping from sky" feel).</li>
     *   <li>{@code Bottom}: {@code (0, -1, 0)} - sections start
     *       below their final position and rise up.</li>
     *   <li>{@code Side / North}: {@code (0, 0, -1)} - sections start
     *       to the north (more negative Z) and slide south.</li>
     *   <li>{@code Side / South}: {@code (0, 0, +1)}.</li>
     *   <li>{@code Side / East}:  {@code (+1, 0, 0)}.</li>
     *   <li>{@code Side / West}:  {@code (-1, 0, 0)}.</li>
     * </ul>
     *
     * <p>Caller-provided buffer keeps this hot-path call allocation-
     * free; render-thread uses are expected to keep a single
     * reusable {@code float[3]}. Unknown / disabled selections fall
     * back to {@code Bottom} so a corrupted config can't crash the
     * renderer.
     */
    public void writeAnimationDirection(float[] out) {
        if (out == null || out.length < 3) return;
        if (animationType.isSelected("Top")) {
            // Top: chunks first appear above their final position and
            // fall downward as the offset magnitude eases to zero.
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
            // unknown side -> fall through to Bottom default
        }
        // Bottom (default): chunks first appear below their final
        // position and slide upward as the offset magnitude eases
        // to zero.
        out[0] = 0.0F; out[1] = -1.0F; out[2] = 0.0F;
    }

    @Override
    public String getIcon() {
        return "chunk_animator.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Y offset (in blocks) the mixin should add to the chunk's model
     * offset uniform this frame. Returns 0 when the module is off,
     * the section's animation already finished, or this is the first
     * time we're seeing the section (in which case the start time is
     * recorded for next-frame interpolation; one frame at full drop
     * is imperceptible compared to the typical 600 ms duration).
     *
     * <p>Always returns a non-negative offset - chunks fall DOWN to
     * their target position, never overshoot below it.
     */
    public float getYOffset(BlockPos origin) {
        if (!isEnabled() || origin == null) {
            return 0.0F;
        }

        // Per-column key (X,Z only) - all sections at the same chunk
        // X,Z share one first-seen timestamp so they animate in sync
        // instead of each Y stratum falling on its own clock. Without
        // this, sections of one chunk column become drawable at
        // slightly different times (Sodium uploads them as the build
        // tasks complete), and each starts its own independent
        // animation - producing the "fragmented chunk strips floating
        // at different heights" artifact reported by users under
        // sustained loading (elytra flight, low render-distance
        // pop-in, etc.).
        long key = ChunkPos.toLong(origin.getX() >> 4, origin.getZ() >> 4);
        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());
        int dist = distance.getInt();

        Long start = firstSeenMs.get(key);
        if (start == null) {
            // Lazy eviction: amortise the cleanup cost against the
            // first-touch latency instead of running a timer thread.
            // Triggered only when the map gets fat enough to matter.
            if (firstSeenMs.size() >= MAX_TRACKED) {
                evictExpired(now, total);
            }
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
        // (1 - eased) inverts the curve so we start at full drop and
        // approach 0 - the easing shapes the arrival, not the launch.
        double offset = dist * (1.0 - eased);
        // Clamp negative values from overshooting easings (Bounce,
        // Elastic) so the chunk never dips BELOW its final position
        // while landing - looks wrong with terrain.
        if (offset < 0.0) {
            offset = 0.0;
        }
        return (float) offset;
    }

    /** Drops sections whose animation finished comfortably in the past. */
    private void evictExpired(long now, long total) {
        evictExpiredFrom(firstSeenMs, now, total);
    }

    /**
     * Per-section Y offsets for one Sodium {@link
     * net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion}
     * packed into the layout the patched terrain vertex shader expects.
     *
     * <p>Sodium's chunk shader receives a per-vertex {@code _draw_id}
     * encoded as {@code (relX << 5) | (relZ << 2) | relY} with masks
     * {@code 7, 3, 7} (see Sodium's {@code chunk_vertex.glsl} -
     * {@code _get_relative_chunk_coord}). The output array uses the
     * same encoding so the shader can do {@code u_PhazeChunkAnimY[draw_id]}
     * and pick the right offset for the section that vertex belongs
     * to. {@code 8 * 4 * 8 = 256} entries cover every possible
     * section slot in a region; unused slots receive {@code 0.0F}
     * which is the no-op identity for the addition the shader does.
     *
     * <p>Each section's offset is computed exactly the same way as
     * the vanilla path: the per-section first-seen timestamp drives
     * the configured easing curve, and the offset is the distance
     * setting times {@code (1 - eased)}. So a freshly-seen section
     * inside an already-known region animates from the full drop
     * height the same way it would on the vanilla renderer - which
     * is what fixes the "elytra flight only animates whole regions"
     * bug under Sodium.
     *
     * <p>Allocates a fresh 1KB array each call. Even at render
     * distance 32 with ~30 visible regions per frame that's
     * {@code 30 * 1KB = 30KB} of throwaway allocation per frame,
     * which the GC absorbs without measurable impact.
     *
     * @param regionOriginBlockX region's block-coord X origin
     *                           (from {@code RenderRegion.getOriginX()})
     * @param regionOriginBlockY region's block-coord Y origin
     * @param regionOriginBlockZ region's block-coord Z origin
     * @return float[256] indexed by the shader's encoded draw_id,
     *         or all-zero array when the module is off
     */
    public boolean writeRegionSectionYOffsets(
            int regionOriginBlockX, int regionOriginBlockY, int regionOriginBlockZ,
            int[] drawableSlots, int drawableCount,
            float[] outOffsets) {
        // Always zero the output buffer so the caller can re-use it
        // across regions without seeing stale values from the previous
        // upload. java.util.Arrays.fill is intrinsified and faster
        // than a Java-level loop.
        java.util.Arrays.fill(outOffsets, 0, 256, 0.0F);
        if (!isEnabled() || drawableSlots == null || drawableCount <= 0) {
            return false;
        }

        // Region origin is in blocks; the section grid in Sodium is
        // 8 (X) * 4 (Y) * 8 (Z) sections. Each section is 16 blocks
        // on a side, so >> 4 turns the region origin into the
        // section/chunk coord of the region's first section.
        int regionChunkX = regionOriginBlockX >> 4;
        int regionChunkY = regionOriginBlockY >> 4;
        int regionChunkZ = regionOriginBlockZ >> 4;

        long now = System.currentTimeMillis();
        long total = Math.max(1L, (long) duration.getInt());
        int dist = distance.getInt();
        Interpolation curve = Interpolations.getByName(easing.getSelected());

        // One eviction per region per frame at most - cheap, and
        // keeps the map from growing unbounded if the player is
        // teleporting around dropping section trackers everywhere.
        if (firstSeenMs.size() >= MAX_TRACKED) {
            evictExpired(now, total);
        }

        // Iterate ONLY the sections actually being drawn this frame
        // (Sodium's ChunkRenderList.sectionsWithGeometryIterator
        // produces these slot indices). Sections without geometry
        // - either not yet uploaded or unloaded - keep their slots
        // at the array's default 0.0F. This is what gives us the
        // per-section animation: as the player flies into a region,
        // sections finish uploading one by one, each registers a
        // first-seen timestamp at that moment, and animates from
        // full distance back to 0 over `duration` ms - matching the
        // vanilla path's per-section feel.
        boolean hasNonZero = false;
        for (int i = 0; i < drawableCount; i++) {
            int slot = drawableSlots[i] & 0xFF;
            // Decode (relX, relZ) from the slot index using the same
            // packing the shader's _get_relative_chunk_coord expects:
            // pos >> (5, 2) & (7, 7). relY (bits 0-1) is intentionally
            // not decoded - the per-column animation key shares one
            // timestamp across all 24 Y strata of a chunk column.
            int relX = (slot >> 5) & 7;
            int relZ = (slot >> 2) & 7;

            int sx = regionChunkX + relX;
            int sz = regionChunkZ + relZ;
            // Per-column (X,Z) key, NOT per-section (X,Y,Z). All
            // 24 sections of a single chunk column share one anim
            // timestamp so they slide in as a coherent vertical
            // strip. The previous per-section keying caused each
            // Y stratum of one chunk to start its own independent
            // animation - sections finish their build/upload at
            // slightly different times, so the "first seen" frame
            // differs per Y, producing the floating-fragments
            // artifact the user reported. ChunkPos.toLong packs
            // (x,z) into a long the same way ChunkSectionPos
            // packs (x,y,z), so the map keying remains O(1).
            long key = ChunkPos.toLong(sx, sz);

            Long start = firstSeenMs.get(key);
            float offset;
            if (start == null) {
                firstSeenMs.put(key, now);
                lastAnimRegisterMs = now;
                offset = (float) dist;
            } else {
                long elapsed = now - start;
                if (elapsed >= total) {
                    // Common steady-state path: animation finished
                    // long ago, leave the buffer slot at 0 and skip
                    // the slot write entirely so the byte ends up in
                    // the GPU upload as a no-op.
                    continue;
                }
                double progress = (double) elapsed / (double) total;
                double eased = curve.interpolate(progress);
                double off = dist * (1.0 - eased);
                offset = off < 0.0 ? 0.0F : (float) off;
                if (offset == 0.0F) {
                    // The easing landed exactly on 0 (Bounce can do
                    // this mid-curve). Skip the assign so we don't
                    // flag hasNonZero on what the GPU sees as a
                    // no-op anyway.
                    continue;
                }
            }

            outOffsets[slot] = offset;
            hasNonZero = true;
        }
        return hasNonZero;
    }

    /**
     * Region-level magnitude for the Sodium fallback path. Used when
     * the per-section shader patch couldn't apply - typically because
     * Iris is active with a shader pack and the chunk programs are
     * compiled from the shader pack source instead of Sodium's
     * default {@code block_layer_opaque.vsh}, so our injected
     * {@code u_PhazeChunkAnimOffset} / {@code u_PhazeChunkAnimDir}
     * uniforms aren't present.
     *
     * <p>Returns the region's current offset magnitude based on
     * {@link #regionFirstSeenMs} - the region animates from
     * {@code distance} down to {@code 0} over {@code duration} ms
     * once, starting the first time this method is called for the
     * region. New sections appearing within the same region while
     * its animation is already in progress (or already finished) do
     * NOT extend or restart the animation; they appear at whatever
     * the region's current offset is. This is the only way to avoid
     * the region "snapping back to full distance" each time a new
     * section first becomes drawable inside it, which is the bulk
     * of what happens during sustained elytra flight.
     *
     * <p>Side-effect: registers first-seen time in
     * {@link #regionFirstSeenMs} for the region (not the section).
     * The {@code drawableSlots} / {@code drawableCount} parameters
     * are still accepted for API symmetry with
     * {@link #writeRegionSectionYOffsets} but are only used as the
     * "is this region drawable at all this frame?" signal - if
     * {@code drawableCount <= 0} we return zero magnitude without
     * registering anything.
     */
    public float getRegionMagnitude(int regionOriginBlockX, int regionOriginBlockY, int regionOriginBlockZ,
                                    int[] drawableSlots, int drawableCount) {
        if (!isEnabled() || drawableSlots == null || drawableCount <= 0) {
            return 0.0F;
        }

        // Region-level animation: each region animates exactly once,
        // when its first set of drawable sections becomes visible.
        // Subsequent new sections appearing within the same region
        // (which is the common case during sustained elytra flight)
        // do NOT re-trigger the animation - they just appear at the
        // region's current offset (settling toward 0). Without this
        // single-shot per-region behaviour, every new section in an
        // already-seen region would force the whole 8x4x8 block back
        // to full distance, producing the user-visible "chunks snap
        // and fall ~10 times in a row" symptom under shaders.
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

    /** Shared eviction primitive for both per-section and per-region trackers. */
    private static void evictExpiredFrom(Map<Long, Long> map, long now, long total) {
        long cutoff = now - 2L * total;
        Iterator<Map.Entry<Long, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) {
                it.remove();
            }
        }
    }

    /**
     * Reset all per-section animation timestamps. Wired from the
     * {@code onGameJoin} / {@code onPlayerRespawn} S2C packet
     * handlers, which fire exactly once per real {@code ClientWorld}
     * swap. Without this hook the tracker would still hold
     * "already animated" timestamps from the previous world and
     * newly-loaded chunks at colliding section coords would skip
     * the animation.
     *
     * <p>Safe to call when the module is disabled - the map is just
     * idle in that state, and clearing it has no side effect on
     * vanilla.
     */
    public void resetTracker() {
        // Called from ClientPlayNetworkHandlerChunkAnimatorResetMixin
        // on onGameJoin / onPlayerRespawn only - i.e. exactly once
        // per actual ClientWorld swap. The old reload()-based hook
        // was removed because Iris fires reload() in bursts on every
        // pipeline rebuild (shader pack apply, settings change, etc.),
        // which would clear an in-flight animation mid-fall.
        firstSeenMs.clear();
        regionFirstSeenMs.clear();
    }
}
