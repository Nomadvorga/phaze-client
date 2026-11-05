/*
 * MIT License
 *
 * Phaze port of the "hitrange" mod by uku3lig (uku).
 * Original source: https://github.com/uku3lig/hitrange
 *
 * Copyright (c) 2023 uku
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * Modifications (Phaze):
 *   - Rewired the configuration backing from HitRangeConfig + ukulib
 *     screens to Phaze's Setting / Module system.
 *   - Inlined the in-range / random-color decision so callers don't
 *     have to repeat the HitRangeConfig.color / inRangeColor lookup
 *     for every entity.
 *   - The class no longer extends RenderPhase; the constructor that
 *     existed only to keep the compiler happy about the COLOR_PROGRAM
 *     constants is replaced by direct constant access on the RenderPhase
 *     superclass via fully-qualified static reference.
 */
package vorga.phazeclient.implement.hitrange;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;

/**
 * Draws the configurable hit-range circle around an entity, in three
 * geometric flavours (line / thick ring / filled disc).
 *
 * <p>Geometry is cached in primitive arrays; the cache is recomputed
 * whenever a geometry-impacting Phaze setting (radius / thickness /
 * segments / render mode) fires its {@code onChange} - see
 * {@link HitRange}'s constructor wiring. Reading from a pre-computed
 * arrays keep the per-frame cost to a tight {@code for} over the
 * segment count, with no per-vertex trig.
 */
public final class HitRangeCircleRenderer {
    private static final float FILLED_OUTLINE_Y_EPSILON = 0.0015F;

    private static int segments;
    private static float[] innerXZ, outerXZ;
    private static float[] transformedInner, transformedOuter;
    // Only used synchronously on the client/render thread. Never retained by BufferBuilder.
    private static final Vector3f TRANSFORM_POINT = new Vector3f();

    static {
        computeAngles();
    }

    private HitRangeCircleRenderer() {
    }

    /**
     * Emits the configured circle around the given player state.
     * Caller must have transformed {@code matrices} to the player's
     * camera-relative render position; the late pass supplies the view.
     * The renderer only walks the cached angle list
     * and lays down vertices at {@code (dx, dy, dz)}.
     */
    public static void drawCircle(MatrixStack matrices, PlayerEntityRenderState state) {
        drawCircle(matrices, state.x, state.y, state.z, state.id, state.name, state.sneaking);
    }

    /** Self rendering doesn't need to rebuild vanilla's full player/item/armor render state. */
    public static void drawCircle(MatrixStack matrices, double x, double y, double z,
                                  int id, String name, boolean sneaking) {
        if (!WorldOverlayRenderer.isRecording()) return;
        HitRange config = HitRange.getInstance();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        int color = config.color.getColor();
        if (config.randomColors.isValue()) {
            // Hash the display name into an opaque-ARGB color. Same name
            // -> same color across frames -> visually stable per player.
            color = name.hashCode() | 0xFF000000;
        } else if (
                config.colorWhenInRange.isValue()
                        && id != player.getId()
                        && isInRange(x, y, z, player.getX(), player.getY(), player.getZ(), config.radius.getValue())
        ) {
            color = config.inRangeColor.getColor();
        }

        // Sneaking lifts the visual model by 1/8 block; mirror that here
        // so the ring stays anchored to the visible feet, not the
        // hitbox feet, when the target is sneaking.
        float dy = (sneaking ? 0.125f : 0.0f) + config.height.getValue();

        HitRange.Mode mode = config.mode();
        switch (mode) {
            case LINE -> {
                if ((color >>> 24) != 0) drawCircleLines(matrices, buffer(VertexFormat.DrawMode.DEBUG_LINES), dy, color);
            }
            case THICK -> {
                if ((color >>> 24) != 0) drawCircleQuad(matrices, buffer(VertexFormat.DrawMode.QUADS), dy, color);
            }
            case FILLED -> {
                ResolvedCircleColors resolvedColors = resolveColors(config, mode, color);
                if (((resolvedColors.fillArgb() >>> 24) & 0xFF) > 0) {
                    drawCircleTriangles(matrices, buffer(VertexFormat.DrawMode.TRIANGLES), dy, resolvedColors.fillArgb());
                }
                if (((resolvedColors.outlineArgb() >>> 24) & 0xFF) > 0) {
                    drawCircleLines(matrices, buffer(VertexFormat.DrawMode.DEBUG_LINES), dy + FILLED_OUTLINE_Y_EPSILON, resolvedColors.outlineArgb());
                }
            }
        }
    }

