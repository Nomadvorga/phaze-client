package vorga.phazeclient.api.system.hud;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Offline exception/state-boundary checks; real framebuffer checks live in RenderSafetyClientTest. */
public final class HudRenderSafetyChecks {
    private static int checks;
    private static final String HUD = "vorga/phazeclient/api/system/hud/";
    private static final String SHAPE = "vorga/phazeclient/api/system/shape/";

    public static void main(String[] args) throws Exception {
        passModes();
        incompleteVertices();
        shaderBeforeBuilder();
        boundaries();
        buffers();
        lifecycle();
        System.out.println("HUD render safety checks passed: " + checks);
    }

    private static void passModes() {
        HudPassState state = new HudPassState();
        check(!state.isBatchPass() && !state.isLogicOnlyPass(), "direct default");
        for (HudPassState.Mode mode : HudPassState.Mode.values()) {
            RuntimeException injected = new RuntimeException("injected " + mode);
            try {
                state.run(mode, () -> {
                    check(state.isBatchPass() == (mode == HudPassState.Mode.CAPTURE || mode == HudPassState.Mode.GRADIENT), "batch mode");
                    check(state.includesBlur() == (mode == HudPassState.Mode.CAPTURE), "capture contains static blur HUDs");
                    check(state.isGradientPass() == (mode == HudPassState.Mode.GRADIENT), "live gradient mode");
                    check(state.isLogicOnlyPass() == (mode == HudPassState.Mode.LOGIC), "logic-only mode");
                    throw injected;
                });
                throw new AssertionError("failure not propagated");
            } catch (RuntimeException e) {
                check(e == injected, "preserves render exception");
            }
            check(!state.isBatchPass() && !state.isGradientPass() && !state.isLogicOnlyPass(), "failure restores direct mode");
        }
        state.run(HudPassState.Mode.CAPTURE, () -> {
            try { state.run(HudPassState.Mode.GRADIENT, () -> { throw new IllegalArgumentException(); }); }
            catch (IllegalArgumentException ignored) {}
            check(state.isBatchPass() && state.includesBlur() && !state.isGradientPass(), "nested failure restores parent capture");
        });
    }

    private static void shaderBeforeBuilder() throws Exception {
        MethodNode rectangle = method(read(SHAPE + "implement/Rectangle"), "render");
        check(index(rectangle, "setShader") < index(rectangle, "begin"), "rectangle checks shader before owned buffer allocation");
        check(index(rectangle, "window") < index(rectangle, "enableBlend"), "invalid window checked before GL changes");
        MethodNode blur = method(read(SHAPE + "implement/Blur"), "renderPreparedShape");
        check(index(blur, "setShader") < index(blur, "drawShape"), "blur shader checked before vertices");
        check(calls(blur).stream().noneMatch(c -> c.name.equals("begin")), "prepared blur cannot abandon a builder on shader-null");
    }

    private static void boundaries() throws Exception {
        ClassNode mixin = read("vorga/phazeclient/mixins/InGameHudMixin");
        MethodNode fallback = method(mixin, "renderCustomHudFallback");
        check(index(fallback, "draw") < index(fallback, "capture"), "flush vanilla before HUD state boundary");
        check(hasFinally(fallback), "fallback always unwinds state and cached blur flags");
        check(calls(fallback).stream().anyMatch(c -> c.name.equals("endCachedFrame")), "cached blur frame ends even on render failure");
        check(mixin.fields.stream().noneMatch(f -> List.of("inBatchPass", "inGradientPass", "batchIncludesBlur", "inLogicOnlyPass").contains(f.name)), "no unscoped global pass booleans");
        MethodNode state = method(read(HUD + "Render2DState"), "join");
        check(calls(state).stream().noneMatch(c -> c.name.startsWith("glGet")), "joining a managed pass never queries GL");
        for (String name : List.of(SHAPE + "implement/Rectangle", SHAPE + "batched/BatchedRectangle")) {
            check(read(name).methods.stream().flatMap(m -> calls(m).stream()).noneMatch(c -> c.name.startsWith("glGet")), "rectangles never directly query GL per primitive");
        }
        MethodNode copy = method(read(SHAPE + "implement/Blur"), "captureFramebufferInput");
        check(hasFinally(copy), "blur framebuffer copies always restore bindings/scissor");
        check(calls(copy).stream().anyMatch(c -> c.name.equals("glGetInteger")), "copies save actual read and draw bindings rather than guessed main framebuffer");
    }

