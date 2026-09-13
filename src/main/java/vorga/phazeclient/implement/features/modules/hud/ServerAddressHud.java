package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.gl.RenderPipelines;

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

    private static final Identifier UNKNOWN_SERVER_TEXTURE =
            Identifier.ofVanilla("textures/misc/unknown_server.png");

    private WorldIcon currentIcon;

    private String currentIconServerAddress;

    private byte[] currentIconFaviconBytes;

    private ServerAddressHud() {
        super("server_address_hud", "Server Address", 100.0f, 50.0f, 1.0f);
        displayServerIcon.setFullWidth(true);
        setup(displayServerIcon, otherSection, cornerRounding);
    }

    public String getServerAddress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return "Local";
        }

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

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                iconX, iconY,
                0.0F, 0.0F,
                iconSize, iconSize,
                iconSize, iconSize
        );
        context.getMatrices().popMatrix();
    }

    private void updateIcon(MinecraftClient client, ServerInfo info) {
        String address = info.address;
        if (address == null || address.isEmpty()) {
            releaseIcon();
            return;
        }
        if (currentIcon == null || !address.equals(currentIconServerAddress)) {

            releaseIcon();
            currentIcon = WorldIcon.forServer(client.getTextureManager(), address);
            currentIconServerAddress = address;
            currentIconFaviconBytes = null;
        }

        byte[] favicon = info.getFavicon();
        if (favicon == null || favicon.length == 0) {

            return;
        }
        if (Arrays.equals(favicon, currentIconFaviconBytes)) {
            return;
        }

        NativeImage image = null;
        try {
            image = NativeImage.read(new ByteArrayInputStream(favicon));
            currentIcon.load(image);

            image = null;
            currentIconFaviconBytes = favicon;
        } catch (Exception ignored) {

            currentIconFaviconBytes = null;
            if (image != null) {
                try {
                    image.close();
                } catch (Exception swallowed) {

                }
            }
        }
    }

    private void releaseIcon() {
        if (currentIcon != null) {
            try {
                currentIcon.close();
            } catch (Exception ignored) {

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
