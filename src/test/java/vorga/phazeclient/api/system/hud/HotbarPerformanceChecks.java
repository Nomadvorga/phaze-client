package vorga.phazeclient.api.system.hud;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vorga.phazeclient.implement.features.modules.other.Animations;

import java.util.Map;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/** CPU/bytecode regression checks, not an in-game FPS or graphical benchmark. */
public final class HotbarPerformanceChecks {
    private static int checks;
    private static final DoubleUnaryOperator SMOOTHNESS = speed -> Animations.getInstance().smoothnessForSpeed((float) speed);

    public static void main(String[] args) throws Exception {
        clockParity();
        settledClock();
        cacheBatching();
        renderContracts();
        System.out.println("Hotbar performance checks passed: " + checks + " (no GL context)");
    }

    private static void clockParity() {
        for (int fps : new int[]{30, 60, 144, 360, 1400}) {
            HotbarSlideAnimation current = new HotbarSlideAnimation();
            LegacyClock old = new LegacyClock();
            Random random = new Random(42);
            long now = 1;
            int selected = 0;
            float[] speeds = {1, 1.5F, 5, 15, 29.5F, 30};
            for (int frame = 0; frame < 6000; frame++) {
                if (frame % 89 == 0) selected = random.nextInt(9);
                if (frame == 1500 || frame == 1800) { current.resetTracking(); old.resetTracking(); }
                boolean rollover = frame % 400 < 300;
                float speed = speeds[(frame / 500) % speeds.length];
                now += frame % 701 == 0 ? 2_000_000_000L : 1_000_000_000L / fps;
                current.advance(selected, rollover, speed, now, SMOOTHNESS);
                old.advance(selected, rollover, speed, now);
                check(Float.floatToIntBits(current.currentX()) == Float.floatToIntBits(old.x), "original trajectory at " + fps + " FPS");
                check(current.mirrorOffset() == old.mirror, "original wrap side at " + fps + " FPS");
            }
        }
        HotbarSlideAnimation wrap = new HotbarSlideAnimation();
        for (int i = 1; i < 500; i++) wrap.advance(8, true, 5, i * 16_666_667L, SMOOTHNESS);
        wrap.advance(0, true, 5, 500 * 16_666_667L, SMOOTHNESS);
        check(wrap.currentX() < 0 && wrap.mirrorOffset() == 180, "8 to 0 follows the short path");
        for (int i = 501; i < 1000; i++) wrap.advance(0, true, 5, i * 16_666_667L, SMOOTHNESS);
        wrap.advance(8, true, 5, 1000 * 16_666_667L, SMOOTHNESS);
        check(wrap.currentX() > 160 && wrap.mirrorOffset() == -180, "0 to 8 follows the short path");
    }

    private static void settledClock() {
        HotbarSlideAnimation clock = new HotbarSlideAnimation();
        int[] evaluations = {0};
        DoubleUnaryOperator counted = speed -> { evaluations[0]++; return SMOOTHNESS.applyAsDouble(speed); };
        for (int i = 1; i <= 10000; i++) clock.advance(0, true, 5, i * 1_000_000L, counted);
        check(evaluations[0] == 0 && clock.currentX() == 0, "idle selector skips decay/smoothness work");
        clock.advance(1, true, 5, 10_001_000_000L, counted);
        check(evaluations[0] == 1 && clock.currentX() < 1, "first change after long idle uses the latest frame delta");
        for (int i = 10002; i < 11000; i++) clock.advance(1, true, 5, i * 1_000_000L, counted);
        check(evaluations[0] == 1, "smoothness is reused until speed changes");
    }

    @SuppressWarnings("unchecked")
    private static void cacheBatching() throws Exception {
        Class<?> bridge = ExordiumAnimationBridge.class;
        var mapField = bridge.getDeclaredField("COMPONENT_BY_BUFFER");
        mapField.setAccessible(true);
        Map<Object, String> components = (Map<Object, String>) mapField.get(null);
        Object buffer = new Object();
        components.put(buffer, ExordiumAnimationBridge.HOTBAR);
        var selectorType = Class.forName(bridge.getName() + "$HotbarSelector");
        var constructor = selectorType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        set("lastHotbarEnabled", true);
        set("hotbarSelector", constructor.newInstance(null, null, 0, 0, 24, 22, null));
        check(!ExordiumAnimationBridge.deferBufferedComponent(buffer), "hotbar cache stays in Exordium's combined batch");
        check((boolean) get("hotbarSubmitted"), "live selector is scheduled separately");
        check(((Map<?, ?>) get("DEFERRED")).isEmpty(), "hotbar never schedules full-screen replay");
        Animations module = Animations.getInstance();
        boolean enabled = module.isEnabled();
        module.setStateSilent(false);
        try {
            ExordiumAnimationBridge.beginHudFrame(null);
            check(!(boolean) get("hotbarSubmitted"), "hidden/unsubmitted hotbar cannot leave a stale selector");
            check(get("hotbarSelector") == null, "turning animation off drops the live selector");
            check(((java.util.Set<?>) get("FORCE_CAPTURE")).contains(ExordiumAnimationBridge.HOTBAR), "turning off recaptures a vanilla selector");
        } finally { module.setStateSilent(enabled); components.remove(buffer); }
    }

    private static void renderContracts() throws Exception {
        MethodNode hook = method(read("vorga/phazeclient/mixins/InGameHudMixin"), "phaze$slideSelection");
        check(!calls(hook, "draw") && !calls(hook, "capture"), "selector hook neither flushes GUI batches nor snapshots GL state");
        MethodNode selector = method(read("vorga/phazeclient/api/system/hud/ExordiumAnimationBridge"), "renderHotbarSelection");
        check(!calls(selector, "drawExordiumTexture") && !calls(selector, "baseModelViewMatrix"), "cached selector adds no full-screen texture/model draw");
        check(!calls(selector, "enableScissor") && !calls(selector, "draw"), "cached selector doesn't split the GUI batch");
        MethodNode crop = method(read("vorga/phazeclient/api/system/hud/HotbarSelectionRenderer"), "drawClipped");
        check(!calls(crop, "draw") && crop.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "resource-pack scaling fallback restores scissor even on failure");
    }

    private static Object get(String name) throws Exception {
        var field = ExordiumAnimationBridge.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void set(String name, Object value) throws Exception {
        var field = ExordiumAnimationBridge.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = HotbarPerformanceChecks.class.getResourceAsStream("/" + name + ".class")) {
            ClassNode result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static MethodNode method(ClassNode type, String name) { return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static boolean calls(MethodNode method, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return true;
        return false;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }

    private static final class LegacyClock {
        float x;
        int last = -1, mirror;
        long previous;
        void resetTracking() { last = -1; previous = 0; mirror = 0; }
        void advance(int selected, boolean rollover, float speed, long now) {
            mirror = 0;
            float target = selected * 20;
            if (rollover && last >= 0) {
                if (selected - last >= 5) x += 180;
                else if (selected - last <= -5) x -= 180;
            }
            last = selected;
            float dt = previous == 0 ? 1F / 60 : Math.min(0.25F, (now - previous) / 1_000_000_000F);
            previous = now;
            x = (x - target) * (float) Math.pow((float) SMOOTHNESS.applyAsDouble(speed), dt) + target;
            if (Math.abs(x - target) < 0.4F) x = target;
            if (rollover) { if (x < 0) mirror = 180; else if (x > 160) mirror = -180; }
        }
    }
}