    private static void incompleteVertices() {
        for (boolean batch : new boolean[]{false, true}) {
            BufferBuilder broken = batch ? Render2DBuffers.beginBatch(VertexFormats.POSITION_COLOR)
                    : Render2DBuffers.begin(VertexFormats.POSITION_COLOR);
            broken.vertex(-123, -456, -789); // Deliberately omit COLOR; endNullable would throw.
            if (batch) Render2DBuffers.discardBatch(); else Render2DBuffers.discardImmediate();
            BufferBuilder clean = batch ? Render2DBuffers.beginBatch(VertexFormats.POSITION)
                    : Render2DBuffers.begin(VertexFormats.POSITION);
            clean.vertex(21, 22, 0); clean.vertex(21, 23, 0); clean.vertex(24, 23, 0); clean.vertex(24, 22, 0);
            try (BuiltBuffer built = clean.end()) {
                check(built.getDrawParameters().vertexCount() == 4, "new draw has only its own vertices");
                check(built.getBuffer().getFloat(0) == 21, "discard rewinds incomplete, un-ended allocator bytes");
            }
        }
        Render2DBuffers.cleanup();
        Render2DBuffers.cleanup();
    }

    private static void buffers() throws Exception {
        for (String name : List.of("HudBuffer", "BatchedHudBuffer")) {
            MethodNode open = method(read(HUD + name), "openCapture");
            check(hasFinally(open) || !open.tryCatchBlocks.isEmpty(), "capture allocation/clear failure closes scope");
            String firstMutation = name.equals("BatchedHudBuffer") ? "ensureFramebufferInternal" : "clear";
            check(index(open, "capture") < index(open, firstMutation) && index(open, firstMutation) < Integer.MAX_VALUE,
                    "capture snapshots before clear/resize modifies GL");
            check(calls(method(read(HUD + name), "endCapture")).stream().anyMatch(c -> c.name.equals("commit")), "legacy endCapture publishes explicitly");
        }
        MethodNode close = method(read(HUD + "HudCapture"), "close");
        check(hasFinally(close), "capture close restores redirects even on flush failure");
        check(calls(close).stream().anyMatch(c -> c.name.equals("discardPending")), "abort drops partial rectangle batch");
        check(calls(close).stream().anyMatch(c -> c.name.equals("restoreRenderTargetFbHeight")), "capture restores parent raw framebuffer height");
        MethodNode blit = method(read(HUD + "HudBlit"), "draw");
        check(hasFinally(blit), "blit owns GPU/buffer cleanup");
        check(calls(blit).stream().noneMatch(c -> c.name.equals("getFramebuffer")), "blit respects currently bound Exordium/framebuffer target");
    }

    private static void lifecycle() throws Exception {
        MethodNode cleanup = method(read(HUD + "HudRenderLifecycle"), "cleanup");
        check(hasFinally(cleanup), "lifecycle attempts every cache cleanup after an error");
        for (String owner : List.of(HUD + "HudBuffer", HUD + "BatchedHudBuffer", HUD + "Render2DState",
                SHAPE + "implement/Blur", SHAPE + "batched/BatchedRectangle", HUD + "Render2DBuffers")) {
            check(calls(cleanup).stream().anyMatch(c -> c.owner.equals(owner)), "unified lifecycle includes " + owner);
        }
    }

    private static boolean hasFinally(MethodNode m) {
        // javac emits Throwable handlers for try-with-resources and catch-all handlers for explicit finally.
        return m.tryCatchBlocks.stream().anyMatch(t -> t.type == null || "java/lang/Throwable".equals(t.type));
    }
    private static int index(MethodNode m, String name) {
        return calls(m).stream().filter(c -> c.name.equals(name)).mapToInt(m.instructions::indexOf).min().orElse(Integer.MAX_VALUE);
    }
    private static List<MethodInsnNode> calls(MethodNode m) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode call) calls.add(call);
        return calls;
    }
    private static MethodNode method(ClassNode c, String name) {
        return c.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static ClassNode read(String name) throws Exception {
        try (InputStream in = HudRenderSafetyChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing class " + name);
            ClassNode c = new ClassNode();
            new ClassReader(in).accept(c, 0);
            return c;
        }
    }
    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}
