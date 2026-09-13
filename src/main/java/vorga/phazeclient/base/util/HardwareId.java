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

public final class HardwareId {

    private static volatile String cached;

    private HardwareId() {
    }

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

            Collections.sort(parts);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("phaze-hwid-v1".getBytes(StandardCharsets.UTF_8));
            for (String part : parts) {
                digest.update((byte) 0);
                digest.update(part.getBytes(StandardCharsets.UTF_8));
            }
            return toHex(digest.digest());
        } catch (Throwable t) {

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
