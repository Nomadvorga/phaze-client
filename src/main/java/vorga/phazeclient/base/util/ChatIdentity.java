package vorga.phazeclient.base.util;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import com.sun.jna.platform.win32.WinReg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The Ed25519 keys that prove a chat login comes from this PC; the user never
 * sees or handles them.
 *
 * <p>The device key is random and lives in the OS user profile, outside every
 * game directory, so reinstalling the mod, the launcher or Minecraft keeps the
 * account. On Windows it is encrypted with DPAPI and is useless on another PC
 * or OS account. The machine key is re-derived from the OS installation id and
 * the OS user; it restores the account if the key file is deleted.
 */
public final class ChatIdentity {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessenger");
    private static final String AUTH_CONTEXT = "phaze-chat-auth-v1";
    private static final String FILE_DPAPI = "v1:dpapi:";
    private static final String FILE_RAW = "v1:raw:";
    private static final byte[] DPAPI_ENTROPY = "phaze-chat-identity-v1".getBytes(StandardCharsets.UTF_8);
    private static final int SEED_BYTES = 32;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    public record Proof(String deviceKey, String deviceSignature, String machineKey, String machineSignature) {}

    private static ChatIdentity instance;
    private static boolean unavailable;

    private final KeyPair device;
    private final KeyPair machine;
    private final String deviceKey;
    private final String machineKey;

    private ChatIdentity(KeyPair device, KeyPair machine) {
        this.device = device;
        this.machine = machine;
        this.deviceKey = rawPublicKey(device);
        this.machineKey = machine == null ? null : rawPublicKey(machine);
    }

    /** Loads or creates the keys on first use; null only if this JVM has no Ed25519 at all. */
    public static synchronized ChatIdentity get() {
        if (instance != null || unavailable) return instance;
        try {
            byte[] deviceSeed = loadOrCreateDeviceSeed();
            byte[] machineSeed = machineSeed();
            instance = new ChatIdentity(keyPair(deviceSeed), machineSeed == null ? null : keyPair(machineSeed));
            Arrays.fill(deviceSeed, (byte) 0);
            if (machineSeed != null) Arrays.fill(machineSeed, (byte) 0);
        } catch (Throwable failure) {
            unavailable = true;
            LOG.warn("Chat identity keys are unavailable; falling back to the legacy login", failure);
        }
        return instance;
    }

    /** Signs the server challenge; the layout must match the server's chatAuthMessage. */
    public Proof sign(String challenge, String clientId, String username) throws GeneralSecurityException {
        byte[] message = String.join("\n",
                AUTH_CONTEXT,
                challenge,
                clientId.toLowerCase(Locale.ROOT),
                username == null ? "" : username,
                deviceKey,
                machineKey == null ? "" : machineKey
        ).getBytes(StandardCharsets.UTF_8);
        return new Proof(
                deviceKey,
                signWith(device, message),
                machineKey,
                machine == null ? null : signWith(machine, message)
        );
    }

    private static String signWith(KeyPair pair, byte[] message) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(message);
        return B64URL.encodeToString(signature.sign());
    }

    private static String rawPublicKey(KeyPair pair) {
        byte[] encoded = pair.getPublic().getEncoded();
        return B64URL.encodeToString(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }

    /** RFC 8032 key pair for a fixed 32-byte seed: the JDK generator draws exactly one seed. */
    private static KeyPair keyPair(byte[] seed) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        generator.initialize(NamedParameterSpec.ED25519, new FixedSeed(seed));
        return generator.generateKeyPair();
    }

    private static final class FixedSeed extends SecureRandom {
        private final byte[] seed;
        private boolean used;

        private FixedSeed(byte[] seed) {
            this.seed = seed.clone();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (used || bytes.length != seed.length) throw new IllegalStateException("unexpected Ed25519 seed request");
            used = true;
            System.arraycopy(seed, 0, bytes, 0, seed.length);
            Arrays.fill(seed, (byte) 0);
        }
    }

    private static Path identityDirectory() {
        String appData = WINDOWS ? System.getenv("APPDATA") : null;
        Path root = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"));
        return root.resolve(".phaze").resolve("identity");
    }

    private static byte[] loadOrCreateDeviceSeed() throws IOException {
        Path file = identityDirectory().resolve("device.key");
        if (Files.isRegularFile(file)) {
            byte[] seed = readSeed(Files.readString(file, StandardCharsets.UTF_8).trim());
            if (seed != null) return seed;
            // Copied from another PC or OS user: it can not be decrypted here, so it proves nothing.
            Files.move(file, file.resolveSibling("device.key.unreadable"), StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] seed = new byte[SEED_BYTES];
        new SecureRandom().nextBytes(seed);
        writeSeed(file, seed);
        return seed;
    }

    private static byte[] readSeed(String contents) {
        try {
            byte[] seed;
            if (contents.startsWith(FILE_DPAPI)) {
                seed = Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(contents.substring(FILE_DPAPI.length())),
                        DPAPI_ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null);
            } else if (contents.startsWith(FILE_RAW)) {
                seed = Base64.getDecoder().decode(contents.substring(FILE_RAW.length()));
            } else {
                return null;
            }
            return seed.length == SEED_BYTES ? seed : null;
        } catch (Throwable unreadable) {
            LOG.debug("Chat identity key file could not be read", unreadable);
            return null;
        }
    }

    private static void writeSeed(Path file, byte[] seed) throws IOException {
        String contents = null;
        if (WINDOWS) {
            try {
                contents = FILE_DPAPI + Base64.getEncoder().encodeToString(
                        Crypt32Util.cryptProtectData(seed, DPAPI_ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "Phaze chat identity", null));
            } catch (Throwable dpapiMissing) {
                LOG.debug("DPAPI unavailable, storing the chat key with file permissions only", dpapiMissing);
            }
        }
        if (contents == null) contents = FILE_RAW + Base64.getEncoder().encodeToString(seed);

        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, contents, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(temp, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException notPosix) {
            // Windows: the profile directory ACL already limits it to this user.
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException noAtomicMove) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Null when the OS offers no stable installation id; the device key still works alone. */
    private static byte[] machineSeed() {
        String machineId = machineId();
        if (machineId == null || machineId.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = "phaze-chat-machine-v1\n" + machineId.trim().toLowerCase(Locale.ROOT)
                    + "\n" + System.getProperty("user.name", "");
            return digest.digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            return null;
        }
    }

    private static String machineId() {
        try {
            if (WINDOWS) {
                return Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                        "SOFTWARE\\Microsoft\\Cryptography", "MachineGuid");
            }
            for (String candidate : new String[]{"/etc/machine-id", "/var/lib/dbus/machine-id"}) {
                Path path = Path.of(candidate);
                if (Files.isReadable(path)) return Files.readString(path, StandardCharsets.UTF_8).trim();
            }
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return macPlatformUuid();
        } catch (Throwable unavailable) {
            LOG.debug("Machine id unavailable for the chat identity", unavailable);
        }
        return null;
    }

    private static String macPlatformUuid() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        for (String line : output.split("\n")) {
            if (!line.contains("IOPlatformUUID")) continue;
            int end = line.lastIndexOf('"');
            int start = line.lastIndexOf('"', end - 1);
            if (start >= 0 && end > start) return line.substring(start + 1, end);
        }
        return null;
    }
}
