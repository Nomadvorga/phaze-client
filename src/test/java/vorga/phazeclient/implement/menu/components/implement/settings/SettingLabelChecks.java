package vorga.phazeclient.implement.menu.components.implement.settings;

/** Run with only SettingLabelState on the classpath: no Minecraft, GL, JUnit or Gradle required. */
public final class SettingLabelChecks {
    // Literal viewport heights keep this standalone suite independent of Minecraft's component classes.
    public static void main(String[] args) {
        fittingAndHoverEntry();
        animationBoundaries();
        oneShotHoverAndReentry();
        fadeClamping();
        layoutChangesAndIsolation();
        firstAttachAndZeroWidth();
        widthGrowthAndShrinkage();
        unchangedWidthTargets();
        rapidWidthReversal();
        widthStateIsolation();
        animatedFadeAndOverflow();
        resetInsetStaysSynchronized();
        adjacentClipEdges();
        System.out.println("Setting label checks PASSED");
    }

    private static SettingLabelState overflowing() {
        SettingLabelState state = new SettingLabelState();
        state.layout("A long translated label", 180, 80, 23.5F, 0);
        return state;
    }

    private static void fittingAndHoverEntry() {
        SettingLabelState state = new SettingLabelState();
        for (float textWidth : new float[]{0, 40, 80}) {
            state.layout("Short", textWidth, 80, 23.5F, 0);
            check(!state.isOverflowing(), "fitting and exactly fitting labels stay unchanged");
            state.updateHover(false, 0);
            state.updateHover(true, 1);
            near(state.offset(Long.MAX_VALUE), 0, "fitting label never animates");
            near(state.alphaAt(80, 0), 1, "fitting label has no fade");
        }
        state = overflowing();
        state.updateHover(false, 0);
        near(state.offset(1_000_000_000), 0, "idle overflow never starts without hover");
        check(!state.isHovered(), "outside card is tracked independently of label overflow");
        state.updateHover(true, 2_000_000_000L);
        check(state.isHovered(), "card entry is tracked");
        near(state.offset(2_450_000_000L), 50, "card entry starts animation");
        state.layout("Long", 180, 0, 23.5F, 2_800_000_000L);
        state.layout("Long", 180, 0, 23.5F, 3_000_000_000L);
        state.updateHover(false, 3_000_000_000L);
        state.updateHover(true, 3_000_000_001L);
        near(state.offset(3_450_000_001L), 0, "zero viewport cannot animate");
    }

    private static void animationBoundaries() {
        SettingLabelState state = overflowing();
        long start = 4_000_000_000L;
        long travel = SettingLabelState.TRAVEL_NANOS;
        long hold = SettingLabelState.HOLD_NANOS;
        long back = SettingLabelState.RETURN_NANOS;
        state.updateHover(true, start);
        near(state.offset(start - 1), 0, "before start is clamped");
        near(state.offset(start), 0, "starts without jumping");
        near(state.offset(start + travel / 2), 50, "outward midpoint");
        near(state.offset(start + travel), 100, "tail revealed at travel boundary");
        near(state.offset(start + travel + hold - 1), 100, "tail held briefly");
        near(state.offset(start + travel + hold), 100, "return starts continuously");
        near(state.offset(start + travel + hold + back / 2), 50, "return midpoint");
        near(state.offset(start + travel + hold + back), 0, "return ends exactly at idle");
        near(state.offset(start + travel + hold + back + 1), 0, "stays idle, never loops automatically");

        state = overflowing();
        state.updateHover(true, start);
        float previous = 0;
        for (int i = 0; i <= 100; i++) {
            float offset = state.offset(start + travel * i / 100);
            check(offset >= previous && offset <= 100, "outward position is bounded and monotonic");
            previous = offset;
        }
        previous = 100;
        for (int i = 0; i <= 100; i++) {
            float offset = state.offset(start + travel + hold + back * i / 100);
            check(offset <= previous && offset >= 0, "return position is bounded and monotonic");
            previous = offset;
        }
    }

