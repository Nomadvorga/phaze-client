package vorga.phazeclient.api.system.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import vorga.phazeclient.implement.features.modules.other.HitRange;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Headless checks of world matrices, actual geometry, batch order and original providers; no GL context. */
public final class WorldOverlayChecks {
    private static final String PIPELINE = "vorga/phazeclient/api/system/render/WorldOverlayRenderer";
    private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
    private static int checks;

    public static void main(String[] args) throws Exception {
        hookOrder();
        matrixContract();
        lineTangents();
        boxGeometry();
        isolatedGeometry();
        circleGeometry();
        batchOrder();
        sortedFaces();
        helperCoordinates();
        independentPipeline();
        originalProviders();
        predictionDepth();
        predictionMovement();
        vanillaCosmetics();
        System.out.println("World overlay checks passed: " + checks);
    }

    private static void hookOrder() throws Exception {
        ClassNode renderer = read("net/minecraft/client/render/GameRenderer");
        MethodNode method = renderer.methods.stream().filter(m -> m.name.equals("renderWorld")).findFirst().orElseThrow();
        int world = -1, handField = -1, handCall = -1, clear = -1, index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/render/WorldRenderer")
                    && call.name.equals("render")) world = index;
            if (instruction instanceof FieldInsnNode field && field.name.equals("renderHand")
                    && field.getOpcode() == Opcodes.GETFIELD && handField == -1) handField = index;
            if (instruction instanceof MethodInsnNode call && call.name.equals("renderHand")) handCall = index;
            if (instruction instanceof MethodInsnNode call && call.owner.equals(RENDER_SYSTEM)
                    && call.name.equals("clear")) clear = index;
            index++;
        }
        check(world >= 0 && handField > world && handCall > handField,
                "overlay hook must be after the complete world pass and before hand rendering (even when hand is hidden)");
        check(clear > handField && clear < handCall, "overlay hook retains world depth before the hand pass clears it");
        MethodNode hook = method(read("vorga/phazeclient/mixins/GameRendererMixin"), "phaze$drawWorldOverlays");
        check(calls(hook).stream().anyMatch(call -> call.owner.equals(PIPELINE) && call.name.equals("draw")),
                "late hook actually draws the independent overlay pipeline");
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

    private static void matrixContract() throws Exception {
        ClassNode pipeline = read(PIPELINE);
        MethodNode begin = method(pipeline, "beginFrame");
        boolean viewSnapshot = false, projectionSnapshot = false;
        for (MethodInsnNode call : calls(begin)) {
            if (!call.name.equals("set") || !call.owner.equals("org/joml/Matrix4f")) continue;
            AbstractInsnNode argument = previousCode(call);
            AbstractInsnNode receiver = previousCode(argument);
            if (argument instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
                    && receiver instanceof FieldInsnNode field && field.owner.equals(PIPELINE)) {
                viewSnapshot |= field.name.equals("VIEW") && load.var == 0;
                projectionSnapshot |= field.name.equals("PROJECTION") && load.var == 1;
            }
        }
        check(viewSnapshot && projectionSnapshot, "frame capture copies both world matrices, not mutable references");
        MethodNode capture = method(read("vorga/phazeclient/mixins/WorldRendererMixin"), "phaze$motionBlurSetMatrices");
        check(calls(capture).stream().anyMatch(call -> call.owner.equals(PIPELINE) && call.name.equals("beginFrame")
                        && call.desc.equals("(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/RenderTickCounter;)V")),
                "world hook captures the actual view and projection supplied by GameRenderer");

        MethodNode draw = method(pipeline, "draw");
        int view = -1, projection = -1, helper = -1;
        boolean restoresView = false, restoresProjection = false;
        for (MethodInsnNode call : calls(draw)) {
            AbstractInsnNode argument = previousCode(call);
            if (call.name.equals("set") && argument instanceof FieldInsnNode field && field.name.equals("VIEW")) {
                view = draw.instructions.indexOf(call);
            }
            if (call.owner.equals(RENDER_SYSTEM) && call.name.equals("setProjectionMatrix")) {
                AbstractInsnNode matrix = previousCode(argument);
                if (matrix instanceof FieldInsnNode field && field.name.equals("PROJECTION")) projection = draw.instructions.indexOf(call);
                else restoresProjection = true;
            }
            restoresView |= call.name.equals("popMatrix");
            if (call.owner.equals("vorga/phazeclient/implement/features/modules/other/FTHelperRenderer")
                    && call.name.equals("renderHighlight")) helper = draw.instructions.indexOf(call);
            check(!(call.owner.equals("net/minecraft/client/util/math/MatrixStack") && call.name.equals("multiplyPositionMatrix")),
                    "late helper matrix does not bake the GPU view in a second time");
        }
        check(view >= 0 && projection >= 0 && helper > view && helper > projection,
                "GPU receives the saved world view/projection before any late geometry");
        check(restoresView && restoresProjection, "late pass restores both GPU matrices on exit");
    }

    private static void boxGeometry() {
        for (double[] position : new double[][]{{0, 64, 0}, {20_000_000.375, 71.25, -20_000_000.625},
                {-20_000_000.625, -48.125, 20_000_000.375}}) {
            double x = position[0], y = position[1], z = position[2];
            double cameraX = x + 4.125, cameraY = y + 1.65, cameraZ = z - 6.125;
            Box box = new Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3).offset(-x, -y, -z);
            MatrixStack local = new MatrixStack();
            local.translate(x - cameraX, y - cameraY, z - cameraZ);
            Matrix4f viewProjection = new Matrix4f().perspective((float) Math.toRadians(73), 1.7F, 0.05F, 1024)
                    .mul(new Matrix4f().rotateX(-0.35F).rotateY(0.8F));
            try (BufferAllocator allocator = new BufferAllocator(4096)) {
                BufferBuilder fill = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                Render3DUtil.vertexBoxFill(local, fill, box, 0xBF3399CC, 0.3F);
                try (BuiltBuffer filled = fill.end()) {
                    BufferBuilder lines = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                    VertexRendering.drawBox(local, lines, box, 0.2F, 0.6F, 0.8F, 191 / 255.0F);
                    try (BuiltBuffer outlined = lines.end()) {
                        check(filled.getDrawParameters().vertexCount() == 24, "box fill contains all six independent faces");
                        check(outlined.getDrawParameters().vertexCount() == 48, "outline contains twelve shader-expanded edges");
                        ByteBuffer vertices = filled.getBuffer();
                        for (int vertex = 0; vertex < 24; vertex++) {
                            Vector3f point = position(vertices, vertex, VertexFormats.POSITION_COLOR.getVertexSizeByte());
                            check(corner(point.x, box.minX + x - cameraX, box.maxX + x - cameraX)
                                            && corner(point.y, box.minY + y - cameraY, box.maxY + y - cameraY)
                                            && corner(point.z, box.minZ + z - cameraZ, box.maxZ + z - cameraZ),
                                    "local box uses camera subtraction once, including at large coordinates");
                            boolean matchingEdge = false;
                            for (int edgeVertex = 0; edgeVertex < 48; edgeVertex++) {
                                matchingEdge |= point.distanceSquared(position(outlined.getBuffer(), edgeVertex,
                                        VertexFormats.LINES.getVertexSizeByte())) < 1.0E-8F;
                            }
                            check(matchingEdge, "fill and outline share the same position, with no offset or extra camera rotation");
                            color(vertices, vertex, VertexFormats.POSITION_COLOR.getVertexSizeByte(), 51, 153, 204, 57);
                            Vector4f clip = viewProjection.transform(new Vector4f(point, 1));
                            Vector4f expected = viewProjection.transform(new Vector4f(
                                    (float) (x - cameraX + (point.x < -4.125F ? box.minX : box.maxX)),
                                    (float) (y - cameraY + (point.y < -0.75F ? box.minY : box.maxY)),
                                    (float) (z - cameraZ + (point.z < 6.125F ? box.minZ : box.maxZ)), 1));
                            check(clip.equals(expected, 0.0001F), "one world GPU view projects local fill/outline to the entity's screen position");
                        }
                        for (int face = 0; face < 6; face++) {
                            Vector3f a = position(vertices, face * 4, 16);
                            Vector3f b = position(vertices, face * 4 + 1, 16).sub(a);
                            Vector3f c = position(vertices, face * 4 + 2, 16).sub(a);
                            check(b.cross(c).lengthSquared() > 0.0001F, "filled face is not degenerate");
                        }
                        for (int vertex = 0; vertex < 48; vertex++) {
                            color(outlined.getBuffer(), vertex, VertexFormats.LINES.getVertexSizeByte(), 51, 153, 204, 191);
                        }
                    }
                }
            }
        }
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

    private static void circleGeometry() throws Exception {
        HitRange config = HitRange.getInstance();
        String oldMode = config.renderMode.getSelected();
        float oldSegments = config.circleSegments.getValue(), oldRadius = config.radius.getValue();
        float oldThickness = config.thickness.getValue(), oldOutline = config.outlineOpacity.getValue();
        Class<?> renderer = Class.forName("vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer");
        try (BufferAllocator allocator = new BufferAllocator(8192)) {
            config.circleSegments.setValue(32);
            config.radius.setValue(3);
            config.thickness.setValue(0.2F);
            for (String mode : new String[]{"Line", "Thick", "Filled"}) {
                config.renderMode.setSelected(mode);
                String methodName = switch (mode) {
                    case "Line" -> "drawCircleLines";
                    case "Thick" -> "drawCircleQuad";
                    default -> "drawCircleTriangles";
                };
                VertexFormat.DrawMode drawMode = switch (mode) {
                    case "Line" -> VertexFormat.DrawMode.DEBUG_LINES;
                    case "Thick" -> VertexFormat.DrawMode.QUADS;
                    default -> VertexFormat.DrawMode.TRIANGLES;
                };
                int primitiveSize = mode.equals("Line") ? 2 : mode.equals("Thick") ? 4 : 3;
                Method append = accessibleMethod(renderer, methodName, MatrixStack.class, BufferBuilder.class, float.class, int.class);
                BufferBuilder builder = new BufferBuilder(allocator, drawMode, VertexFormats.POSITION_COLOR);
                for (int entity = 0; entity < 2; entity++) {
                    MatrixStack matrices = new MatrixStack();
                    matrices.translate(entity * 100.0, -1.625, -8.25);
                    append.invoke(null, matrices, builder, 0.125F, entity == 0 ? 0x604080C0 : 0xAA4080C0);
                }
                try (BuiltBuffer built = builder.end()) {
                    check(built.getDrawParameters().vertexCount() == 64 * primitiveSize, "actual " + mode + " circles have complete independent primitives");
                    for (int primitive = 0; primitive < 64; primitive++) {
                        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                        for (int vertex = 0; vertex < primitiveSize; vertex++) {
                            int index = primitive * primitiveSize + vertex;
                            Vector3f point = position(built.getBuffer(), index, 16);
                            min = Math.min(min, point.x);
                            max = Math.max(max, point.x);
                            near(point.y, -1.5F, "circle stays at the camera-relative feet height");
                            color(built.getBuffer(), index, 16, 64, 128, 192, primitive < 32 ? 96 : 170);
                        }
                        check(max - min < 6.3F, "actual " + mode + " geometry cannot bridge two different players");
                    }
                }
                allocator.clear();
            }
            config.outlineOpacity.setValue(0.7F);
            Method resolve = accessibleMethod(renderer, "resolveColors", HitRange.class, HitRange.Mode.class, int.class);
            Object colors = resolve.invoke(null, config, HitRange.Mode.FILLED, 0x254080C0);
            int fill = (int) accessibleMethod(colors.getClass(), "fillArgb").invoke(colors);
            int outline = (int) accessibleMethod(colors.getClass(), "outlineArgb").invoke(colors);
            check((fill >>> 24) == 37 && (outline >>> 24) == Math.round(0.7F * 255), "Hit Range fill and outline opacity remain independent");
            check((fill & 0xFFFFFF) == (outline & 0xFFFFFF), "separate circle opacities preserve the selected RGB color");
        } finally {
            config.renderMode.setSelected(oldMode);
            config.circleSegments.setValue(oldSegments);
            config.radius.setValue(oldRadius);
            config.thickness.setValue(oldThickness);
            config.outlineOpacity.setValue(oldOutline);
        }
    }

    private static void batchOrder() throws Exception {
        ClassNode pipeline = read(PIPELINE);
        MethodNode flush = pipeline.methods.stream().filter(method -> method.name.equals("flush") && method.desc.equals("()V")).findFirst().orElseThrow();
        List<Integer> passes = new ArrayList<>();
        for (MethodInsnNode call : calls(flush)) {
            if (call.owner.equals(PIPELINE) && call.name.equals("flush") && call.desc.equals("(Z)V")) {
                passes.add(previousCode(call).getOpcode());
            }
        }
        check(passes.equals(List.of(Opcodes.ICONST_0, Opcodes.ICONST_1)), "surfaces are flushed before lines, independent of cached batch insertion order");
        MethodNode pass = pipeline.methods.stream().filter(method -> method.name.equals("flush") && method.desc.equals("(Z)V")).findFirst().orElseThrow();
        int filter = -1, end = -1;
        boolean bias = false, noDepthWrites = false, blend = false;
        for (MethodInsnNode call : calls(pass)) {
            if (call.name.equals("isLines")) filter = pass.instructions.indexOf(call);
            if (call.owner.equals(PIPELINE + "$Batch") && call.name.equals("end")) end = pass.instructions.indexOf(call);
            if (call.owner.equals(RENDER_SYSTEM)) {
                bias |= call.name.equals("enablePolygonOffset");
                blend |= call.name.equals("defaultBlendFunc");
                noDepthWrites |= call.name.equals("depthMask") && previousCode(call).getOpcode() == Opcodes.ICONST_0;
            }
        }
        check(filter >= 0 && end > filter, "each pass filters the batch kind before ending/drawing its builder");
        check(bias && blend && noDepthWrites, "translucent overlays blend without depth writes and retain surface bias");

        Class<?> keyType = Class.forName(PIPELINE.replace('/', '.') + "$Key");
        Method isLines = accessibleMethod(keyType, "isLines");
        Set<VertexFormat.DrawMode> lineModes = Set.of(VertexFormat.DrawMode.LINES, VertexFormat.DrawMode.LINE_STRIP,
                VertexFormat.DrawMode.DEBUG_LINES, VertexFormat.DrawMode.DEBUG_LINE_STRIP);
        for (VertexFormat.DrawMode mode : VertexFormat.DrawMode.values()) {
            check((boolean) isLines.invoke(key(mode)) == lineModes.contains(mode), "batch classification: " + mode);
        }
        @SuppressWarnings("unchecked") Map<Object, Object> batches = (Map<Object, Object>) field(WorldOverlayRenderer.class, "BATCHES").get(null);
        check(batches.isEmpty(), "batch insertion regression fixture starts isolated");
        try {
            WorldOverlayRenderer.buffer(VertexFormat.DrawMode.LINES, VertexFormats.LINES, 1, true);
            WorldOverlayRenderer.buffer(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR, 1, true);
            List<Boolean> cachedOrder = new ArrayList<>();
            for (Object key : batches.keySet()) cachedOrder.add((boolean) isLines.invoke(key));
            check(cachedOrder.equals(List.of(true, false)), "enabling Fill after the outline reproduces the reversed cache order");
        } finally {
            for (Object batch : batches.values()) {
                accessibleMethod(batch.getClass(), "discard").invoke(batch);
                ((BufferAllocator) field(batch.getClass(), "allocator").get(batch)).close();
            }
            batches.clear();
        }
    }

    private static void sortedFaces() throws Exception {
        try (TestBatch batch = new TestBatch()) {
            MatrixStack matrices = new MatrixStack();
            Box box = new Box(-1, -1, -1, 1, 1, 1);
            BufferBuilder builder = batch.builder(VertexFormat.DrawMode.QUADS);
            matrices.translate(0, 0, -4);
            Render3DUtil.vertexBoxFill(matrices, builder, box, 0x603399CC, 1);
            matrices.translate(0, 0, -20);
            Render3DUtil.vertexBoxFill(matrices, builder, box, 0xA03399CC, 1);
            try (BuiltBuffer built = batch.end(VertexFormat.DrawMode.QUADS)) {
                check(built != null && built.getSortedBuffer() != null, "actual batch end creates sorted indices for colored quads");
                ByteBuffer indices = built.getSortedBuffer().order(ByteOrder.nativeOrder());
                int indexSize = built.getDrawParameters().indexType() == VertexFormat.IndexType.SHORT ? 2 : 4;
                boolean[] seen = new boolean[12];
                double previousDistance = Double.POSITIVE_INFINITY;
                int[] offsets = {0, 1, 2, 2, 3, 0};
                for (int face = 0; face < 12; face++) {
                    int base = index(indices, face * 6, indexSize);
                    int quad = base / 4;
                    check(base % 4 == 0 && quad < 12 && !seen[quad], "sorted indices contain each independent face once");
                    seen[quad] = true;
                    for (int vertex = 0; vertex < 6; vertex++) {
                        check(index(indices, face * 6 + vertex, indexSize) == base + offsets[vertex], "sorting preserves complete quad triangles");
                    }
                    Vector3f center = position(built.getBuffer(), base, 16).add(position(built.getBuffer(), base + 2, 16)).mul(0.5F);
                    double distance = center.lengthSquared();
                    check(distance <= previousDistance + 0.0001, "translucent box faces are back-to-front across different entities");
                    previousDistance = distance;
                }
                check(index(indices, 0, indexSize) >= 24, "far box is drawn before the earlier-inserted near box");
            }
            check(field(batch.type, "builder").get(batch.instance) == null, "ending a batch detaches its builder");
            batch.builder(VertexFormat.DrawMode.QUADS);
            check(batch.end(VertexFormat.DrawMode.QUADS) == null, "empty/reused batch has no stale vertices or sorted indices");
        }
        check(calls(method(read("net/minecraft/client/render/BufferRenderer"), "upload")).stream()
                        .anyMatch(call -> call.owner.equals("net/minecraft/client/gl/VertexBuffer") && call.name.equals("upload"))
                        && read("net/minecraft/client/gl/VertexBuffer").methods.stream().flatMap(method -> calls(method).stream())
                        .anyMatch(call -> call.name.equals("getSortedBuffer")),
                "Minecraft's GPU upload consumes the sorted index buffer");
    }

    private static void helperCoordinates() throws Exception {
        ClassNode ft = read("vorga/phazeclient/implement/features/modules/other/FTHelperRenderer");
        MethodNode feet = method(ft, "drawCircleAtFeet");
        int subtractions = 0;
        for (AbstractInsnNode instruction : feet.instructions) if (instruction.getOpcode() == Opcodes.DSUB) subtractions++;
        check(subtractions == 3, "FT feet circle subtracts all three camera axes in double precision");
        check(calls(feet).stream().noneMatch(call -> call.owner.equals("net/minecraft/client/util/math/MatrixStack") && call.name.equals("translate")),
                "FT feet circle cannot apply an additional negative camera translation");
        Set<String> renderPositions = new java.util.HashSet<>();
        for (AbstractInsnNode instruction : method(ft, "lerpedPlayerPos").instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.startsWith("lastRender")) renderPositions.add(field.name);
        }
        check(renderPositions.equals(Set.of("lastRenderX", "lastRenderY", "lastRenderZ")), "FT helper uses the same tick-interpolated position as entity rendering");

        Class<?> renderer = Class.forName("vorga.phazeclient.implement.features.modules.other.HolyWorldHelperRenderer");
        Class<?> edgeType = Class.forName(renderer.getName() + "$Edge");
        Object edge = accessibleMethod(edgeType, "of", float.class, float.class, float.class, float.class, float.class, float.class)
                .invoke(null, 0F, 0F, 0F, 1F, 0F, 0F);
        Method append = accessibleMethod(renderer, "addLine", BufferBuilder.class, MatrixStack.Entry.class, edgeType,
                float.class, float.class, float.class, float.class, float.class, float.class, float.class);
        try (BufferAllocator allocator = new BufferAllocator(4096)) {
            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            append.invoke(null, builder, matrices.peek(), edge, 0F, 0F, 0F, 1F, 1F, 1F, 0.5F);
            try (BuiltBuffer built = builder.end()) {
                int nx = built.getBuffer().get(16), nz = built.getBuffer().get(18);
                // Quaternion rotation and signed-byte normal packing can round -1 to -126/-127.
                check(Math.abs(nx) <= 1 && Math.abs(nz + 127) <= 1,
                        "HolyWorld cached edge tangent follows its local transform: " + nx + ", " + nz);
            }
        }
    }

    private static void originalProviders() throws Exception {
        for (String name : new String[]{"implement/features/modules/other/ChunkBorders", "mixins/EntityRendererMixin",
                "mixins/EquipmentRendererMixin", "mixins/ArmorFeatureRendererMixin"}) {
            ClassNode node = read("vorga/phazeclient/" + name);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    check(!(instruction instanceof MethodInsnNode call && call.owner.equals(PIPELINE))
                                    && !(instruction instanceof FieldInsnNode field && field.owner.equals(PIPELINE)),
                            "original debug/text/Hit Color provider is not replaced with overlay consumers: " + name);
                }
            }
        }
        MethodNode borders = method(read("vorga/phazeclient/implement/features/modules/other/ChunkBorders"), "renderCustomChunkBorders");
        check(calls(borders).stream().filter(call -> call.name.equals("getDebugLineStrip")).count() == 3,
                "all three Chunk Borders categories use their original debug layers");
        check(calls(borders).stream().filter(call -> call.name.equals("getBuffer")
                        && call.owner.equals("net/minecraft/client/render/VertexConsumerProvider")).count() == 3,
                "all Chunk Borders buffers are supplied by the original provider");
        ClassNode range = read("vorga/phazeclient/implement/hitrange/HitRangeCircleRenderer");
        check(!method(range, "drawCircle").desc.contains("VertexConsumerProvider"), "Hit Range entry point cannot substitute a vanilla vertex provider");
        check(calls(method(range, "buffer")).stream().anyMatch(call -> call.owner.equals(PIPELINE) && call.name.equals("buffer")),
                "Hit Range remains on the independent BufferBuilder path");
        MethodNode hitbox = method(read("vorga/phazeclient/mixins/EntityRenderDispatcherMixin"), "phaze$redirectHitboxRender");
        check(calls(hitbox).stream().filter(call -> call.owner.equals(PIPELINE) && call.name.equals("buffer")).count() == 2,
                "both custom-hitbox fill and outline remain on BufferBuilder");
        check(calls(hitbox).stream().noneMatch(call -> call.name.equals("getBuffer")), "custom hitboxes do not use vanilla/Iris layer consumers");
        MethodNode hitColor = method(read("vorga/phazeclient/mixins/LivingEntityRendererMixin"), "phaze$renderFeatureWithHitColorOverlay");
        check(calls(hitColor).stream().noneMatch(call -> call.owner.equals(PIPELINE)), "Hit Color features keep their original entity/armor providers");
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

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        return calls;
    }

    private static Method accessibleMethod(Class<?> type, String name, Class<?>... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object key(VertexFormat.DrawMode mode) throws Exception {
        Class<?> type = Class.forName(PIPELINE.replace('/', '.') + "$Key");
        Constructor<?> constructor = type.getDeclaredConstructor(VertexFormat.DrawMode.class, VertexFormat.class,
                net.minecraft.util.Identifier.class, float.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mode, VertexFormats.POSITION_COLOR, null, 1F, true);
    }

    private static final class TestBatch implements AutoCloseable {
        final Class<?> type = Class.forName(PIPELINE.replace('/', '.') + "$Batch");
        final Object instance;
        final BufferAllocator allocator;

        TestBatch() throws Exception {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
            allocator = (BufferAllocator) field(type, "allocator").get(instance);
        }

        BufferBuilder builder(VertexFormat.DrawMode mode) throws Exception {
            BufferBuilder builder = new BufferBuilder(allocator, mode, VertexFormats.POSITION_COLOR);
            field(type, "builder").set(instance, builder);
            return builder;
        }

        BuiltBuffer end(VertexFormat.DrawMode mode) throws Exception {
            Object key = key(mode);
            return (BuiltBuffer) accessibleMethod(type, "end", key.getClass()).invoke(instance, key);
        }

        @Override public void close() throws Exception {
            try { accessibleMethod(type, "discard").invoke(instance); }
            finally { allocator.close(); }
        }
    }

    private static Vector3f position(ByteBuffer buffer, int vertex, int stride) {
        int offset = vertex * stride;
        return new Vector3f(buffer.getFloat(offset), buffer.getFloat(offset + 4), buffer.getFloat(offset + 8));
    }

    private static int index(ByteBuffer buffer, int index, int size) {
        return size == 2 ? Short.toUnsignedInt(buffer.getShort(index * size)) : buffer.getInt(index * size);
    }

    private static boolean corner(float value, double min, double max) {
        return Math.abs(value - min) <= 0.0001 || Math.abs(value - max) <= 0.0001;
    }

    private static void color(ByteBuffer buffer, int vertex, int stride, int r, int g, int b, int a) {
        int offset = vertex * stride + 12;
        check(Math.abs(Byte.toUnsignedInt(buffer.get(offset)) - r) <= 1
                        && Math.abs(Byte.toUnsignedInt(buffer.get(offset + 1)) - g) <= 1
                        && Math.abs(Byte.toUnsignedInt(buffer.get(offset + 2)) - b) <= 1
                        && Math.abs(Byte.toUnsignedInt(buffer.get(offset + 3)) - a) <= 1,
                "geometry preserves RGB and configured fill/outline alpha");
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= 0.0001F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
