package vorga.phazeclient.implement.features.modules.other;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Three independent late-world draws for ALL held/live paths, halos and marker bodies. */
final class PredictionRenderBatch implements AutoCloseable {
    private static final Identifier GLOW_TEXTURE = Identifier.of("phaze", "textures/particles/bloom/bloom_soft.png");
    private static final int[] SPHERE_STACKS = {6, 8, 10, 12};
    private static final int[] SPHERE_SEGMENTS = {10, 12, 16, 18};
    private static final float[][] SPHERES = new float[SPHERE_STACKS.length][];
    private static final float[][] CIRCLES = new float[65][];
    private final BufferAllocator lineAllocator = new BufferAllocator(65_536);
    private final BufferAllocator markerAllocator = new BufferAllocator(65_536);
    private final BufferAllocator glowAllocator = new BufferAllocator(4096);
    private final Vector3f position = new Vector3f(), normal = new Vector3f();
    private final Vector3f right = new Vector3f(), up = new Vector3f();
    private final Matrix4f forward = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private MatrixStack.Entry entry;
    private Matrix4f matrix;
    private BufferBuilder lines, markers, glows;
    private float width, focalPixels, depthScale, inverseXScale, inverseYScale;
    private boolean sphere;

    void begin(MatrixStack matrices, Quaternionf cameraRotation, Matrix4f forward,
               float focalPixels, float width, boolean sphere) {
        discard();
        lineAllocator.clear();
        markerAllocator.clear();
        glowAllocator.clear();
        entry = matrices.peek();
        matrix = entry.getPositionMatrix();
        cameraRotation.transform(1, 0, 0, right);
        cameraRotation.transform(0, 1, 0, up);
        this.forward.set(forward);
        frustum.set(forward);
        depthScale = (float) Math.sqrt(forward.m03() * forward.m03() + forward.m13() * forward.m13()
                + forward.m23() * forward.m23());
        inverseXScale = depthScale / Math.max(0.0001F, (float) Math.sqrt(forward.m00() * forward.m00()
                + forward.m10() * forward.m10() + forward.m20() * forward.m20()));
        inverseYScale = depthScale / Math.max(0.0001F, (float) Math.sqrt(forward.m01() * forward.m01()
                + forward.m11() * forward.m11() + forward.m21() * forward.m21()));
        this.focalPixels = focalPixels;
        this.width = width;
        this.sphere = sphere;
    }

    boolean visible(float x, float y, float z, float radius) {
        return frustum.testSphere(x, y, z, radius);
    }

    float projectedRadius(float x, float y, float z, float radius) {
        float depth = forward.m03() * x + forward.m13() * y + forward.m23() * z + forward.m33();
        float clipX = forward.m00() * x + forward.m10() * y + forward.m20() * z + forward.m30();
        float clipY = forward.m01() * x + forward.m11() * y + forward.m21() * z + forward.m31();
        float offAxis = Math.max(Math.abs(clipX) * inverseXScale, Math.abs(clipY) * inverseYScale)
                / Math.max(0.0001F, depth);
        // Conservative near-side/projective bound, including wide-FOV screen edges.
        // Close or off-axis markers cannot silently drop below their original detail.
        return focalPixels * radius * (1 + offAxis) / Math.max(0.0001F, depth - radius * depthScale);
    }

    void path(HeldPredictionView path, Vec3d camera, int color) {
        for (int i = 0, end = path.size() - 1; i < end; i++) {
            line(path.x(i) - camera.x, path.y(i) - camera.y, path.z(i) - camera.z,
                    path.x(i + 1) - camera.x, path.y(i + 1) - camera.y, path.z(i + 1) - camera.z, color, color);
        }
    }

