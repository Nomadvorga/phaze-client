package vorga.phazeclient.implement.menu;

import net.minecraft.client.gl.RenderPipelines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.CubemapTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

public final class MenuPanoramaRenderer {
    private static final Identifier OVERLAY_TEXTURE = Identifier.ofVanilla("textures/gui/title/background/panorama_overlay.png");
    private static volatile int resourceGeneration = 0;

    private final CubeMapRenderer cubeMap;
    private final Identifier cubeMapBase;
    private long lastFrameTimeNs = -1L;
    private int preparedResourceGeneration = Integer.MIN_VALUE;
    private boolean cubeMapReady;
    private float pitch;

    public MenuPanoramaRenderer(Identifier cubeMapBase) {
        this.cubeMap = new CubeMapRenderer(cubeMapBase);
        this.cubeMapBase = cubeMapBase;
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

        if (this.cubeMapReady) {

            this.cubeMap.draw(client, 10.0F, -this.pitch);
        }
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
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
        prepareTextures(client, true);
    }

    public void prepareTextures(MinecraftClient client, boolean prepareCubeMap) {
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
        if (!prepareCubeMap) {
            return;
        }

        boolean ready = false;
        if (MenuPanoramaRegistry.isDynamicCubeMap(this.cubeMapBase)) {

            try {
                NativeImage[] faces = MenuPanoramaRegistry.dynamicFacesFor(this.cubeMapBase);
                if (faces != null) {
                    textureManager.registerTexture(this.cubeMapBase, new PhazeCubeMapTexture(this.cubeMapBase, faces));
                    ready = true;
                }
            } catch (Throwable ignored) {

            }
        } else {

            try {
                textureManager.registerTexture(this.cubeMapBase, new CubemapTexture(this.cubeMapBase));
                ready = textureManager.getTexture(this.cubeMapBase) != null;
            } catch (Throwable ignored) {

            }
        }

        this.cubeMapReady = ready;
        this.preparedResourceGeneration = resourceGeneration;
        this.lastFrameTimeNs = -1L;
    }
}
