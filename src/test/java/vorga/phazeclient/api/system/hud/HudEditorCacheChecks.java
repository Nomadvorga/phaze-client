package vorga.phazeclient.api.system.hud;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic cadence/transform checks and contracts for the real editor dispatch. No GL/FPS claim. */
public final class HudEditorCacheChecks {
    private static final String HUD = "vorga/phazeclient/api/system/hud/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        cadence();
        policy();
        transformedReplay();
        dispatch();
        captures();
        System.out.println("HUD editor cache checks passed: " + checks + " (no game FPS benchmark)");
    }

    private static void cadence() {
        for (int fps : new int[]{10, 30, 60, 120, 144, 180, 240, 360}) {
            HudAnimationRefreshClock clock = new HudAnimationRefreshClock(fps);
            long interval = 1_000_000_000L / fps;
            long start = 8_000_000_000L;
            check(clock.due(start), "first capture is due");
            clock.captured(start);
            check(!clock.due(start + interval - 1) && clock.due(start + interval), "nanosecond deadline is exact: " + fps);
            check(clock.due(start + interval), "due queries do not consume a failed/skipped capture");
            clock.captured(start + interval * 3 + interval / 3);
            check(!clock.due(start + interval * 4 - 1) && clock.due(start + interval * 4), "late frames retain cadence phase");
            clock.reset();
            check(clock.due(start), "explicit invalidation is immediate");

            for (int displayFps : new int[]{500, 1000, 2000}) {
                clock.reset();
                int count = 0;
                for (int frame = 0; frame < displayFps * 2; frame++) {
                    long now = start + frame * 1_000_000_000L / displayFps;
                    if (clock.due(now)) {
                        clock.captured(now);
                        count++;
                        check(!clock.due(now), "one successful capture per display frame");
                    }
                }
                check(Math.abs(count - fps * 2) <= 1, "uncapped rendering does not increase content refresh: " + fps + "/" + displayFps);
            }
            clock.setFps(fps == 10 ? 30 : 10);
            check(clock.due(start), "changing the refresh setting resets its schedule");
        }

        // A selected static widget has its own lower budget even if neighbours have a live background.
        HudAnimationRefreshClock parent = new HudAnimationRefreshClock(120);
        HudAnimationRefreshClock selected = new HudAnimationRefreshClock(30);
        int parentCount = 0, selectedCount = 0;
        for (int frame = 0; frame < 2000; frame++) {
            long now = 12_000_000_000L + frame * 1_000_000L;
            if (!parent.due(now)) continue;
            parent.captured(now);
            parentCount++;
            if (selected.due(now)) {
                selected.captured(now);
                selectedCount++;
            }
        }
        check(parentCount == 240 && selectedCount == 60, "child captures retain their own rate inside a faster batch");
    }

    private static void policy() {
        for (int configured : new int[]{10, 30, 60, 120}) {
            for (int blurFps : new int[]{10, 60, 120, 180, 240, 360}) {
                check(HudRefreshPolicy.batch(configured, false, false, blurFps) == configured,
                        "idle chat/menu keep the configured budget");
                check(HudRefreshPolicy.batch(configured, true, false, blurFps) == Math.max(configured, 120),
                        "existing animated background minimum is preserved");
                check(HudRefreshPolicy.batch(configured, true, true, blurFps) == Math.max(configured, Math.max(120, blurFps)),
                        "shared blur budget is preserved");
                for (boolean gradient : new boolean[]{false, true}) for (boolean blur : new boolean[]{false, true}) {
                    int expected = Math.max(configured, Math.max(gradient ? 120 : 1, blur ? blurFps : 1));
                    check(HudRefreshPolicy.edited(configured, gradient, blur, blurFps) == expected,
                            "selected backgrounds use settings, not mouse/display FPS");
                }
            }
        }
        check(HudRefreshPolicy.edited(0, false, true, 999) == 360, "refresh settings have bounded safe limits");
    }

    private static void transformedReplay() {
        Random random = new Random(0xD12A6);
        for (int i = 0; i < 10_000; i++) {
            int fw = 320 + random.nextInt(2000), fh = 240 + random.nextInt(1200);
            float x = random.nextFloat() * (fw - 100), y = random.nextFloat() * (fh - 60);
            float width = 16 + random.nextFloat() * 80, height = 12 + random.nextFloat() * 48;
            float scale = 0.5F + random.nextFloat() * 5.5F, padding = random.nextFloat() * 20;
            HudCachedTransform cached = HudCachedTransform.capture(x, y, width, height, scale, padding, fw, fh);
            check(cached != null, "visible positive bounds produce a crop");
            check(cached.left() >= 0 && cached.top() >= 0 && cached.right() <= fw && cached.bottom() <= fh,
                    "crop is inside the texture");
            check(cached.left() <= x && cached.top() <= y && cached.right() >= x + width && cached.bottom() >= y + height,
                    "crop keeps the full widget and fractional edges");
            float liveX = random.nextFloat() * fw, liveY = random.nextFloat() * fh;
            float liveScale = 0.5F + random.nextFloat() * 5.5F;
            close(cached.mapX(x, liveX, liveScale), liveX, "mouse translation is live");
            close(cached.mapY(y, liveY, liveScale), liveY, "vertical translation is live");
            close(cached.mapX(x + width, liveX, liveScale), liveX + width * liveScale / scale, "resizing changes replay width");
            close(cached.mapY(y + height, liveY, liveScale), liveY + height * liveScale / scale, "resizing changes replay height");
            check(cached.x() == x && cached.y() == y && cached.scale() == scale, "live motion never overwrites the published transform");
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            check(HudCachedTransform.capture(invalid, 10, 20, 20, 1, 4, 200, 100) == null, "invalid position rejected");
            check(HudCachedTransform.capture(10, 10, 20, 20, invalid, 4, 200, 100) == null, "invalid scale rejected");
            check(HudCachedTransform.capture(10, 10, 20, 20, 1, invalid, 200, 100) == null, "invalid crop padding rejected");
        }
        check(HudCachedTransform.capture(10, 10, 0, 20, 1, 4, 200, 100) == null, "empty widget rejected");
        check(HudCachedTransform.capture(250, 10, 20, 20, 1, 4, 200, 100) == null, "offscreen widget has no replay");
        check(HudCachedTransform.capture(10, 10, 20, 20, 1, 4, 0, 100) == null, "minimized target rejected");
    }

    private static void dispatch() throws Exception {
        ClassNode hud = read("vorga/phazeclient/implement/hud/CustomHudRenderer");
        MethodNode frame = method(hud, "renderCustomHud");
        int logic = index(frame, "run"), refresh = index(frame, "shouldRefresh"), blit = index(frame, "blit");
        check(logic < refresh && refresh < blit, "live input runs before deciding whether to capture");
        check(index(frame, "drawIsolatedHud") > blit && index(frame, "renderLiveHudEditor") > blit,
                "selected replay and handles follow the shared cache");
        for (MethodInsnNode call : calls(frame)) if (call.name.equals("shouldRefresh")) {
            check(call.getPrevious().getOpcode() == Opcodes.ICONST_0, "chat/mouse state never forces a capture");
        }
        MethodNode input = method(hud, "updateCachedHudInteractions");
        check(calls(input).stream().noneMatch(call -> call.name.equals("invalidate") || call.name.startsWith("render")
                        || call.name.equals("drawItem") || call.name.equals("openCapture") || call.name.equals("computeLayout")),
                "mouse input neither renders content nor dirties caches");
        check(calls(input).stream().anyMatch(call -> call.name.equals("selectIsolatedHud")), "input only changes cache membership on selection/release");
        for (String name : List.of("renderRectHud", "renderArmorHud", "renderConsumableHud", "renderInventoryHud", "renderPlayerModelHud")) {
            check(hud.methods.stream().filter(m -> m.name.equals(name)).flatMap(m -> calls(m).stream())
                            .anyMatch(call -> call.name.equals("isBatchPass")), name + " keeps legacy input out of captures");
        }
        check(hud.methods.stream().noneMatch(m -> m.name.equals("drawPlayerModel")), "Player Model has no unthrottled TAIL path");
        check(read("vorga/phazeclient/mixins/InGameHudMixin").methods.stream().noneMatch(m -> m.name.contains("PlayerModel")),
                "Player Model has no unthrottled vanilla hook");
        check(hud.methods.stream().flatMap(m -> calls(m).stream()).anyMatch(call -> call.name.equals("renderPlayerModelHud")),
                "Player Model participates in the common dispatcher");
        check(calls(method(hud, "hasAnyHudEnabled")).stream().anyMatch(call -> call.owner.endsWith("/PackDisplayHud")),
                "a standalone Pack Display does not get dropped by the guard");
        MethodNode child = method(hud, "renderBufferedHudInternal");
        check(index(child, "shouldUpdate") < index(child, "openCapture"), "selected content has an independent clock");
        check(index(child, "draw") < index(child, "openCapture"), "neighbour draws are flushed before child capture");
    }

    private static void captures() throws Exception {
        MethodNode redirect = method(read("vorga/phazeclient/mixins/MinecraftClientMixin"), "phaze$redirectFramebufferToBatchCapture");
        check(calls(redirect).stream().anyMatch(call -> call.owner.equals(HUD + "HudBuffer") && call.name.equals("getActiveCaptureFramebuffer")),
                "render layers rebind the innermost framebuffer");
        check(calls(method(read(HUD + "HudCapture"), "close")).stream().anyMatch(call -> call.name.equals("restoreCaptureTarget")),
                "child exit restores its parent framebuffer reference and sentinel");
        check(calls(method(read(HUD + "HudBuffer"), "abortAllCaptures")).stream().anyMatch(call -> call.owner.equals(HUD + "HudCapture") && call.name.equals("abortAll")),
                "lifecycle aborts the stack in nesting order, not weak-set order");
        for (String name : List.of("HudBuffer", "BatchedHudBuffer")) {
            MethodNode clock = method(read(HUD + name), name.equals("HudBuffer") ? "shouldUpdate" : "shouldRefresh");
            check(calls(clock).stream().anyMatch(call -> call.name.equals("getFramebufferWidth")), "resize invalidates " + name + " immediately");
        }
        MethodNode transformed = method(read(HUD + "HudBlit"), "drawTransformed");
        check(calls(transformed).stream().noneMatch(call -> call.name.equals("getFramebuffer") || call.name.contains("TexParameter") || call.name.equals("setTexFilter")),
                "transformed composition neither rebinds main nor changes texture filtering");
        check(calls(transformed).stream().anyMatch(call -> call.name.equals("capture")) && !transformed.tryCatchBlocks.isEmpty(),
                "transformed replay restores GL state on all exits");
        check(read(HUD + "HudBuffer").fields.stream().anyMatch(f -> f.name.equals("textureFilter") && (f.access & Opcodes.ACC_FINAL) != 0),
                "filtering is an owned per-buffer allocation policy");
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= Math.max(0.001F, Math.abs(expected) * 0.000001F), message);
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = HudEditorCacheChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static int index(MethodNode method, String name) {
        return calls(method).stream().filter(call -> call.name.equals(name)).mapToInt(method.instructions::indexOf).min().orElse(Integer.MAX_VALUE);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
