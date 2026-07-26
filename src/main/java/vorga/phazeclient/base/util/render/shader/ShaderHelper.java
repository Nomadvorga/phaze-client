package vorga.phazeclient.base.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;

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
            copyFbo = new SimpleFramebuffer(width, height, true);
            fbo1 = new SimpleFramebuffer(width, height, true);
            fbo2 = new SimpleFramebuffer(width, height, true);
            effectFbo = new SimpleFramebuffer(width, height, true);
            solidFbo = new SimpleFramebuffer(width, height, true);
            shader1Fbo = new SimpleFramebuffer(width, height, true);
            shader2Fbo = new SimpleFramebuffer(width, height, true);
        }
    }

    public static void drawFullScreenQuad() {
        RenderSystem.assertOnRenderThread();
        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(
            VertexFormat.DrawMode.QUADS,
            VertexFormats.POSITION
        );
        bufferBuilder.vertex(-1.0f, -1.0f, 0.0f);
        bufferBuilder.vertex(1.0f, -1.0f, 0.0f);
        bufferBuilder.vertex(1.0f, 1.0f, 0.0f);
        bufferBuilder.vertex(-1.0f, 1.0f, 0.0f);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
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
