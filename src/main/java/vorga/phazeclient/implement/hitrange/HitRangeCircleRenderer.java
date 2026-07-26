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
import net.minecraft.client.render.RenderLayer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vorga.phazeclient.implement.features.modules.other.HitRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the configurable hit-range circle around an entity, in three
 * geometric flavours (line / thick ring / filled disc).
 *
 * <p>Geometry is cached in {@link #angles}; the cache is recomputed
 * whenever a geometry-impacting Phaze setting (radius / thickness /
 * segments / render mode) fires its {@code onChange} - see
 * {@link HitRange}'s constructor wiring. Reading from a pre-computed
 * list keeps the per-frame cost to a tight {@code for} over the
 * segment count, with no per-vertex trig.
 */
public final class HitRangeCircleRenderer {
    private static final float FILLED_OUTLINE_Y_EPSILON = 0.0015F;
    private static final RenderLayer DEBUG_LINES = makeLayer(VertexFormat.DrawMode.DEBUG_LINES);
    private static final RenderLayer DEBUG_QUADS = makeLayer(VertexFormat.DrawMode.QUADS);
    private static final RenderLayer TRIANGLES = makeLayer(VertexFormat.DrawMode.TRIANGLES);

    private static final List<Angle> angles = new ArrayList<>();

    static {
        computeAngles();
    }

    private HitRangeCircleRenderer() {
    }

    /**
     * Emits the configured circle around the given player state.
     * Caller must have transformed {@code matrices} to the player's
     * render position; the renderer only walks the cached angle list
     * and lays down vertices at {@code (dx, dy, dz)}.
     */
    public static void drawCircle(MatrixStack matrices, VertexConsumerProvider vertexConsumers, PlayerEntityRenderState state) {
        HitRange config = HitRange.getInstance();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        Vec3d entityPos = new Vec3d(state.x, state.y, state.z);

        int color = config.color.getColor();
        if (config.randomColors.isValue()) {
            // Hash the display name into an opaque-ARGB color. Same name
            // -> same color across frames -> visually stable per player.
            // 1.21.11: PlayerEntityRenderState.name (String) was replaced by
            // playerName (Text); hash its plain-text form so the derived
            // color stays identical to the pre-port one for a given name.
            color = playerNameHash(state) | 0xFF000000;
        } else if (
                config.colorWhenInRange.isValue()
                        && state.id != player.getId()
                        // 1.21.11: Entity.getPos() -> getEntityPos().
                        && entityPos.isInRange(player.getEntityPos(), config.radius.getValue())
        ) {
            color = config.inRangeColor.getColor();
        }

        // Sneaking lifts the visual model by 1/8 block; mirror that here
        // so the ring stays anchored to the visible feet, not the
        // hitbox feet, when the target is sneaking.
        float dy = (state.sneaking ? 0.125f : 0.0f) + config.height.getValue();

        HitRange.Mode mode = config.mode();
        ResolvedCircleColors resolvedColors = resolveColors(config, mode, color);

        matrices.push();
        switch (mode) {
            case LINE -> drawCircleLines(matrices, vertexConsumers.getBuffer(DEBUG_LINES), dy, resolvedColors.outlineArgb());
            case THICK -> drawCircleQuad(matrices, vertexConsumers.getBuffer(DEBUG_QUADS), dy, resolvedColors.outlineArgb());
            case FILLED -> {
                if (((resolvedColors.fillArgb() >>> 24) & 0xFF) > 0) {
                    drawCircleTriangles(matrices, vertexConsumers.getBuffer(TRIANGLES), dy, resolvedColors.fillArgb());
                }
                if (((resolvedColors.outlineArgb() >>> 24) & 0xFF) > 0) {
                    drawCircleLines(matrices, vertexConsumers.getBuffer(DEBUG_LINES), dy + FILLED_OUTLINE_Y_EPSILON, resolvedColors.outlineArgb());
                }
            }
        }
        matrices.pop();
    }

    /**
     * 1.21.11 replacement for the removed {@code PlayerEntityRenderState.name}
     * String field. Falls back to the generic display name and finally to the
     * entity id so a missing name still yields a stable, per-player value
     * instead of an NPE.
     */
    private static int playerNameHash(PlayerEntityRenderState state) {
        Text name = state.playerName != null ? state.playerName : state.displayName;
        return name != null ? name.getString().hashCode() : state.id;
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

    private static void drawCircleLines(MatrixStack matrices, VertexConsumer vertices, float dy, int argb) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        // DEBUG_LINES (not DEBUG_LINE_STRIP) so the vertex stream is
        // pairs of independent line segments. Strip mode connects the
        // last vertex of one player's ring to the first vertex of the
        // next player's ring when both render in the same batch,
        // producing the spurious "cones" that fan out from each player
        // toward the local player.
        int n = angles.size();
        for (int i = 0; i < n; i++) {
            Angle a = angles.get(i);
            Angle b = angles.get((i + 1) % n);
            vertices.vertex(positionMatrix, a.dx, dy, a.dz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
            vertices.vertex(positionMatrix, b.dx, dy, b.dz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
        }
    }

    private static void drawCircleQuad(MatrixStack matrices, VertexConsumer vertices, float dy, int argb) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        for (int i = 1; i < angles.size() + 1; i++) {
            Angle angle = angles.get(i % angles.size());
            Angle prevAngle = angles.get(i - 1);

            vertices.vertex(positionMatrix, prevAngle.dx, dy, prevAngle.dz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
            vertices.vertex(positionMatrix, prevAngle.farDx, dy, prevAngle.farDz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
            vertices.vertex(positionMatrix, angle.farDx, dy, angle.farDz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
            vertices.vertex(positionMatrix, angle.dx, dy, angle.dz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
        }
    }

    private static void drawCircleTriangles(MatrixStack matrices, VertexConsumer vertices, float dy, int argb) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        // TRIANGLES (not TRIANGLE_FAN) so each disc is independent.
        // Fan mode shares the first emitted vertex as the pivot for
        // every subsequent triangle in the entire batch, which means
        // the second player's first vertex becomes part of triangles
        // anchored on the FIRST player's centre - the source of the
        // "cones" the user sees on a server with many ranged rings.
        // Emitting (centre, A, B) for each segment as an explicit
        // triangle list makes every disc self-contained and cone-free.
        int n = angles.size();
        for (int i = 0; i < n; i++) {
            Angle a = angles.get(i);
            Angle b = angles.get((i + 1) % n);
            vertices.vertex(positionMatrix, 0.0f, dy, 0.0f).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
            vertices.vertex(positionMatrix, a.dx, dy, a.dz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
            vertices.vertex(positionMatrix, b.dx, dy, b.dz).color(argb).normal(matrices.peek(), 0.0f, 0.0f, 0.0f);
        }
    }

    /**
     * Rebuilds the cached angle table. Invoked at class load and from
     * every geometry-impacting setting's {@code onChange} on
     * {@link HitRange}.
     */
    public static void computeAngles() {
        angles.clear();
        HitRange config = HitRange.getInstance();

        int segments = Math.max(3, config.circleSegments.getInt());
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

                angles.add(new Angle(dx, dz, farDx, farDz));
            }
        } else {
            for (int i = 0; i < segments; i++) {
                float angle = 2.0f * MathHelper.PI * ((float) i / segments);
                float dx = radius * MathHelper.sin(angle);
                float dz = radius * MathHelper.cos(angle);

                angles.add(new Angle(dx, dz));
            }
        }
    }

    /**
     * 1.21.11: {@code RenderPhase} and {@code MultiPhaseParameters} were
     * removed. The GPU state that used to be assembled from phase objects
     * (program, transparency, cull, write mask, depth test) now lives on a
     * {@link RenderPipeline}; lightmap / overlay / layering stayed on the
     * render-graph side and move to {@link RenderSetup}.
     *
     * <p>Same intent as the upstream mod: POSITION_COLOR verts, translucent
     * blending, depth-tested LEQUAL so the ring is clipped by world
     * geometry, and the view-offset layering that nudges it forward
     * fractionally to avoid z-fighting against the ground plane.
     */
    private static RenderLayer makeLayer(VertexFormat.DrawMode mode) {
        String name = "phaze_hitrange_" + mode.name().toLowerCase(Locale.ROOT);

        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/hitrange_" + mode.name().toLowerCase(Locale.ROOT)))
                .withVertexShader(Identifier.of("minecraft", "core/position_color"))
                .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
                // core/position_color reads both std140 blocks. Vanilla supplies them
                // through the private RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET,
                // which is not reachable from a mod, so declare them by hand - the
                // names must match exactly or the draw renders nothing.
                // RenderLayer.draw() fills DynamicTransforms via RenderSystem's
                // DynamicUniforms; Projection is bound globally for the frame.
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(VertexFormats.POSITION_COLOR, mode)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();

        return RenderLayer.of(
                name,
                RenderSetup.builder(pipeline)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .useLightmap()
                        .useOverlay()
                        .translucent()
                        .expectedBufferSize(1536)
                        .build());
    }

    /**
     * Per-segment cached angle. {@code (dx, dz)} is the on-radius point;
     * {@code (farDx, farDz)} is the outer-radius point used only by the
     * THICK render mode (= 0 for LINE / FILLED).
     */
    private record Angle(float dx, float dz, float farDx, float farDz) {
        Angle(float dx, float dz) {
            this(dx, dz, 0.0f, 0.0f);
        }
    }

    private record ResolvedCircleColors(int fillArgb, int outlineArgb) {
    }
}
