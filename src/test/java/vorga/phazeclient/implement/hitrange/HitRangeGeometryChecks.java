package vorga.phazeclient.implement.hitrange;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import vorga.phazeclient.implement.features.modules.other.HitRange;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/** Exact old/new vertex comparison; retains segments, colors, topology and quad sorting. */
public final class HitRangeGeometryChecks {
    private static int checks;

    public static void main(String[] args) throws Throwable {
        HitRange config = HitRange.getInstance();
        String oldMode = config.renderMode.getSelected();
        float oldSegments = config.circleSegments.getValue(), oldRadius = config.radius.getValue(), oldThickness = config.thickness.getValue();
        try (BufferAllocator before = new BufferAllocator(65536); BufferAllocator after = new BufferAllocator(65536)) {
            for (String mode : new String[]{"Line", "Thick", "Filled"}) {
                config.renderMode.setSelected(mode);
                MethodHandle emitter = emitter(mode);
                VertexFormat.DrawMode drawMode = drawMode(mode);
                for (int count : new int[]{3, 60, 180}) {
                    config.circleSegments.setValue(count);
                    for (float[] dimensions : new float[][]{{3, 0.15F}, {0.05F, 2}, {5, 0.01F}}) {
                        config.radius.setValue(dimensions[0]); config.thickness.setValue(dimensions[1]);
                        LegacyGeometry legacy = new LegacyGeometry(config);
                        for (int transform = 0; transform < 3; transform++) {
                            BufferBuilder original = new BufferBuilder(before, drawMode, VertexFormats.POSITION_COLOR);
                            BufferBuilder current = new BufferBuilder(after, drawMode, VertexFormats.POSITION_COLOR);
                            for (int entity = 0; entity < 3; entity++) {
                                MatrixStack matrices = new MatrixStack();
                                matrices.translate(entity * 50.125 - 14.875, -2.5625, -8.25);
                                if (transform >= 1) matrices.multiplyPositionMatrix(new Matrix4f().rotateXYZ(0.13F, 1.27F, -0.45F));
                                if (transform >= 2) matrices.scale(0.35F, 1.75F, 2.25F);
                                int color = new int[]{0x004080C0, 0x804080C0, 0xFF123456}[entity];
                                legacy.emit(mode, matrices, original, 0.125F, color);
                                emitter.invokeExact(matrices, current, 0.125F, color);
                            }
                            try (BuiltBuffer a = original.end(); BuiltBuffer b = current.end()) {
                                check(a.getDrawParameters().equals(b.getDrawParameters()), "unchanged " + mode + " topology/segment count");
                                var av = a.getBuffer(); var bv = b.getBuffer();
                                check(av.remaining() == bv.remaining(), "unchanged vertex stream size");
                                for (int offset = 0; offset < av.remaining(); offset++) {
                                    check(av.get(offset) == bv.get(offset), "bit-identical " + mode + " position/color, segments=" + count + ", byte=" + offset);
                                }
                                if (drawMode == VertexFormat.DrawMode.QUADS) {
                                    a.sortQuads(before, com.mojang.blaze3d.systems.VertexSorter.BY_DISTANCE);
                                    b.sortQuads(after, com.mojang.blaze3d.systems.VertexSorter.BY_DISTANCE);
                                    var ai = a.getSortedBuffer(); var bi = b.getSortedBuffer();
                                    check(ai.remaining() == bi.remaining(), "same transparent quad index count");
                                    for (int offset = 0; offset < ai.remaining(); offset++) check(ai.get(offset) == bi.get(offset), "same transparent quad ordering");
                                }
                            }
                            before.clear(); after.clear();
                        }
                    }
                }
            }
            distanceParity();
            bytecodeContracts();
        } finally {
            config.renderMode.setSelected(oldMode); config.circleSegments.setValue(oldSegments);
            config.radius.setValue(oldRadius); config.thickness.setValue(oldThickness);
        }
        System.out.println("Hit Range geometry checks passed: " + checks + " (bit-identical vertices/indices; no GL)");
    }

    static MethodHandle emitter(String mode) throws Exception {
        String methodName = mode.equals("Line") ? "drawCircleLines" : mode.equals("Thick") ? "drawCircleQuad" : "drawCircleTriangles";
        var method = HitRangeCircleRenderer.class.getDeclaredMethod(methodName, MatrixStack.class, BufferBuilder.class, float.class, int.class);
        method.setAccessible(true); return MethodHandles.lookup().unreflect(method);
    }

