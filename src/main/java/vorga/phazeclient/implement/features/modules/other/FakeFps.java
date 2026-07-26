package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Replaces the visible FPS reading with a randomised value drawn
 * from a user-defined {@code [min, max]} range. The substitution
 * happens at the {@link net.minecraft.client.MinecraftClient#getCurrentFps()}
 * call site via {@code MinecraftClientFakeFpsMixin}, so every
 * downstream consumer (FPS HUD, vanilla F3 debug overlay, mods
 * that read the same accessor) sees the same fake value.
 *
 * <h3>Why a randomised range</h3>
 * A static fake value is too obvious - real FPS jitters every
 * tick. The renderer regenerates the random pick on a fixed
 * cadence ({@link #UPDATE_INTERVAL_MS} = 800ms) so the fake
 * reading drifts naturally instead of either freezing on one
 * number or flickering on every frame.
 *
 * <h3>Range semantics</h3>
 * {@code min} and {@code max} are independent ValueSettings; we
 * normalise them before sampling so swapping the two won't break
 * the math, and clamp the inclusive max to {@code Integer.MAX_VALUE - 1}
 * so {@code Random.nextInt(max - min + 1)} stays positive.
 */
public final class FakeFps extends Module {
    private static final FakeFps INSTANCE = new FakeFps();

    /** How often the random fake-fps pick is regenerated, in
     *  milliseconds. Vanilla refreshes its F3 fps line exactly
     *  once per second (the {@code nextDebugInfoUpdateTime}
     *  cadence in {@code MinecraftClient.run}), so resampling at
     *  the same beat keeps the visible number changing in sync
     *  with the real reading and avoids the "fake number jitters
     *  on a different rhythm than real FPS" tell. Also matches
     *  Sodium's HUD update interval. */
    private static final long UPDATE_INTERVAL_MS = 1000L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting minFps = new ValueSetting(
            "Min FPS",
            "Lower bound of the fake FPS range (inclusive)"
    ).range(1, 9999).step(1).setValue(120);
    public final ValueSetting maxFps = new ValueSetting(
            "Max FPS",
            "Upper bound of the fake FPS range (inclusive)"
    ).range(1, 9999).step(1).setValue(240);

    /** Most recent fake FPS sample. Updated every {@link #UPDATE_INTERVAL_MS}. */
    private int lastFps = 0;
    /** Wall-clock timestamp ({@code System.currentTimeMillis}) of the
     *  last sample. Compared against the interval so we only roll a
     *  new pick when enough time has passed - keeps consecutive frames
     *  reading the same value within the same window. */
    private long lastUpdateMs = 0L;
    private final java.util.Random random = new java.util.Random();

    private FakeFps() {
        super("fake_fps", "Fake FPS", ModuleCategory.OTHER);
        minFps.setFullWidth(true);
        maxFps.setFullWidth(true);
        setup(generalSection, minFps, maxFps);
    }

    public static FakeFps getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Replaces the visible FPS counter with a randomised value drawn from your configured range";
    }

    @Override
    public String getIcon() {
        return "fake_fps.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Resolve the current fake FPS reading. Caches the rolled
     * value for {@link #UPDATE_INTERVAL_MS} so subsequent calls
     * within the same window return the identical number; once the
     * window expires, picks a new sample from the (normalised)
     * {@code [min, max]} range.
     *
     * <p>Caller (the mixin) is responsible for the {@link #isEnabled()}
     * gate. We don't check it here so the method can also serve
     * future direct callers (e.g. a debug command).
     */
    public int getFakeFps() {
        long now = System.currentTimeMillis();
        if (lastUpdateMs == 0L || now - lastUpdateMs >= UPDATE_INTERVAL_MS) {
            int lo = Math.round(minFps.getValue());
            int hi = Math.round(maxFps.getValue());
            // Normalise reversed input so swapping the two sliders
            // doesn't produce a negative range argument to nextInt.
            if (lo > hi) {
                int tmp = lo;
                lo = hi;
                hi = tmp;
            }
            int span = hi - lo + 1;
            if (span <= 0) span = 1; // identical lo/hi -> single value
            lastFps = lo + random.nextInt(span);
            lastUpdateMs = now;
        }
        return lastFps;
    }
}
