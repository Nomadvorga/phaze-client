package vorga.phazeclient.api.system.render;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

/** Headless bytecode contract checks; these do not execute OpenGL or measure FPS. */
public final class RenderLifecycleChecks {
    private static final String ROOT = "vorga/phazeclient/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassNode snapshots = read(ROOT + "api/system/snapshot/CardSnapshotCache");
        MethodNode begin = method(snapshots, "beginCapture", "(Lvorga/phazeclient/api/system/snapshot/CardSnapshotCache$Snapshot;FF)V");
        check(calls(begin, "endCapture", "(Z)V"), "begin failure rolls capture back");
        check(begin.tryCatchBlocks.stream().anyMatch(t -> "java/lang/Error".equals(t.type)), "begin rolls back Errors as well as exceptions");
        MethodNode end = method(snapshots, "endCapture", "(Z)V");
        check(end.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "end capture has unconditional finally restoration");
        check(calls(end, "restoreCaptureState", null), "end restores the caller state");
        MethodNode restore = method(snapshots, "restoreCaptureState", "(Lvorga/phazeclient/api/system/snapshot/CardSnapshotCache$CaptureState;)V");
        check(calls(restore, "close", "()V"), "snapshots use the shared 2D state boundary");
        MethodNode stateClose = method(read(ROOT + "api/system/hud/Render2DState"), "close", "()V");
        for (String call : new String[]{"_glBindFramebuffer", "viewport", "setProjectionMatrix", "enableScissor", "disableScissor", "setShader", "setShaderTexture", "blendFuncSeparate", "depthMask"}) {
            check(calls(stateClose, call, null), "capture restoration includes " + call);
        }
        check(calls(method(snapshots, "clearAll", "()V"), "endCapture", "(Z)V"), "cache disposal aborts active capture first");
        ClassNode allocationState = read(ROOT + "api/system/snapshot/CardSnapshotCache$FramebufferState");
        check(calls(method(allocationState, "close", "()V"), "_glBindFramebuffer", null), "snapshot allocation/deletion restores framebuffer bindings");
        check(calls(method(allocationState, "close", "()V"), "viewport", null), "snapshot allocation/deletion restores viewport");

        ClassNode overlays = read(ROOT + "api/system/render/WorldOverlayRenderer");
        MethodNode clear = method(overlays, "clear", "()V");
        check(calls(clear, "close", "()V"), "world cache closes native allocators");
        check(clear.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "world allocator release is exception safe");

        ClassNode client = read(ROOT + "core/client/ClientMain");
        check(client.methods.stream().noneMatch(m -> calls(m, "saveCurrentConfig", null)), "client resource lifecycle does not duplicate config saves");
        check(calls(method(client, "onClientStopping", "(Lnet/minecraft/client/MinecraftClient;)V"), "close", null), "normal shutdown closes render resources");
        check(read(ROOT + "core/client/ClientRenderResources").methods.stream().anyMatch(m -> calls(m, "execute", null)), "GPU lifecycle is scheduled on client thread");
        ClassNode rpc = read(ROOT + "api/system/discord/DiscordManager");
        check(rpc.fields.stream().anyMatch(f -> f.name.equals("running") && (f.access & Opcodes.ACC_VOLATILE) != 0), "RPC shutdown flag is visible to its worker");
        check(calls(method(rpc, "init", "()V"), "setDaemon", "(Z)V"), "RPC worker cannot keep the process alive");
        check(calls(method(rpc, "stopRPC", "()V"), "interrupt", "()V"), "RPC shutdown wakes the sleeping worker");
        System.out.println("Render lifecycle checks PASSED (" + checks + " contracts; no GL context)");
    }

    private static boolean calls(MethodNode method, String name, String desc) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)
                    && (desc == null || call.desc.equals(desc))) return true;
        }
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow();
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = RenderLifecycleChecks.class.getResourceAsStream("/" + name + ".class")) {
            if (input == null) throw new AssertionError("Missing " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
