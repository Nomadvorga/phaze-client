package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vorga.phazeclient.api.system.render.Render3DUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Actual Minecraft buffers, without a GL context: batching must change cost, not the displayed arc. */
public final class PredictionBatchChecks {
    private static final Object WORLD = new Object(), OWNER = new Object();
    private static final float[][] OLD_COS = new float[65][], OLD_SIN = new float[65][];
    private static final float[][] OLD_LAT_COS = new float[13][], OLD_LAT_SIN = new float[13][];
    private static int checks;

    public static void main(String[] args) throws Exception {
        lineParity();
        trailFade();
        sphereMeshes();
        faceRings();
        billboards();
        visibilityAndDetail();
        preparedGeometryCaching();
        bufferReuse();
        System.out.println("Prediction batch checks passed: " + checks);
    }

    private static void lineParity() {
        Random random = new Random(90214);
        try (PredictionRenderBatch batch = new PredictionRenderBatch(); BufferAllocator allocator = new BufferAllocator(4096)) {
            for (int frame = 0; frame < 200; frame++) {
                MatrixStack matrices = new MatrixStack();
                matrices.multiplyPositionMatrix(new Matrix4f().rotateXYZ(random.nextFloat(), random.nextFloat(), random.nextFloat()));
                if ((frame & 1) == 0) matrices.scale(0.75F, 1.3F, 2.0F);
                batch.begin(matrices, new Quaternionf(), projection(), 800, 2, true);
                BufferBuilder reference = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                for (int line = 0; line < 20; line++) {
                    double x = random.nextDouble() * 300 - 150, y = random.nextDouble() * 300 - 150, z = random.nextDouble() * 300 - 150;
                    double ex = x + random.nextDouble() * 3, ey = y - random.nextDouble() * 3, ez = z + random.nextDouble() * 3;
                    int a = random.nextInt(), b = random.nextInt();
                    Render3DUtil.vertexLine(matrices, reference, x, y, z, ex, ey, ez, a, b);
                    batch.line(x, y, z, ex, ey, ez, a, b);
                }
                try (BuiltBuffer old = reference.end(); BuiltBuffer current = batch.endLines()) {
                    compare(old, current, VertexFormats.LINES.getVertexSizeByte(), 0, true);
                }
                allocator.clear();
                batch.begin(matrices, new Quaternionf(), projection(), 800, 2, true);
                batch.line(1, 2, 3, 1, 2, 3, -1, -1);
                batch.line(Double.NaN, 0, 0, 1, 2, 3, -1, -1);
                check(batch.endLines() == null, "zero-length/non-finite segments never reach the line shader");
            }
        }
    }

