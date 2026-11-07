package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Background HTTP/SSE transport for the private messenger.
 *
 * <p>A game launch costs the API three requests: a session, one bootstrap with
 * everything the messenger shows, and one long-lived event stream. Presence,
 * friend changes, requests and blocks then arrive as stream events; nothing is
 * polled. Lists are replaced as immutable snapshots so the render thread never
 * observes a half-rebuilt list.
 */
public final class MessengerService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessenger");
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_LOCAL_MESSAGES = 100;
    private static final String USER_AGENT = "PhazeClient-Messenger/2";

    /** Server error codes for a direct message refused because of a block. */
    public static final String BLOCKED_BY_RECIPIENT = "blocked_by_recipient";
    public static final String RECIPIENT_BLOCKED = "recipient_blocked";

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
    private final Object listLock = new Object();
    private volatile List<Conversation> conversations = List.of();
    private volatile List<Friend> friends = List.of();
    private volatile List<FriendRequest> requests = List.of();
    private volatile List<Friend> blocked = List.of();
    private volatile Set<String> blockedBy = Set.of();
    private final Map<String, CopyOnWriteArrayList<Message>> messages = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presence = new ConcurrentHashMap<>();
    private volatile CopyOnWriteArrayList<Message> globalMessages = new CopyOnWriteArrayList<>();

    private volatile String token;
    private volatile boolean streamConnected;
    private volatile String status = "Offline";
    private volatile String selectedConversation;
    private volatile ChatUser currentUser;

    public static final String GLOBAL_CONVERSATION_ID = "00000000000000000000000000000001";

    private MessengerService() {
    }

    /** Called once from client init: everything runs on daemon threads, game loading never waits. */
    public void start() {
        if (!started.compareAndSet(false, true)) return;
        io.execute(this::ensureSessionAndRefresh);
    }

    public String getStatus() {
        return status;
    }

    /** The local player's own presence: online exactly while the event stream is open. */
    public boolean isConnected() {
        return streamConnected;
    }

    /** Drops a stale send/action error, e.g. when the user switches to another conversation. */
    public void clearError() {
        if (token != null && !token.isBlank()) status = "Online";
    }

    /** Shows a block error the client already knows about without asking the server. */
    public void reportBlocked(String code) {
        status = code;
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    /** Online friends first, each group in the server's alphabetical order. */
    public List<Friend> getFriends() {
        List<Friend> sorted = new ArrayList<>(friends);
        sorted.sort(Comparator.comparing((Friend friend) -> !isUserOnline(friend.id())));
        return Collections.unmodifiableList(sorted);
    }

    public List<FriendRequest> getRequests() {
        return requests;
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
        return blocked;
    }

    /** True when this user blocked the local player; their avatar is then shown anonymised. */
    public boolean isBlockedBy(String userId) {
        return userId != null && blockedBy.contains(userId);
    }

    public ChatUser getCurrentUser() {
        return currentUser;
    }

    public int getOnlineCount() {
        return RemoteRulesService.getInstance().getOnlineCount();
    }

    /** Pure memory read: presence is pushed by the server, never polled. */
    public boolean isUserOnline(String userId) {
        if (userId == null || userId.isBlank()) return false;
        if (currentUser != null && userId.equals(currentUser.id())) return streamConnected;
        return presence.getOrDefault(userId, false);
    }

    public List<Message> getMessages(String conversationId) {
        if (conversationId == null) return List.of();
        CopyOnWriteArrayList<Message> list = messages.get(conversationId);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public List<Message> getGlobalMessages() {
        return Collections.unmodifiableList(globalMessages);
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
                if (findConversation(conversationId) == null) refreshConversations();
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
                removeFriendLocally(userId);
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
            if (success) {
                synchronized (listLock) {
                    blocked = without(blocked, friend -> friend.id().equals(userId));
                }
            }
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
                JsonObject friend = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonObject("friend");
                if (friend != null) addFriendLocally(friendOf(friend), onlineOf(friend));
                synchronized (listLock) {
                    requests = without(requests, request -> request.id() == requestId);
                }
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
            if (success) {
                synchronized (listLock) {
                    requests = without(requests, request -> request.id() == requestId);
                }
            }
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
                removeFriendLocally(userId);
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
        if (!ensureSessionSync()) {
            // Retried by the stream loop, which re-bootstraps once a session exists.
            startStream();
            return;
        }
        if (!bootstrap()) {
            refreshFriends();
            refreshRequests();
            refreshBlocked();
            refreshConversations();
            loadGlobalMessages();
        }
        preloadAvatars();
        startStream();
    }

    /** One request for the whole messenger state; false only when the server predates it. */
    private boolean bootstrap() {
        HttpResult result = request("GET", "/api/chat/bootstrap", null, true);
        if (result.status == 401) token = null;
        if (result.status != 200) return false;
        try {
            JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
            List<Friend> nextFriends = new ArrayList<>();
            Map<String, Boolean> nextPresence = new java.util.HashMap<>();
            for (JsonElement element : root.getAsJsonArray("friends")) {
                JsonObject row = element.getAsJsonObject();
                Friend friend = friendOf(row);
                nextFriends.add(friend);
                nextPresence.put(friend.id(), onlineOf(row));
            }
            List<FriendRequest> nextRequests = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("requests")) {
                FriendRequest request = requestOf(element.getAsJsonObject());
                if (request != null) nextRequests.add(request);
            }
            List<Friend> nextBlocked = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("blocked")) nextBlocked.add(friendOf(element.getAsJsonObject()));
            Set<String> nextBlockedBy = new HashSet<>();
            for (JsonElement element : root.getAsJsonArray("blockedBy")) nextBlockedBy.add(element.getAsString());
            List<Conversation> nextConversations = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("conversations")) {
                nextConversations.add(conversationOf(element.getAsJsonObject()));
            }
            JsonObject history = root.getAsJsonObject("messages");
            for (Map.Entry<String, JsonElement> entry : history.entrySet()) {
                messages.put(entry.getKey(), messageList(entry.getValue().getAsJsonArray()));
            }
            JsonObject global = root.getAsJsonObject("global");
            globalMessages = messageList(global.getAsJsonArray("messages"));

            presence.keySet().retainAll(nextPresence.keySet());
            presence.putAll(nextPresence);
            synchronized (listLock) {
                friends = List.copyOf(nextFriends);
                requests = List.copyOf(nextRequests);
                blocked = List.copyOf(nextBlocked);
                blockedBy = Set.copyOf(nextBlockedBy);
                conversations = List.copyOf(nextConversations);
            }
            status = "Online";
            return true;
        } catch (RuntimeException malformed) {
            LOG.debug("Messenger bootstrap could not be parsed", malformed);
            return false;
        }
    }

    private void preloadAvatars() {
        Set<String> usernames = new HashSet<>();
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
        List<Friend> next = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject row = element.getAsJsonObject();
            Friend friend = friendOf(row);
            next.add(friend);
            presence.put(friend.id(), onlineOf(row));
        }
        synchronized (listLock) {
            friends = List.copyOf(next);
        }
    }

    private void refreshRequests() {
        HttpResult result = request("GET", "/api/chat/friends/requests", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("requests");
        List<FriendRequest> next = new ArrayList<>();
        for (JsonElement element : array) {
            FriendRequest request = requestOf(element.getAsJsonObject());
            if (request != null) next.add(request);
        }
        synchronized (listLock) {
            requests = List.copyOf(next);
        }
    }

    private void refreshBlocked() {
        HttpResult result = request("GET", "/api/chat/friends/blocked", null, true);
        if (result.status != 200) return;
        JsonArray array = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("blocked");
        List<Friend> next = new ArrayList<>();
        for (JsonElement element : array) next.add(friendOf(element.getAsJsonObject()));
        synchronized (listLock) {
            blocked = List.copyOf(next);
        }
    }

    private void loadGlobalMessages() {
        HttpResult result = request("GET", "/api/chat/global/messages?limit=100", null, true);
        if (result.status != 200) return;
        globalMessages = messageList(JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("messages"));
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
            String clientId = RemoteRulesService.getInstance().getClientId();
            String username = chatUsername();
            JsonObject body = new JsonObject();
            body.addProperty("clientId", clientId);
            body.addProperty("username", username);
            ChatIdentity identity = ChatIdentity.get();
            if (identity != null) {
                HttpResult challenge = request("POST", "/api/chat/challenge", "{}", false);
                if (challenge.status == 200) {
                    String value = JsonParser.parseString(challenge.body).getAsJsonObject().get("challenge").getAsString();
                    ChatIdentity.Proof signed = identity.sign(value, clientId, username);
                    JsonObject proof = new JsonObject();
                    proof.addProperty("challenge", value);
                    proof.addProperty("deviceKey", signed.deviceKey());
                    proof.addProperty("deviceSignature", signed.deviceSignature());
                    proof.addProperty("machineKey", signed.machineKey());
                    proof.addProperty("machineSignature", signed.machineSignature());
                    body.add("proof", proof);
                } else if (challenge.status != 404) {
                    // 404 is a server without key logins; anything else is retried by the stream loop.
                    status = parseError(challenge.body, "Session unavailable");
                    return false;
                }
            }
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
        List<Conversation> next = new ArrayList<>();
        for (JsonElement element : array) next.add(conversationOf(element.getAsJsonObject()));
        synchronized (listLock) {
            conversations = List.copyOf(next);
        }
    }

    private void loadMessages(String conversationId) {
        HttpResult result = request("GET", "/api/chat/conversations/" + conversationId + "/messages?limit=100", null, true);
        if (result.status != 200) return;
        messages.put(conversationId, messageList(JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("messages")));
    }

    private void startStream() {
        if (!streamStarted.compareAndSet(false, true)) return;
        streamIo.execute(() -> {
            long backoff = 5_000L;
            boolean resync = false;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (resync && ensureSessionSync()) {
                        // Events missed while disconnected are recovered with one bootstrap.
                        io.execute(() -> {
                            if (bootstrap()) preloadAvatars();
                        });
                    }
                    listenStream();
                    backoff = 5_000L;
                } catch (Throwable throwable) {
                    status = "Messenger reconnecting";
                    LOG.debug("Messenger SSE ended", throwable);
                }
                resync = true;
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
            int code = connection.getResponseCode();
            if (code == 401) token = null;
            if (code != 200) throw new IllegalStateException("SSE " + code);
            status = "Online";
            streamConnected = true;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String event = null;
                StringBuilder data = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (event != null && data != null) handleEvent(event, data.toString());
                        event = null;
                        data = null;
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        String chunk = line.substring(5).trim();
                        if (data == null) data = new StringBuilder(chunk);
                        else data.append(chunk);
                    }
                }
            }
        } finally {
            streamConnected = false;
            connection.disconnect();
        }
    }

    private void handleEvent(String event, String data) {
        try {
            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
            switch (event) {
                case "message" -> {
                    String conversationId = root.get("conversationId").getAsString();
                    if (GLOBAL_CONVERSATION_ID.equals(conversationId)) {
                        acceptGlobalMessage(root.getAsJsonObject("message"));
                    } else {
                        acceptMessage(conversationId, root.getAsJsonObject("message"));
                    }
                }
                case "presence" -> presence.put(root.get("userId").getAsString(), root.get("online").getAsBoolean());
                case "friend" -> {
                    JsonObject user = root.getAsJsonObject("user");
                    String userId = user.get("id").getAsString();
                    if ("added".equals(root.get("action").getAsString())) {
                        addFriendLocally(new Friend(userId, usernameOf(user), System.currentTimeMillis()),
                                root.has("online") && root.get("online").getAsBoolean());
                        SkinAvatarService.getInstance().preload(List.of(usernameOf(user)));
                    } else {
                        removeFriendLocally(userId);
                    }
                }
                case "friend_request" -> io.execute(this::refreshRequests);
                case "block" -> {
                    String userId = root.get("userId").getAsString();
                    boolean nowBlocked = root.get("blocked").getAsBoolean();
                    synchronized (listLock) {
                        Set<String> next = new HashSet<>(blockedBy);
                        if (nowBlocked) next.add(userId);
                        else next.remove(userId);
                        blockedBy = Set.copyOf(next);
                    }
                    if (nowBlocked) removeFriendLocally(userId);
                }
                default -> {
                }
            }
        } catch (RuntimeException malformed) {
            LOG.debug("Ignoring malformed messenger event {}", event, malformed);
        }
    }

    private void addFriendLocally(Friend friend, boolean online) {
        presence.put(friend.id(), online);
        synchronized (listLock) {
            List<Friend> next = new ArrayList<>(without(friends, existing -> existing.id().equals(friend.id())));
            next.add(friend);
            next.sort(Comparator.comparing((Friend item) -> item.username().toLowerCase()));
            friends = List.copyOf(next);
        }
    }

    private void removeFriendLocally(String userId) {
        presence.remove(userId);
        synchronized (listLock) {
            friends = without(friends, friend -> friend.id().equals(userId));
        }
    }

    private void acceptMessage(String conversationId, JsonObject message) {
        if (message == null || !message.has("id")) return;
        CopyOnWriteArrayList<Message> target = messages.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
        Message parsed = messageOf(message);
        target.removeIf(existing -> existing.id() == parsed.id());
        target.add(parsed);
        while (target.size() > MAX_LOCAL_MESSAGES) target.remove(0);

        boolean known;
        synchronized (listLock) {
            known = updateConversation(conversationId, conversation -> new Conversation(
                    conversation.id(), conversation.participantId(), conversation.username(),
                    parsed.text(), parsed.createdAt()));
        }
        if (!known) io.execute(this::refreshConversations);
    }

    /** Applies {@code update} and moves the conversation to the top, as the server orders them. */
    private boolean updateConversation(String conversationId, UnaryOperator<Conversation> update) {
        List<Conversation> next = new ArrayList<>(conversations.size());
        Conversation updated = null;
        for (Conversation conversation : conversations) {
            if (conversation.id().equals(conversationId)) updated = update.apply(conversation);
            else next.add(conversation);
        }
        if (updated == null) return false;
        next.add(0, updated);
        conversations = List.copyOf(next);
        return true;
    }

    private Conversation findConversation(String conversationId) {
        for (Conversation conversation : conversations) {
            if (conversation.id().equals(conversationId)) return conversation;
        }
        return null;
    }

    private void acceptGlobalMessage(JsonObject message) {
        if (message == null || !message.has("id")) return;
        Message parsed = messageOf(message);
        CopyOnWriteArrayList<Message> target = globalMessages;
        target.removeIf(existing -> existing.id() == parsed.id());
        target.add(parsed);
        while (target.size() > MAX_LOCAL_MESSAGES) target.remove(0);
    }

    private static <T> List<T> without(List<T> source, java.util.function.Predicate<T> remove) {
        List<T> next = new ArrayList<>(source.size());
        for (T item : source) if (!remove.test(item)) next.add(item);
        return List.copyOf(next);
    }

    private static CopyOnWriteArrayList<Message> messageList(JsonArray array) {
        List<Message> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject message = element.getAsJsonObject();
            if (message.has("id")) parsed.add(messageOf(message));
        }
        int from = Math.max(0, parsed.size() - MAX_LOCAL_MESSAGES);
        return new CopyOnWriteArrayList<>(parsed.subList(from, parsed.size()));
    }

    private static Message messageOf(JsonObject message) {
        return new Message(
                message.get("id").getAsLong(),
                message.get("senderId").getAsString(),
                message.get("senderUsername").isJsonNull() ? "Unknown" : message.get("senderUsername").getAsString(),
                message.get("text").getAsString(),
                message.get("createdAt").getAsLong()
        );
    }

    private static Friend friendOf(JsonObject row) {
        return new Friend(row.get("id").getAsString(), usernameOf(row),
                row.has("createdAt") ? row.get("createdAt").getAsLong() : 0L);
    }

    private static boolean onlineOf(JsonObject row) {
        return row.has("online") && !row.get("online").isJsonNull() && row.get("online").getAsBoolean();
    }

    private static FriendRequest requestOf(JsonObject row) {
        if (!"pending".equals(row.get("status").getAsString())) return null;
        JsonObject requester = row.getAsJsonObject("requester");
        JsonObject target = row.getAsJsonObject("target");
        return new FriendRequest(
                row.get("id").getAsLong(),
                requester.get("id").getAsString(), usernameOf(requester),
                target.get("id").getAsString(), usernameOf(target),
                row.get("status").getAsString(), row.get("createdAt").getAsLong()
        );
    }

    private static Conversation conversationOf(JsonObject conversation) {
        JsonObject participant = conversation.getAsJsonObject("participant");
        JsonObject last = conversation.get("lastMessage").isJsonNull()
                ? null : conversation.getAsJsonObject("lastMessage");
        return new Conversation(
                conversation.get("id").getAsString(),
                participant.get("id").getAsString(),
                usernameOf(participant),
                last == null ? "" : last.get("text").getAsString(),
                last == null ? 0L : last.get("createdAt").getAsLong()
        );
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

    /** The nickname as the server normalizes it; the login signature covers this exact value. */
    private static String chatUsername() {
        String name = currentUsername();
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.matches("[A-Za-z0-9_]{1,16}") ? trimmed : null;
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
