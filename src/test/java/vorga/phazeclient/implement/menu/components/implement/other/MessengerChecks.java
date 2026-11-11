package vorga.phazeclient.implement.menu.components.implement.other;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.MessengerService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Drives the real {@link MessengerService} against a local fake chat API, plus the
 * messenger view's error classification. Needs no game, GL context or network.
 */
public final class MessengerChecks {
    private static final List<String> calls = new CopyOnWriteArrayList<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", MessengerChecks::handle);
        server.start();
        System.setProperty("phaze.rules.api", "http://127.0.0.1:" + server.getAddress().getPort());
        try {
            friendRequests();
            errorLines();
            serverModeration();
            socialFeatures();
        } finally {
            server.stop(0);
            MessengerService.getInstance().stop();
        }
        System.out.println("Messenger checks passed: " + checks);
        System.exit(0);
    }

    private static void friendRequests() throws Exception {
        MessengerService service = MessengerService.getInstance();

        check(send(service, "bob"), "a request to a known player succeeds");
        check(calls.contains("POST /api/chat/friends/requests bob"), "the request is sent by user id, without a nickname lookup");
        check(calls.stream().noneMatch(call -> call.contains("/users/lookup")), "no nickname lookup is needed");
        check("Friend request sent".equals(service.getStatus()), "status reports the sent request");
        check(MessengerViewComponent.inputErrorFor(service.getStatus(), service.getStatusChangedAt(),
                System.currentTimeMillis()) == null, "a sent request is not shown as a send error");

        check(service.hasSentFriendRequest("bob"), "the sent request is remembered");
        int before = calls.size();
        check(send(service, "bob"), "a repeated request is not a failure");
        check(calls.size() == before, "a repeated request is answered locally, without traffic");
        check("Friend request pending".equals(service.getStatus()), "a repeated request reports the pending one");
        check(MessengerViewComponent.friendRequestNotice(service.getStatus(), 0L).text()
                .equals(Lang.t("messenger.friend_request_already_sent")), "the player is told the request was already sent");

        check(send(service, "dave"), "a request the server already holds is not a failure");
        check("Friend request pending".equals(service.getStatus()), "the server's pending answer is reported as already sent");
        check(service.hasSentFriendRequest("dave"), "the server's pending answer is remembered too");

        check(!send(service, "spam"), "a rate-limited request fails");
        check(MessengerViewComponent.actionErrorFor(service.getStatus())
                .equals(Lang.t("messenger.error_friend_slowdown")), "the rate limit has its own message");

        check(send(service, "friend"), "a request to an existing friend is not a failure");
        check("Friend added".equals(service.getStatus()), "an existing friendship is reported as added");

        check(!send(service, "ghost"), "a request to an unavailable player fails");
        check(MessengerViewComponent.actionErrorFor(service.getStatus())
                .equals(Lang.t("messenger.error_friend_unavailable")), "an unavailable player gets a specific error");

        service.refreshRequestsAsync();
        waitFor(() -> !service.getRequests().isEmpty(), "incoming requests load");
        calls.clear();
        check(send(service, "carol"), "adding a player who already asked us succeeds");
        check(calls.contains("POST /api/chat/friends/accept 7"), "their pending request is accepted instead of crossed");
        check(calls.stream().noneMatch(call -> call.startsWith("POST /api/chat/friends/requests")),
                "no second request is created");
        check(service.getRequests().isEmpty(), "the accepted request leaves the list");
        check(service.getFriends().stream().anyMatch(friend -> friend.id().equals("carol")), "the player becomes a friend");

        calls.clear();
        check(service.isSelf("me"), "the local player is recognised");
        check(!send(service, "me"), "a request to yourself is refused");
        check(calls.isEmpty(), "a request to yourself never reaches the server");
        MessengerViewComponent.ActionMessage self = MessengerViewComponent.friendRequestFailure(service.getStatus(), 0L);
        check(self.text().equals(Lang.t("messenger.friend_request_self")), "the player is told they can't add themselves");
        check(self.color() == 0xFFE8C468, "the self request notice is amber");
        check(MessengerViewComponent.inputErrorFor(service.getStatus(), System.currentTimeMillis(),
                System.currentTimeMillis()) == null, "a refused friend request is not a send error");
    }

    private static void serverModeration() throws Exception {
        MessengerService service = MessengerService.getInstance();
        calls.clear();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        service.sendGlobal("сука", result::complete);
        check(!result.get(10, TimeUnit.SECONDS), "the server's refusal fails the send");
        check(calls.contains("POST /api/chat/global/messages"), "the client no longer filters: the server decides");
        long now = System.currentTimeMillis();
        check(Lang.t("messenger.mod_abuse").equals(MessengerViewComponent.inputErrorFor(service.getStatus(),
                service.getStatusChangedAt(), now)), "the player is told why");
        check(MessengerViewComponent.isModerationStatus(service.getStatus()), "a moderation refusal is shown in amber");
        check(MessengerViewComponent.inputErrorFor(service.getStatus(), service.getStatusChangedAt(),
                service.getStatusChangedAt() + 5_000L) == null, "the moderation notice disappears after 5 seconds");
        for (String[] reason : new String[][] {{"hate", "messenger.mod_hate"}, {"politics", "messenger.mod_politics"},
                {"advertising", "messenger.mod_ads"}, {"spam", "messenger.mod_spam"}, {"repeated_flood", "messenger.mod_flood"}}) {
            check(Lang.t(reason[1]).equals(MessengerViewComponent.moderationText(reason[0])), "server reason " + reason[0]);
        }
    }

    private static final String GROUP_ID = "abcdef0123456789abcdef0123456789";
    private static final String JOINED_ID = "fedcba9876543210fedcba9876543210";

    /** @nick mentions, and point cards becoming, changing and leaving the shared world list. */
    private static void mentionsAndSharedPoints(MessengerService service, MessengerService.Message card) throws Exception {
        check(service.mentionsMe("@Tester глянь") && service.mentionsMe("эй, @tester!") && service.mentionsMe("(@TESTER)"),
                "a mention matches the nick case-insensitively at word bounds");
        check(!service.mentionsMe("@Tester2 нет") && !service.mentionsMe("mail@Tester") && !service.mentionsMe("Tester")
                && !service.mentionsMe("@") && !service.mentionsMe(null), "longer names, e-mail-like text and plain names are not mentions");

        check(service.sharedPoints().stream().anyMatch(point -> point.message().id() == card.id()),
                "a point card from a group is a shared point");
        java.lang.reflect.Method handle = MessengerService.class.getDeclaredMethod("handleEvent", String.class, String.class);
        handle.setAccessible(true);
        long id = 777L;
        String point = "\"attachment\":{\"type\":\"waypoint\",\"serverKey\":\"" + "a".repeat(64) + "\","
                + "\"dimension\":\"minecraft:overworld\",\"x\":10,\"y\":64,\"z\":-5}";
        handle.invoke(service, "message", "{\"conversationId\":\"" + JOINED_ID + "\",\"message\":{\"id\":" + id
                + ",\"sequence\":2,\"senderId\":\"carol\",\"senderUsername\":\"Carol\",\"text\":\"База\",\"createdAt\":1," + point + "}}");
        check(service.sharedPoints().stream().anyMatch(shared -> shared.message().id() == id),
                "a new point card is shared at once");
        int version = service.messagesVersion();
        String moved = "{\"conversationId\":\"" + JOINED_ID + "\",\"message\":{\"id\":" + id + ",\"sequence\":2,"
                + "\"senderId\":\"carol\",\"senderUsername\":\"Carol\",\"text\":\"Шахта\",\"createdAt\":1,"
                + "\"attachment\":{\"type\":\"waypoint\",\"serverKey\":\"" + "a".repeat(64) + "\","
                + "\"dimension\":\"minecraft:overworld\",\"x\":99,\"y\":64,\"z\":-5,\"color\":1193046,\"display\":5}}}";
        handle.invoke(service, "message_update", moved);
        MessengerService.Message updated = service.getMessages(JOINED_ID).stream()
                .filter(message -> message.id() == id).findFirst().orElseThrow();
        check(updated.text().equals("Шахта") && updated.attachment().x() == 99 && updated.attachment().color() == 1193046
                && service.messagesVersion() > version, "an edit from its author replaces the card in place");
        check(service.sharedPoints().stream().anyMatch(shared -> shared.message().attachment().x() == 99),
                "the shared world list follows the edit");
        handle.invoke(service, "message_update", moved.replace("\"id\":" + id, "\"id\":987654"));
        check(service.getMessages(JOINED_ID).stream().noneMatch(message -> message.id() == 987654L),
                "an edit of a message we never had is ignored");
        handle.invoke(service, "message_delete", "{\"conversationId\":\"" + JOINED_ID + "\",\"messageId\":" + id + "}");
        check(service.getMessages(JOINED_ID).stream().noneMatch(message -> message.id() == id)
                && service.sharedPoints().stream().noneMatch(shared -> shared.message().id() == id),
                "a deleted card leaves the chat and the world");
    }

    /** A card's server name is shown only when it hashes to the card's server key. */
    private static void pointCardServerNames() throws Exception {
        java.lang.reflect.Method parse = MessengerService.class.getDeclaredMethod("attachmentOf", com.google.gson.JsonElement.class);
        parse.setAccessible(true);
        String host = "mc.example.net";
        String key = vorga.phazeclient.implement.messenger.ChatWorld.keyOf(host);
        String base = "{\"type\":\"waypoint\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3,";
        MessengerService.Attachment named = (MessengerService.Attachment) parse.invoke(null, com.google.gson.JsonParser.parseString(
                base + "\"serverKey\":\"" + key + "\",\"server\":\"" + host + "\",\"color\":9366475,\"display\":5}"));
        check(named != null && host.equals(named.server()) && named.color() == 9366475 && named.display() == 5,
                "a named, styled card keeps its server, colour and switches");
        MessengerService.Attachment spoofed = (MessengerService.Attachment) parse.invoke(null, com.google.gson.JsonParser.parseString(
                base + "\"serverKey\":\"" + "a".repeat(64) + "\",\"server\":\"" + host + "\",\"color\":99999999,\"display\":40}"));
        check(spoofed != null && spoofed.server() == null && spoofed.color() == -1 && spoofed.display() == -1,
                "a server name that is not the card's server, and out-of-range styles, are dropped");
        MessengerService.Attachment malformed = (MessengerService.Attachment) parse.invoke(null, com.google.gson.JsonParser.parseString(
                base + "\"serverKey\":\"" + key + "\",\"server\":{\"x\":1}}"));
        check(malformed != null && malformed.server() == null, "a non-text server name is ignored");
        MessengerService.Attachment missing = (MessengerService.Attachment) parse.invoke(null, com.google.gson.JsonParser.parseString(
                "{\"type\":\"waypoint\",\"serverKey\":\"" + key + "\"}"));
        check(missing == null, "a card without coordinates is dropped instead of throwing");
    }

    private static void socialFeatures() throws Exception {
        MessengerService service = MessengerService.getInstance();
        waitFor(() -> service.getOutgoingRequests().stream().anyMatch(request -> request.targetId().equals("erin")),
                "outgoing requests load with the incoming ones");
        check(service.hasSentFriendRequest("erin"), "an outgoing request from the server counts as sent");
        CompletableFuture<Boolean> cancelled = new CompletableFuture<>();
        service.cancelFriendRequest(9L, cancelled::complete);
        check(cancelled.get(10, TimeUnit.SECONDS), "the requester can withdraw");
        check(calls.contains("POST /api/chat/friends/cancel 9"), "cancel is sent by request id");
        check(service.getOutgoingRequests().isEmpty() && !service.hasSentFriendRequest("erin"),
                "the withdrawn request leaves the list");

        CompletableFuture<String> created = new CompletableFuture<>();
        service.createGroup("  Squad ", List.of("carol"), created::complete);
        check(GROUP_ID.equals(created.get(10, TimeUnit.SECONDS)), "a created group reports its id");
        check(lastBody.contains("\"name\":\"Squad\"") && lastBody.contains("carol"), "the name is trimmed, invites ride along");
        MessengerService.Group group = service.getGroup(GROUP_ID);
        check(group != null && group.member("me").isOwner(), "the creator is the owner");

        CompletableFuture<String> joined = new CompletableFuture<>();
        service.acceptGroupInvite(4L, joined::complete);
        check(JOINED_ID.equals(joined.get(10, TimeUnit.SECONDS)), "accepting an invite joins its group");
        waitFor(() -> !service.getMessages(JOINED_ID).isEmpty(), "the joined group's history loads");
        MessengerService.Message card = service.getMessages(JOINED_ID).get(0);
        check(card.attachment() != null && card.attachment().isPoint() && card.attachment().x() == 10,
                "a waypoint card is parsed");
        check(card.attachment().server() == null && card.attachment().color() == -1 && card.attachment().display() == -1,
                "a plain card has no server name or style");
        pointCardServerNames();
        mentionsAndSharedPoints(service, card);
        check(card.sequence() == 3L, "messages keep their sequence for read markers");
        check(service.getMessages(JOINED_ID).get(1).attachment() == null, "an unknown card type is dropped, not guessed");

        calls.clear();
        service.selectConversation(JOINED_ID);
        waitFor(() -> calls.contains("POST /api/chat/conversations/" + JOINED_ID + "/read"), "opening a conversation reads it");
        check(lastBody.contains("\"sequence\":4"), "the read marker is the newest message");
        int before = calls.size();
        service.markRead(JOINED_ID);
        Thread.sleep(100L);
        check(calls.size() == before, "an already-read conversation costs no request");
        service.selectConversation(null);

        CompletableFuture<Boolean> privacy = new CompletableFuture<>();
        service.setShareServer(true, privacy::complete);
        check(privacy.get(10, TimeUnit.SECONDS) && service.isSharingServer(), "sharing the server is saved");

        service.refreshTeammatesAsync();
        waitFor(() -> service.getTeammates().contains("bob"), "teammates load lower-cased for name matching");
    }

    private static void errorLines() {
        long now = 1_000_000L;
        for (String ok : List.of("Online", "Offline", "Messenger reconnecting", "Friend request sent",
                "Friend request pending", "Friend added", "Friend removed", "Friend request declined", "User unblocked")) {
            check(MessengerViewComponent.inputErrorFor(ok, now, now) == null, "not an error: " + ok);
        }
        check(Lang.t("messenger.error_send").equals(MessengerViewComponent.inputErrorFor("Send failed", now, now)),
                "a failed send is shown");
        check(MessengerViewComponent.inputErrorFor("Send failed", now, now + 4_999L) != null, "an error stays for 5 seconds");
        check(MessengerViewComponent.inputErrorFor("Send failed", now, now + 5_000L) == null, "an error disappears after 5 seconds");
        check(MessengerViewComponent.inputErrorFor("chat_slow_down", now, now + 1_000L)
                .equals(Lang.t("messenger.error_slowmode")), "slowmode keeps its own message");
    }

    private static boolean send(MessengerService service, String userId) throws Exception {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        service.sendFriendRequest(userId, result::complete);
        return result.get(10, TimeUnit.SECONDS);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject json = body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        String argument = json.has("targetUserId") ? json.get("targetUserId").getAsString()
                : json.has("requestId") ? json.get("requestId").getAsString() : "";
        lastBody = body;
        calls.add((method + " " + path + " " + argument).trim());
        if (path.startsWith("/api/chat/conversations/") && path.endsWith("/read")) {
            reply(exchange, 200, "{\"sequence\":4}");
            return;
        }
        if (method.equals("GET") && path.equals("/api/chat/conversations/" + JOINED_ID + "/messages")) {
            reply(exchange, 200, "{\"messages\":["
                    + "{\"id\":30,\"sequence\":3,\"senderId\":\"carol\",\"senderUsername\":\"Carol\",\"text\":\"База\","
                    + "\"createdAt\":1,\"attachment\":{\"type\":\"waypoint\",\"serverKey\":\"" + "a".repeat(64) + "\","
                    + "\"dimension\":\"minecraft:overworld\",\"x\":10,\"y\":64,\"z\":-5}},"
                    + "{\"id\":31,\"sequence\":4,\"senderId\":\"carol\",\"senderUsername\":\"Carol\",\"text\":\"?\","
                    + "\"createdAt\":2,\"attachment\":{\"type\":\"script\"}}]}");
            return;
        }
        switch (method + " " + path) {
            case "POST /api/chat/challenge" -> reply(exchange, 404, "{\"error\":\"not_found\"}");
            case "POST /api/chat/global/messages" -> reply(exchange, 422,
                    "{\"error\":\"message_rejected\",\"reason\":\"abusive_content\"}");
            case "POST /api/chat/session" -> reply(exchange, 201,
                    "{\"token\":\"t\",\"user\":{\"id\":\"me\",\"username\":\"Tester\"}}");
            case "POST /api/chat/friends/requests" -> {
                switch (argument) {
                    case "bob" -> reply(exchange, 201, "{\"status\":\"created\"}");
                    case "friend" -> reply(exchange, 200, "{\"status\":\"already_friends\"}");
                    case "dave" -> reply(exchange, 200, "{\"status\":\"pending\"}");
                    case "spam" -> reply(exchange, 429, "{\"error\":\"friend_request_slow_down\",\"retryAfterSec\":30}");
                    default -> reply(exchange, 404, "{\"error\":\"friend_target_unavailable\"}");
                }
            }
            case "GET /api/chat/friends" -> reply(exchange, 200,
                    "{\"friends\":[{\"id\":\"friend\",\"username\":\"Friend\",\"createdAt\":1,\"online\":false}]}");
            case "GET /api/chat/friends/requests" -> reply(exchange, 200,
                    "{\"requests\":[{\"id\":7,\"status\":\"pending\",\"createdAt\":1,"
                            + "\"requester\":{\"id\":\"carol\",\"username\":\"Carol\"},"
                            + "\"target\":{\"id\":\"me\",\"username\":\"Tester\"}}],"
                            + "\"outgoing\":[{\"id\":9,\"status\":\"pending\",\"createdAt\":1,"
                            + "\"requester\":{\"id\":\"me\",\"username\":\"Tester\"},"
                            + "\"target\":{\"id\":\"erin\",\"username\":\"Erin\"}}]}");
            case "POST /api/chat/friends/cancel" -> reply(exchange, 200, "{\"cancelled\":true}");
            case "POST /api/chat/groups" -> reply(exchange, 201, "{\"group\":" + group(GROUP_ID, "Squad") + ",\"invited\":[\"carol\"]}");
            case "POST /api/chat/groups/invites/accept" -> reply(exchange, 200, "{\"group\":" + group(JOINED_ID, "Raid") + "}");
            case "POST /api/chat/me/privacy" -> reply(exchange, 200, "{\"privacy\":{\"shareServer\":true}}");
            case "GET /api/chat/teammates" -> reply(exchange, 200, "{\"usernames\":[\"Bob\"]}");
            case "POST /api/chat/friends/accept" -> reply(exchange, 200,
                    "{\"friend\":{\"id\":\"carol\",\"username\":\"Carol\",\"createdAt\":1,\"online\":true}}");
            default -> reply(exchange, 404, "{\"error\":\"not_found\"}");
        }
    }

    private static volatile String lastBody = "";

    private static String group(String id, String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"ownerId\":\"me\",\"createdAt\":1,\"updatedAt\":1,"
                + "\"members\":[{\"id\":\"me\",\"username\":\"Tester\",\"role\":\"owner\",\"joinedAt\":1,\"online\":true,\"server\":null}]}";
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void waitFor(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("timed out: " + message);
            Thread.sleep(10L);
        }
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
