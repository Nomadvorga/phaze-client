package vorga.phazeclient.base.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;

public class ShaderHelper {
    private static Shader chromaShader;
    private static Shader solidShader;
    private static Shader balatroShader;
    private static Shader smokeShader;
    private static Shader stripesShader;
    private static Shader glowShader;
    private static Shader glassShader;
    private static Shader snowShader;
    private static Shader blendShader;

    private static SimpleFramebuffer copyFbo;
    private static SimpleFramebuffer fbo1;
    private static SimpleFramebuffer fbo2;
    private static SimpleFramebuffer effectFbo;
    private static SimpleFramebuffer solidFbo;
    private static SimpleFramebuffer shader1Fbo;
    private static SimpleFramebuffer shader2Fbo;

    private static boolean initialized = false;

    public static void initShadersIfNeeded() {
        if (initialized) return;
        try {
            chromaShader = new Shader("hand", "chroma");
            solidShader = new Shader("hand", "solid");
            balatroShader = new Shader("hand", "balatro");
            smokeShader = new Shader("hand", "smoke");
            stripesShader = new Shader("hand", "stripes");
            glowShader = new Shader("hand", "glow");
            glassShader = new Shader("hand", "glass");
            snowShader = new Shader("hand", "snow");
            blendShader = new Shader("hand", "blend");
            initialized = true;
        } catch (Exception e) {
            System.err.println("Failed to initialize hand shaders!");
            e.printStackTrace();
        }
    }

    public static void checkFramebuffers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (copyFbo == null || copyFbo.textureWidth != width || copyFbo.textureHeight != height) {
            if (copyFbo != null) {
                copyFbo.delete();
                fbo1.delete();
                fbo2.delete();
                effectFbo.delete();
                solidFbo.delete();
                if (shader1Fbo != null) shader1Fbo.delete();
                if (shader2Fbo != null) shader2Fbo.delete();
            }
            // 1.21.11: SimpleFramebuffer takes a debug name as its FIRST argument.
            copyFbo = new SimpleFramebuffer("phaze/hand/copy", width, height, true);
            fbo1 = new SimpleFramebuffer("phaze/hand/fbo1", width, height, true);
            fbo2 = new SimpleFramebuffer("phaze/hand/fbo2", width, height, true);
            effectFbo = new SimpleFramebuffer("phaze/hand/effect", width, height, true);
            solidFbo = new SimpleFramebuffer("phaze/hand/solid", width, height, true);
            shader1Fbo = new SimpleFramebuffer("phaze/hand/shader1", width, height, true);
            shader2Fbo = new SimpleFramebuffer("phaze/hand/shader2", width, height, true);
        }
    }

    /**
     * Draws a full-screen NDC quad through whatever shader program is currently bound.
     *
     * <p>TODO(1.21.11): stubbed - no equivalent exists.
     *
     * <p>On 1.21.4 this built a {@code POSITION} quad on the render-thread tessellator and
     * submitted it with {@code BufferRenderer.drawWithGlobalProgram}, which drew using the
     * program that {@link Shader#bind()} had just installed with {@code glUseProgram}. Both
     * halves of that contract are gone in 1.21.11:
     * <ul>
     *   <li>{@code BufferRenderer} was deleted outright, and with it the whole concept of a
     *       "global program" - every draw now names a {@code RenderPipeline}, which owns its
     *       own compiled program plus its blend/depth/cull state.</li>
     *   <li>{@code RenderSystem.renderThreadTesselator()} is gone as well
     *       ({@link net.minecraft.client.render.Tessellator#getInstance()} is the survivor).</li>
     * </ul>
     *
     * <p>Routing the quad through a stock {@code RenderLayer} would compile, but it would draw
     * with vanilla's position shader instead of the hand shader - a flat full-screen rectangle
     * painted over the frame, which is strictly worse than drawing nothing. And re-binding the
     * raw GL program by hand is explicitly off the table: {@code GlCommandEncoder} caches
     * {@code currentPipeline}/{@code currentProgram}, so a stray {@code glUseProgram} corrupts
     * every vanilla draw that follows.
     *
     * <p>Making this real means giving each {@code assets/Phaze/shaders/hand/*} program a
     * {@code RenderPipeline} whose loose uniforms have become a std140 UBO block (see J-8 in
     * PORTING-WAVE2.md), at which point this method disappears in favour of
     * {@code layer.draw(builtBuffer)} / {@code GpuDraw.draw(...)}.
     *
     * <p>Current blast radius: none. The only two callers are {@code Blur.runDualKawasePass}
     * and {@code Blur.runGaussianPass}, both of which are themselves unreachable while their
     * {@code ShaderProgram} lookup is stubbed to {@code null}.
     */
    public static void drawFullScreenQuad() {
        RenderSystem.assertOnRenderThread();
        // Intentionally empty - see javadoc. Do NOT begin a Tessellator buffer here without
        // ending it; an unfinished build leaks into vanilla's next begin() and throws.
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Shader getChromaShader() {
        return chromaShader;
    }

    public static Shader getSolidShader() {
        return solidShader;
    }

    public static Shader getBalatroShader() {
        return balatroShader;
    }

    public static Shader getSmokeShader() {
        return smokeShader;
    }

    public static SimpleFramebuffer getCopyFbo() {
        return copyFbo;
    }

    public static SimpleFramebuffer getFbo1() {
        return fbo1;
    }

    public static SimpleFramebuffer getFbo2() {
        return fbo2;
    }

    public static SimpleFramebuffer getEffectFbo() {
        return effectFbo;
    }

    public static SimpleFramebuffer getSolidFbo() {
        return solidFbo;
    }

    public static Shader getStripesShader() {
        return stripesShader;
    }

    public static Shader getGlowShader() {
        return glowShader;
    }

    public static Shader getGlassShader() {
        return glassShader;
    }

    public static Shader getSnowShader() {
        return snowShader;
    }

    public static Shader getBlendShader() {
        return blendShader;
    }

    public static SimpleFramebuffer getShader1Fbo() {
        return shader1Fbo;
    }

    public static SimpleFramebuffer getShader2Fbo() {
        return shader2Fbo;
    }
}
