package vorga.phazeclient.implement.menu;

import net.minecraft.client.gl.RenderPipelines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.CubemapTexture;
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

        // 1.21.11: CubeMapRenderer.draw lost its alpha parameter - it writes a
        // hardcoded white tint into the DynamicTransforms UBO, and vanilla's
        // own RotatingCubeMapRenderer no longer fades the cube map either.
        // TODO(1.21.11): no API for a tinted cube map; only the overlay below
        // still honours `alpha`, so the panorama itself no longer fades in.
        this.cubeMap.draw(client, 10.0F, -this.pitch);
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

        // 1.21.11: CubeMapRenderer no longer binds the six face textures
        // itself - it samples a single CubemapTexture registered under the
        // base id - so warming the faces up one by one does nothing now.
        // CubeMapRenderer.registerTextures() goes through the non-loading
        // registerTexture(Identifier, AbstractTexture) overload, which only
        // works for vanilla because it registers before the startup resource
        // reload uploads every registered ReloadableTexture. Phaze registers
        // from screen init, i.e. after that reload, so the loading
        // registerTexture(Identifier, ReloadableTexture) overload is used
        // here instead; it uploads the cube map immediately.
        try {
            textureManager.registerTexture(this.cubeMapBase, new CubemapTexture(this.cubeMapBase));
        } catch (Throwable ignored) {
            // TODO(1.21.11): custom (zip-loaded) panoramas publish six
            // dynamic NativeImageBackedTextures under <base>_<n>.png instead
            // of shipping resources, and CubemapTexture can only load from
            // the ResourceManager. Those need their own cube map upload path;
            // swallowed here so a broken/absent panorama cannot crash the
            // menu (registerTexture rethrows load failures as CrashException).
        }
        this.preparedResourceGeneration = resourceGeneration;
        this.lastFrameTimeNs = -1L;
    }
}
