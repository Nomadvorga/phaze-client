package vorga.phazeclient.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

public final class PhazeRenderLayers {

    private static final RenderPipeline HITBOX_FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/hitbox_fill"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer HITBOX_FILL = RenderLayer.of(
            "phaze_hitbox_fill",
            RenderSetup.builder(HITBOX_FILL_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline THICK_LINES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/thick_lines"))
            .withVertexShader(Identifier.of("minecraft", "core/rendertype_lines"))
            .withFragmentShader(Identifier.of("minecraft", "core/rendertype_lines"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build();

    private static final Function<Float, RenderLayer> THICK_LINES = Util.memoize(
            (Function<Float, RenderLayer>) (width -> RenderLayer.of(
                    "phaze_thick_lines_" + width,
                    RenderSetup.builder(THICK_LINES_PIPELINE)
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()))
    );

    private static final RenderPipeline BLOCK_FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/block_fill"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthBias(-1.0F, -1.0F)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer BLOCK_FILL = RenderLayer.of(
            "phaze_block_fill",
            RenderSetup.builder(BLOCK_FILL_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline TRIANGLES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/world_triangles"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer TRIANGLES = RenderLayer.of(
            "phaze_world_triangles",
            RenderSetup.builder(TRIANGLES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline TEXTURED_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/world_textured"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderLayer> TEXTURED = Util.memoize(
            (Function<Identifier, RenderLayer>) (texture -> RenderLayer.of(
                    "phaze_world_textured_" + texture,
                    RenderSetup.builder(TEXTURED_PIPELINE)
                            .texture("Sampler0", texture)
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()))
    );

    private PhazeRenderLayers() {}

    public static RenderLayer getHitboxFill() {
        return HITBOX_FILL;
    }

    public static RenderLayer getTriangles() {
        return TRIANGLES;
    }

    public static RenderLayer getTextured(Identifier texture) {
        return TEXTURED.apply(texture);
    }

    public static RenderLayer getBlockFill() {
        return BLOCK_FILL;
    }

    public static RenderLayer getThickLines(float width) {
        return THICK_LINES.apply(width);
    }
}