    private static void oneShotHoverAndReentry() {
        // nanoTime has no fixed origin and is allowed to be negative.
        for (long start : new long[]{4_000_000_000L, -4_000_000_000L}) {
            SettingLabelState state = overflowing();
            long travel = SettingLabelState.TRAVEL_NANOS;
            long cycle = travel + SettingLabelState.HOLD_NANOS + SettingLabelState.RETURN_NANOS;
            state.updateHover(true, start);
            for (int i = 0; i <= 100; i++) {
                long now = start + cycle * i / 100;
                state.updateHover(true, now);
                check(state.offset(now) >= 0 && state.offset(now) <= 100, "hovered frames remain bounded");
                if (i == 50) near(state.offset(now), 100, "hover frames do not restart outbound travel");
            }
            state.updateHover(true, start + cycle * 4);
            near(state.offset(start + cycle * 4), 0, "continuous hover cannot repeat after completion");
            state.updateHover(false, start + cycle * 4 + 1);
            state.updateHover(true, start + cycle * 4 + 2);
            near(state.offset(start + cycle * 4 + 2 + travel / 2), 50, "leave/re-enter permits another cycle");

            state = overflowing();
            state.updateHover(true, start);
            float beforeLeave = state.offset(start + travel / 2);
            state.updateHover(false, start + travel / 2);
            near(state.offset(start + travel / 2), beforeLeave, "leaving during travel never jumps");
            near(state.offset(start + travel), 100, "cycle finishes even after leaving");
            near(state.offset(start + cycle), 0, "left card still returns to idle");

            state = overflowing();
            state.updateHover(true, start);
            long duringReturn = start + travel + SettingLabelState.HOLD_NANOS + SettingLabelState.RETURN_NANOS / 2;
            state.updateHover(false, duringReturn);
            float beforeEntry = state.offset(duringReturn);
            state.updateHover(true, duringReturn);
            near(state.offset(duringReturn), beforeEntry, "re-entry during return starts continuously from current offset");
            near(state.offset(duringReturn + travel), 100, "fresh entry can reveal the tail again");
        }
    }

    private static void fadeClamping() {
        SettingLabelState state = overflowing();
        near(state.alphaAt(0, 0), 1, "idle has no left fade");
        near(state.alphaAt(80, 0), 0, "idle right edge fades to zero alpha");
        near(state.alphaAt(0, 100), 0, "tail view fades left edge");
        near(state.alphaAt(80, 100), 1, "tail itself is fully opaque");
        near(state.alphaAt(40, 50), 1, "middle stays fully opaque");
        near(state.alphaAt(-1, 50), 0, "outside viewport is transparent");
        near(state.alphaAt(81, 50), 0, "outside right viewport is transparent");
        long now = 0;
        for (float width : new float[]{0.25F, 2, 12, 24, 80}) {
            state.layout("Long", width + 100, width, 20, now);
            now += SettingLabelState.WIDTH_TRANSITION_NANOS;
            state.layout("Long", width + 100, width, 20, now);
            check(state.fadeWidth() <= width / 2, "fade bands cannot overlap in narrow areas");
            for (int x = -10; x < 110; x++) {
                for (float offset : new float[]{-100, 0, 0.01F, 5, 50, 100, 200}) {
                    float alpha = state.alphaAt(width * x / 100, offset);
                    check(Float.isFinite(alpha) && alpha >= 0 && alpha <= 1, "fade alpha is finite and clamped");
                }
            }
        }
    }

    private static void layoutChangesAndIsolation() {
        SettingLabelState first = overflowing();
        SettingLabelState second = overflowing();
        first.updateHover(true, 0);
        near(second.offset(450_000_000), 0, "marquee state is per component");
        first.layout("A long translated label", 180, 80, 23.5F, 450_000_000L);
        near(first.offset(450_000_000), 50, "moving/scrolling parent preserves animation");
        first.layout("A long translated label", 180, 100, 23.5F, 450_000_000L);
        long settled = 450_000_000L + SettingLabelState.WIDTH_TRANSITION_NANOS;
        first.layout("A long translated label", 180, 100, 23.5F, settled);
        first.updateHover(true, settled);
        float phase = (float) settled / SettingLabelState.TRAVEL_NANOS;
        near(first.offset(settled), 80 * phase * phase * (3 - 2 * phase),
                "viewport changes preserve normalized phase rather than restart");
        first.layout("Changed language", 180, 100, 23.5F, settled);
        first.updateHover(true, settled);
        near(first.offset(settled), 0, "translation change clears stale animation");
        first.updateHover(true, 900_000_000);
        near(first.offset(900_000_000), 0, "translation changes do not fabricate hover entries");
        first.updateHover(false, 900_000_001);
        first.updateHover(true, 900_000_002);
        near(first.offset(1_350_000_002), 40, "new translation animates on next actual entry");
        first.layout("Changed language", 80, 100, 23.5F, 1_500_000_000L);
        check(!first.isOverflowing(), "resize to fitting removes marquee");
        near(first.offset(1_500_000_000), 0, "fitting labels remain stationary");
        first.layout("Changed language", 180, 100, 23.5F, 1_500_000_001L);
        first.updateHover(true, 1_500_000_001);
        near(first.offset(2_000_000_000), 0, "becoming overflow during the same hover does not loop");
    }

