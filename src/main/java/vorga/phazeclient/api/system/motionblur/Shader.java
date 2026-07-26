package vorga.phazeclient.api.system.motionblur;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;

public class Shader {
    private final MotionBlur config;
    private final PostEffectShader motionBlurShader;

    /**
     * Smoothing factor for the frame-rate estimate.
     *
     * <p>{@code currentFPS} used to be the reciprocal of a single frame's
     * delta, which is extremely noisy - one hitched frame was enough to move
     * it across a sample-count threshold. Since the sample count drives the
     * shader's loop length, that showed up as the blur visibly changing
     * quality from frame to frame. An exponential moving average over
     * roughly ten frames removes the jitter without adding perceptible lag.
     */
    private static final float FPS_SMOOTHING = 0.1f;
    /** Fractional dead-band around each sample tier boundary. */
    private static final float TIER_GUARD = 0.06f;

    private long lastNano = System.nanoTime();
    private float currentBlur = 0.0f;
    private float currentFPS = 0.0f;
    private int sampleTier = -1;
    /**
     * Consecutive frames whose camera matrices matched the previous frame's.
     *
     * <p>A single-frame test is not usable here: at high frame rates the
     * render loop outruns the mouse polling rate, so while the player is
     * actively turning there are still plenty of individual frames with an
     * unchanged camera. Gating the pass on that made it run every other
     * frame, and since the pass rebinds the framebuffer and normalises depth
     * / blend state right before the hand is drawn, the hand visibly blinked.
     * The pass is only dropped after the camera has genuinely been at rest
     * for {@link #STATIC_FRAMES_TO_SKIP} frames, and resumes on the first
     * frame that moves.
     */
    private int staticFrames = 0;
    private static final int STATIC_FRAMES_TO_SKIP = 8;

    // Last values pushed to the shader. The uniform setters walk every pass
    // and look the uniform up by name, so re-sending a value that has not
    // changed is pure overhead once per frame per uniform.
    private int lastSampleAmount = -1;
    private float lastViewWidth = -1.0f;
    private float lastViewHeight = -1.0f;
    private float lastHandDepthThreshold = Float.NaN;
    private boolean blurAlgorithmSent = false;

    private final Matrix4f tempPrevModelView = new Matrix4f();
    private final Matrix4f tempPrevProjection = new Matrix4f();
    private final Matrix4f tempProjInverse = new Matrix4f();
    private final Matrix4f tempMvInverse = new Matrix4f();

    public Shader(MotionBlur config) {
        this.config = config;
        motionBlurShader = new PostEffectShader(
                Identifier.of("phazeclient", "motion_blur"),
                shader -> shader.setUniformValue("BlendFactor", config.getStrength())
        );
    }

