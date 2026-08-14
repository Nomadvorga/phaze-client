package vorga.phazeclient.implement.cosmetics.bridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Arrays;

/** Produces a privacy-safe stable key for the multiplayer/LAN world. */
public final class SharedWorldKey {
    public static String get(MinecraftClient client) {
        if (client == null || client.world == null) return null;
        String connectedAddress = actualAddress(client, true);
        if (connectedAddress != null && connectedAddress.startsWith("local:")) {
            return hash(connectedAddress);
        }
        ServerInfo info = client.getCurrentServerEntry();
        String address = info != null && info.address != null && !info.address.isBlank()
                ? canonicalConfiguredAddress(info.address)
                : actualAddress(client);
        return hash(address);
    }

    /** Previous key format, used only to migrate already saved placements. */
    public static String legacy(MinecraftClient client) {
        if (client == null || client.world == null) return null;
        String address = null;
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            address = normalize(info.address);
        } else if (client.getServer() != null && client.getServer().getServerPort() > 0) {
            address = "local:" + client.getServer().getServerPort();
        }
        return hash(address);
    }

    /** Short-lived migration helper for builds that keyed by proxy IP. */
    public static String actual(MinecraftClient client) {
        return client == null || client.world == null ? null : hash(actualAddress(client, false));
    }

    private static String actualAddress(MinecraftClient client) {
        return actualAddress(client, true);
    }

    private static String actualAddress(MinecraftClient client, boolean includeLocalInterfaces) {
        if (client.getNetworkHandler() != null) {
            SocketAddress remote = client.getNetworkHandler().getConnection().getAddress();
            if (remote instanceof InetSocketAddress inet) {
                String host = inet.getAddress() == null
                        ? inet.getHostString()
                        : inet.getAddress().getHostAddress();
                if (inet.getAddress() != null && (inet.getAddress().isLoopbackAddress()
                        || includeLocalInterfaces && isThisComputer(inet.getAddress()))) {
                    return "local:" + inet.getPort();
                }
                if (host.contains(":")) host = '[' + host + ']';
                return normalize(host + ':' + inet.getPort());
            }
        }
        if (client.getServer() != null && client.getServer().getServerPort() > 0) {
            return "local:" + client.getServer().getServerPort();
        }
        ServerInfo info = client.getCurrentServerEntry();
        return info == null ? null : normalize(info.address);
    }

    private static boolean isThisComputer(InetAddress address) {
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String hash(String address) {
        if (address == null || address.isBlank()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(address.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String canonicalConfiguredAddress(String value) {
        String address = value.trim().toLowerCase(Locale.ROOT);
        if (address.startsWith("localhost:") || address.startsWith("127.0.0.1:")
                || address.startsWith("[::1]:")) return normalize(address);

        String host = address;
        int port = 25565;
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end > 0) {
                host = address.substring(1, end);
                if (end + 2 < address.length() && address.charAt(end + 1) == ':') {
                    try { port = Integer.parseInt(address.substring(end + 2)); } catch (NumberFormatException ignored) { }
                }
            }
        } else {
            int colon = address.lastIndexOf(':');
            if (colon > 0 && address.indexOf(':') == colon) {
                host = address.substring(0, colon);
                try { port = Integer.parseInt(address.substring(colon + 1)); } catch (NumberFormatException ignored) { }
            }
        }

        if (!host.matches("[0-9.]+") && !isSharedHosting(host)) {
            String[] labels = host.split("\\.");
            int keep = 2;
            if (labels.length >= 3 && isCountrySecondLevel(labels[labels.length - 2])) keep = 3;
            if (labels.length > keep) host = String.join(".", Arrays.copyOfRange(labels, labels.length - keep, labels.length));
        }
        return host + ':' + port;
    }

    private static boolean isCountrySecondLevel(String label) {
        return label.equals("co") || label.equals("com") || label.equals("net")
                || label.equals("org") || label.equals("gov") || label.equals("ac");
    }

    private static boolean isSharedHosting(String host) {
        return host.endsWith(".aternos.me") || host.endsWith(".minehut.gg")
                || host.endsWith(".falixsrv.me") || host.endsWith(".server.pro");
    }

    public static String dimension(MinecraftClient client) {
        return client == null || client.world == null ? null
                : client.world.getRegistryKey().getValue().toString();
    }

    private static String normalize(String value) {
        String address = value.trim().toLowerCase(Locale.ROOT);
        if (address.startsWith("127.0.0.1:")) return "local:" + address.substring(10);
        if (address.startsWith("localhost:")) return "local:" + address.substring(10);
        if (address.startsWith("[::1]:")) return "local:" + address.substring(6);
        return address;
    }

    private SharedWorldKey() { }
}