    private static void trailFade() {
        for (Vec3d camera : new Vec3d[]{new Vec3d(5, 65, -9), new Vec3d(29_999_000.25, 110, -29_999_000.125)}) {
            List<Vec3d> points = new ArrayList<>();
            points.add(camera.add(0.125, -0.4, -2));
            points.add(points.getFirst());
            points.add(camera.add(0.2, -0.45, -2.2));
            points.add(camera.add(0.7, -0.9, -5.7));
            points.add(camera.add(1.3, -1.7, -10.8));
            for (float fade : new float[]{0, 0.5F, 2, 8, 20}) {
                for (int color : new int[]{0xFF3162D9, 0x8015A4F3, 0x00010203}) {
                    MatrixStack matrices = new MatrixStack();
                    matrices.multiplyPositionMatrix(new Matrix4f().rotateXYZ(0.1F, -0.5F, 0.03F));
                    try (PredictionRenderBatch batch = new PredictionRenderBatch(); BufferAllocator allocator = new BufferAllocator(4096)) {
                        batch.begin(matrices, new Quaternionf(), projection(), 800, 2, true);
                        batch.trail(points, camera, color, fade);
                        BufferBuilder old = new BufferBuilder(allocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                        emitOldTrail(matrices, old, points, camera, color, fade);
                        try (BuiltBuffer reference = old.end(); BuiltBuffer current = batch.endLines()) {
                            compare(reference, current, VertexFormats.LINES.getVertexSizeByte(), 0.000002F, true);
                        }
                    }
                }
            }
        }
    }

    /** Original local-origin fade math, including the split at the fade-distance boundary. */
    static void emitOldTrail(MatrixStack matrices, BufferBuilder buffer, List<Vec3d> points, Vec3d camera, int color, float fade) {
        Vec3d origin = points.getFirst();
        matrices.push();
        matrices.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        float maxAlpha = (color >>> 24) / 255.0F;
        double accumulated = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d a = points.get(i), b = points.get(i + 1);
            double sx = a.x - origin.x, sy = a.y - origin.y, sz = a.z - origin.z;
            double ex = b.x - origin.x, ey = b.y - origin.y, ez = b.z - origin.z;
            double dx = ex - sx, dy = ey - sy, dz = ez - sz;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Double.isFinite(length) || length < 1E-7) continue;
            double end = accumulated + length;
            int aColor = fade <= 0 ? color : fadeColor(color, maxAlpha, accumulated, fade);
            int bColor = fade <= 0 ? color : fadeColor(color, maxAlpha, end, fade);
            if (accumulated < fade && end > fade) {
                double t = (fade - accumulated) / length;
                double mx = sx + dx * t, my = sy + dy * t, mz = sz + dz * t;
                Render3DUtil.vertexLine(matrices, buffer, sx, sy, sz, mx, my, mz, aColor, color);
                Render3DUtil.vertexLine(matrices, buffer, mx, my, mz, ex, ey, ez, color, color);
            } else Render3DUtil.vertexLine(matrices, buffer, sx, sy, sz, ex, ey, ez, aColor, bColor);
            accumulated = end;
        }
        matrices.pop();
    }

    private static int fadeColor(int color, float alpha, double distance, float fade) {
        return ((int) ((float) Math.min(1, Math.max(0, distance / fade)) * alpha * 255) << 24) | (color & 0xFFFFFF);
    }

    private static void sphereMeshes() throws Exception {
        int[] stacks = {6, 8, 10, 12}, segments = {10, 12, 16, 18};
        try (PredictionRenderBatch batch = new PredictionRenderBatch(); BufferAllocator allocator = new BufferAllocator(65_536)) {
            for (float depth : new float[]{100, 50, 20, 3}) {
                MatrixStack matrices = new MatrixStack();
                batch.begin(matrices, new Quaternionf(), projection(), 800, 2, true);
                int level = PredictionRenderBatch.sphereLevel(batch.projectedRadius(0, 0, -depth, 0.35F));
                batch.sphere(0, 0, -depth, 0.35F, 0x803377DD);
                BufferBuilder old = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                emitOldSphere(matrices.peek().getPositionMatrix(), old, 0, 0, -depth, 0.35F, 0x803377DD, stacks[level], segments[level]);
                try (BuiltBuffer reference = old.end(); BuiltBuffer current = batch.endMarkers()) {
                    compare(reference, current, 16, 0.000008F, false);
                    check(current.getDrawParameters().vertexCount() == stacks[level] * segments[level] * 4,
                            "sphere level keeps a complete outward-wound UV mesh");
                    ByteBuffer data = current.getBuffer();
                    for (int quad = 0; quad < stacks[level] * segments[level]; quad++) {
                        Vector3f a = position(data, quad * 4, 16), b = position(data, quad * 4 + 1, 16), c = position(data, quad * 4 + 2, 16);
                        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
                        check(normal.dot(new Vector3f(a).add(b).add(c).mul(1F / 3).add(0, 0, depth)) >= -0.0000001F,
                                "back-face culling keeps the outside, not a two-tone rear hemisphere");
                    }
                }
                allocator.clear();
                Object mesh = ((Object[]) field(PredictionRenderBatch.class, "SPHERES").get(null))[level];
                batch.begin(matrices, new Quaternionf(), projection(), 800, 2, true);
                batch.sphere(0, 0, -depth, 0.35F, -1);
                batch.discard();
                check(((Object[]) field(PredictionRenderBatch.class, "SPHERES").get(null))[level] == mesh,
                        "unchanged sphere detail reuses the immutable mesh");
            }
        }
    }

    /** Original sphere geometry with cached angles; no GL operations, suitable for the CPU benchmark. */
    static void emitOldSphere(Matrix4f matrix, BufferBuilder buffer, float x, float y, float z, float radius,
                              int color, int stacks, int segments) {
        float red = ((color >>> 16) & 255) / 255F, green = ((color >>> 8) & 255) / 255F;
        float blue = (color & 255) / 255F, alpha = (color >>> 24) / 255F;
        float[] cos = OLD_COS[segments], sin = OLD_SIN[segments];
        if (cos == null) {
            OLD_COS[segments] = cos = new float[segments + 1];
            OLD_SIN[segments] = sin = new float[segments + 1];
            for (int j = 0; j <= segments; j++) {
                double angle = Math.PI * 2 * j / segments;
                cos[j] = (float) Math.cos(angle);
                sin[j] = (float) Math.sin(angle);
            }
        }
        float[] latCos = OLD_LAT_COS[stacks], latSin = OLD_LAT_SIN[stacks];
        if (latCos == null) {
            OLD_LAT_COS[stacks] = latCos = new float[stacks + 1];
            OLD_LAT_SIN[stacks] = latSin = new float[stacks + 1];
            for (int i = 0; i <= stacks; i++) {
                double angle = Math.PI * i / stacks - Math.PI / 2;
                latCos[i] = (float) Math.cos(angle);
                latSin[i] = (float) Math.sin(angle);
            }
        }
        for (int i = 0; i < stacks; i++) {
            float y1 = latSin[i] * radius, y2 = latSin[i + 1] * radius;
            float r1 = latCos[i] * radius, r2 = latCos[i + 1] * radius;
            for (int j = 0; j < segments; j++) {
                buffer.vertex(matrix, x + r1 * cos[j], y + y1, z + r1 * sin[j]).color(red, green, blue, alpha);
                buffer.vertex(matrix, x + r2 * cos[j], y + y2, z + r2 * sin[j]).color(red, green, blue, alpha);
                buffer.vertex(matrix, x + r2 * cos[j + 1], y + y2, z + r2 * sin[j + 1]).color(red, green, blue, alpha);
                buffer.vertex(matrix, x + r1 * cos[j + 1], y + y1, z + r1 * sin[j + 1]).color(red, green, blue, alpha);
            }
        }
    }

    private static void faceRings() {
        try (PredictionRenderBatch batch = new PredictionRenderBatch(); BufferAllocator allocator = new BufferAllocator(4096)) {
            for (Direction face : Direction.values()) {
                for (float depth : new float[]{100, 25, 8, 1}) {
                    MatrixStack matrices = new MatrixStack();
                    batch.begin(matrices, new Quaternionf(), projection(), 800, 2, false);
                    float radius = 0.35F, inner = radius - 0.01F, outer = radius + 0.01F;
                    int segments = PredictionRenderBatch.ringSegments(batch.projectedRadius(0, 0, -depth, outer));
                    float nx = face.getOffsetX(), ny = face.getOffsetY(), nz = face.getOffsetZ();
                    batch.ring(0, 0, -depth, nx, ny, nz, radius, 0.02F, -1);
                    Vector3f normal = new Vector3f(nx, ny, nz);
                    Vector3f helper = Math.abs(nx) <= Math.abs(ny) && Math.abs(nx) <= Math.abs(nz) ? new Vector3f(1, 0, 0)
                            : Math.abs(ny) <= Math.abs(nz) ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
                    Vector3f u = helper.cross(normal, new Vector3f()).normalize(), v = normal.cross(u, new Vector3f());
                    BufferBuilder old = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                    for (int i = 0; i < segments; i++) {
                        Vector3f a = new Vector3f(u).mul((float) Math.cos(2 * Math.PI * i / segments))
                                .fma((float) Math.sin(2 * Math.PI * i / segments), v);
                        Vector3f b = new Vector3f(u).mul((float) Math.cos(2 * Math.PI * (i + 1) / segments))
                                .fma((float) Math.sin(2 * Math.PI * (i + 1) / segments), v);
                        for (Vector3f p : new Vector3f[]{new Vector3f(a).mul(inner), new Vector3f(b).mul(inner),
                                new Vector3f(b).mul(outer), new Vector3f(a).mul(outer)}) old.vertex(p.x, p.y, p.z - depth).color(-1);
                    }
                    try (BuiltBuffer reference = old.end(); BuiltBuffer current = batch.endMarkers()) {
                        compare(reference, current, 16, 0.000001F, false);
                    }
                    allocator.clear();
                }
            }
        }
    }

    private static void billboards() {
        try (PredictionRenderBatch batch = new PredictionRenderBatch(); BufferAllocator allocator = new BufferAllocator(4096)) {
            for (int i = 0; i < 30; i++) {
                Quaternionf rotation = new Quaternionf().rotateXYZ(i * 0.03F, i * 0.15F, i * 0.01F);
                MatrixStack matrices = new MatrixStack();
                matrices.multiplyPositionMatrix(new Matrix4f().rotate(rotation.conjugate(new Quaternionf())));
                batch.begin(matrices, rotation, new Matrix4f(projection()).mul(matrices.peek().getPositionMatrix()), 800, 2, true);
                batch.glow(3, 2, -10, 1.3F, 0x701345DE);
                Vector3f right = rotation.transform(new Vector3f(1.3F, 0, 0)), up = rotation.transform(new Vector3f(0, 1.3F, 0));
                BufferBuilder old = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (int vertex = 0; vertex < 4; vertex++) {
                    float r = vertex == 0 || vertex == 3 ? -1 : 1, u = vertex < 2 ? -1 : 1;
                    old.vertex(matrices.peek().getPositionMatrix(), 3 + r * right.x + u * up.x,
                            2 + r * right.y + u * up.y, -10 + r * right.z + u * up.z)
                            .texture(r < 0 ? 0 : 1, u < 0 ? 1 : 0).color(0x701345DE);
                }
                try (BuiltBuffer reference = old.end(); BuiltBuffer current = batch.endGlows()) {
                    compare(reference, current, VertexFormats.POSITION_TEXTURE_COLOR.getVertexSizeByte(), 0.000003F, false);
                }
                allocator.clear();
            }
        }
    }

    private static void visibilityAndDetail() {
        int[] stacks = {6, 8, 10, 12}, segments = {10, 12, 16, 18};
        for (float pixels : new float[]{0.5F, 4, 8, 18, 20, 100}) {
            int level = PredictionRenderBatch.sphereLevel(pixels);
            if (level < 3) check(pixels * (2 - Math.cos(Math.PI / (2 * stacks[level])) - Math.cos(Math.PI / segments[level])) < 0.65,
                    "sphere silhouette simplification is bounded to a subpixel at reduced detail");
            int ring = PredictionRenderBatch.ringSegments(pixels);
            if (ring < 64) check(pixels * (1 - Math.cos(Math.PI / ring)) < 0.32,
                    "reduced ring detail changes the silhouette by less than one third of a pixel");
        }
        check(PredictionRenderBatch.sphereLevel(19) == 3 && PredictionRenderBatch.ringSegments(65) == 64,
                "large nearby markers retain the original 12x18 sphere and 64-segment ring");
        try (PredictionRenderBatch batch = new PredictionRenderBatch()) {
            for (float fov : new float[]{30, 70, 110}) {
                for (float aspect : new float[]{1, 16F / 9, 21F / 9}) {
                    Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.05F, 1024);
                    float focal = projection.m11() * 540;
                    MatrixStack matrices = new MatrixStack();
                    batch.begin(matrices, new Quaternionf(), projection, focal, 2, true);
                    check(batch.visible(0, 0, -5, 0.35F), "visible markers survive culling");
                    check(!batch.visible(0, 0, 5, 0.35F) && !batch.visible(0, 0, -2000, 0.35F),
                            "markers wholly behind the camera/far plane emit no geometry");
                    for (float depth : new float[]{0.5F, 3, 20, 100}) {
                        for (float ndcX : new float[]{0, 0.95F, 1.02F, -1.02F}) {
                            float x = ndcX * depth / projection.m00(), radius = 0.35F;
                            Vector3f center = projection.transformProject(new Vector3f(x, 0, -depth));
                            float bound = batch.projectedRadius(x, 0, -depth, radius);
                            for (int angle = 0; angle < 160; angle++) {
                                float a = angle * (float) Math.PI / 80;
                                Vector3f point = projection.transformProject(new Vector3f(x + radius * (float) Math.cos(a), 0,
                                        -depth + radius * (float) Math.sin(a)));
                                check(Math.abs(point.x - center.x) * aspect * 540 <= bound + 0.1F,
                                        "wide-FOV screen edges cannot underestimate marker detail");
                                if (Math.abs(point.x) <= 1 && Math.abs(point.y) <= 1 && Math.abs(point.z) <= 1)
                                    check(batch.visible(x, 0, -depth, radius), "culling cannot discard a partially visible sphere");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void preparedGeometryCaching() throws Exception {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult first = trajectory(0, 80);
        update(view, 1, 0, first);
        view.prepare(0.1F);
        Vec3d origin = new Vec3d(0.01, 1.6, -0.052);
        view.setLineOrigin(origin);
        Field version = field(HeldPredictionView.class, "physicalVersion");
        long initialVersion = version.getLong(view);
        for (int frame = 0; frame < 100; frame++) {
            view.prepare(frame / 100F);
            view.setLineOrigin(new Vec3d(origin.x, origin.y, origin.z));
            check(version.getLong(view) == initialVersion, "immediate/cached results are not re-copied for irrelevant tick alpha");
            vector(view.impact(), first.impact(), 0, "cache never shifts the exact landing point");
        }
        Vec3d camera = new Vec3d(0, 1.6, 0);
        Matrix4f forward = projection(), inverse = new Matrix4f(forward).invert();
        for (int frame = 0; frame < 40; frame++) {
            if (frame % 4 == 0) { forward.rotateY(0.01F); inverse.set(forward).invert(); }
            if (frame % 4 == 1) camera = camera.add(0.002, 0, 0);
            if (frame % 4 == 2) origin = origin.add(0.0001, -0.0001, 0);
            view.prepare(0.5F);
            view.setLineOrigin(origin, forward, inverse, camera);
            HeldPredictionView fresh = new HeldPredictionView();
            update(fresh, 1, 0, first);
            fresh.prepare(0.5F);
            fresh.setLineOrigin(origin, forward, inverse, camera);
            check(view.size() == fresh.size(), "origin caches restore the full point count after prepare");
            for (int i = 0; i < view.size(); i++) vector(point(view, i), point(fresh, i), 0,
                    "projection/camera/origin changes invalidate geometry, including in-place matrix edits");
        }
        Predictions.TrajectoryResult next = trajectory(0.3, 80);
        update(view, 2, 0, next);
        view.prepare(0.25F);
        view.setWorldLaunchOrigin(first.path().getFirst().lerp(next.path().getFirst(), 0.25));
        long quarterVersion = version.getLong(view);
        for (int i = 2; i < view.size(); i++) vector(point(view, i), first.path().get(i).lerp(next.path().get(i), 0.25), 1E-12,
                "same-size interpolation fast path preserves every physical tail coordinate");
        view.prepare(0.25F);
        view.setWorldLaunchOrigin(first.path().getFirst().lerp(next.path().getFirst(), 0.25));
        check(version.getLong(view) == quarterVersion, "unchanged interpolated alpha reuses prepared geometry");
        view.prepare(0.75F);
        check(version.getLong(view) > quarterVersion, "changed interpolation alpha cannot return stale geometry");
        update(view, 2, 20, next);
        view.useImmediateResult();
        view.prepare(0);
        vector(view.impact(), next.impact(), 0, "aim changes bypass interpolation immediately");
        view.reset();
        update(view, 10, 0, first);
        view.prepare(0);
        view.setLineOrigin(origin);
        vector(view.impact(), first.impact(), 0, "context reset/reappearance invalidates every cache");
    }

    private static void bufferReuse() throws Exception {
        try (PredictionRenderBatch batch = new PredictionRenderBatch()) {
            MatrixStack matrices = new MatrixStack();
            Field pointer = field(BufferAllocator.class, "pointer");
            String[] names = {"lineAllocator", "markerAllocator", "glowAllocator"};
            long[] pointers = new long[3];
            for (int frame = 0; frame < 80; frame++) {
                batch.begin(matrices, new Quaternionf(), projection(), 800, 2, true);
                for (int line = 0; line < 1000; line++) batch.line(line * 0.001, 0, -3, line * 0.001, 1, -4, -1, -1);
                for (int marker = 0; marker < 8; marker++) batch.sphere(marker * 0.1F, 0, -3, 0.35F, -1);
                for (int glow = 0; glow < 300; glow++) batch.glow(glow * 0.001F, 0, -3, 0.7F, -1);
                try (BuiltBuffer lines = batch.endLines(); BuiltBuffer markers = batch.endMarkers(); BuiltBuffer glows = batch.endGlows()) {
                    check(lines.getDrawParameters().vertexCount() == 4000, "all lines occupy one buffer without cross-path bridges");
                    check(markers.getDrawParameters().vertexCount() == 8 * 12 * 18 * 4, "all marker bodies share one independent quad buffer");
                    check(glows.getDrawParameters().vertexCount() == 1200, "all original bloom billboards share one additive buffer");
                }
                for (int i = 0; i < names.length; i++) {
                    long address = pointer.getLong(field(PredictionRenderBatch.class, names[i]).get(batch));
                    if (frame == 0) pointers[i] = address;
                    check(pointers[i] == address, "grown native buffers are reused, not allocated each frame");
                }
            }
            batch.begin(matrices, new Quaternionf(), projection(), 800, 2, false);
            batch.sphere(0, 0, -3, 1, 0x003377DD);
            batch.ring(0, 0, -3, 0, 1, 0, 1, 0.02F, 0x003377DD);
            batch.glow(0, 0, -3, 1, 0x003377DD);
            check(batch.endLines() == null && batch.endMarkers() == null && batch.endGlows() == null,
                    "empty/zero-opacity frames contain no preceding-frame geometry");
            batch.line(0, 0, -3, 1, 0, -3, -1, -1);
            batch.discard();
            batch.begin(matrices, new Quaternionf(), projection(), 800, 2, false);
            check(batch.endLines() == null, "unfinished frames are discarded without a draw or ghost line");
        }
    }

    private static void compare(BuiltBuffer reference, BuiltBuffer current, int stride, float epsilon, boolean lines) {
        check(current != null && reference.getDrawParameters().vertexCount() == current.getDrawParameters().vertexCount(),
                "batch keeps the original primitive count at the selected detail");
        ByteBuffer a = reference.getBuffer(), b = current.getBuffer();
        for (int vertex = 0; vertex < reference.getDrawParameters().vertexCount(); vertex++) {
            int offset = vertex * stride;
            for (int coordinate = 0; coordinate < 12; coordinate += 4)
                check(Math.abs(a.getFloat(offset + coordinate) - b.getFloat(offset + coordinate)) <= epsilon,
                        "batch preserves transformed vertex positions");
            for (int attribute = 12; attribute < stride; attribute++) {
                if (lines && attribute == 19) continue; // Unspecified alignment padding is not an attribute.
                check(a.get(offset + attribute) == b.get(offset + attribute), "batch preserves colour, UV and packed line tangents");
            }
        }
    }

    private static Matrix4f projection() { return new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9, 0.05F, 1024); }
    private static Field field(Class<?> owner, String name) throws Exception { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Vector3f position(ByteBuffer data, int vertex, int stride) { int offset = vertex * stride; return new Vector3f(data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8)); }
    private static Vec3d point(HeldPredictionView view, int i) { return new Vec3d(view.x(i), view.y(i), view.z(i)); }
    private static void update(HeldPredictionView view, long tick, float yaw, Predictions.TrajectoryResult result) { view.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 1, 0, tick, yaw, 0, result); }
    private static Predictions.TrajectoryResult trajectory(double x, int count) {
        List<Vec3d> path = new ArrayList<>();
        for (int i = 0; i < count; i++) { double t = (double) i / (count - 1); path.add(new Vec3d(x + t * 2, (1 - t) * 1.5, -t * 24)); }
        return new Predictions.TrajectoryResult(path, path.getLast(), HitResult.Type.BLOCK, Direction.UP, null);
    }
    private static void vector(Vec3d actual, Vec3d expected, double epsilon, String message) { check(actual.distanceTo(expected) <= epsilon, message); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
