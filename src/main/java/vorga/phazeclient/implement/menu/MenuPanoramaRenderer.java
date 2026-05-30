package vorga.phazeclient.implement.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

public final class MenuPanoramaRenderer {
    private static final Identifier OVERLAY_TEXTURE = Identifier.ofVanilla("textures/gui/title/background/panorama_overlay.png");

    private final CubeMapRenderer cubeMap;
    private long lastFrameTimeNs = -1L;
    private float pitch;
    private boolean texturesRegistered = false;

    public MenuPanoramaRenderer(CubeMapRenderer cubeMap) {
        this.cubeMap = cubeMap;
        registerTexturesIfNeeded(MinecraftClient.getInstance());
    }

    public void render(DrawContext context, int width, int height, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        registerTexturesIfNeeded(client);

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

    private void registerTexturesIfNeeded(MinecraftClient client) {
        if (texturesRegistered || client == null) {
            return;
        }
        TextureManager textureManager = client.getTextureManager();
        if (textureManager == null) {
            return;
        }
        this.cubeMap.registerTextures(textureManager);
        textureManager.registerTexture(OVERLAY_TEXTURE);
        this.texturesRegistered = true;
    }
}
