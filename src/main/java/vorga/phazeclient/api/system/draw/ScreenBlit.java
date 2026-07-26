package vorga.phazeclient.api.system.draw;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * Full-screen blit of one framebuffer's colour attachment over the main one.
 *
 * <h3>Why not {@code Framebuffer.drawBlit}</h3>
 *
 * 1.21.11 does ship {@code Framebuffer.drawBlit(GpuTextureView)} - note the
 * receiver is the SOURCE and the argument the DESTINATION - but it runs
 * {@code RenderPipelines.ENTITY_OUTLINE_BLIT}, whose blend is
 * {@code (SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ZERO, ONE)} and which is built
 * with {@code withColorWrite(true, false)}.
 *
 * <p>Phaze's HUD framebuffers hold PREMULTIPLIED alpha - the 1.21.4 code
 * blitted them with {@code blendFunc(ONE, ONE_MINUS_SRC_ALPHA)}. Going
 * through {@code drawBlit} would multiply colour by alpha a second time,
 * visibly darkening semi-transparent HUD pixels, and would never write
 * destination alpha. So this uses the same vanilla screen-quad shaders with
 * the correct premultiplied blend instead.
 *
 * <p>The quad is synthesised from {@code gl_VertexID} inside
 * {@code core/screenquad}, so there is no vertex buffer and no matrix - the
 * pass covers the whole target by construction and sets its own viewport.
 */
public final class ScreenBlit {

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/screen_blit"))
            .withVertexShader(Identifier.of("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.of("minecraft", "core/blit_screen"))
            .withSampler("InSampler")
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, true)
            // Builder defaults cull to true; a screen quad renders nothing
            // with culling on.
            .withCull(false)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private ScreenBlit() {
    }

    /** Draws {@code source}'s colour attachment over the main framebuffer. */
    public static void blitOverMain(Framebuffer source) {
        if (source == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        if (target == null) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "phaze/screen blit",
                target.getColorAttachmentView(),
                OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture(
                    "InSampler",
                    source.getColorAttachmentView(),
                    RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }
}