    public void applyMotionBlurBeforeHands() {
        long now = System.nanoTime();
        float deltaTime = (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;

        if (deltaTime > 0 && deltaTime < 1.0f) {
            float instantFps = 1.0f / deltaTime;
            currentFPS = currentFPS <= 0.0f
                    ? instantFps
                    : currentFPS + (instantFps - currentFPS) * FPS_SMOOTHING;
        } else {
            currentFPS = 0.0f;
        }

        if (config.getStrength() == 0 || !config.isEnabled()) {
            return;
        }

        if (staticFrames >= STATIC_FRAMES_TO_SKIP) {
            // Camera at rest: the pass would write back exactly what it read,
            // so skip the work - but leave the render state exactly as the
            // pass would have, because renderHand runs straight after this
            // and depends on it. Without this the hand blinked on every
            // transition between the two paths.
            leaveRenderStateAsPassWould();
            return;
        }

        applyMotionBlur();
    }

    /**
     * Reproduces the framebuffer binding and depth / blend state that a
     * completed motion-blur pass leaves behind, for the path where the pass
     * itself is skipped.
     */
    private void leaveRenderStateAsPassWould() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) {
            return;
        }
        client.getFramebuffer().beginWrite(true);
        RenderSystem.depthFunc(515); // GL_LEQUAL
    }

    private void applyMotionBlur() {
        MinecraftClient client = MinecraftClient.getInstance();

        MonitorInfoProvider.updateDisplayInfo();
        int displayRefreshRate = MonitorInfoProvider.getRefreshRate();

        float baseStrength = config.getStrength();
        float scaledStrength = baseStrength;
        if (config.isUseRRC()) {
            float fpsOverRefresh = (displayRefreshRate > 0) ? currentFPS / displayRefreshRate : 1.0f;
            if (fpsOverRefresh < 1.0f) fpsOverRefresh = 1.0f;
            scaledStrength = baseStrength * fpsOverRefresh;
        }

        if (currentBlur != scaledStrength) {
            motionBlurShader.setUniformValue("BlendFactor", scaledStrength);
            currentBlur = scaledStrength;
        }

        int sampleAmount = getSampleAmountForFPS(currentFPS);
        if (sampleAmount != lastSampleAmount) {
            motionBlurShader.setUniformValue("motionBlurSamples", sampleAmount);
            motionBlurShader.setUniformValue("halfSamples", sampleAmount / 2);
            motionBlurShader.setUniformValue("inverseSamples", 1.0f / sampleAmount);
            lastSampleAmount = sampleAmount;
        }

        float viewWidth = client.getFramebuffer().viewportWidth;
        float viewHeight = client.getFramebuffer().viewportHeight;
        if (viewWidth != lastViewWidth || viewHeight != lastViewHeight) {
            motionBlurShader.setUniformValue("view_res", viewWidth, viewHeight);
            lastViewWidth = viewWidth;
            lastViewHeight = viewHeight;
        }

        if (!blurAlgorithmSent) {
            motionBlurShader.setUniformValue("blurAlgorithm", 1);
            blurAlgorithmSent = true;
        }

        float handDepthThreshold = config.getHandDepthThreshold();
        if (handDepthThreshold != lastHandDepthThreshold) {
            motionBlurShader.setUniformValue("handDepthThreshold", handDepthThreshold);
            lastHandDepthThreshold = handDepthThreshold;
        }

        motionBlurShader.render(0.0f);

        RenderSystem.depthFunc(515); // GL_LEQUAL
    }

    private int getSampleAmountForFPS(float fps) {
        int quality = config.getQuality();

        int baseSamples = switch (quality) {
            case 0 -> 8;
            case 1 -> 12;
            case 2 -> 16;
            case 3 -> 24;
            default -> 12;
        };

        sampleTier = resolveSampleTier(fps);

        return switch (sampleTier) {
            case 0 -> Math.max(6, baseSamples / 2);
            case 1 -> Math.max(8, (int) (baseSamples * 0.75f));
            case 3 -> (int) (baseSamples * 1.25f);
            default -> baseSamples;
        };
    }

    /**
     * Picks the quality tier for the current frame rate, with a dead-band
     * around each boundary.
     *
     * <p>The tier used to be recomputed from scratch with hard comparisons,
     * so a frame rate sitting on 60 or 144 flipped it constantly and the
     * blur's sample count - and therefore its look - oscillated. The tier now
     * only moves once the smoothed frame rate has cleared the boundary by
     * {@link #TIER_GUARD}, and only one step at a time, which the smoothing
     * makes sufficient.
     */
    private int resolveSampleTier(float fps) {
        if (sampleTier < 0) {
            return fps < 30.0f ? 0 : fps < 60.0f ? 1 : fps > 144.0f ? 3 : 2;
        }
        if (sampleTier > 0 && fps < tierLowerBound(sampleTier) * (1.0f - TIER_GUARD)) {
            return sampleTier - 1;
        }
        if (sampleTier < 3 && fps > tierUpperBound(sampleTier) * (1.0f + TIER_GUARD)) {
            return sampleTier + 1;
        }
        return sampleTier;
    }

    private static float tierLowerBound(int tier) {
        return switch (tier) {
            case 1 -> 30.0f;
            case 2 -> 60.0f;
            case 3 -> 144.0f;
            default -> 0.0f;
        };
    }

    private static float tierUpperBound(int tier) {
        return switch (tier) {
            case 0 -> 30.0f;
            case 1 -> 60.0f;
            case 2 -> 144.0f;
            default -> Float.MAX_VALUE;
        };
    }

    public void setFrameMotionBlur(Matrix4f modelView, Matrix4f prevModelView,
                                   Matrix4f projection, Matrix4f prevProjection,
                                   Vector3f cameraPos, Vector3f prevCameraPos) {
        // With both matrices and the camera position unchanged, reproject()
        // maps every pixel back onto itself, so the velocity is exactly zero
        // across the frame and the pass would write back what it read. The
        // shader has a per-pixel guard for this too, but detecting it here
        // skips the framebuffer bind, the blit and the fragment work
        // altogether.
        boolean unchanged = modelView.equals(prevModelView)
                && projection.equals(prevProjection)
                && cameraPos.equals(prevCameraPos);
        staticFrames = unchanged ? Math.min(staticFrames + 1, STATIC_FRAMES_TO_SKIP) : 0;

        motionBlurShader.setUniformValue("mvInverse", tempMvInverse.set(modelView).invert());
        motionBlurShader.setUniformValue("projInverse", tempProjInverse.set(projection).invert());
        motionBlurShader.setUniformValue("prevModelView", tempPrevModelView.set(prevModelView));
        motionBlurShader.setUniformValue("prevProjection", tempPrevProjection.set(prevProjection));
        motionBlurShader.setUniformValue("cameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
        motionBlurShader.setUniformValue("prevCameraPos", prevCameraPos.x, prevCameraPos.y, prevCameraPos.z);
    }

    public void updateBlurStrength(float strength) {
        motionBlurShader.setUniformValue("BlendFactor", strength);
        currentBlur = strength;
    }

    public void reload() {
        motionBlurShader.reload();
        // The processor is rebuilt from scratch, so every uniform is back at
        // its default. Drop the "already sent" state or the skip-if-unchanged
        // guards above would suppress the values the new program needs.
        lastSampleAmount = -1;
        lastViewWidth = -1.0f;
        lastViewHeight = -1.0f;
        lastHandDepthThreshold = Float.NaN;
        blurAlgorithmSent = false;
        currentBlur = Float.NaN;
    }
}
