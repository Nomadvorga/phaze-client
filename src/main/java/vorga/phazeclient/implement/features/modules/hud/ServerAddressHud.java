package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public final class ServerAddressHud extends RectHudModule {
    private static final ServerAddressHud INSTANCE = new ServerAddressHud();

    public static ServerAddressHud getInstance() {
        return INSTANCE;
    }

    public final BooleanSetting displayServerIcon = new BooleanSetting("Display Server Icon", "Show server icon next to address").setValue(true);

    private ServerAddressHud() {
        super("server_address_hud", "Server Address", 100.0f, 50.0f, 1.0f);
        setup(showBrackets, displayServerIcon);
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

    public void renderServerIcon(DrawContext context, int x, int y, float scale) {
        if (!displayServerIcon.isValue()) {
            return;
        }
        ServerInfo serverInfo = getServerInfo();
        if (serverInfo == null) {
            return;
        }
        // Icon rendering - TODO: Implement with correct API
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
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