    /** Same strict squared-distance comparison as Vec3d.isInRange, without temporary Vec3ds. */
    public static boolean isInRange(double x, double y, double z, double px, double py, double pz, double radius) {
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz < radius * radius;
    }

    private static ResolvedCircleColors resolveColors(HitRange config, HitRange.Mode mode, int color) {
        if (mode != HitRange.Mode.FILLED) {
            return new ResolvedCircleColors(color, color);
        }

        int rgb = color & 0x00FFFFFF;
        int fillAlpha = (color >>> 24) & 0xFF;
        int outlineAlpha = MathHelper.clamp(Math.round(config.outlineOpacity.getValue() * 255.0F), 0, 255);
        return new ResolvedCircleColors((fillAlpha << 24) | rgb, (outlineAlpha << 24) | rgb);
    }

    private static BufferBuilder buffer(VertexFormat.DrawMode mode) {
        return WorldOverlayRenderer.buffer(mode, VertexFormats.POSITION_COLOR, 1, true);
    }

    private static void drawCircleLines(MatrixStack matrices, BufferBuilder vertices, float dy, int argb) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        if ((positionMatrix.properties() & Matrix4f.PROPERTY_TRANSLATION) != 0) {
            float x = positionMatrix.m30(), y = dy + positionMatrix.m31(), z = positionMatrix.m32();
            for (int i = 0; i < segments; i++) {
                translatedVertex(vertices, innerXZ, i * 2, x, y, z, argb);
                translatedVertex(vertices, innerXZ, (i + 1) * 2, x, y, z, argb);
            }
            return;
        }

