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

        // 1.21.11: CubeMapRenderer.draw lost its alpha parameter - it writes a
        // hardcoded white tint into the DynamicTransforms UBO, and vanilla's
        // own RotatingCubeMapRenderer no longer fades the cube map either.
        // TODO(1.21.11): no API for a tinted cube map; only the overlay below
        // still honours `alpha`, so the panorama itself no longer fades in.
        // Only draw once the cube map is known to be uploaded. CubeMapRenderer
        // opens its own render pass and binds the texture INSIDE it, so if that
        // bind is the first access the resulting lazy upload dies with
        // "Close the existing render pass before performing additional
        // commands" - a hard crash on the main menu. prepareTextures forces the
        // upload ahead of time and only sets this flag when it succeeded.
        if (this.cubeMapReady) {
            // Exactly vanilla's call. An earlier +180 here was a wrong guess:
            // with and without it the panorama looked equally wrong, and two
            // states 180 degrees apart cannot both be a yaw error - so the
            // offset is a vertical flip, which PhazeCubeMapTexture now applies
            // per face at upload.
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

    /**
     * @param prepareCubeMap {@code false} warms only the overlay. The theme grid
     *                       draws preset preview textures, never the cube map, and
     *                       building one costs 6 x size^2 x 4 bytes of VRAM per
     *                       listed panorama - so previews skip it. The generation
     *                       latch is deliberately left alone in that case, so the
     *                       real prepare still runs afterwards.
     */
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

        // 1.21.11: CubeMapRenderer no longer binds the six face textures
        // itself - it samples a single cube-map texture registered under the
        // base id - so warming the faces up one by one does nothing now.
        // Whatever ends up under cubeMapBase must ALREADY be uploaded before
        // the draw: CubeMapRenderer looks the texture up from inside its own
        // render pass, and any upload triggered there dies with "Close the
        // existing render pass before performing additional commands".
        boolean ready = false;
        if (MenuPanoramaRegistry.isDynamicCubeMap(this.cubeMapBase)) {
            // Zip-loaded panorama: its faces are dynamic NativeImageBackedTextures,
            // never resources, so vanilla's CubemapTexture cannot load them at all.
            // Build the cube map here instead, straight from those NativeImages,
            // and register it through the non-loading
            // registerTexture(Identifier, AbstractTexture) overload - which also
            // closes whatever was registered under this id before, so a generation
            // bump replaces the old cube map instead of leaking it.
            try {
                NativeImage[] faces = MenuPanoramaRegistry.dynamicFacesFor(this.cubeMapBase);
                if (faces != null) {
                    textureManager.registerTexture(this.cubeMapBase, new PhazeCubeMapTexture(this.cubeMapBase, faces));
                    ready = true;
                }
            } catch (Throwable ignored) {
                // Malformed archive (empty/absent faces) or a GPU allocation
                // failure - fall through to the skip below rather than taking
                // the main menu down with it.
            }
        } else {
            // Vanilla / resource-pack panorama, unchanged path.
            // CubeMapRenderer.registerTextures() goes through the non-loading
            // registerTexture(Identifier, AbstractTexture) overload, which only
            // works for vanilla because it registers before the startup resource
            // reload uploads every registered ReloadableTexture. Phaze registers
            // from screen init, i.e. after that reload, so the loading
            // registerTexture(Identifier, ReloadableTexture) overload is used
            // here instead; it uploads the cube map immediately.
            try {
                textureManager.registerTexture(this.cubeMapBase, new CubemapTexture(this.cubeMapBase));
                ready = textureManager.getTexture(this.cubeMapBase) != null;
            } catch (Throwable ignored) {
                // A missing/broken resource pack panorama must not crash the menu
                // (registerTexture rethrows load failures as CrashException).
            }
        }
        // Latched so render() can skip the cube map draw when nothing usable was
        // uploaded above - that skip is the degrade path, not a crash.
        this.cubeMapReady = ready;
        this.preparedResourceGeneration = resourceGeneration;
        this.lastFrameTimeNs = -1L;
    }
}