    private static SettingLabelState widthState(float width, long now) {
        SettingLabelState state = new SettingLabelState();
        widthAt(state, width, now);
        return state;
    }

    private static float widthAt(SettingLabelState state, float target, long now) {
        state.layout("Width label", 180, target, 23.5F, now);
        return state.getWidth();
    }

    private static void firstAttachAndZeroWidth() {
        check(SettingLabelState.WIDTH_TRANSITION_NANOS == 200_000_000L,
                "width transition lasts exactly 200 milliseconds");
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        for (long start : new long[]{0, -4_000_000_000L, 4_000_000_000L}) {
            for (float width : new float[]{0, 0.25F, 2, 12, 80, 200}) {
                SettingLabelState state = widthState(width, start);
                near(state.getWidth(), width, "first attachment snaps without an appear animation");
                near(state.getOverflow(), Math.max(0, 180 - width), "first attachment has immediate geometry");
                check(state.isOverflowing() == (width > 0 && width < 180),
                        "first attachment uses actual viewport for overflow eligibility");
                near(widthAt(state, width, start + duration / 2), width,
                        "first attachment never grows from zero on later frames");
            }
        }

        long start = 1_000_000_000L;
        SettingLabelState state = widthState(80, start);
        near(widthAt(state, 0, start), 80, "zero target starts continuously");
        near(widthAt(state, 0, start + duration / 2), 20, "zero target eases through positive widths");
        check(state.isOverflowing(), "shrinking toward zero remains usable while width is positive");
        near(widthAt(state, 0, start + duration), 0, "zero target settles exactly");
        near(state.fadeWidth(), 0, "zero viewport has no fade band");
        check(!state.isOverflowing(), "zero viewport cannot marquee");
        state.updateHover(true, start + duration);
        near(state.offset(start + duration + SettingLabelState.TRAVEL_NANOS / 2), 0,
                "settled zero viewport stays stationary on hover");

        long reopen = start + duration + SettingLabelState.TRAVEL_NANOS;
        near(widthAt(state, 2, reopen), 0, "reopening a zero viewport animates after first attachment");
        near(widthAt(state, 2, reopen + duration / 2), 1.5F, "narrow viewport grows with ease-out");
        near(state.fadeWidth(), 0.75F, "narrow animated fade is bounded by half the viewport");
        for (float x : new float[]{-1, 0, 0.25F, 0.75F, 1.5F, 2}) {
            float alpha = state.alphaAt(x, 50);
            check(Float.isFinite(alpha) && alpha >= 0 && alpha <= 1,
                    "narrow animated viewport alpha remains finite and bounded");
        }
        near(widthAt(state, 2, reopen + duration), 2, "narrow viewport converges exactly");
    }

    private static void widthGrowthAndShrinkage() {
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        for (long start : new long[]{-4_000_000_000L, 4_000_000_000L}) {
            for (boolean growing : new boolean[]{true, false}) {
                float from = growing ? 40 : 120;
                float target = growing ? 120 : 40;
                SettingLabelState state = widthState(from, start);
                near(widthAt(state, target, start), from, "changed width starts without jumping");
                float previous = from;
                for (int i = 0; i <= 100; i++) {
                    float current = widthAt(state, target, start + duration * i / 100);
                    float t = i / 100.0F;
                    float expected = from + (target - from) * (1 - (1 - t) * (1 - t));
                    near(current, expected, "width follows quadratic ease-out at sample " + i);
                    check(Float.isFinite(current) && current >= 40 && current <= 120,
                            "animated width stays finite and bounded");
                    check(growing ? current >= previous : current <= previous,
                            "animated width converges monotonically");
                    previous = current;
                }
                near(state.getWidth(), target, "width reaches target at exact duration");
                near(widthAt(state, target, start + duration + 1), target, "width stays at target after completion");
                near(widthAt(state, target, start + duration * 100), target, "settled width never loops");
            }
        }
    }

