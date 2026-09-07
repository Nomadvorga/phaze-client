package vorga.phazeclient.api.system.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vorga.phazeclient.api.system.discord.utils.DiscordRichPresence;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Minimal, source-visible implementation of Discord's local IPC protocol.
 * This replaces the legacy native discord-rpc DLL while keeping Rich Presence.
 *
 * <h3>Threading model</h3>
 * Windows named pipes opened through {@link RandomAccessFile} are
 * synchronous handles: while one thread holds a blocking read, a
 * concurrent write on the same handle blocks at the OS level (this
 * was verified with a thread dump - the RPC daemon hung forever in
 * a native write while the reader thread waited for data). This
 * client therefore performs ALL I/O on the calling thread under
 * {@link #ioLock}, in strict request -> response order:
 * write a frame, then read frames until the matching response
 * (identified by nonce) arrives. Unsolicited dispatch frames and
 * pings from Discord are consumed and skipped inside that loop.
 */
final class DiscordIpcClient implements AutoCloseable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    /** Max unsolicited frames to skip while hunting for a response. */
    private static final int MAX_SKIPPED_FRAMES = 16;
    /** Guard against absurd frames from a corrupted stream. */
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private final String clientId;
    private final Consumer<User> readyHandler;
    private final Object ioLock = new Object();
    private volatile RandomAccessFile pipe;
    private volatile boolean connected;
    private long lastUnavailableLog;

    DiscordIpcClient(String clientId, Consumer<User> readyHandler) {
        this.clientId = clientId;
        this.readyHandler = readyHandler;
    }

    boolean connect() {
        synchronized (ioLock) {
            if (connected) return true;
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;

            for (int i = 0; i < 10; i++) {
                RandomAccessFile candidate = null;
                try {
                    candidate = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    pipe = candidate;
                    connected = true;

                    JsonObject handshake = new JsonObject();
                    handshake.addProperty("v", 1);
                    handshake.addProperty("client_id", clientId);
                    write(OP_HANDSHAKE, handshake.toString().getBytes(StandardCharsets.UTF_8));

                    waitForHandshakeReady();
                    System.out.println("[Phaze] Discord IPC connected (discord-ipc-" + i + ")");
                    return true;
                } catch (IOException | RuntimeException error) {
                    System.out.println("[Phaze] Discord IPC pipe discord-ipc-" + i
                            + " failed: " + error.getMessage());
                    connected = false;
                    pipe = null;
                    if (candidate != null) {
                        try { candidate.close(); } catch (IOException ignored) {}
                    }
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastUnavailableLog > 300000) {
                lastUnavailableLog = now;
                System.out.println("[Phaze] Discord IPC unavailable (Discord not running?) - will keep retrying every 15s");
            }
            return false;
        }
    }

    boolean isConnected() {
        return connected;
    }

    void setActivity(DiscordRichPresence presence) throws IOException {
        synchronized (ioLock) {
            if (!connected || pipe == null) throw new IOException("Discord IPC is not connected");

            String nonce = UUID.randomUUID().toString();
            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.addProperty("nonce", nonce);
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", presence == null ? JsonNull.INSTANCE : presence.toJson());
            root.add("args", args);
            try {
                write(OP_FRAME, root.toString().getBytes(StandardCharsets.UTF_8));
                waitForResponse(nonce);
            } catch (IOException error) {
                // Broken pipe (Discord quit / restarted): drop the
                // connection so the daemon reconnects on its next
                // cycle instead of retrying writes to a dead handle.
                hardDisconnect();
                throw error;
            }
        }
    }

    private void hardDisconnect() {
        connected = false;
        try { if (pipe != null) pipe.close(); } catch (IOException ignored) {}
        pipe = null;
    }

    @Override
    public void close() {
        synchronized (ioLock) {
            if (!connected) return;
            connected = false;
            try { write(OP_CLOSE, new byte[0]); } catch (IOException ignored) {}
            try { if (pipe != null) pipe.close(); } catch (IOException ignored) {}
            pipe = null;
        }
    }

    /** Read frames until the READY dispatch arrives and hand the
     *  user payload to the ready handler. */
    private void waitForHandshakeReady() throws IOException {
        for (int i = 0; i < MAX_SKIPPED_FRAMES; i++) {
            Frame frame = readFrame();
            if (frame.opcode() == OP_CLOSE) {
                throw new EOFException("Discord closed the connection during handshake");
            }
            if (frame.opcode() == OP_PING) {
                write(OP_PONG, frame.payload());
                continue;
            }
            if (frame.opcode() != OP_FRAME) continue;

            JsonObject root = parse(frame);
            String evt = string(root, "evt");
            if (!"READY".equals(evt)) continue;
            JsonObject user = root.getAsJsonObject("data").getAsJsonObject("user");
            String id = string(user, "id");
            String username = string(user, "global_name");
            if (username.isEmpty()) username = string(user, "username");
            System.out.println("[Phaze] Discord IPC handshake READY (user: " + username + ")");
            readyHandler.accept(new User(username, id, string(user, "avatar")));
            return;
        }
        throw new EOFException("Discord IPC handshake response missing");
    }

    /** Read frames until the response carrying our nonce arrives.
     *  Pings are answered, close frames throw, stale dispatch
     *  frames (e.g. responses to earlier commands) are skipped. */
    private void waitForResponse(String nonce) throws IOException {
        for (int i = 0; i < MAX_SKIPPED_FRAMES; i++) {
            Frame frame = readFrame();
            if (frame.opcode() == OP_CLOSE) {
                throw new EOFException("Discord closed the connection");
            }
            if (frame.opcode() == OP_PING) {
                write(OP_PONG, frame.payload());
                continue;
            }
            if (frame.opcode() != OP_FRAME) continue;

            JsonObject root = parse(frame);
            String evt = string(root, "evt");
            if ("error".equals(evt)) {
                JsonObject data = root.has("data") && root.get("data").isJsonObject()
                        ? root.getAsJsonObject("data") : null;
                System.err.println("[Phaze] Discord IPC command error: "
                        + (data != null ? data.toString() : root.toString()));
                return;
            }
            if (nonce.equals(string(root, "nonce"))) {
                return;
            }
            // Stale / unsolicited dispatch - keep reading.
        }
        // No matching response within the bound - treat as a broken
        // stream so the caller disconnects and reconnects.
        throw new EOFException("Discord IPC response missing for nonce " + nonce);
    }

    private static JsonObject parse(Frame frame) throws IOException {
        try {
            return JsonParser.parseString(new String(frame.payload(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Malformed Discord IPC frame", error);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private void write(int opcode, byte[] payload) throws IOException {
        RandomAccessFile current = pipe;
        if (!connected || current == null) throw new IOException("Discord IPC is closed");
        writeLittleEndianInt(current, opcode);
        writeLittleEndianInt(current, payload.length);
        current.write(payload);
    }

    private Frame readFrame() throws IOException {
        RandomAccessFile current = pipe;
        if (!connected || current == null) throw new IOException("Discord IPC is closed");
        int opcode = readLittleEndianInt(current);
        int length = readLittleEndianInt(current);
        if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("Invalid Discord IPC frame");
        byte[] payload = new byte[length];
        current.readFully(payload);
        return new Frame(opcode, payload);
    }

    private static int readLittleEndianInt(RandomAccessFile file) throws IOException {
        return Integer.reverseBytes(file.readInt());
    }

    private static void writeLittleEndianInt(RandomAccessFile file, int value) throws IOException {
        file.writeInt(Integer.reverseBytes(value));
    }

    private record Frame(int opcode, byte[] payload) {}

    record User(String username, String id, String avatar) {}
}
