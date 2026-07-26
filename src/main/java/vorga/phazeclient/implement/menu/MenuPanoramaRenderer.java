package vorga.phazeclient.implement.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.stream.IntStream;

public final class MenuPanoramaRenderer {
    private static final Identifier OVERLAY_TEXTURE = Identifier.ofVanilla("textures/gui/title/background/panorama_overlay.png");
    private static volatile int resourceGeneration = 0;

    private final CubeMapRenderer cubeMap;
    private final Identifier[] faceTextures;
    private long lastFrameTimeNs = -1L;
    private int preparedResourceGeneration = Integer.MIN_VALUE;
    private float pitch;

    public MenuPanoramaRenderer(Identifier cubeMapBase) {
        this.cubeMap = new CubeMapRenderer(cubeMapBase);
        this.faceTextures = IntStream.range(0, 6)
                .mapToObj(face -> cubeMapBase.withPath(cubeMapBase.getPath() + "_" + face + ".png"))
                .toArray(Identifier[]::new);
    }

    public void render(DrawContext context, int width, int height, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        prepareTextures(client);

        long nowNs = System.nanoTime();
        float dtSeconds = lastFrameTimeNs > 0L
                ? Math.min(0.05F, (nowNs - lastFrameTimeNs) / 1_000_000_000.0F)
                : (1.0F / 60.0F);
        lastFrameTimeNs = nowNs;

        float speedMultiplier = (float) (MenuUiSettings.getInstance().getPanoramaSpeed() / 1000.0D);
        this.pitch = wrapOnce(this.pitch + dtSeconds * 72.0F * speedMultiplier, 360.0F);

        context.draw();
        this.cubeMap.draw(client, 10.0F, -this.pitch, alpha);
        context.draw();
        context.drawTexture(
                RenderLayer::getGuiTextured,
                OVERLAY_TEXTURE,
                0,
                0,
                0.0F,
                0.0F,
                width,
                height,
                16,
                128,
                16,
                128,
                ColorHelper.getWhite(alpha)
        );
    }

    private static float wrapOnce(float value, float max) {
        return value > max ? value - max : value;
    }

    public static void onResourcesReloaded() {
        resourceGeneration++;
    }

    public void prepareTextures(MinecraftClient client) {
        if (client == null) {
            return;
        }
        TextureManager textureManager = client.getTextureManager();
        if (textureManager == null) {
            return;
        }
        if (this.preparedResourceGeneration == resourceGeneration) {
            return;
        }
        textureManager.getTexture(OVERLAY_TEXTURE);
        for (Identifier faceTexture : this.faceTextures) {
            textureManager.getTexture(faceTexture);
        }
        this.preparedResourceGeneration = resourceGeneration;
        this.lastFrameTimeNs = -1L;
    }
}