    private static void unchangedWidthTargets() {
        long start = 3_000_000_000L;
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        SettingLabelState state = widthState(40, start);
        widthAt(state, 120, start);
        near(widthAt(state, 120, start + duration / 4), 75, "unchanged target advances the original transition");
        for (int i = 0; i < 10; i++) {
            near(widthAt(state, 120, start + duration / 4), 75, "repeated layout at one timestamp is idempotent");
        }
        near(widthAt(state, 120, start + duration / 2), 100, "unchanged target does not restart easing");
        near(widthAt(state, 120, start + duration), 120, "unchanged target keeps original completion deadline");
        near(widthAt(state, 120, start + duration * 2), 120, "settled repeated target remains exact");
        near(widthAt(state, 80, start + duration * 2), 120, "next transition starts from settled width");
        near(widthAt(state, 80, start + duration * 3), 80, "next transition has its own full duration");
    }

    private static void rapidWidthReversal() {
        long start = 5_000_000_000L;
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        SettingLabelState state = widthState(40, start);
        widthAt(state, 120, start);
        long firstReversal = start + duration / 4;
        // No intervening frame: retargeting itself must sample the in-flight width.
        near(widthAt(state, 20, firstReversal), 75, "reversal samples old trajectory without a stale-width jump");
        near(widthAt(state, 20, firstReversal), 75, "same-time reversal layout remains continuous");
        long secondReversal = firstReversal + duration / 4;
        float secondWidth = 75 + (20 - 75) * 0.4375F;
        near(widthAt(state, 140, secondReversal), secondWidth, "rapid growth retarget starts from sampled shrink width");
        long thirdReversal = secondReversal + duration / 2;
        float thirdWidth = secondWidth + (140 - secondWidth) * 0.75F;
        near(widthAt(state, 40, thirdReversal), thirdWidth, "another reversal samples current growth continuously");
        float previous = thirdWidth;
        for (int i = 0; i <= 100; i++) {
            float current = widthAt(state, 40, thirdReversal + duration * i / 100);
            check(current <= previous && current >= 40 && current <= thirdWidth,
                    "retargeted width remains bounded and monotonic toward final target");
            previous = current;
        }
        near(state.getWidth(), 40, "last reversal gets a full transition and converges exactly");
        near(widthAt(state, 40, thirdReversal + duration * 10), 40, "obsolete targets never resume");
    }

    private static void widthStateIsolation() {
        long start = 7_000_000_000L;
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        SettingLabelState first = widthState(40, start);
        SettingLabelState second = widthState(120, start);
        widthAt(first, 120, start);
        near(second.getWidth(), 120, "another state cannot change an untouched viewport");
        widthAt(second, 20, start + duration / 4);
        near(widthAt(first, 120, start + duration / 2), 100, "second transition does not restart first");
        near(widthAt(first, 40, start + duration / 2), 100, "first state independently reverses");
        near(widthAt(second, 20, start + duration / 2), 76.25F, "first reversal cannot affect second progress");
        near(widthAt(first, 40, start + duration * 3 / 4), 73.75F, "first retains its own reversal clock");
        near(widthAt(second, 20, start + duration * 5 / 4), 20, "second finishes on its own deadline");
        near(widthAt(first, 40, start + duration * 3 / 2), 40, "first finishes on its own deadline");
    }

