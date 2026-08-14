package vorga.phazeclient.base.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Stable per-machine identifier used as one of the counters behind the
 * cloud-config quota.
 *
 * <h3>What it is</h3>
 * A SHA-256 digest of a few machine-scoped strings: the MAC addresses
 * of the physical network interfaces, the host name, the OS account
 * name and the OS/architecture pair. Only the digest ever leaves the
 * machine - the underlying values are hashed here and discarded.
 *
 * <h3>What it is not</h3>
 * It is not a security boundary. This code runs on the user's own
 * computer, so anyone willing to patch it can send whatever digest
 * they like. It is also not perfectly stable: swapping a network
 * adapter, or docking a laptop, changes the interface list and
 * therefore the id.
 *
 * <p>Both of those are why the server counts three independent things
 * - source address, install id and this - and refuses when any single
 * one is over the limit. The point is to make casual quota evasion
 * (delete the client_id file, reconnect for a new address) more work
 * than it is worth, not to make it impossible.
 *
 * <p>Loopback, virtual and point-to-point interfaces are skipped:
 * VPNs and container bridges come and go, and including them would
 * change the id every time the user connected to one.
 */
public final class HardwareId {

    private static volatile String cached;

    private HardwareId() {
    }

    /**
     * Lower-case hex SHA-256, or null when the fingerprint could not
     * be built. Callers treat null as "no hardware counter" rather
     * than failing - the server still applies the other two.
     */
    public static String get() {
        String local = cached;
        if (local != null) {
            return local.isEmpty() ? null : local;
        }
        synchronized (HardwareId.class) {
            if (cached == null) {
                cached = compute();
            }
        }
        return cached.isEmpty() ? null : cached;
    }

    private static String compute() {
        try {
            List<String> parts = new ArrayList<>();
            parts.addAll(collectMacAddresses());
            parts.add("host:" + safeHostName());
            parts.add("user:" + normalize(System.getProperty("user.name")));
            parts.add("os:" + normalize(System.getProperty("os.name"))
                    + "/" + normalize(System.getProperty("os.arch")));

            // Sorted so interface enumeration order - which is not
            // guaranteed stable across boots - cannot change the id.
            Collections.sort(parts);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("phaze-hwid-v1".getBytes(StandardCharsets.UTF_8));
            for (String part : parts) {
                digest.update((byte) 0);
                digest.update(part.getBytes(StandardCharsets.UTF_8));
            }
            return toHex(digest.digest());
        } catch (Throwable t) {
            // Empty string means "computed and failed", so we do not
            // retry the whole enumeration on every upload.
            return "";
        }
    }

    private static List<String> collectMacAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                try {
                    if (iface.isLoopback() || iface.isVirtual() || iface.isPointToPoint()) {
                        continue;
                    }
                    byte[] mac = iface.getHardwareAddress();
                    if (mac == null || mac.length == 0) {
                        continue;
                    }
                    macs.add("mac:" + toHex(mac));
                } catch (Throwable ignored) {
                    // A single unreadable interface must not sink the
                    // whole fingerprint.
                }
            }
        } catch (Throwable ignored) {
        }
        return macs;
    }

    private static String safeHostName() {
        try {
            return normalize(InetAddress.getLocalHost().getHostName());
        } catch (Throwable t) {
            return "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