        // DEBUG_LINES (not DEBUG_LINE_STRIP) so the vertex stream is
        // pairs of independent line segments. Strip mode connects the
        // last vertex of one player's ring to the first vertex of the
        // next player's ring when both render in the same batch,
        // producing the spurious "cones" that fan out from each player
        // toward the local player.
        transformPoints(positionMatrix, innerXZ, transformedInner, dy);
        for (int i = 0; i < segments; i++) {
            vertex(vertices, transformedInner, i * 3, argb);
            vertex(vertices, transformedInner, (i + 1) * 3, argb);
        }
    }

    private static void drawCircleQuad(MatrixStack matrices, BufferBuilder vertices, float dy, int argb) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        if ((positionMatrix.properties() & Matrix4f.PROPERTY_TRANSLATION) != 0) {
            float x = positionMatrix.m30(), y = dy + positionMatrix.m31(), z = positionMatrix.m32();
            for (int i = 0; i < segments; i++) {
                int a = i * 2, b = a + 2;
                translatedVertex(vertices, innerXZ, a, x, y, z, argb);
                translatedVertex(vertices, outerXZ, a, x, y, z, argb);
                translatedVertex(vertices, outerXZ, b, x, y, z, argb);
                translatedVertex(vertices, innerXZ, b, x, y, z, argb);
            }
            return;
        }

        transformPoints(positionMatrix, innerXZ, transformedInner, dy);
        transformPoints(positionMatrix, outerXZ, transformedOuter, dy);
        for (int i = 0; i < segments; i++) {
            int a = i * 3, b = a + 3;
            vertex(vertices, transformedInner, a, argb);
            vertex(vertices, transformedOuter, a, argb);
            vertex(vertices, transformedOuter, b, argb);
            vertex(vertices, transformedInner, b, argb);
        }
    }

    private static void drawCircleTriangles(MatrixStack matrices, BufferBuilder vertices, float dy, int argb) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        if ((positionMatrix.properties() & Matrix4f.PROPERTY_TRANSLATION) != 0) {
            float x = positionMatrix.m30(), y = dy + positionMatrix.m31(), z = positionMatrix.m32();
            for (int i = 0; i < segments; i++) {
                vertices.vertex(x, y, z).color(argb);
                translatedVertex(vertices, innerXZ, i * 2, x, y, z, argb);
                translatedVertex(vertices, innerXZ, (i + 1) * 2, x, y, z, argb);
            }
            return;
        }

        // TRIANGLES (not TRIANGLE_FAN) so each disc is independent.
        // Fan mode shares the first emitted vertex as the pivot for
        // every subsequent triangle in the entire batch, which means
        // the second player's first vertex becomes part of triangles
        // anchored on the FIRST player's centre - the source of the
        // "cones" the user sees on a server with many ranged rings.
        // Emitting (centre, A, B) for each segment as an explicit
        // triangle list makes every disc self-contained and cone-free.
        transformPoints(positionMatrix, innerXZ, transformedInner, dy);
        positionMatrix.transformPosition(0, dy, 0, TRANSFORM_POINT);
        float cx = TRANSFORM_POINT.x, cy = TRANSFORM_POINT.y, cz = TRANSFORM_POINT.z;
        for (int i = 0; i < segments; i++) {
            vertices.vertex(cx, cy, cz).color(argb);
            vertex(vertices, transformedInner, i * 3, argb);
            vertex(vertices, transformedInner, (i + 1) * 3, argb);
        }
    }

    private static void vertex(BufferBuilder vertices, float[] points, int offset, int argb) {
        vertices.vertex(points[offset], points[offset + 1], points[offset + 2]).color(argb);
    }

    private static void translatedVertex(BufferBuilder vertices, float[] xz, int offset, float x, float y, float z, int argb) {
        vertices.vertex(xz[offset] + x, y, xz[offset + 1] + z).color(argb);
    }

    private static void transformPoints(Matrix4f matrix, float[] xz, float[] xyz, float dy) {
        for (int i = 0; i < segments; i++) {
            matrix.transformPosition(xz[i * 2], dy, xz[i * 2 + 1], TRANSFORM_POINT);
            int offset = i * 3;
            xyz[offset] = TRANSFORM_POINT.x;
            xyz[offset + 1] = TRANSFORM_POINT.y;
            xyz[offset + 2] = TRANSFORM_POINT.z;
        }
        // Copy the exact first point; no modulo, extra transform or seam rounding difference.
        System.arraycopy(xyz, 0, xyz, segments * 3, 3);
    }

    /**
     * Rebuilds the cached angle table. Invoked at class load and from
     * every geometry-impacting setting's {@code onChange} on
     * {@link HitRange}.
     */
    public static void computeAngles() {
        HitRange config = HitRange.getInstance();

        segments = Math.max(3, config.circleSegments.getInt());
        innerXZ = new float[(segments + 1) * 2];
        outerXZ = new float[(segments + 1) * 2];
        transformedInner = new float[(segments + 1) * 3];
        transformedOuter = new float[(segments + 1) * 3];
        float radius = config.radius.getValue();
        HitRange.Mode mode = config.mode();

        if (mode == HitRange.Mode.THICK) {
            float thickness = Math.max(0.001f, config.thickness.getValue());
            for (int i = 0; i < segments; i++) {
                float angle = 2.0f * MathHelper.PI * ((float) i / segments);
                float inner = radius - (thickness / 2.0f);
                float outer = inner + thickness;

                float dx = inner * MathHelper.sin(angle);
                float dz = inner * MathHelper.cos(angle);

                float farDx = outer * MathHelper.sin(angle);
                float farDz = outer * MathHelper.cos(angle);

                innerXZ[i * 2] = dx;
                innerXZ[i * 2 + 1] = dz;
                outerXZ[i * 2] = farDx;
                outerXZ[i * 2 + 1] = farDz;
            }
        } else {
            for (int i = 0; i < segments; i++) {
                float angle = 2.0f * MathHelper.PI * ((float) i / segments);
                float dx = radius * MathHelper.sin(angle);
                float dz = radius * MathHelper.cos(angle);

                innerXZ[i * 2] = dx;
                innerXZ[i * 2 + 1] = dz;
            }
        }
        System.arraycopy(innerXZ, 0, innerXZ, segments * 2, 2);
        System.arraycopy(outerXZ, 0, outerXZ, segments * 2, 2);
    }

    private record ResolvedCircleColors(int fillArgb, int outlineArgb) {
    }
}