    private static void animatedFadeAndOverflow() {
        long start = 9_000_000_000L;
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        SettingLabelState state = widthState(100, start);
        state.updateHover(true, start);
        long resize = start + SettingLabelState.TRAVEL_NANOS / 2 - duration / 2;
        widthAt(state, 200, resize);
        check(state.isOverflowing(), "fitting target does not remove overflow before animation advances");
        near(state.getOverflow(), 80, "overflow starts from displayed width rather than target");
        near(widthAt(state, 200, resize + duration / 2), 175, "growing geometry samples animated width");
        near(state.getOverflow(), 5, "overflow follows intermediate width rather than fitting target");
        check(state.isOverflowing(), "text still overflows intermediate width");
        near(state.offset(start + SettingLabelState.TRAVEL_NANOS / 2), 2.5F,
                "marquee distance uses animated overflow while preserving phase");
        near(state.alphaAt(175, 0), 1 - 5 / SettingLabelState.FADE_WIDTH,
                "right-edge fade strength follows the five pixels of animated overflow");
        near(state.alphaAt(176, 0), 0, "area beyond animated edge remains clipped");
        near(state.alphaAt(100, 0), 1, "old viewport edge no longer fades after growth");
        widthAt(state, 200, resize + duration * 3 / 4);
        check(!state.isOverflowing(), "overflow clears as soon as animated width fits text");
        near(state.getOverflow(), 0, "fitting intermediate width has zero overflow");
        near(state.alphaAt(state.getWidth(), 0), 1, "fitting intermediate viewport has no edge fade");
        near(state.offset(resize + duration * 3 / 4), 0, "marquee stops when intermediate viewport fits");

        state = widthState(200, start);
        state.updateHover(true, start);
        widthAt(state, 100, start);
        check(!state.isOverflowing(), "overflowing target does not immediately shrink fitting geometry");
        near(widthAt(state, 100, start + duration / 2), 125, "shrinking geometry uses eased width");
        near(state.getOverflow(), 55, "shrinking overflow is based on displayed width");
        check(state.isOverflowing(), "shrinking intermediate width activates overflow");
        state.updateHover(true, start + duration / 2);
        near(state.offset(start + duration / 2), 0, "animated overflow never fabricates a hover entry");
        near(state.alphaAt(125, 0), 0, "shrinking edge fade follows intermediate viewport");

        state = widthState(40, start);
        widthAt(state, 8, start);
        near(widthAt(state, 8, start + duration / 2), 16, "narrowing viewport has intermediate width");
        near(state.fadeWidth(), 8, "fade clamp follows animated width, not narrow target or old width");
        near(state.alphaAt(16, 0), 0, "narrowing fade ends at current edge");
        check(state.alphaAt(14, 0) > 0 && state.alphaAt(14, 0) < 1,
                "fade band moves continuously with narrowing viewport");
        near(widthAt(state, 8, start + duration), 8, "narrowing viewport reaches target");
        near(state.fadeWidth(), 4, "settled narrow viewport updates fade band");
    }

    private static void resetInsetStaysSynchronized() {
        SettingLabelState state = new SettingLabelState();
        long duration = SettingLabelState.WIDTH_TRANSITION_NANOS;
        state.layout("Label", 180, 100, 23.5F, 0, 0);
        for (int i = 0; i <= 100; i++) {
            float t = i / 100.0F;
            float inset = 10 * (1 - (1 - t) * (1 - t));
            state.layout("Label", 180, 100 - inset, 23.5F, duration * i / 100, inset);
            near(state.getWidth(), 100 - inset, "reset inset is not animated twice");
            near(inset + state.getWidth(), 100, "right clip edge stays anchored while reset icon moves");
        }
        state.layout("Label", 180, 70, 23.5F, duration, 10);
        near(state.getWidth(), 90, "control width change starts from current reset-adjusted viewport");
        state.layout("Label", 180, 72.5F, 23.5F, duration + duration / 2, 7.5F);
        near(state.getWidth(), 77.5F, "control width eases independently of returning reset inset");
        state.layout("Label", 180, 80, 23.5F, duration * 2, 0);
        near(state.getWidth(), 80, "control and reset finish together without extra lag");
    }

    private static void adjacentClipEdges() {
        for (float scale : new float[]{0.9F, 1, 1.1F, 1.25F, 1.7F, 2.5F}) {
            float translation = 37.37F;
            int previousRight = Integer.MIN_VALUE;
            for (int i = 0; i < 8; i++) {
                float from = SettingLabelState.snapClipEdge(10.3F + i * 1.5F, scale, translation);
                float to = SettingLabelState.snapClipEdge(10.3F + (i + 1) * 1.5F, scale, translation);
                int left = Math.round(from * scale + translation);
                int right = left + Math.round((to - from) * scale);
                check(i == 0 || left == previousRight, "adjacent clipped bands have no gap or double blend");
                previousRight = right;
            }
        }
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
