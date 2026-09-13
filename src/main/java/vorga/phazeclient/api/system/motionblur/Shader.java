package vorga.phazeclient.api.system.motionblur;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;

public class Shader {
    private final MotionBlur config;
    private final PostEffectShader motionBlurShader;

    private static final float FPS_SMOOTHING = 0.1f;

    private static final float TIER_GUARD = 0.06f;

    private long lastNano = System.nanoTime();
    private float currentBlur = 0.0f;
    private float currentFPS = 0.0f;
    private int sampleTier = -1;

    private int staticFrames = 0;
    private static final int STATIC_FRAMES_TO_SKIP = 8;

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

        motionBlurShader.declareUniformBlock("MotionBlurConfig",
                "mvInverse", "projInverse", "prevModelView", "prevProjection",
                "cameraPos", "prevCameraPos", "view_res",
                "BlendFactor", "inverseSamples", "handDepthThreshold",
                "motionBlurSamples", "halfSamples", "blurAlgorithm");
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

            return;
        }

        applyMotionBlur();
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

        float viewWidth = client.getFramebuffer().textureWidth;
        float viewHeight = client.getFramebuffer().textureHeight;
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

        lastSampleAmount = -1;
        lastViewWidth = -1.0f;
        lastViewHeight = -1.0f;
        lastHandDepthThreshold = Float.NaN;
        blurAlgorithmSent = false;
        currentBlur = Float.NaN;
    }
}
