package vorga.phazeclient.api.system.draw;

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

/**
 * Shared draw layers replacing the 1.21.4
 * {@code RenderSystem.setShader(ShaderProgramKeys.X)} +
 * {@code BufferRenderer.drawWithGlobalProgram(buffer.end())} pair.
 *
 * <p>Both of those are gone in 1.21.11. A draw now needs a
 * {@link RenderPipeline} wrapped in a {@link RenderLayer}, whose
 * {@link RenderLayer#draw(BuiltBuffer)} runs the whole GpuDevice /
 * CommandEncoder / RenderPass submission internally.
 *
 * <p>The pipelines here are built against vanilla's own core shaders
 * rather than reusing entries from {@code RenderPipelines}. Those entries
 * carry vanilla's blend / depth / cull choices for the specific place
 * they are used (GUI, sky, particles...), which do not all match what
 * Phaze wants. Pointing at the same shader assets with our own state
 * keeps the ported call sites behaving like the 1.21.4 ones did.
 */
public final class PhazeDrawLayers {

    private static final RenderPipeline POSITION_COLOR_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/position_color"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** POSITION_COLOR quads, alpha blended, no depth write. */
    public static final RenderLayer POSITION_COLOR = RenderLayer.of(
            "phaze_position_color",
            RenderSetup.builder(POSITION_COLOR_PIPELINE).translucent().build());

    private static final RenderPipeline POSITION_COLOR_TRIS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/position_color_tris"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** Same as {@link #POSITION_COLOR} but for TRIANGLE_STRIP geometry. */
    public static final RenderLayer POSITION_COLOR_TRIANGLE_STRIP = RenderLayer.of(
            "phaze_position_color_tri_strip",
            RenderSetup.builder(POSITION_COLOR_TRIS_PIPELINE).translucent().build());

    private static final RenderPipeline POSITION_TEX_COLOR_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/position_tex_color"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /**
     * One layer per texture.
     *
     * <p>Textures are no longer bound imperatively - the sampler is part
     * of the {@link RenderSetup} - so a layer is specific to the texture
     * it samples. The set of textures Phaze draws through here is small
     * and stable, so they are cached for the process lifetime.
     */
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
