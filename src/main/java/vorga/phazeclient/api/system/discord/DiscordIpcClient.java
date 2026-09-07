package vorga.phazeclient.api.system.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 */
final class DiscordIpcClient implements AutoCloseable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    private final String clientId;
    private final Consumer<User> readyHandler;
    private final Object writeLock = new Object();
    private volatile RandomAccessFile pipe;
    private volatile boolean connected;

    DiscordIpcClient(String clientId, Consumer<User> readyHandler) {
        this.clientId = clientId;
        this.readyHandler = readyHandler;
    }

    boolean connect() {
        if (connected) return true;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;

        for (int i = 0; i < 10; i++) {
            try {
                RandomAccessFile candidate = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                pipe = candidate;
                connected = true;

                JsonObject handshake = new JsonObject();
                handshake.addProperty("v", 1);
                handshake.addProperty("client_id", clientId);
                write(OP_HANDSHAKE, handshake.toString().getBytes(StandardCharsets.UTF_8));

                Thread reader = new Thread(this::readLoop, "Phaze-Discord-IPC-Reader");
                reader.setDaemon(true);
                reader.start();
                return true;
            } catch (IOException ignored) {
                disconnect();
            }
        }
        return false;
    }

    boolean isConnected() {
        return connected;
    }

    void setActivity(DiscordRichPresence presence) throws IOException {
        if (!connected) throw new IOException("Discord IPC is not connected");

        JsonObject root = new JsonObject();
        root.addProperty("cmd", "SET_ACTIVITY");
        root.addProperty("nonce", UUID.randomUUID().toString());
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", presence == null ? null : presence.toJson());
        root.add("args", args);
        write(OP_FRAME, root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void readLoop() {
        try {
            while (connected) {
                RandomAccessFile current = pipe;
                if (current == null) break;
                int opcode = readLittleEndianInt(current);
                int length = readLittleEndianInt(current);
                if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("Invalid Discord IPC frame");
                byte[] payload = new byte[length];
                current.readFully(payload);

                if (opcode == OP_PING) {
                    write(OP_PONG, payload);
                } else if (opcode == OP_CLOSE) {
                    break;
                } else if (opcode == OP_FRAME) {
                    handleFrame(new String(payload, StandardCharsets.UTF_8));
                }
            }
        } catch (EOFException ignored) {
        } catch (Throwable error) {
            System.err.println("[Phaze] Discord IPC reader stopped: " + error.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleFrame(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!"READY".equals(root.has("evt") ? root.get("evt").getAsString() : "")) return;
            JsonObject user = root.getAsJsonObject("data").getAsJsonObject("user");
            String id = string(user, "id");
            String username = string(user, "global_name");
            if (username.isEmpty()) username = string(user, "username");
            readyHandler.accept(new User(username, id, string(user, "avatar")));
        } catch (Throwable ignored) {
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private void write(int opcode, byte[] payload) throws IOException {
        synchronized (writeLock) {
            RandomAccessFile current = pipe;
            if (!connected || current == null) throw new IOException("Discord IPC is closed");
            writeLittleEndianInt(current, opcode);
            writeLittleEndianInt(current, payload.length);
            current.write(payload);
        }
    }

    private static int readLittleEndianInt(RandomAccessFile file) throws IOException {
        return Integer.reverseBytes(file.readInt());
    }

    private static void writeLittleEndianInt(RandomAccessFile file, int value) throws IOException {
        file.writeInt(Integer.reverseBytes(value));
    }

    private void disconnect() {
        connected = false;
        RandomAccessFile current = pipe;
        pipe = null;
        if (current != null) {
            try { current.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    record User(String username, String id, String avatar) {}
}
