package vorga.phazeclient.base.util.render;

/** Standalone, offline checks: compile only this class and GlintTextureClock. */
public final class GlintTextureClockChecks {
    private static int checks;

    public static void main(String[] args) {
        defaultIsVanilla();
        slowAndFastElapsed();
        sliderChangesAreContinuous();
        fractionalTimeAndRepeatedPasses();
        disableIsVanillaIdentity();
        backwardsInputDoesNotReverse();
        vanillaOptionStillMultipliesElapsed();
        System.out.println("Glint texture clock checks passed: " + checks);
    }

    private static void defaultIsVanilla() {
        GlintTextureClock clock = new GlintTextureClock();
        long epoch = 9_000_000_000_000L;
        for (long elapsed : new long[]{0, 1, 17, 1000, 60_000, 86_400_000}) {
            equal(epoch + elapsed, clock.sample(epoch + elapsed, true, 1.0), "default phase is vanilla");
        }
    }

    private static void slowAndFastElapsed() {
        long epoch = 9_000_000_000_000L;
        GlintTextureClock slow = new GlintTextureClock();
        GlintTextureClock fast = new GlintTextureClock();
        equal(epoch, slow.sample(epoch, true, 0.2), "slow starts at vanilla phase");
        equal(epoch, fast.sample(epoch, true, 4.0), "fast starts at vanilla phase");
        equal(200, slow.sample(epoch + 1000, true, 0.2) - epoch, "0.2x elapsed texture time");
        equal(4000, fast.sample(epoch + 1000, true, 4.0) - epoch, "4x elapsed texture time");
    }

    private static void sliderChangesAreContinuous() {
        GlintTextureClock clock = new GlintTextureClock();
        long epoch = 9_000_000_000_000L;
        clock.sample(epoch, true, 0.5);
        long phase = clock.sample(epoch + 1000, true, 0.5);
        equal(epoch + 500, phase, "initial slow interval");
        equal(phase, clock.sample(epoch + 1000, true, 4.0), "fast slider change does not jump");
        phase = clock.sample(epoch + 1100, true, 4.0);
        equal(epoch + 900, phase, "new interval uses fast speed");
        equal(phase, clock.sample(epoch + 1100, true, 0.2), "slow slider change does not reset");
        equal(epoch + 920, clock.sample(epoch + 1200, true, 1.0), "speed observation integrates previous interval");
        equal(epoch + 1020, clock.sample(epoch + 1300, true, 1.0), "return to default keeps phase offset");
        phase = clock.sample(epoch + 1300, true, 1.0);
        for (int step = 2; step <= 40; step++) {
            equal(phase, clock.sample(epoch + 1300, true, step / 10.0), "dragging cannot rescale epoch");
        }
    }

    private static void fractionalTimeAndRepeatedPasses() {
        GlintTextureClock clock = new GlintTextureClock();
        long epoch = 100_000;
        clock.sample(epoch, true, 0.2);
        long previous = epoch;
        for (int ms = 1; ms <= 1000; ms++) {
            long phase = clock.sample(epoch + ms, true, 0.2);
            check(phase >= previous, "slow clock remains monotonic");
            equal(phase, clock.sample(epoch + ms, true, 0.2), "item/armor passes do not advance twice");
            previous = phase;
        }
        check(Math.abs(previous - epoch - 200) <= 1, "fractional slow intervals accumulate instead of freezing");
    }

    private static void disableIsVanillaIdentity() {
        GlintTextureClock clock = new GlintTextureClock();
        equal(1000, clock.sample(1000, false, 4.0), "disabled first sample is vanilla");
        clock.sample(2000, true, 4.0);
        equal(6000, clock.sample(3000, true, 4.0), "active clock acquires an offset");
        equal(3000, clock.sample(3000, false, 4.0), "disable immediately restores exact vanilla time");
        equal(20_000, clock.sample(20_000, false, 0.2), "disabled ignores speed and old phase");
        equal(30_000, clock.sample(30_000, true, 0.5), "reenable anchors without integrating disabled gap");
        equal(30_500, clock.sample(31_000, true, 0.5), "reenable uses new speed");
    }

    private static void backwardsInputDoesNotReverse() {
        GlintTextureClock clock = new GlintTextureClock();
        clock.sample(1000, true, 1.0);
        equal(1100, clock.sample(1100, true, 1.0), "forward interval");
        equal(1100, clock.sample(1050, true, 1.0), "backwards input cannot reverse phase");
        equal(1100, clock.sample(1100, true, 1.0), "recovery cannot count interval twice");
        equal(1110, clock.sample(1110, true, 1.0), "only fresh elapsed time is integrated");
    }

    private static void vanillaOptionStillMultipliesElapsed() {
        for (double option : new double[]{0.0, 0.25, 0.5, 1.0}) {
            GlintTextureClock clock = new GlintTextureClock();
            long start = clock.sample(100_000, true, 2.0);
            long end = clock.sample(101_000, true, 2.0);
            // setupGlintTexturing's unchanged downstream expression, before UV modulo.
            long phaseDelta = (long) (end * option * 8.0) - (long) (start * option * 8.0);
            equal((long) (2000 * option * 8.0), phaseDelta, "vanilla option composes, including zero");
        }
    }

    private static void equal(long expected, long actual, String message) {
        check(expected == actual, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
