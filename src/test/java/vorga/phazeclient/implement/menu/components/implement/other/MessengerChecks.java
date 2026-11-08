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
        calls.add((method + " " + path + " " + argument).trim());
        switch (method + " " + path) {
            case "POST /api/chat/challenge" -> reply(exchange, 404, "{\"error\":\"not_found\"}");
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
                            + "\"target\":{\"id\":\"me\",\"username\":\"Tester\"}}]}");
            case "POST /api/chat/friends/accept" -> reply(exchange, 200,
                    "{\"friend\":{\"id\":\"carol\",\"username\":\"Carol\",\"createdAt\":1,\"online\":true}}");
            default -> reply(exchange, 404, "{\"error\":\"not_found\"}");
        }
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
