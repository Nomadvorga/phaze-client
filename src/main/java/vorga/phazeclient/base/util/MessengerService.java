package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.base.util.RemoteRulesService;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Background HTTP/SSE transport for the private messenger. */
public final class MessengerService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessenger");
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_LOCAL_MESSAGES = 100;
    private static final String USER_AGENT = "PhazeClient-Messenger/1";

    private static final class Holder {
        private static final MessengerService INSTANCE = new MessengerService();
    }

    public static MessengerService getInstance() {
        return Holder.INSTANCE;
    }

    public record Conversation(String id, String participantId, String username,
                               String lastText, long lastCreatedAt) {}

    public record Friend(String id, String username, long createdAt) {}

    public record FriendRequest(long id, String requesterId, String requesterUsername,
                                String targetId, String targetUsername, String status,
                                long createdAt) {}

    public record ChatUser(String id, String username) {}

    public record Message(long id, String senderId, String senderUsername,
                          String text, long createdAt) {}

    private record HttpResult(int status, String body) {}

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "phaze-messenger-io");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService streamIo = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "phaze-messenger-sse");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean sessionRequestInFlight = new AtomicBoolean(false);
    private final AtomicBoolean streamStarted = new AtomicBoolean(false);
    private final AtomicBoolean requestsRefreshInFlight = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Conversation> conversations = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Friend> friends = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<FriendRequest> requests = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Friend> blocked = new CopyOnWriteArrayList<>();
    private final Map<String, CopyOnWriteArrayList<Message>> messages = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presence = new ConcurrentHashMap<>();
    private final Map<String, Long> presenceFetchedAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presenceInFlight = new ConcurrentHashMap<>();

    private volatile String token;
    private volatile String status = "Offline";
    private volatile String selectedConversation;
    private volatile ChatUser currentUser;
    private final CopyOnWriteArrayList<Message> globalMessages = new CopyOnWriteArrayList<>();

    public static final String GLOBAL_CONVERSATION_ID = "00000000000000000000000000000001";

    private MessengerService() {
    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        io.execute(this::ensureSessionAndRefresh);
    }

    public String getStatus() {
        return status;
    }

    public List<Conversation> getConversations() {
        return Collections.unmodifiableList(new ArrayList<>(conversations));
    }

    public List<Friend> getFriends() {
        return Collections.unmodifiableList(new ArrayList<>(friends));
    }

    public List<FriendRequest> getRequests() {
        return Collections.unmodifiableList(new ArrayList<>(requests));
    }

    /** Refresh incoming friend requests without blocking the render thread. */
    public void refreshRequestsAsync() {
        if (!requestsRefreshInFlight.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                if (ensureSessionSync()) refreshRequests();
            } finally {
                requestsRefreshInFlight.set(false);
            }
        });
    }

    public List<Friend> getBlocked() {
        return Collections.unmodifiableList(new ArrayList<>(blocked));
    }

    public ChatUser getCurrentUser() {
        return currentUser;
    }

    public int getOnlineCount() {
        return RemoteRulesService.getInstance().getOnlineCount();
    }

    public boolean isUserOnline(String userId) {
        if (userId == null || userId.isBlank()) return false;
        if (currentUser != null && userId.equals(currentUser.id())) return "Online".equals(status);
        long now = System.currentTimeMillis();
        if (now - presenceFetchedAt.getOrDefault(userId, 0L) > 30_000L
                && presenceInFlight.putIfAbsent(userId, Boolean.TRUE) == null) {
            io.execute(() -> {
                try {
                    HttpResult result = request("GET", "/api/chat/users/" + userId + "/presence", null, true);
                    if (result.status == 200) {
                        JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
                        presence.put(userId, root.has("online") && root.get("online").getAsBoolean());
                        presenceFetchedAt.put(userId, System.currentTimeMillis());
                    }
                } catch (Throwable throwable) {
                    LOG.debug("Presence request failed for {}", userId, throwable);
                } finally {
                    presenceInFlight.remove(userId);
                }
            });
        }
        return presence.getOrDefault(userId, false);
    }

    public List<Message> getMessages(String conversationId) {
        return Collections.unmodifiableList(new ArrayList<>(messages.getOrDefault(
                conversationId, new CopyOnWriteArrayList<>())));
    }

    public List<Message> getGlobalMessages() {
        return Collections.unmodifiableList(new ArrayList<>(globalMessages));
    }

    public void sendGlobal(String text) {
        sendGlobal(text, null);
    }

    public void sendGlobal(String text, Consumer<Boolean> completion) {
        if (text == null || text.isBlank()) return;
        MessengerTextFilter.Result filter = MessengerTextFilter.check(text);
        if (filter != MessengerTextFilter.Result.ALLOW) {
            status = filter == MessengerTextFilter.Result.REPEATED_FLOOD
                    ? "Message blocked: flood" : "Message blocked: abusive content";
            if (completion != null) completion.accept(false);
            return;
        }
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("clientMessageId", UUID.randomUUID().toString());
            body.addProperty("text", text.trim());
            HttpResult result = request("POST", "/api/chat/global/messages", body.toString(), true);
            if (result.status == 201 || result.status == 200) {
                JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
                acceptGlobalMessage(root.getAsJsonObject("message"));
                status = "Online";
                if (completion != null) completion.accept(true);
            } else {
                status = parseError(result.body, "Send failed");
                if (completion != null) completion.accept(false);
            }
        });
    }

    public String getSelectedConversation() {
        return selectedConversation;
    }

    public void selectConversation(String conversationId) {
        selectedConversation = conversationId;
        if (conversationId != null && !messages.containsKey(conversationId)) {
            io.execute(() -> loadMessages(conversationId));
        }
    }

    public void send(String conversationId, String text) {
        send(conversationId, text, null);
    }

    public void send(String conversationId, String text, Consumer<Boolean> completion) {
        if (conversationId == null || text == null || text.isBlank()) return;
        MessengerTextFilter.Result filter = MessengerTextFilter.check(text);
        if (filter != MessengerTextFilter.Result.ALLOW) {
            status = filter == MessengerTextFilter.Result.REPEATED_FLOOD
                    ? "Message blocked: flood"
                    : "Message blocked: abusive content";
            if (completion != null) completion.accept(false);
            return;
        }
        String trimmed = text.trim();
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("clientMessageId", UUID.randomUUID().toString());
            body.addProperty("text", trimmed);
            HttpResult result = request("POST", "/api/chat/conversations/"
                    + conversationId + "/messages", body.toString(), true);
            if (result.status == 201 || result.status == 200) {
                acceptMessage(conversationId, JsonParser.parseString(result.body).getAsJsonObject().getAsJsonObject("message"));
                status = "Online";
                if (completion != null) completion.accept(true);
            } else if (result.status == 401) {
                token = null;
                status = "Session expired";
                if (completion != null) completion.accept(false);
            } else {
                status = parseError(result.body, "Send failed");
                if (completion != null) completion.accept(false);
            }
        });
    }

    public void addFriendByUsername(String username) {
        addFriendByUsername(username, null);
    }

    public void addFriendByUsername(String username, Consumer<Boolean> completion) {
        if (username == null || username.isBlank()) return;
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            HttpResult lookup = request("GET", "/api/chat/users/lookup?username="
                    + java.net.URLEncoder.encode(username.trim(), StandardCharsets.UTF_8), null, true);
            if (lookup.status != 200) {
                status = parseError(lookup.body, "Player not found");
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject user = JsonParser.parseString(lookup.body).getAsJsonObject().getAsJsonObject("user");
            JsonObject body = new JsonObject();
            body.addProperty("targetUserId", user.get("id").getAsString());
            HttpResult result = request("POST", "/api/chat/friends/requests", body.toString(), true);
            boolean success = result.status == 201 || result.status == 200;
            status = success ? "Friend request sent" : parseError(result.body, "Friend request failed");
            if (completion != null) completion.accept(success);
        });
    }

    public void openConversation(String participantId, Consumer<String> completion) {
        if (participantId == null || participantId.isBlank()) return;
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(null);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("participantId", participantId);
            HttpResult result = request("POST", "/api/chat/conversations", body.toString(), true);
            if (result.status == 201 || result.status == 200) {
                String conversationId = JsonParser.parseString(result.body).getAsJsonObject()
                        .get("conversationId").getAsString();
                refreshConversations();
                selectConversation(conversationId);
                if (completion != null) completion.accept(conversationId);
            } else {
                status = parseError(result.body, "Conversation unavailable");
                if (completion != null) completion.accept(null);
            }
        });
    }

    public void blockUser(String userId) {
        if (userId == null || userId.isBlank()) return;
        io.execute(() -> {
            if (!ensureSessionSync()) return;
            JsonObject body = new JsonObject();
            body.addProperty("targetUserId", userId);
            HttpResult result = request("POST", "/api/chat/friends/block", body.toString(), true);
            if (result.status == 200) {
                status = "User blocked";
                refreshFriends();
                refreshBlocked();
            } else {
                status = parseError(result.body, "Block failed");
            }
        });
    }

    public void unblockUser(String userId, Consumer<Boolean> completion) {
        if (userId == null || userId.isBlank()) return;
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("targetUserId", userId);
            HttpResult result = request("POST", "/api/chat/friends/unblock", body.toString(), true);
            boolean success = result.status == 200;
            status = success ? "User unblocked" : parseError(result.body, "Unblock failed");
            if (success) refreshBlocked();
            if (completion != null) completion.accept(success);
        });
    }

    public void acceptFriendRequest(long requestId) {
        io.execute(() -> {
            if (!ensureSessionSync()) return;
            JsonObject body = new JsonObject();
            body.addProperty("requestId", requestId);
            HttpResult result = request("POST", "/api/chat/friends/accept", body.toString(), true);
            if (result.status == 200) {
                status = "Friend added";
                refreshFriends();
                refreshRequests();
            } else {
                status = parseError(result.body, "Request failed");
            }
        });
    }

    public void declineFriendRequest(long requestId, Consumer<Boolean> completion) {
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("requestId", requestId);
            HttpResult result = request("POST", "/api/chat/friends/decline", body.toString(), true);
            boolean success = result.status == 200;
            status = success ? "Friend request declined" : parseError(result.body, "Request failed");
            if (success) refreshRequests();
            if (completion != null) completion.accept(success);
        });
    }

    public void removeFriend(String userId, Consumer<Boolean> completion) {
        if (userId == null || userId.isBlank()) return;
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("targetUserId", userId);
            HttpResult result = request("POST", "/api/chat/friends/remove", body.toString(), true);
            boolean success = result.status == 200;
            status = success ? "Friend removed" : parseError(result.body, "Remove friend failed");
            if (success) {
                refreshFriends();
                refreshConversations();
            }
            if (completion != null) completion.accept(success);
        });
    }

    public void stop() {
        io.shutdownNow();
        streamIo.shutdownNow();
    }

    private void ensureSessionAndRefresh() {
        if (!ensureSessionSync()) return;
        refreshFriends();
        refreshRequests();
        refreshBlocked();
        refreshConversations();
        loadGlobalMessages();
        preloadAvatars();
        startStream();
    }

    private void preloadAvatars() {
        java.util.Set<String> usernames = new java.util.HashSet<>();
        if (currentUser != null) usernames.add(currentUser.username());
        friends.forEach(friend -> usernames.add(friend.username()));
        blocked.forEach(friend -> usernames.add(friend.username()));
        conversations.forEach(conversation -> usernames.add(conversation.username()));
        globalMessages.forEach(message -> usernames.add(message.senderUsername()));
        SkinAvatarService.getInstance().preload(usernames);
    }

    private void refreshFriends() {
        HttpResult result = request("GET", "/api/chat/friends", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("friends");
        friends.clear();
        for (JsonElement element : array) {
            JsonObject row = element.getAsJsonObject();
            friends.add(new Friend(row.get("id").getAsString(), usernameOf(row), row.get("createdAt").getAsLong()));
        }
    }

    private void refreshRequests() {
        HttpResult result = request("GET", "/api/chat/friends/requests", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("requests");
        requests.clear();
        for (JsonElement element : array) {
            JsonObject row = element.getAsJsonObject();
            if (!"pending".equals(row.get("status").getAsString())) continue;
            JsonObject requester = row.getAsJsonObject("requester");
            JsonObject target = row.getAsJsonObject("target");
            requests.add(new FriendRequest(
                    row.get("id").getAsLong(),
                    requester.get("id").getAsString(), usernameOf(requester),
                    target.get("id").getAsString(), usernameOf(target),
                    row.get("status").getAsString(), row.get("createdAt").getAsLong()
            ));
        }
    }

    private void refreshBlocked() {
        HttpResult result = request("GET", "/api/chat/friends/blocked", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("blocked");
        blocked.clear();
        for (JsonElement element : array) {
            JsonObject row = element.getAsJsonObject();
            blocked.add(new Friend(row.get("id").getAsString(), usernameOf(row), row.get("createdAt").getAsLong()));
        }
    }

    private void loadGlobalMessages() {
        HttpResult result = request("GET", "/api/chat/global/messages?limit=100", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("messages");
        globalMessages.clear();
        for (JsonElement element : array) acceptGlobalMessage(element.getAsJsonObject());
    }

    private boolean ensureSessionSync() {
        if (token != null && !token.isBlank()) return true;
        if (!sessionRequestInFlight.compareAndSet(false, true)) {
            for (int i = 0; i < 50 && (token == null || token.isBlank()); i++) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return token != null && !token.isBlank();
        }
        try {
            String base = RemoteRulesService.getInstance().getApiBase();
            if (base == null || base.isBlank()) {
                status = "Messenger disabled";
                return false;
            }
            JsonObject body = new JsonObject();
            body.addProperty("clientId", RemoteRulesService.getInstance().getClientId());
            body.addProperty("username", currentUsername());
            HttpResult result = request("POST", "/api/chat/session", body.toString(), false);
            if (result.status != 201) {
                status = parseError(result.body, "Session unavailable");
                return false;
            }
            JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
            token = root.get("token").getAsString();
            JsonObject user = root.getAsJsonObject("user");
            currentUser = new ChatUser(user.get("id").getAsString(), usernameOf(user));
            status = "Online";
            return true;
        } catch (Throwable throwable) {
            status = "Network unavailable";
            LOG.debug("Messenger session failed", throwable);
            return false;
        } finally {
            sessionRequestInFlight.set(false);
        }
    }

    private void refreshConversations() {
        HttpResult result = request("GET", "/api/chat/conversations", null, true);
        if (result.status != 200) {
            if (result.status == 401) token = null;
            status = parseError(result.body, "Conversations unavailable");
            return;
        }
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("conversations");
        conversations.clear();
        for (JsonElement element : array) {
            JsonObject conversation = element.getAsJsonObject();
            JsonObject participant = conversation.getAsJsonObject("participant");
            JsonObject last = conversation.get("lastMessage").isJsonNull()
                    ? null : conversation.getAsJsonObject("lastMessage");
            conversations.add(new Conversation(
                    conversation.get("id").getAsString(),
                    participant.get("id").getAsString(),
                    participant.get("username").isJsonNull() ? "Unknown" : participant.get("username").getAsString(),
                    last == null ? "" : last.get("text").getAsString(),
                    last == null ? 0L : last.get("createdAt").getAsLong()
            ));
        }
        if (selectedConversation == null && !conversations.isEmpty()) {
            selectConversation(conversations.get(0).id());
        }
    }

    private void loadMessages(String conversationId) {
        HttpResult result = request("GET", "/api/chat/conversations/" + conversationId + "/messages?limit=100", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("messages");
        CopyOnWriteArrayList<Message> target = messages.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
        target.clear();
        for (JsonElement element : array) acceptMessage(conversationId, element.getAsJsonObject());
    }

    private void startStream() {
        if (!streamStarted.compareAndSet(false, true)) return;
        streamIo.execute(() -> {
            long backoff = 5_000L;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    listenStream();
                    backoff = 5_000L;
                } catch (Throwable throwable) {
                    status = "Messenger reconnecting";
                    LOG.debug("Messenger SSE ended", throwable);
                }
                try {
                    Thread.sleep(backoff + (long) (Math.random() * 1_000L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = Math.min(300_000L, backoff * 2L);
            }
        });
    }

    private void listenStream() throws Exception {
        if (!ensureSessionSync()) throw new IllegalStateException("no chat session");
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                RemoteRulesService.getInstance().getApiBase() + "/api/chat/events"
        ).toURL().openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(40_000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection.getResponseCode() != 200) throw new IllegalStateException("SSE " + connection.getResponseCode());
            status = "Online";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String event = null;
                String data = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if ("message".equals(event) && data != null) {
                            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                            String conversationId = root.get("conversationId").getAsString();
                            if (GLOBAL_CONVERSATION_ID.equals(conversationId)) {
                                acceptGlobalMessage(root.getAsJsonObject("message"));
                            } else {
                                acceptMessage(conversationId, root.getAsJsonObject("message"));
                            }
                        }
                        event = null;
                        data = null;
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        String chunk = line.substring(5).trim();
                        data = data == null ? chunk : data + chunk;
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private void acceptMessage(String conversationId, JsonObject message) {
        if (message == null || !message.has("id")) return;
        CopyOnWriteArrayList<Message> target = messages.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
        long id = message.get("id").getAsLong();
        String text = message.get("text").getAsString();
        long createdAt = message.get("createdAt").getAsLong();
        target.removeIf(existing -> existing.id() == id);
        target.add(new Message(
                id,
                message.get("senderId").getAsString(),
                message.get("senderUsername").isJsonNull() ? "Unknown" : message.get("senderUsername").getAsString(),
                text,
                createdAt
        ));
        while (target.size() > MAX_LOCAL_MESSAGES) target.remove(0);

        boolean conversationKnown = false;
        for (int index = 0; index < conversations.size(); index++) {
            Conversation conversation = conversations.get(index);
            if (conversation.id().equals(conversationId)) {
                conversationKnown = true;
                conversations.set(index, new Conversation(
                        conversation.id(), conversation.participantId(), conversation.username(), text, createdAt));
                break;
            }
        }
        if (!conversationKnown) refreshConversations();
    }

    private void acceptGlobalMessage(JsonObject message) {
        if (message == null || !message.has("id")) return;
        long id = message.get("id").getAsLong();
        globalMessages.removeIf(existing -> existing.id() == id);
        globalMessages.add(new Message(
                id,
                message.get("senderId").getAsString(),
                message.get("senderUsername").isJsonNull() ? "Unknown" : message.get("senderUsername").getAsString(),
                message.get("text").getAsString(),
                message.get("createdAt").getAsLong()
        ));
        while (globalMessages.size() > 100) globalMessages.remove(0);
    }

    private HttpResult request(String method, String path, String body, boolean authenticated) {
        try {
            String base = RemoteRulesService.getInstance().getApiBase();
            HttpURLConnection connection = (HttpURLConnection) URI.create(base + path).toURL().openConnection();
            try {
                connection.setRequestMethod(method);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                if (authenticated && token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                    }
                }
                int statusCode = connection.getResponseCode();
                var input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String response = input == null ? "{}" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
                return new HttpResult(statusCode, response);
            } finally {
                connection.disconnect();
            }
        } catch (Throwable throwable) {
            LOG.debug("Messenger request failed: {} {}", method, path, throwable);
            return new HttpResult(599, "{}");
        }
    }

    private static String currentUsername() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client.getSession() == null ? "Player" : client.getSession().getUsername();
        } catch (Throwable ignored) {
            return "Player";
        }
    }

    private static String usernameOf(JsonObject object) {
        return object != null && object.has("username") && !object.get("username").isJsonNull()
                ? object.get("username").getAsString() : "Unknown";
    }

    private static String parseError(String body, String fallback) {
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            if (object.has("reason")) return "Message blocked: " + object.get("reason").getAsString();
            if (object.has("error")) {
                String error = object.get("error").getAsString();
                return "not_found".equals(error) ? "Chat server is outdated" : error;
            }
            return fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
