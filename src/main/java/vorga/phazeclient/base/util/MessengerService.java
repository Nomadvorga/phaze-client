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
import java.util.Collection;
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
 * friend changes, requests, groups and blocks then arrive as stream events;
 * nothing is polled except the teammate list while the player is on a server.
 * Lists are replaced as immutable snapshots so the render thread never observes
 * a half-rebuilt list.
 */
public final class MessengerService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessenger");
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_LOCAL_MESSAGES = 100;
    private static final String USER_AGENT = "PhazeClient-Messenger/3";

    /** Server error codes for a direct message refused because of a block. */
    public static final String BLOCKED_BY_RECIPIENT = "blocked_by_recipient";
    public static final String RECIPIENT_BLOCKED = "recipient_blocked";
    /** Status prefix for a message the server's moderation refused; the rest is the reason code. */
    public static final String MESSAGE_BLOCKED = "Message blocked: ";
    public static final String FRIEND_REQUEST_SELF = "friend_request_self";

    private static final class Holder {
        private static final MessengerService INSTANCE = new MessengerService();
    }

    public static MessengerService getInstance() {
        return Holder.INSTANCE;
    }

    /** The admin panel's sender; it has no profile, friends or presence. */
    public static final String ADMIN_USER_ID = "000000000000000000000000000000ad";

    public static boolean isAdmin(String userId) {
        return ADMIN_USER_ID.equals(userId);
    }

    public record Conversation(String id, String participantId, String username,
                               String lastText, long lastCreatedAt, int unreadCount) {}

    public record Friend(String id, String username, long createdAt) {}

    public record FriendRequest(long id, String requesterId, String requesterUsername,
                                String targetId, String targetUsername, String status,
                                long createdAt) {}

    public record ChatUser(String id, String username) {}

    /** A known server a friend chose to share; {@code address} is what "join" connects to. */
    public record ServerInfo(String name, String address) {}

    /** A world point or a shared config; {@code configId} is set only for configs. */
    public record Attachment(String type, String serverKey, String dimension, int x, int y, int z, String configId) {
        public boolean isPoint() {
            return "waypoint".equals(type) || "death".equals(type);
        }
    }

    public record Message(long id, long sequence, String senderId, String senderUsername,
                          String text, Attachment attachment, long createdAt) {}

    public record GroupMember(String id, String username, String role) {
        public boolean isOwner() {
            return "owner".equals(role);
        }

        public boolean isAdmin() {
            return "admin".equals(role);
        }
    }

    public record Group(String id, String name, String ownerId, List<GroupMember> members,
                        String lastSender, String lastText, long lastCreatedAt, int unreadCount) {
        public GroupMember member(String userId) {
            for (GroupMember member : members) if (member.id().equals(userId)) return member;
            return null;
        }
    }

    public record GroupInvite(long id, String groupId, String groupName, String inviterId, String inviterUsername) {}

    /** A group mate's marker in the world; shown only on the same server and dimension. {@code label} is Admin's caption. */
    public record Ping(String userId, String username, String label, String serverKey, String dimension,
                       int x, int y, int z, long expiresAt) {}

    public enum NoticeKind { MESSAGE, FRIEND_ONLINE, FRIEND_REQUEST, GROUP_INVITE, PING }

    /** Something worth an in-game toast; the toast layer decides whether to show it. {@code ping} is set for PING. */
    public record Notice(NoticeKind kind, String username, String text, String conversationId, Ping ping) {}

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
    private final AtomicBoolean teammatesInFlight = new AtomicBoolean(false);
    private final Object listLock = new Object();
    private volatile List<Conversation> conversations = List.of();
    private volatile List<Friend> friends = List.of();
    private volatile List<FriendRequest> requests = List.of();
    private volatile List<FriendRequest> outgoing = List.of();
    private volatile List<Friend> blocked = List.of();
    private volatile Set<String> blockedBy = Set.of();
    private volatile List<Group> groups = List.of();
    private volatile List<GroupInvite> groupInvites = List.of();
    private volatile Set<String> teammates = Set.of();
    private final Map<String, CopyOnWriteArrayList<Message>> messages = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presence = new ConcurrentHashMap<>();
    private final Map<String, ServerInfo> servers = new ConcurrentHashMap<>();
    /** Highest sequence each conversation was reported read up to, so a read is sent once. */
    private final Map<String, Long> readSent = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Ping> pings = new CopyOnWriteArrayList<>();
    /** Users this launch already asked; a repeat click is answered locally instead of by the server. */
    private final Set<String> sentFriendRequests = ConcurrentHashMap.newKeySet();
    private final Set<String> friendRequestsInFlight = ConcurrentHashMap.newKeySet();
    private volatile CopyOnWriteArrayList<Message> globalMessages = new CopyOnWriteArrayList<>();

    private volatile String token;
    private volatile boolean streamConnected;
    private volatile boolean shareServer;
    private volatile String status = "Offline";
    private volatile long statusChangedAt;
    private volatile String selectedConversation;
    private volatile ChatUser currentUser;
    private volatile Consumer<Notice> noticeListener;

    public static final String GLOBAL_CONVERSATION_ID = "00000000000000000000000000000001";
    private static final long PING_LIFETIME_MS = 10_000L;

    private MessengerService() {
    }

    /** Called once from client init: everything runs on daemon threads, game loading never waits. */
    public void start() {
        if (!started.compareAndSet(false, true)) return;
        io.execute(this::ensureSessionAndRefresh);
    }

    public void setNoticeListener(Consumer<Notice> listener) {
        noticeListener = listener;
    }

    private void notice(NoticeKind kind, String username, String text, String conversationId) {
        notice(kind, username, text, conversationId, null);
    }

    private void notice(NoticeKind kind, String username, String text, String conversationId, Ping ping) {
        Consumer<Notice> listener = noticeListener;
        if (listener == null) return;
        try {
            listener.accept(new Notice(kind, username, text, conversationId, ping));
        } catch (RuntimeException failure) {
            LOG.debug("Messenger notice listener failed", failure);
        }
    }

    public String getStatus() {
        return status;
    }

    /** When {@link #getStatus()} was last written; a repeated identical error refreshes it. */
    public long getStatusChangedAt() {
        return statusChangedAt;
    }

    private void setStatus(String next) {
        status = next;
        statusChangedAt = System.currentTimeMillis();
    }

    /** The local player's own presence: online exactly while the event stream is open. */
    public boolean isConnected() {
        return streamConnected;
    }

    /** Drops a stale send/action error, e.g. when the user switches to another conversation. */
    public void clearError() {
        if (token != null && !token.isBlank()) setStatus("Online");
    }

    /** Shows a block error the client already knows about without asking the server. */
    public void reportBlocked(String code) {
        setStatus(code);
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

    /** Requests the local player sent that nobody answered yet. */
    public List<FriendRequest> getOutgoingRequests() {
        return outgoing;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public Group getGroup(String groupId) {
        if (groupId == null) return null;
        for (Group group : groups) if (group.id().equals(groupId)) return group;
        return null;
    }

    public List<GroupInvite> getGroupInvites() {
        return groupInvites;
    }

    public int getDirectUnread() {
        int total = 0;
        for (Conversation conversation : conversations) total += conversation.unreadCount();
        return total;
    }

    public int getGroupUnread() {
        int total = 0;
        for (Group group : groups) total += group.unreadCount();
        return total;
    }

    /** Refresh incoming and outgoing friend requests without blocking the render thread. */
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

    public boolean isBlocked(String userId) {
        if (userId == null) return false;
        for (Friend user : blocked) if (user.id().equals(userId)) return true;
        return false;
    }

    public boolean isFriend(String userId) {
        if (userId == null) return false;
        for (Friend friend : friends) if (friend.id().equals(userId)) return true;
        return false;
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

    /** The known server a friend or group mate shares, while they are online. */
    public ServerInfo getServer(String userId) {
        if (userId == null || !isUserOnline(userId)) return null;
        return servers.get(userId);
    }

    public boolean isSharingServer() {
        return shareServer;
    }

    public List<Message> getMessages(String conversationId) {
        if (conversationId == null) return List.of();
        CopyOnWriteArrayList<Message> list = messages.get(conversationId);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public List<Message> getGlobalMessages() {
        return Collections.unmodifiableList(globalMessages);
    }

    /** Lowercase nicknames of group mates on the same server, refreshed by {@link #refreshTeammatesAsync}. */
    public Set<String> getTeammates() {
        return teammates;
    }

    /** Pings younger than their lifetime; older ones are dropped here. */
    public List<Ping> getActivePings() {
        long now = System.currentTimeMillis();
        pings.removeIf(ping -> now > ping.expiresAt());
        return pings;
    }

    public void sendGlobal(String text) {
        sendGlobal(text, null);
    }

    public void sendGlobal(String text, Consumer<Boolean> completion) {
        if (text == null || text.isBlank()) return;
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
                setStatus("Online");
                if (completion != null) completion.accept(true);
            } else {
                setStatus(parseError(result.body, "Send failed"));
                if (completion != null) completion.accept(false);
            }
        });
    }

    public String getSelectedConversation() {
        return selectedConversation;
    }

    /** The conversation on screen; its messages count as read while it stays selected. */
    public void selectConversation(String conversationId) {
        selectedConversation = conversationId;
        if (conversationId == null) return;
        if (!messages.containsKey(conversationId)) {
            io.execute(() -> {
                loadMessages(conversationId);
                markRead(conversationId);
            });
        } else {
            markRead(conversationId);
        }
    }

    /**
     * Clears the conversation's badge now and tells the server once per new
     * last message; repeated calls for an already-read conversation cost nothing.
     */
    public void markRead(String conversationId) {
        if (conversationId == null || GLOBAL_CONVERSATION_ID.equals(conversationId)) return;
        List<Message> list = getMessages(conversationId);
        long last = list.isEmpty() ? 0L : list.get(list.size() - 1).sequence();
        clearUnreadLocally(conversationId);
        if (last <= 0L || readSent.getOrDefault(conversationId, 0L) >= last) return;
        readSent.put(conversationId, last);
        io.execute(() -> {
            if (!ensureSessionSync()) return;
            JsonObject body = new JsonObject();
            body.addProperty("sequence", last);
            request("POST", "/api/chat/conversations/" + conversationId + "/read", body.toString(), true);
        });
    }

    private void clearUnreadLocally(String conversationId) {
        synchronized (listLock) {
            replaceConversation(conversationId, conversation -> conversation.unreadCount() == 0 ? conversation
                    : new Conversation(conversation.id(), conversation.participantId(), conversation.username(),
                    conversation.lastText(), conversation.lastCreatedAt(), 0));
            replaceGroup(conversationId, group -> group.unreadCount() == 0 ? group
                    : withUnread(group, 0));
        }
    }

    public void send(String conversationId, String text) {
        send(conversationId, text, null);
    }

    public void send(String conversationId, String text, Consumer<Boolean> completion) {
        sendWithAttachment(conversationId, text, null, completion);
    }

    /** {@code attachment} is the validated card body; the server re-checks every field. */
    public void sendWithAttachment(String conversationId, String text, JsonObject attachment, Consumer<Boolean> completion) {
        if (conversationId == null || text == null || text.isBlank()) return;
        String trimmed = text.trim();
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("clientMessageId", UUID.randomUUID().toString());
            body.addProperty("text", trimmed);
            if (attachment != null) body.add("attachment", attachment);
            HttpResult result = request("POST", "/api/chat/conversations/"
                    + conversationId + "/messages", body.toString(), true);
            if (result.status == 201 || result.status == 200) {
                acceptMessage(conversationId, JsonParser.parseString(result.body).getAsJsonObject().getAsJsonObject("message"));
                setStatus("Online");
                if (completion != null) completion.accept(true);
            } else if (result.status == 401) {
                token = null;
                setStatus("Session expired");
                if (completion != null) completion.accept(false);
            } else {
                setStatus(parseError(result.body, "Send failed"));
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
                setStatus(parseError(lookup.body, "Player not found"));
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject user = JsonParser.parseString(lookup.body).getAsJsonObject().getAsJsonObject("user");
            boolean success = requestFriendSync(user.get("id").getAsString());
            if (success && !isFriend(user.get("id").getAsString())) refreshRequests();
            if (completion != null) completion.accept(success);
        });
    }

    /**
     * Sends a friend request by chat user id. The server stores it, so a player who
     * is offline finds it in their requests the next time the mod starts. If that
     * player already asked us, their request is accepted instead of crossing it.
     */
    public void sendFriendRequest(String userId, Consumer<Boolean> completion) {
        if (userId == null || userId.isBlank() || !friendRequestsInFlight.add(userId)) return;
        io.execute(() -> {
            try {
                boolean success = ensureSessionSync() && requestFriendSync(userId);
                if (completion != null) completion.accept(success);
            } finally {
                friendRequestsInFlight.remove(userId);
            }
        });
    }

    /** True once this launch knows a request to {@code userId} is waiting for an answer. */
    public boolean hasSentFriendRequest(String userId) {
        if (userId == null) return false;
        if (sentFriendRequests.contains(userId)) return true;
        for (FriendRequest request : outgoing) if (request.targetId().equals(userId)) return true;
        return false;
    }

    /** True when {@code userId} is the local player, as far as this launch already knows. */
    public boolean isSelf(String userId) {
        ChatUser me = currentUser;
        return userId != null && me != null && userId.equals(me.id());
    }

    private boolean requestFriendSync(String userId) {
        if (isSelf(userId)) {
            setStatus(FRIEND_REQUEST_SELF);
            return false;
        }
        for (FriendRequest incoming : requests) {
            if (incoming.requesterId().equals(userId)) return acceptFriendRequestSync(incoming.id());
        }
        if (sentFriendRequests.contains(userId)) {
            setStatus("Friend request pending");
            return true;
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetUserId", userId);
        HttpResult result = request("POST", "/api/chat/friends/requests", body.toString(), true);
        if (result.status == 401) {
            token = null;
            if (!ensureSessionSync()) return false;
            result = request("POST", "/api/chat/friends/requests", body.toString(), true);
        }
        if (result.status != 201 && result.status != 200) {
            setStatus(parseError(result.body, "Friend request failed"));
            return false;
        }
        String outcome = stringField(result.body, "status");
        if ("already_friends".equals(outcome)) {
            refreshFriends();
            setStatus("Friend added");
        } else {
            sentFriendRequests.add(userId);
            setStatus("pending".equals(outcome) ? "Friend request pending" : "Friend request sent");
        }
        return true;
    }

    /** Withdraws a request the local player sent. */
    public void cancelFriendRequest(long requestId, Consumer<Boolean> completion) {
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("requestId", requestId);
            HttpResult result = request("POST", "/api/chat/friends/cancel", body.toString(), true);
            boolean success = result.status == 200;
            if (success) removeRequestLocally(requestId);
            else setStatus(parseError(result.body, "Request failed"));
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
                setStatus(parseError(result.body, "Conversation unavailable"));
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
                setStatus("User blocked");
                removeFriendLocally(userId);
                refreshBlocked();
            } else {
                setStatus(parseError(result.body, "Block failed"));
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
            setStatus(success ? "User unblocked" : parseError(result.body, "Unblock failed"));
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
            if (ensureSessionSync()) acceptFriendRequestSync(requestId);
        });
    }

    private boolean acceptFriendRequestSync(long requestId) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        HttpResult result = request("POST", "/api/chat/friends/accept", body.toString(), true);
        if (result.status != 200) {
            setStatus(parseError(result.body, "Request failed"));
            return false;
        }
        setStatus("Friend added");
        JsonObject friend = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonObject("friend");
        if (friend != null) addFriendLocally(friendOf(friend), onlineOf(friend));
        removeRequestLocally(requestId);
        return true;
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
            setStatus(success ? "Friend request declined" : parseError(result.body, "Request failed"));
            if (success) removeRequestLocally(requestId);
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
            setStatus(success ? "Friend removed" : parseError(result.body, "Remove friend failed"));
            if (success) {
                removeFriendLocally(userId);
                refreshConversations();
            }
            if (completion != null) completion.accept(success);
        });
    }

    /* ---- groups ------------------------------------------------------ */

    /** Creates a group and invites {@code inviteIds}; completes with the new group id or null. */
    public void createGroup(String name, Collection<String> inviteIds, Consumer<String> completion) {
        io.execute(() -> {
            if (!ensureSessionSync()) {
                completion.accept(null);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("name", name == null ? "" : name.trim());
            JsonArray invites = new JsonArray();
            for (String id : inviteIds) invites.add(id);
            body.add("inviteUserIds", invites);
            HttpResult result = request("POST", "/api/chat/groups", body.toString(), true);
            if (result.status != 201) {
                setStatus(parseError(result.body, "Group failed"));
                completion.accept(null);
                return;
            }
            JsonObject group = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonObject("group");
            Group parsed = group == null ? null : groupOf(group, 0, null);
            if (parsed != null) upsertGroup(parsed);
            setStatus("Online");
            completion.accept(parsed == null ? null : parsed.id());
        });
    }

    public void renameGroup(String groupId, String name, Consumer<Boolean> completion) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name == null ? "" : name.trim());
        groupAction(groupId, "rename", body, completion);
    }

    public void inviteToGroup(String groupId, String userId, Consumer<Boolean> completion) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUserId", userId);
        groupAction(groupId, "invite", body, completion);
    }

    public void kickFromGroup(String groupId, String userId, Consumer<Boolean> completion) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUserId", userId);
        groupAction(groupId, "kick", body, completion);
    }

    public void setGroupRole(String groupId, String userId, String role, Consumer<Boolean> completion) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUserId", userId);
        body.addProperty("role", role);
        groupAction(groupId, "role", body, completion);
    }

    public void leaveGroup(String groupId, Consumer<Boolean> completion) {
        groupAction(groupId, "leave", new JsonObject(), success -> {
            if (success) removeGroupLocally(groupId);
            if (completion != null) completion.accept(success);
        });
    }

    public void deleteGroup(String groupId, Consumer<Boolean> completion) {
        groupAction(groupId, "delete", new JsonObject(), success -> {
            if (success) removeGroupLocally(groupId);
            if (completion != null) completion.accept(success);
        });
    }

    private void groupAction(String groupId, String action, JsonObject body, Consumer<Boolean> completion) {
        if (groupId == null || !groupId.matches("[0-9a-f]{32}")) {
            if (completion != null) completion.accept(false);
            return;
        }
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            HttpResult result = request("POST", "/api/chat/groups/" + groupId + "/" + action, body.toString(), true);
            boolean success = result.status == 200 || result.status == 201;
            setStatus(success ? "Online" : parseError(result.body, "Group failed"));
            if (completion != null) completion.accept(success);
        });
    }

    public void acceptGroupInvite(long inviteId, Consumer<String> completion) {
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(null);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("inviteId", inviteId);
            HttpResult result = request("POST", "/api/chat/groups/invites/accept", body.toString(), true);
            removeInviteLocally(inviteId);
            if (result.status != 200) {
                setStatus(parseError(result.body, "Group failed"));
                if (completion != null) completion.accept(null);
                return;
            }
            JsonObject group = JsonParser.parseString(result.body).getAsJsonObject().getAsJsonObject("group");
            Group parsed = group == null ? null : groupOf(group, 0, null);
            if (parsed != null) {
                upsertGroup(parsed);
                loadMessages(parsed.id());
            }
            setStatus("Online");
            if (completion != null) completion.accept(parsed == null ? null : parsed.id());
        });
    }

    public void declineGroupInvite(long inviteId, Consumer<Boolean> completion) {
        io.execute(() -> {
            if (!ensureSessionSync()) {
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("inviteId", inviteId);
            HttpResult result = request("POST", "/api/chat/groups/invites/decline", body.toString(), true);
            removeInviteLocally(inviteId);
            boolean success = result.status == 200;
            if (!success) setStatus(parseError(result.body, "Group failed"));
            if (completion != null) completion.accept(success);
        });
    }

    /* ---- privacy, teammates and pings -------------------------------- */

    public void setShareServer(boolean share, Consumer<Boolean> completion) {
        boolean previous = shareServer;
        shareServer = share;
        io.execute(() -> {
            if (!ensureSessionSync()) {
                shareServer = previous;
                if (completion != null) completion.accept(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("shareServer", share);
            HttpResult result = request("POST", "/api/chat/me/privacy", body.toString(), true);
            boolean success = result.status == 200;
            if (!success) shareServer = previous;
            if (completion != null) completion.accept(success);
        });
    }

    /** One request; skipped while the previous answer is still in flight. */
    public void refreshTeammatesAsync() {
        if (groups.isEmpty()) {
            teammates = Set.of();
            return;
        }
        if (!teammatesInFlight.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                if (!ensureSessionSync()) return;
                HttpResult result = request("GET", "/api/chat/teammates", null, true);
                if (result.status != 200) return;
                Set<String> next = new HashSet<>();
                for (JsonElement element : JsonParser.parseString(result.body).getAsJsonObject().getAsJsonArray("usernames")) {
                    next.add(element.getAsString().toLowerCase(java.util.Locale.ROOT));
                }
                teammates = Set.copyOf(next);
            } catch (RuntimeException malformed) {
                LOG.debug("Teammates response could not be parsed", malformed);
            } finally {
                teammatesInFlight.set(false);
            }
        });
    }

    public void clearTeammates() {
        teammates = Set.of();
    }

    public void sendPing(String serverKey, String dimension, int x, int y, int z) {
        if (groups.isEmpty()) return;
        io.execute(() -> {
            if (!ensureSessionSync()) return;
            JsonObject body = new JsonObject();
            body.addProperty("serverKey", serverKey);
            body.addProperty("dimension", dimension);
            body.addProperty("x", x);
            body.addProperty("y", y);
            body.addProperty("z", z);
            request("POST", "/api/chat/ping", body.toString(), true);
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
            Map<String, ServerInfo> nextServers = new java.util.HashMap<>();
            for (JsonElement element : root.getAsJsonArray("friends")) {
                JsonObject row = element.getAsJsonObject();
                Friend friend = friendOf(row);
                nextFriends.add(friend);
                nextPresence.put(friend.id(), onlineOf(row));
                ServerInfo server = serverOf(row);
                if (server != null) nextServers.put(friend.id(), server);
            }
            List<FriendRequest> nextRequests = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("requests")) {
                FriendRequest request = requestOf(element.getAsJsonObject());
                if (request != null) nextRequests.add(request);
            }
            List<FriendRequest> nextOutgoing = new ArrayList<>();
            if (root.has("outgoing")) {
                for (JsonElement element : root.getAsJsonArray("outgoing")) {
                    FriendRequest request = requestOf(element.getAsJsonObject());
                    if (request != null) nextOutgoing.add(request);
                }
            }
            List<Friend> nextBlocked = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("blocked")) nextBlocked.add(friendOf(element.getAsJsonObject()));
            Set<String> nextBlockedBy = new HashSet<>();
            for (JsonElement element : root.getAsJsonArray("blockedBy")) nextBlockedBy.add(element.getAsString());
            List<Conversation> nextConversations = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("conversations")) {
                nextConversations.add(conversationOf(element.getAsJsonObject()));
            }
            List<Group> nextGroups = new ArrayList<>();
            if (root.has("groups")) {
                for (JsonElement element : root.getAsJsonArray("groups")) {
                    JsonObject row = element.getAsJsonObject();
                    nextGroups.add(groupOf(row, intOf(row, "unreadCount"), row.get("lastMessage")));
                    collectMemberPresence(row, nextPresence, nextServers);
                }
            }
            List<GroupInvite> nextInvites = new ArrayList<>();
            if (root.has("groupInvites")) {
                for (JsonElement element : root.getAsJsonArray("groupInvites")) nextInvites.add(inviteOf(element.getAsJsonObject()));
            }
            if (root.has("privacy") && root.get("privacy").isJsonObject()) {
                JsonObject privacy = root.getAsJsonObject("privacy");
                shareServer = privacy.has("shareServer") && privacy.get("shareServer").getAsBoolean();
            }
            JsonObject history = root.getAsJsonObject("messages");
            for (Map.Entry<String, JsonElement> entry : history.entrySet()) {
                messages.put(entry.getKey(), messageList(entry.getValue().getAsJsonArray()));
            }
            JsonObject global = root.getAsJsonObject("global");
            globalMessages = messageList(global.getAsJsonArray("messages"));

            presence.keySet().retainAll(nextPresence.keySet());
            presence.putAll(nextPresence);
            servers.clear();
            servers.putAll(nextServers);
            synchronized (listLock) {
                friends = List.copyOf(nextFriends);
                requests = List.copyOf(nextRequests);
                outgoing = List.copyOf(nextOutgoing);
                blocked = List.copyOf(nextBlocked);
                blockedBy = Set.copyOf(nextBlockedBy);
                conversations = List.copyOf(nextConversations);
                groups = List.copyOf(nextGroups);
                groupInvites = List.copyOf(nextInvites);
            }
            setStatus("Online");
            String selected = selectedConversation;
            if (selected != null) markRead(selected);
            return true;
        } catch (RuntimeException malformed) {
            LOG.debug("Messenger bootstrap could not be parsed", malformed);
            return false;
        }
    }

    private void collectMemberPresence(JsonObject group, Map<String, Boolean> nextPresence, Map<String, ServerInfo> nextServers) {
        if (!group.has("members")) return;
        for (JsonElement element : group.getAsJsonArray("members")) {
            JsonObject member = element.getAsJsonObject();
            String id = member.get("id").getAsString();
            if (isSelf(id)) continue;
            if (member.has("online")) nextPresence.put(id, onlineOf(member));
            ServerInfo server = serverOf(member);
            if (server != null) nextServers.put(id, server);
        }
    }

    private void preloadAvatars() {
        Set<String> usernames = new HashSet<>();
        if (currentUser != null) usernames.add(currentUser.username());
        friends.forEach(friend -> usernames.add(friend.username()));
        blocked.forEach(friend -> usernames.add(friend.username()));
        conversations.forEach(conversation -> usernames.add(conversation.username()));
        groups.forEach(group -> group.members().forEach(member -> usernames.add(member.username())));
        requests.forEach(request -> usernames.add(request.requesterUsername()));
        outgoing.forEach(request -> usernames.add(request.targetUsername()));
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
            ServerInfo server = serverOf(row);
            if (server != null) servers.put(friend.id(), server);
            else servers.remove(friend.id());
        }
        synchronized (listLock) {
            friends = List.copyOf(next);
        }
    }

    private void refreshRequests() {
        HttpResult result = request("GET", "/api/chat/friends/requests", null, true);
        if (result.status != 200) return;
        JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
        List<FriendRequest> next = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("requests")) {
            FriendRequest request = requestOf(element.getAsJsonObject());
            if (request != null) next.add(request);
        }
        List<FriendRequest> nextOutgoing = new ArrayList<>();
        if (root.has("outgoing")) {
            for (JsonElement element : root.getAsJsonArray("outgoing")) {
                FriendRequest request = requestOf(element.getAsJsonObject());
                if (request != null) nextOutgoing.add(request);
            }
        }
        synchronized (listLock) {
            requests = List.copyOf(next);
            if (root.has("outgoing")) outgoing = List.copyOf(nextOutgoing);
        }
        List<String> names = new ArrayList<>();
        next.forEach(request -> names.add(request.requesterUsername()));
        nextOutgoing.forEach(request -> names.add(request.targetUsername()));
        SkinAvatarService.getInstance().preload(names);
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

    private void refreshGroups() {
        HttpResult result = request("GET", "/api/chat/groups", null, true);
        if (result.status != 200) return;
        JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
        List<Group> next = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("groups")) {
            JsonObject row = element.getAsJsonObject();
            next.add(groupOf(row, intOf(row, "unreadCount"), row.get("lastMessage")));
            Map<String, Boolean> memberPresence = new java.util.HashMap<>();
            Map<String, ServerInfo> memberServers = new java.util.HashMap<>();
            collectMemberPresence(row, memberPresence, memberServers);
            presence.putAll(memberPresence);
            servers.putAll(memberServers);
        }
        List<GroupInvite> nextInvites = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("invites")) nextInvites.add(inviteOf(element.getAsJsonObject()));
        synchronized (listLock) {
            groups = List.copyOf(next);
            groupInvites = List.copyOf(nextInvites);
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
                setStatus("Messenger disabled");
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
                    setStatus(parseError(challenge.body, "Session unavailable"));
                    return false;
                }
            }
            HttpResult result = request("POST", "/api/chat/session", body.toString(), false);
            if (result.status != 201) {
                setStatus(parseError(result.body, "Session unavailable"));
                return false;
            }
            JsonObject root = JsonParser.parseString(result.body).getAsJsonObject();
            token = root.get("token").getAsString();
            JsonObject user = root.getAsJsonObject("user");
            currentUser = new ChatUser(user.get("id").getAsString(), usernameOf(user));
            setStatus("Online");
            return true;
        } catch (Throwable throwable) {
            setStatus("Network unavailable");
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
            setStatus(parseError(result.body, "Conversations unavailable"));
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
                    setStatus("Messenger reconnecting");
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
            setStatus("Online");
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
                case "presence" -> {
                    String userId = root.get("userId").getAsString();
                    boolean online = root.get("online").getAsBoolean();
                    boolean wasOnline = presence.getOrDefault(userId, false);
                    presence.put(userId, online);
                    ServerInfo server = serverOf(root);
                    if (server != null) servers.put(userId, server);
                    else servers.remove(userId);
                    if (online && !wasOnline && isFriend(userId)) {
                        notice(NoticeKind.FRIEND_ONLINE, usernameFor(userId), null, null);
                    }
                }
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
                case "friend_request" -> {
                    if (root.has("removedId")) {
                        removeRequestLocally(root.get("removedId").getAsLong());
                        return;
                    }
                    FriendRequest pushed = root.has("request") && root.get("request").isJsonObject()
                            ? requestOf(root.getAsJsonObject("request")) : null;
                    if (pushed == null) {
                        io.execute(this::refreshRequests);
                    } else {
                        synchronized (listLock) {
                            List<FriendRequest> next = new ArrayList<>(without(requests, request -> request.id() == pushed.id()));
                            next.add(0, pushed);
                            requests = List.copyOf(next);
                        }
                        SkinAvatarService.getInstance().preload(List.of(pushed.requesterUsername()));
                        notice(NoticeKind.FRIEND_REQUEST, pushed.requesterUsername(), null, null);
                    }
                }
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
                case "read" -> clearUnreadLocally(root.get("conversationId").getAsString());
                case "group" -> {
                    if ("removed".equals(root.get("action").getAsString())) {
                        removeGroupLocally(root.get("groupId").getAsString());
                        return;
                    }
                    JsonObject group = root.getAsJsonObject("group");
                    Group existing = getGroup(group.get("id").getAsString());
                    Group parsed = groupOf(group, existing == null ? 0 : existing.unreadCount(), null);
                    if (existing != null) {
                        parsed = new Group(parsed.id(), parsed.name(), parsed.ownerId(), parsed.members(),
                                existing.lastSender(), existing.lastText(), existing.lastCreatedAt(), existing.unreadCount());
                    }
                    Map<String, Boolean> memberPresence = new java.util.HashMap<>();
                    Map<String, ServerInfo> memberServers = new java.util.HashMap<>();
                    collectMemberPresence(group, memberPresence, memberServers);
                    presence.putAll(memberPresence);
                    servers.putAll(memberServers);
                    upsertGroup(parsed);
                    List<String> names = new ArrayList<>();
                    parsed.members().forEach(member -> names.add(member.username()));
                    SkinAvatarService.getInstance().preload(names);
                }
                case "group_invite" -> {
                    if (root.has("removedId")) {
                        removeInviteLocally(root.get("removedId").getAsLong());
                    } else if (root.has("invite") && root.get("invite").isJsonObject()) {
                        GroupInvite invite = inviteOf(root.getAsJsonObject("invite"));
                        synchronized (listLock) {
                            List<GroupInvite> next = new ArrayList<>(without(groupInvites, existing -> existing.id() == invite.id()));
                            next.add(0, invite);
                            groupInvites = List.copyOf(next);
                        }
                        notice(NoticeKind.GROUP_INVITE, invite.inviterUsername(), invite.groupName(), invite.groupId());
                    } else {
                        io.execute(this::refreshGroups);
                    }
                }
                case "ping" -> {
                    String userId = root.get("userId").getAsString();
                    // Only Admin's pings carry a caption and their own lifetime.
                    boolean admin = isAdmin(userId);
                    long lifetime = admin && root.has("durationSec")
                            ? Math.max(5, Math.min(600, root.get("durationSec").getAsInt())) * 1_000L : PING_LIFETIME_MS;
                    String label = admin && root.has("label") && !root.get("label").isJsonNull()
                            ? root.get("label").getAsString() : null;
                    Ping ping = new Ping(userId, usernameOf(root), label,
                            root.get("serverKey").getAsString(), root.get("dimension").getAsString(),
                            root.get("x").getAsInt(), root.get("y").getAsInt(), root.get("z").getAsInt(),
                            System.currentTimeMillis() + lifetime);
                    pings.removeIf(existing -> existing.userId().equals(userId));
                    pings.add(ping);
                    while (pings.size() > 16) pings.remove(0);
                    if (admin) notice(NoticeKind.PING, ping.username(), label, null, ping);
                }
                default -> {
                }
            }
        } catch (RuntimeException malformed) {
            LOG.debug("Ignoring malformed messenger event {}", event, malformed);
        }
    }

    private String usernameFor(String userId) {
        for (Friend friend : friends) if (friend.id().equals(userId)) return friend.username();
        for (Group group : groups) {
            GroupMember member = group.member(userId);
            if (member != null) return member.username();
        }
        return "Unknown";
    }

    private void addFriendLocally(Friend friend, boolean online) {
        sentFriendRequests.remove(friend.id());
        presence.put(friend.id(), online);
        synchronized (listLock) {
            List<Friend> next = new ArrayList<>(without(friends, existing -> existing.id().equals(friend.id())));
            next.add(friend);
            next.sort(Comparator.comparing((Friend item) -> item.username().toLowerCase()));
            friends = List.copyOf(next);
            outgoing = without(outgoing, request -> request.targetId().equals(friend.id()));
            requests = without(requests, request -> request.requesterId().equals(friend.id()));
        }
    }

    private void removeFriendLocally(String userId) {
        if (!isInAnyGroup(userId)) {
            presence.remove(userId);
            servers.remove(userId);
        }
        synchronized (listLock) {
            friends = without(friends, friend -> friend.id().equals(userId));
        }
    }

    private boolean isInAnyGroup(String userId) {
        for (Group group : groups) if (group.member(userId) != null) return true;
        return false;
    }

    private void removeRequestLocally(long requestId) {
        synchronized (listLock) {
            for (FriendRequest request : outgoing) {
                if (request.id() == requestId) sentFriendRequests.remove(request.targetId());
            }
            requests = without(requests, request -> request.id() == requestId);
            outgoing = without(outgoing, request -> request.id() == requestId);
        }
    }

    private void removeInviteLocally(long inviteId) {
        synchronized (listLock) {
            groupInvites = without(groupInvites, invite -> invite.id() == inviteId);
        }
    }

    private void upsertGroup(Group group) {
        synchronized (listLock) {
            if (!replaceGroup(group.id(), ignored -> group)) {
                List<Group> next = new ArrayList<>(groups);
                next.add(0, group);
                groups = List.copyOf(next);
            }
        }
    }

    private void removeGroupLocally(String groupId) {
        messages.remove(groupId);
        readSent.remove(groupId);
        synchronized (listLock) {
            groups = without(groups, group -> group.id().equals(groupId));
        }
    }

    private void acceptMessage(String conversationId, JsonObject message) {
        if (message == null || !message.has("id")) return;
        CopyOnWriteArrayList<Message> target = messages.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
        Message parsed = messageOf(message);
        boolean fresh = target.stream().noneMatch(existing -> existing.id() == parsed.id());
        target.removeIf(existing -> existing.id() == parsed.id());
        target.add(parsed);
        while (target.size() > MAX_LOCAL_MESSAGES) target.remove(0);

        boolean own = isSelf(parsed.senderId());
        boolean onScreen = conversationId.equals(selectedConversation);
        int bump = fresh && !own && !onScreen ? 1 : 0;
        boolean known;
        synchronized (listLock) {
            known = updateConversation(conversationId, conversation -> new Conversation(
                    conversation.id(), conversation.participantId(), conversation.username(),
                    parsed.text(), parsed.createdAt(), conversation.unreadCount() + bump));
            if (!known) {
                known = updateGroup(conversationId, group -> new Group(group.id(), group.name(), group.ownerId(),
                        group.members(), parsed.senderUsername(), parsed.text(), parsed.createdAt(),
                        group.unreadCount() + bump));
            }
        }
        if (!known) {
            io.execute(() -> {
                refreshConversations();
                refreshGroups();
            });
        }
        if (onScreen && !own) markRead(conversationId);
        if (fresh && !own && !onScreen) {
            notice(NoticeKind.MESSAGE, parsed.senderUsername(), parsed.text(), conversationId);
        }
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

    /** Applies {@code update} in place, keeping the order. */
    private boolean replaceConversation(String conversationId, UnaryOperator<Conversation> update) {
        List<Conversation> next = new ArrayList<>(conversations.size());
        boolean found = false;
        for (Conversation conversation : conversations) {
            if (conversation.id().equals(conversationId)) {
                next.add(update.apply(conversation));
                found = true;
            } else {
                next.add(conversation);
            }
        }
        if (found) conversations = List.copyOf(next);
        return found;
    }

    private boolean updateGroup(String groupId, UnaryOperator<Group> update) {
        List<Group> next = new ArrayList<>(groups.size());
        Group updated = null;
        for (Group group : groups) {
            if (group.id().equals(groupId)) updated = update.apply(group);
            else next.add(group);
        }
        if (updated == null) return false;
        next.add(0, updated);
        groups = List.copyOf(next);
        return true;
    }

    private boolean replaceGroup(String groupId, UnaryOperator<Group> update) {
        List<Group> next = new ArrayList<>(groups.size());
        boolean found = false;
        for (Group group : groups) {
            if (group.id().equals(groupId)) {
                next.add(update.apply(group));
                found = true;
            } else {
                next.add(group);
            }
        }
        if (found) groups = List.copyOf(next);
        return found;
    }

    private static Group withUnread(Group group, int unread) {
        return new Group(group.id(), group.name(), group.ownerId(), group.members(),
                group.lastSender(), group.lastText(), group.lastCreatedAt(), unread);
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
                message.has("sequence") ? message.get("sequence").getAsLong() : 0L,
                message.get("senderId").getAsString(),
                message.get("senderUsername").isJsonNull() ? "Unknown" : message.get("senderUsername").getAsString(),
                message.get("text").getAsString(),
                attachmentOf(message.get("attachment")),
                message.get("createdAt").getAsLong()
        );
    }

    /** Unknown card types are dropped, not guessed at: an old client simply shows the caption. */
    private static Attachment attachmentOf(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String type = object.has("type") ? object.get("type").getAsString() : "";
        return switch (type) {
            case "waypoint", "death" -> new Attachment(type, object.get("serverKey").getAsString(),
                    object.get("dimension").getAsString(), object.get("x").getAsInt(),
                    object.get("y").getAsInt(), object.get("z").getAsInt(), null);
            case "config" -> new Attachment(type, null, null, 0, 0, 0, object.get("configId").getAsString());
            default -> null;
        };
    }

    private static Friend friendOf(JsonObject row) {
        return new Friend(row.get("id").getAsString(), usernameOf(row),
                row.has("createdAt") ? row.get("createdAt").getAsLong() : 0L);
    }

    private static boolean onlineOf(JsonObject row) {
        return row.has("online") && !row.get("online").isJsonNull() && row.get("online").getAsBoolean();
    }

    private static ServerInfo serverOf(JsonObject row) {
        if (!row.has("server") || !row.get("server").isJsonObject()) return null;
        JsonObject server = row.getAsJsonObject("server");
        if (!server.has("name") || !server.has("address")) return null;
        return new ServerInfo(server.get("name").getAsString(), server.get("address").getAsString());
    }

    private static int intOf(JsonObject row, String field) {
        return row.has(field) && !row.get(field).isJsonNull() ? row.get(field).getAsInt() : 0;
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

    private static Group groupOf(JsonObject row, int unread, JsonElement lastMessage) {
        List<GroupMember> members = new ArrayList<>();
        if (row.has("members")) {
            for (JsonElement element : row.getAsJsonArray("members")) {
                JsonObject member = element.getAsJsonObject();
                members.add(new GroupMember(member.get("id").getAsString(), usernameOf(member),
                        member.has("role") ? member.get("role").getAsString() : "member"));
            }
        }
        JsonObject last = lastMessage != null && lastMessage.isJsonObject() ? lastMessage.getAsJsonObject() : null;
        return new Group(
                row.get("id").getAsString(),
                row.get("name").getAsString(),
                row.get("ownerId").getAsString(),
                List.copyOf(members),
                last == null || last.get("senderUsername").isJsonNull() ? null : last.get("senderUsername").getAsString(),
                last == null ? "" : last.get("text").getAsString(),
                last == null ? 0L : last.get("createdAt").getAsLong(),
                unread
        );
    }

    private static GroupInvite inviteOf(JsonObject row) {
        JsonObject inviter = row.getAsJsonObject("inviter");
        return new GroupInvite(row.get("id").getAsLong(), row.get("groupId").getAsString(),
                row.get("groupName").getAsString(), inviter.get("id").getAsString(), usernameOf(inviter));
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
                last == null ? 0L : last.get("createdAt").getAsLong(),
                intOf(conversation, "unreadCount")
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

    private static String stringField(String body, String field) {
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String parseError(String body, String fallback) {
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            if (object.has("reason")) return MESSAGE_BLOCKED + object.get("reason").getAsString();
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
