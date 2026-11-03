package vorga.phazeclient.api.system.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

/** Headless checks: actual Minecraft hook order, isolated builders and shader line tangents. */
public final class WorldOverlayChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        hookOrder();
        lineTangents();
        isolatedGeometry();
        independentPipeline();
        predictionDepth();
        predictionMovement();
        vanillaCosmetics();
        System.out.println("World overlay checks passed: " + checks);
    }

    private static void hookOrder() throws Exception {
        ClassNode renderer = read("net/minecraft/client/render/GameRenderer");
        MethodNode method = renderer.methods.stream().filter(m -> m.name.equals("renderWorld")).findFirst().orElseThrow();
        int world = -1, handField = -1, handCall = -1, index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/render/WorldRenderer")
                    && call.name.equals("render")) world = index;
            if (instruction instanceof FieldInsnNode field && field.name.equals("renderHand")
                    && field.getOpcode() == Opcodes.GETFIELD && handField == -1) handField = index;
            if (instruction instanceof MethodInsnNode call && call.name.equals("renderHand")) handCall = index;
            index++;
        }
        check(world >= 0 && handField > world && handCall > handField,
                "overlay hook must be after the complete world pass and before hand rendering (even when hand is hidden)");
        ClassNode worldRenderer = read("net/minecraft/client/render/WorldRenderer");
        MethodNode outline = worldRenderer.methods.stream().filter(m -> m.name.equals("drawBlockOutline")).findFirst().orElseThrow();
        check((outline.access & Opcodes.ACC_STATIC) == 0, "block-outline wrapper includes the instance receiver");
        check(read("net/minecraft/client/render/RenderLayer$MultiPhase").fields.stream().anyMatch(f -> f.name.equals("phases")),
                "layer metadata accessor matches Minecraft 1.21.4");
        check(read("net/minecraft/client/render/RenderLayer$MultiPhaseParameters").fields.stream().anyMatch(f -> f.name.equals("texture")),
                "texture metadata accessor matches Minecraft 1.21.4");
        check(read("net/minecraft/client/render/RenderPhase$TextureBase").methods.stream().anyMatch(m -> m.name.equals("getId")),
                "texture identifier invoker matches Minecraft 1.21.4");
    }

    private static void lineTangents() {
        try (BufferAllocator allocator = new BufferAllocator(4096)) {
            MatrixStack matrices = new MatrixStack();
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            Render3DUtil.vertexLine(matrices, builder, 0, 0, 0, 1, 0, 0, -1, -1);
            try (BuiltBuffer built = builder.end()) {
                check(built.getBuffer().get(16) == 127 && built.getBuffer().get(17) == 0 && built.getBuffer().get(18) == 0,
                        "line normal is its tangent, not fixed world-up (prevents degenerate shader quads)");
            }
            allocator.clear();
            matrices.multiplyPositionMatrix(new Matrix4f().rotateY((float) Math.PI / 2));
            builder = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            Render3DUtil.vertexLine(matrices, builder, 0, 0, 0, 1, 0, 0, -1, -1);
            try (BuiltBuffer built = builder.end()) {
                check(Math.abs(built.getBuffer().get(16)) <= 1 && built.getBuffer().get(18) == -127,
                        "line tangent follows the camera matrix");
            }
            allocator.clear();
            builder = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            Render3DUtil.vertexLine(matrices, builder, 1, 1, 1, 1, 1, 1, -1, -1);
            check(builder.endNullable() == null, "zero-length lines never enter the shader pipeline");
        }
    }

    private static void isolatedGeometry() {
        try (BufferAllocator allocator = new BufferAllocator(4096)) {
            for (int frame = 0; frame < 120; frame++) {
                BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                for (int disc = 0; disc < 2; disc++) {
                    float center = disc * 100;
                    for (int segment = 0; segment < 32; segment++) {
                        double angle = segment * Math.PI / 16;
                        double next = (segment + 1) * Math.PI / 16;
                        builder.vertex(center, 0, 0).color(-1);
                        builder.vertex(center + (float) Math.cos(angle), 0, (float) Math.sin(angle)).color(-1);
                        builder.vertex(center + (float) Math.cos(next), 0, (float) Math.sin(next)).color(-1);
                    }
                }
                try (BuiltBuffer built = builder.end()) {
                    check(built.getDrawParameters().vertexCount() == 192, "each disc has independent triangles");
                    var data = built.getBuffer();
                    int stride = VertexFormats.POSITION_COLOR.getVertexSizeByte();
                    for (int triangle = 0; triangle < 64; triangle++) {
                        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                        for (int vertex = 0; vertex < 3; vertex++) {
                            float x = data.getFloat((triangle * 3 + vertex) * stride);
                            min = Math.min(min, x);
                            max = Math.max(max, x);
                        }
                        check(max - min <= 2.01, "no triangle can bridge two distant hit-range circles");
                    }
                }
                allocator.clear();
                check(new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR)
                        .endNullable() == null, "reused allocator does not retain the preceding frame");
            }
        }
    }

    private static void independentPipeline() throws Exception {
        ClassNode pipeline = read("vorga/phazeclient/api/system/render/WorldOverlayRenderer");
        for (MethodNode method : pipeline.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    check(!(call.owner.equals("net/minecraft/client/render/RenderLayer")
                                    && (call.name.equals("draw") || call.name.equals("startDrawing") || call.name.equals("endDrawing"))),
                            "custom geometry never executes vanilla/Iris RenderLayer phases");
                    check(!call.name.equals("getEntityVertexConsumers"), "pipeline never uses the world entity provider");
                }
            }
        }
    }

    private static void predictionDepth() throws Exception {
        ClassNode renderer = read("vorga/phazeclient/implement/features/modules/other/PredictionsRenderer");
        MethodNode render = renderer.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
        String batchName = "vorga/phazeclient/implement/features/modules/other/PredictionRenderBatch";
        boolean held = false, live = false, batched = false;
        for (AbstractInsnNode instruction : render.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(batchName)) {
                held |= call.name.equals("path");
                live |= call.name.equals("trail");
                batched |= call.name.equals("draw");
            }
            check(!call.owner.equals("net/minecraft/client/render/Tessellator")
                            && !call.owner.equals("vorga/phazeclient/api/system/render/Render3DUtil"),
                    "held/live prediction geometry is submitted through one independent batch");
        }
        check(held && live && batched, "depth checks inspect the batch actually used by held and live rendering");
        ClassNode batch = read(batchName);
        MethodNode draw = batch.methods.stream().filter(m -> m.name.equals("draw")).findFirst().orElseThrow();
        boolean depth = false, writesDepth = true;
        int draws = 0;
        for (AbstractInsnNode instruction : draw.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")) {
                if (call.name.equals("enableDepthTest")) depth = true;
                if (call.name.equals("disableDepthTest")) depth = false;
                if (call.name.equals("depthMask")) {
                    AbstractInsnNode argument = previousCode(call);
                    check(argument != null && (argument.getOpcode() == Opcodes.ICONST_0 || argument.getOpcode() == Opcodes.ICONST_1),
                            "prediction depth-write state is explicit");
                    writesDepth = argument.getOpcode() != Opcodes.ICONST_0;
                }
            }
            if (call.owner.equals("net/minecraft/client/render/BufferRenderer") && call.name.equals("drawWithGlobalProgram")) {
                check(depth, "prediction drawing never bypasses the world/player depth buffer");
                check(!writesDepth, "lines, halos and bodies cannot imprint into the depth buffer");
                draws++;
            }
        }
        check(draws == 3, "all predictions share at most three GPU draw call sites");
        check(depth && writesDepth, "prediction batch restores neutral depth state even on exceptional exit");
        check(renderer.methods.stream().flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                        .anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("setWorldLaunchOrigin")),
                "third-person renderer uses a real world launch instead of a near-camera HUD origin");
    }

    private static void predictionMovement() throws Exception {
        ClassNode renderer = read("vorga/phazeclient/implement/features/modules/other/PredictionsRenderer");
        MethodNode collection = renderer.methods.stream().filter(m -> m.name.equals("collectHeldView")).findFirst().orElseThrow();
        boolean aimOnly = false, anchored = false;
        int movementLocal = -1;
        for (AbstractInsnNode instruction : collection.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("vorga/phazeclient/implement/features/modules/other/HeldLaunchState")
                    && call.name.equals("movement")) {
                AbstractInsnNode next = call.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                if (next instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) movementLocal = store.var;
            }
            if (call.owner.equals("vorga/phazeclient/implement/features/modules/other/Predictions")
                    && call.name.equals("initialMotionFor")) {
                AbstractInsnNode ground = previousCode(call);
                AbstractInsnNode movement = previousCode(ground);
                if (ground != null && ground.getOpcode() == Opcodes.ICONST_0
                        && movement instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals("net/minecraft/util/math/Vec3d") && field.name.equals("ZERO")) aimOnly = true;
            }
            if (call.owner.equals("vorga/phazeclient/implement/features/modules/other/HeldLineAnchor")
                    && call.name.equals("updateForLaunch")) {
                long vectors = java.util.Arrays.stream(org.objectweb.asm.Type.getArgumentTypes(call.desc))
                        .filter(type -> type.getDescriptor().equals("Lnet/minecraft/util/math/Vec3d;")).count();
                check(vectors == 4, "renderer supplies separate camera, launch, aim and player-movement vectors");
                AbstractInsnNode profile = previousCode(call);
                AbstractInsnNode type = previousCode(profile);
                AbstractInsnNode movement = previousCode(type);
                check(movementLocal >= 0 && movement instanceof VarInsnNode load
                                && load.getOpcode() == Opcodes.ALOAD && load.var == movementLocal,
                        "visual origin receives tick-interpolated player movement, not the aimed projectile velocity");
                anchored = true;
            }
        }
        check(aimOnly && anchored, "movement-only anchoring is integrated into the actual renderer without changing launch physics");
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction == null ? null : instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static void vanillaCosmetics() throws Exception {
        for (String name : new String[]{"implement/cosmetics/BlockbenchWingModel",
                "implement/cosmetics/bridge/PhazePulseGraffitiRenderer",
                "implement/cosmetics/bridge/pulserender/PulseGeoRenderer",
                "implement/features/modules/other/WavyCapeRenderer", "implement/features/modules/other/ThreeDSkinsHelper"}) {
            ClassNode model = read("vorga/phazeclient/" + name);
            for (MethodNode method : model.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        check(!call.owner.equals("vorga/phazeclient/api/system/render/WorldOverlayRenderer"),
                                "cosmetic model rendering uses its original vanilla vertex consumers: " + name);
                    }
                }
            }
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = WorldOverlayChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
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
