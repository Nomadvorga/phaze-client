package vorga.phazeclient.api.system.draw;

import net.minecraft.client.gl.RenderPipelines;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gl.UniformType;

public final class PhazeDrawLayers {

    private static final RenderPipeline POSITION_COLOR_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/position_color"))
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

    public static final RenderLayer POSITION_COLOR = RenderLayer.of(
            "phaze_position_color",
            RenderSetup.builder(POSITION_COLOR_PIPELINE).translucent().build());

    private static final RenderPipeline POSITION_COLOR_TRIS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/position_color_tris"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    public static final RenderLayer POSITION_COLOR_TRIANGLE_STRIP = RenderLayer.of(
            "phaze_position_color_tri_strip",
            RenderSetup.builder(POSITION_COLOR_TRIS_PIPELINE).translucent().build());

    private static final RenderPipeline POSITION_TEX_COLOR_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/position_tex_color"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    public static final RenderLayer LINES = RenderLayer.of(
            "phaze_lines",
            RenderSetup.builder(net.minecraft.client.gl.RenderPipelines.LINES).translucent().build());

    private static final Map<Identifier, RenderLayer> TEXTURED = new HashMap<>();

    public static RenderLayer positionTexColor(Identifier texture) {
        return TEXTURED.computeIfAbsent(texture, id -> RenderLayer.of(
                "phaze_position_tex_color_" + id.getNamespace() + "_" + id.getPath().replace('/', '_'),
                RenderSetup.builder(POSITION_TEX_COLOR_PIPELINE)
                        .texture("Sampler0", id)
                        .translucent()
                        .build()));
    }

    private PhazeDrawLayers() {
    }
}