    static VertexFormat.DrawMode drawMode(String mode) {
        return mode.equals("Line") ? VertexFormat.DrawMode.DEBUG_LINES : mode.equals("Thick") ? VertexFormat.DrawMode.QUADS : VertexFormat.DrawMode.TRIANGLES;
    }

    private static void distanceParity() {
        Vec3d player = new Vec3d(30_000_000.125, -64, -30_000_000.125);
        for (double radius : new double[]{0.05, 3, 100, 200}) {
            for (double delta : new double[]{0, radius, radius - 0.0001, radius + 0.0001}) {
                Vec3d target = player.add(delta, 0, 0);
                check(target.isInRange(player, radius) == HitRangeCircleRenderer.isInRange(target.x, target.y, target.z,
                        player.x, player.y, player.z, radius), "same strict distance boundary at large world coordinates");
            }
        }
    }

    private static void bytecodeContracts() throws Exception {
        ClassNode self = read("vorga/phazeclient/mixins/WorldRendererMixin");
        var selfHook = self.methods.stream().filter(m -> m.name.equals("phaze$drawHitRangeSelf")).findFirst().orElseThrow();
        for (var instruction : selfHook.instructions) if (instruction instanceof MethodInsnNode call) {
            check(!call.name.equals("getAndUpdateRenderState") && !call.name.equals("getRenderer"), "self circle doesn't rebuild the complete player model");
        }
        ClassNode renderer = read("vorga/phazeclient/implement/hitrange/HitRangeCircleRenderer");
        for (var method : renderer.methods) {
            if (!(method.name.startsWith("drawCircle") || method.name.equals("transformPoints") || method.name.equals("vertex"))) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == org.objectweb.asm.Opcodes.NEW) {
                    check(!allocation.desc.equals("org/joml/Vector3f") && !allocation.desc.equals("net/minecraft/util/math/Vec3d"), "no temporary vectors in circle emission");
                }
                if (instruction instanceof MethodInsnNode call) {
                    check(!(call.name.equals("vertex") && call.desc.startsWith("(Lorg/joml/Matrix4f;")), "unique points are transformed once, not for every emitted vertex");
                    check(!call.owner.contains("RenderLayer"), "circle stays on BufferBuilder, never RenderLayer/Iris phases");
                }
            }
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (var input = HitRangeGeometryChecks.class.getResourceAsStream("/" + name + ".class")) {
            ClassNode result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }

    /** Reproduction of the previous CPU emission path, including its vertex order and float math. */
    static final class LegacyGeometry {
        private final List<Angle> angles = new ArrayList<>();
        LegacyGeometry(HitRange config) {
            int n = config.circleSegments.getInt();
            float radius = config.radius.getValue();
            for (int i = 0; i < n; i++) {
                float angle = 2F * MathHelper.PI * ((float) i / n);
                if (config.mode() == HitRange.Mode.THICK) {
                    float thickness = Math.max(0.001F, config.thickness.getValue());
                    float inner = radius - thickness / 2F, outer = inner + thickness;
                    angles.add(new Angle(inner * MathHelper.sin(angle), inner * MathHelper.cos(angle),
                            outer * MathHelper.sin(angle), outer * MathHelper.cos(angle)));
                } else angles.add(new Angle(radius * MathHelper.sin(angle), radius * MathHelper.cos(angle), 0, 0));
            }
        }
        void emit(String mode, MatrixStack matrices, BufferBuilder vertices, float y, int color) {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            int n = angles.size();
            for (int i = 0; i < n; i++) {
                Angle a = angles.get(i), b = angles.get((i + 1) % n);
                if (mode.equals("Thick")) {
                    vertices.vertex(matrix, a.x, y, a.z).color(color);
                    vertices.vertex(matrix, a.outerX, y, a.outerZ).color(color);
                    vertices.vertex(matrix, b.outerX, y, b.outerZ).color(color);
                    vertices.vertex(matrix, b.x, y, b.z).color(color);
                } else {
                    if (mode.equals("Filled")) vertices.vertex(matrix, 0, y, 0).color(color);
                    vertices.vertex(matrix, a.x, y, a.z).color(color);
                    vertices.vertex(matrix, b.x, y, b.z).color(color);
                }
            }
        }
        private record Angle(float x, float z, float outerX, float outerZ) {}
    }
}
