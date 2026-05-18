package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.WorldIcon;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

public final class ServerAddressHud extends RectHudModule {
    private static final ServerAddressHud INSTANCE = new ServerAddressHud();

    public static ServerAddressHud getInstance() {
        return INSTANCE;
    }

    public final BooleanSetting displayServerIcon = new BooleanSetting("Display Server Icon", "Show server icon next to address").setValue(true);

    /**
     * Vanilla's placeholder texture for servers whose favicon failed to
     * load (or that never sent one). Same identifier the multiplayer
     * server list falls back to. Stored at module scope so the lookup
     * happens at class-load instead of every render frame.
     */
    private static final Identifier UNKNOWN_SERVER_TEXTURE =
            Identifier.ofVanilla("textures/misc/unknown_server.png");

    /**
     * Live texture handle for the currently-connected server's favicon.
     * Lazily created on first render against a new server address, kept
     * alive across frames so the texture upload only happens once per
     * server (and again only when the server pushes a new favicon).
     * Closed and nulled on server change / disconnect / module disable
     * via {@link #releaseIcon()} so the GL texture handle doesn't leak.
     */
    private WorldIcon currentIcon;
    /**
     * Identifies which server {@link #currentIcon} was built for. Used
     * to detect cross-server transitions (player joins server B while
     * still holding the icon from server A) so we close + recreate
     * instead of mis-displaying the old favicon under the new address.
     */
    private String currentIconServerAddress;
    /**
     * Last favicon bytes loaded into {@link #currentIcon}. The favicon
     * itself is byte-comparison stable; cheap {@link Arrays#equals}
     * skips the PNG decode + GL upload on every frame and only refreshes
     * when the server actually pushed new bytes (icon change via
     * server.properties reload, or initial favicon arriving after a
     * slow first connect).
     */
    private byte[] currentIconFaviconBytes;

    private ServerAddressHud() {
        super("server_address_hud", "Server Address", 100.0f, 50.0f, 1.0f);
        setup(displayServerIcon);
    }