    void trail(List<Vec3d> points, Vec3d camera, int color, float fadeDistance) {
        double accumulated = 0;
        float maxAlpha = (color >>> 24) / 255.0F;
        for (int i = 0, end = points.size() - 1; i < end; i++) {
            Vec3d start = points.get(i), finish = points.get(i + 1);
            if (start == null || finish == null) continue;
            double sx = start.x - camera.x, sy = start.y - camera.y, sz = start.z - camera.z;
            double ex = finish.x - camera.x, ey = finish.y - camera.y, ez = finish.z - camera.z;
            if (fadeDistance <= 0 || accumulated >= fadeDistance) {
                line(sx, sy, sz, ex, ey, ez, color, color);
                continue;
            }
            double dx = ex - sx, dy = ey - sy, dz = ez - sz;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Double.isFinite(length) || length < 1.0E-7) continue;
            tangent(dx, dy, dz, length);
            double next = accumulated + length;
            int startColor = fadedColor(color, maxAlpha, accumulated, fadeDistance);
            int endColor = fadedColor(color, maxAlpha, next, fadeDistance);
            if (accumulated < fadeDistance && next > fadeDistance) {
                double t = (fadeDistance - accumulated) / length;
                double mx = sx + dx * t, my = sy + dy * t, mz = sz + dz * t;
                segment(sx, sy, sz, mx, my, mz, startColor, color);
                segment(mx, my, mz, ex, ey, ez, color, color);
            } else {
                segment(sx, sy, sz, ex, ey, ez, startColor, endColor);
            }
            accumulated = next;
        }
    }

    private static int fadedColor(int color, float maxAlpha, double distance, float fade) {
        float alpha = (float) Math.min(1.0, Math.max(0.0, distance / fade)) * maxAlpha;
        return ((int) (alpha * 255.0F) << 24) | (color & 0x00FFFFFF);
    }

    void line(double sx, double sy, double sz, double ex, double ey, double ez, int startColor, int endColor) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1.0E-7) return;
        tangent(dx, dy, dz, length);
        segment(sx, sy, sz, ex, ey, ez, startColor, endColor);
    }

    private void tangent(double dx, double dy, double dz, double length) {
        // Transform the tangent ONCE per segment, using Minecraft's exact normal-matrix rules.
        entry.transformNormal((float) (dx / length), (float) (dy / length), (float) (dz / length), normal);
    }

    private void segment(double sx, double sy, double sz, double ex, double ey, double ez, int a, int b) {
        if (lines == null) lines = new BufferBuilder(lineAllocator, VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        vertex(lines, (float) sx, (float) sy, (float) sz).color(a).normal(normal.x, normal.y, normal.z);
        vertex(lines, (float) ex, (float) ey, (float) ez).color(b).normal(normal.x, normal.y, normal.z);
    }

    void sphere(float x, float y, float z, float radius, int color) {
        if ((color >>> 24) == 0) return;
        int lod = sphereLevel(projectedRadius(x, y, z, radius));
        float[] mesh = SPHERES[lod];
        if (mesh == null) SPHERES[lod] = mesh = sphereMesh(SPHERE_STACKS[lod], SPHERE_SEGMENTS[lod]);
        BufferBuilder buffer = markerBuffer();
        for (int i = 0; i < mesh.length; i += 3) {
            vertex(buffer, x + mesh[i] * radius, y + mesh[i + 1] * radius, z + mesh[i + 2] * radius).color(color);
        }
    }

    void ring(float x, float y, float z, float nx, float ny, float nz,
              float radius, float thickness, int color) {
        if ((color >>> 24) == 0) return;
        float inner = Math.max(0, radius - thickness * 0.5F), outer = radius + thickness * 0.5F;
        int segments = ringSegments(projectedRadius(x, y, z, outer));
        float[] circle = circle(segments);
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        float hx = 0, hy = 0, hz = 0;
        if (ax <= ay && ax <= az) hx = 1;
        else if (ay <= ax && ay <= az) hy = 1;
        else hz = 1;
        float ux = hy * nz - hz * ny, uy = hz * nx - hx * nz, uz = hx * ny - hy * nx;
        float length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (length < 1.0E-6F) return;
        ux /= length;
        uy /= length;
        uz /= length;
        float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        BufferBuilder buffer = markerBuffer();
        for (int i = 0; i < segments; i++) {
            float c1 = circle[i * 2], s1 = circle[i * 2 + 1];
            float c2 = circle[(i + 1) * 2], s2 = circle[(i + 1) * 2 + 1];
            float dx1 = ux * c1 + vx * s1, dy1 = uy * c1 + vy * s1, dz1 = uz * c1 + vz * s1;
            float dx2 = ux * c2 + vx * s2, dy2 = uy * c2 + vy * s2, dz2 = uz * c2 + vz * s2;
            vertex(buffer, x + dx1 * inner, y + dy1 * inner, z + dz1 * inner).color(color);
            vertex(buffer, x + dx2 * inner, y + dy2 * inner, z + dz2 * inner).color(color);
            vertex(buffer, x + dx2 * outer, y + dy2 * outer, z + dz2 * outer).color(color);
            vertex(buffer, x + dx1 * outer, y + dy1 * outer, z + dz1 * outer).color(color);
        }
    }

    void glow(float x, float y, float z, float radius, int color) {
        if ((color >>> 24) == 0) return;
        if (glows == null) glows = new BufferBuilder(glowAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float rx = right.x * radius, ry = right.y * radius, rz = right.z * radius;
        float ux = up.x * radius, uy = up.y * radius, uz = up.z * radius;
        vertex(glows, x - rx - ux, y - ry - uy, z - rz - uz).texture(0, 1).color(color);
        vertex(glows, x + rx - ux, y + ry - uy, z + rz - uz).texture(1, 1).color(color);
        vertex(glows, x + rx + ux, y + ry + uy, z + rz + uz).texture(1, 0).color(color);
        vertex(glows, x - rx + ux, y - ry + uy, z - rz + uz).texture(0, 0).color(color);
    }

    private BufferBuilder markerBuffer() {
        if (markers == null) markers = new BufferBuilder(markerAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        return markers;
    }

    private BufferBuilder vertex(BufferBuilder buffer, float x, float y, float z) {
        // VertexConsumer's matrix overload allocates a temporary Vector3f at EVERY vertex.
        matrix.transformPosition(x, y, z, position);
        buffer.vertex(position.x, position.y, position.z);
        return buffer;
    }

    void draw() {
        if (lines == null && markers == null && glows == null) return;
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            if (lines != null) {
                RenderSystem.lineWidth(width);
                RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
                try (BuiltBuffer built = endLines()) {
                    if (built != null) BufferRenderer.drawWithGlobalProgram(built);
                }
            }
            if (glows != null) {
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
                RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                try (BuiltBuffer built = endGlows()) {
                    if (built != null) BufferRenderer.drawWithGlobalProgram(built);
                }
            }
            if (markers != null) {
                RenderSystem.defaultBlendFunc();
                // Same outward sphere winding and back-face culling as the original renderer.
                if (sphere) RenderSystem.enableCull();
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                try (BuiltBuffer built = endMarkers()) {
                    if (built != null) BufferRenderer.drawWithGlobalProgram(built);
                }
            }
        } finally {
            discard();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    BuiltBuffer endLines() {
        BufferBuilder buffer = lines;
        lines = null;
        return buffer == null ? null : buffer.endNullable();
    }

    BuiltBuffer endMarkers() {
        BufferBuilder buffer = markers;
        markers = null;
        return buffer == null ? null : buffer.endNullable();
    }

    BuiltBuffer endGlows() {
        BufferBuilder buffer = glows;
        glows = null;
        return buffer == null ? null : buffer.endNullable();
    }

    void discard() {
        try (BuiltBuffer ignored = endLines(); BuiltBuffer ignoredMarkers = endMarkers(); BuiltBuffer ignoredGlows = endGlows()) {
            // Close unfinished/exceptional frames without issuing a GPU draw.
        }
    }

    @Override public void close() {
        discard();
        lineAllocator.close();
        markerAllocator.close();
        glowAllocator.close();
    }

    static int sphereLevel(float pixels) {
        return pixels <= 4 ? 0 : pixels <= 8 ? 1 : pixels <= 18 ? 2 : 3;
    }

    static int ringSegments(float pixels) {
        return pixels <= 8 ? 16 : pixels <= 24 ? 24 : pixels <= 64 ? 32 : 64;
    }

    private static float[] circle(int segments) {
        float[] circle = CIRCLES[segments];
        if (circle == null) {
            circle = new float[(segments + 1) * 2];
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * 2.0 * i / segments;
                circle[i * 2] = (float) Math.cos(angle);
                circle[i * 2 + 1] = (float) Math.sin(angle);
            }
            CIRCLES[segments] = circle;
        }
        return circle;
    }

    private static float[] sphereMesh(int stacks, int segments) {
        float[] circle = circle(segments);
        float[] mesh = new float[stacks * segments * 12];
        int offset = 0;
        for (int i = 0; i < stacks; i++) {
            double first = Math.PI * i / stacks - Math.PI / 2.0;
            double next = Math.PI * (i + 1) / stacks - Math.PI / 2.0;
            float y1 = (float) Math.sin(first), y2 = (float) Math.sin(next);
            float r1 = (float) Math.cos(first), r2 = (float) Math.cos(next);
            for (int j = 0; j < segments; j++) {
                float c1 = circle[j * 2], s1 = circle[j * 2 + 1];
                float c2 = circle[(j + 1) * 2], s2 = circle[(j + 1) * 2 + 1];
                mesh[offset++] = r1 * c1;
                mesh[offset++] = y1;
                mesh[offset++] = r1 * s1;
                mesh[offset++] = r2 * c1;
                mesh[offset++] = y2;
                mesh[offset++] = r2 * s1;
                mesh[offset++] = r2 * c2;
                mesh[offset++] = y2;
                mesh[offset++] = r2 * s2;
                mesh[offset++] = r1 * c2;
                mesh[offset++] = y1;
                mesh[offset++] = r1 * s2;
            }
        }
        return mesh;
    }
}