    public String getServerAddress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return "Local";
        }
        // Check if it's singleplayer/integrated server
        if (client.isIntegratedServerRunning()) {
            return "Local";
        }
        ServerInfo serverInfo = getServerInfo();
        if (serverInfo != null && serverInfo.address != null && !serverInfo.address.isEmpty()) {
            String address = normalizeServerAddress(serverInfo.address);
            if (!isNumericAddress(address)) {
                return address;
            }
        }
        if (serverInfo != null && serverInfo.name != null && !serverInfo.name.isEmpty() && !isNumericAddress(serverInfo.name)) {
            return serverInfo.name;
        }
        String address = client.getNetworkHandler().getConnection().getAddress().toString();
        address = normalizeServerAddress(address);
        if (isNumericAddress(address)) {
            return "Unknown";
        }
        
        return address;
    }

    private String normalizeServerAddress(String address) {
        if (address.startsWith("/")) {
            address = address.substring(1);
        }

        if (address.contains("[")) {
            int bracketEnd = address.indexOf("]");
            if (bracketEnd != -1 && bracketEnd < address.length() - 1 && address.charAt(bracketEnd + 1) == ':') {
                address = address.substring(0, bracketEnd + 1);
            }
            address = address.replace("[", "").replace("]", "");
        } else {
            int lastColon = address.lastIndexOf(':');
            if (lastColon != -1 && address.indexOf(':') == lastColon) {
                address = address.substring(0, lastColon);
            }
        }

        return address;
    }

    private boolean isNumericAddress(String address) {
        String normalized = address.trim().toLowerCase();
        return normalized.matches("\\d{1,3}(\\.\\d{1,3}){3}") || normalized.matches("[0-9a-f:]+") && normalized.contains(":");
    }

    public ServerInfo getServerInfo() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return null;
        }
        return client.getCurrentServerEntry();
    }

    /**
     * Draws the connected server's favicon as a square to the LEFT of
     * the address rect, sized to the rect's height so the icon visually
     * matches the rect at 1:1 vertical scale.
     *
     * <p>Geometry contract: caller passes the rect's top-left corner
     * ({@code rectX}, {@code rectY}) and rendered height in the same
     * coordinate space {@code renderRectHud} positions itself in -
     * which is PHYSICAL screen pixels, NOT GUI-scaled pixels, because
     * {@code renderRectHud} applies {@code scale(inverseGuiScale)} to
     * the matrix stack before translating to its rect position. We
     * mirror that exact transform here so the icon's size and position
     * track the rect at every GUI Scale setting; without it the icon
     * gets implicitly multiplied by the current GUI scale and ends up
     * 2x / 3x / 4x larger than the rect on common scale settings.
     *
     * <p>Icon position is {@code (rectX - rectHeight, rectY)} with size
     * {@code rectHeight x rectHeight} - touching the rect's left edge
     * with no gap, square, height-matched. No hit-test / no drag
     * handle: the icon is purely decorative and the rect's drag bounds
     * stay exactly where they were before this method existed.
     *
     * <p>Texture source: {@link WorldIcon#forServer} produces a stable
     * {@link Identifier} per server address; we lazily decode the
     * favicon bytes into a {@link NativeImage} on first sight or when
     * the bytes change, and otherwise just reuse the existing GL
     * texture. When no favicon is available (offline server, freshly
     * connected before the status response, or malformed PNG) we draw
     * the vanilla {@code unknown_server.png} placeholder so the icon
     * slot is never empty.
     */
    public void renderServerIcon(DrawContext context, float rectX, float rectY, float rectHeight, float inverseGuiScale) {
        if (!displayServerIcon.isValue()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        ServerInfo serverInfo = getServerInfo();
        if (serverInfo == null) {
            // No server connection - integrated server / disconnected.
            // Drop any cached texture so a future connect rebuilds
            // cleanly under the new address.
            releaseIcon();
            return;
        }
        updateIcon(client, serverInfo);

        Identifier textureId = (currentIcon != null && currentIconFaviconBytes != null)
                ? currentIcon.getTextureId()
                : UNKNOWN_SERVER_TEXTURE;

        int iconSize = Math.max(1, Math.round(rectHeight));
        int iconX = Math.round(rectX - rectHeight);
        int iconY = Math.round(rectY);

        // Mirror renderRectHud's matrix transform so the icon ends up
        // in the same physical-pixel space as the rect. Without this
        // the DrawContext is still in GUI-scaled coords and the icon
        // renders 2x / 3x / 4x oversized at higher GUI Scale settings.
        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.drawTexture(
                RenderLayer::getGuiTextured,
                textureId,
                iconX, iconY,
                0.0F, 0.0F,
                iconSize, iconSize,
                iconSize, iconSize
        );
        context.getMatrices().pop();
    }

    /**
     * Reconciles {@link #currentIcon} / {@link #currentIconFaviconBytes}
     * with the live {@link ServerInfo}. Cheap fast path when the server
     * address and favicon bytes are both unchanged (the common steady-
     * state case once the connection has settled), one PNG decode + GL
     * upload on first sight or when the server hot-swaps its favicon.
     */
    private void updateIcon(MinecraftClient client, ServerInfo info) {
        String address = info.address;
        if (address == null || address.isEmpty()) {
            releaseIcon();
            return;
        }
        if (currentIcon == null || !address.equals(currentIconServerAddress)) {
            // Cross-server transition (or first sight). Close the old
            // WorldIcon - it owns a GL texture handle - before allocating
            // a fresh one keyed on the new address.
            releaseIcon();
            currentIcon = WorldIcon.forServer(client.getTextureManager(), address);
            currentIconServerAddress = address;
            currentIconFaviconBytes = null;
        }

        byte[] favicon = info.getFavicon();
        if (favicon == null || favicon.length == 0) {
            // No favicon (yet?) - leave currentIconFaviconBytes null so
            // renderServerIcon falls back to UNKNOWN_SERVER_TEXTURE. A
            // future call after the server status arrives will see the
            // bytes and load them then.
            return;
        }
        if (Arrays.equals(favicon, currentIconFaviconBytes)) {
            return;
        }
        // Manual lifecycle (no try-with-resources) because
        // WorldIcon.load takes OWNERSHIP of the NativeImage - it hands
        // the buffer to a NativeImageBackedTexture which will close()
        // it when the texture is destroyed. Auto-closing here would
        // free the underlying pixel buffer from under the texture.
        NativeImage image = null;
        try {
            image = NativeImage.read(new ByteArrayInputStream(favicon));
            currentIcon.load(image);
            // Past this line `image` is owned by WorldIcon. Null out
            // the local so the failure-path close in the catch block
            // doesn't double-free.
            image = null;
            currentIconFaviconBytes = favicon;
        } catch (Exception ignored) {
            // Malformed PNG / IO error / GL upload failure. Leave
            // currentIconFaviconBytes null so the next frame falls back
            // to UNKNOWN_SERVER_TEXTURE instead of hammering the decoder
            // with the same bad bytes.
            currentIconFaviconBytes = null;
            if (image != null) {
                try {
                    image.close();
                } catch (Exception swallowed) {
                    // Already in error path; double-faulting on the
                    // close just hides the original problem.
                }
            }
        }
    }

    /**
     * Closes the cached {@link WorldIcon} (releasing its GL texture
     * handle) and clears the address / bytes tracking. Safe to call
     * when nothing is cached. Invoked on server-address change inside
     * {@link #updateIcon}, on disconnect path in {@link #renderServerIcon},
     * and from {@link #deactivate()} so disabling the module doesn't
     * leak a texture into the GL state.
     */
    private void releaseIcon() {
        if (currentIcon != null) {
            try {
                currentIcon.close();
            } catch (Exception ignored) {
                // close() may surface a GL error if the context is
                // already torn down (mod-reload / world unload race);
                // we've cleared our reference so the texture will be
                // GC-collected regardless, no further action needed.
            }
            currentIcon = null;
        }
        currentIconServerAddress = null;
        currentIconFaviconBytes = null;
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        // Disabling the module shouldn't leave a GPU texture allocated
        // for a server the user is no longer displaying an icon for.
        // Cheaper to re-decode the favicon when the module flips back
        // on than to keep the texture handle pinned indefinitely.
        releaseIcon();
    }

    @Override
    public String getDescription() {
        return "Shows server IP address in HUD";
    }

    @Override
    public String getIcon() {
        return "server_address_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
