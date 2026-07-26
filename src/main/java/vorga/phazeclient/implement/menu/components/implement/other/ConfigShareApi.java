package vorga.phazeclient.implement.menu.components.implement.other;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import vorga.phazeclient.base.util.HardwareId;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.RemoteRulesService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Client for the cloud config share endpoints.
 *
 * <p>Uploads go to {@code POST /api/configs} and come back with a short
 * id in the shape {@code nick-xxxx-xxxx}; downloads are
 * {@code GET /api/configs/:id}. The payload on the wire is exactly the
 * {@code PHAZE1:} share-string {@link
 * vorga.phazeclient.implement.config.ConfigManager#exportCurrentToString}
 * already produces, so nothing about the format is specific to the
 * cloud path - the same string works pasted into chat.
 *
 * <p>Every upload carries three owner hints: the install id, a
 * hardware digest and (implicitly) the source address the server sees.
 * The server caps stored configs per owner and answers 429 with
 * {@code quota_exceeded} once any one of them is over the limit, which
 * is surfaced to the user rather than swallowed - "nothing happened"
 * is the worst possible response to a full quota.
 *
 * <p>Failures never throw. Every entry point returns null and leaves a
 * human-readable reason in {@link #getLastError()} for the modal to
 * render.
 */
public final class ConfigShareApi {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/126.0.0.0 Safari/537.36";

    private static volatile String lastError = "";

    private ConfigShareApi() {}

    public static String getLastError() {
        return lastError;
    }

    public static String upload(String share, String author, Integer maxUses, String name) {
        lastError = "";

        if (share == null || !share.startsWith("PHAZE1:")) {
            lastError = Lang.t("status.cloud_bad_payload");
            return null;
        }
        String base = apiBase();
        if (base == null) {
            lastError = Lang.t("status.cloud_disabled_detail");
            return null;
        }

        JsonObject body = new JsonObject();
        body.addProperty("payload", share);
        if (author != null && !author.isBlank()) {
            body.addProperty("author", author.trim());
        }
        if (name != null && !name.isBlank()) {
            body.addProperty("name", name.trim());
        }
        if (maxUses != null && maxUses > 0) {
            body.addProperty("maxUses", maxUses);
        }
        body.addProperty("clientId", RemoteRulesService.getInstance().getClientId());
        String hwid = HardwareId.get();
        if (hwid != null) {
            body.addProperty("hwid", hwid);
        }

        try {
            Response response = send(base + "/api/configs", "POST", body.toString());
            if (response.status == 201) {
                JsonObject json = parseObject(response.body);
                String id = json != null && json.has("id") ? json.get("id").getAsString() : null;
                if (id == null || id.isEmpty()) {
                    lastError = Lang.t("status.cloud_bad_response");
                    return null;
                }
                return id;
            }
            lastError = describeUploadFailure(response);
            return null;
        } catch (IOException e) {
            lastError = Lang.t("status.cloud_network_error");
            return null;
        }
    }

    public static String upload(String share, String author, Integer maxUses) {
        return upload(share, author, maxUses, null);
    }

    public static String upload(String share) {
        return upload(share, null, null, null);
    }

    public static DownloadResult downloadFull(String code) {
        lastError = "";

        if (code == null || code.isBlank()) {
            lastError = Lang.t("status.cloud_bad_code");
            return null;
        }
        String base = apiBase();
        if (base == null) {
            lastError = Lang.t("status.cloud_disabled_detail");
            return null;
        }

        String id = code.trim().toLowerCase();
        try {
            Response response = send(
                    base + "/api/configs/" + URLEncoder.encode(id, StandardCharsets.UTF_8),
                    "GET",
                    null);
            if (response.status == 200) {
                JsonObject json = parseObject(response.body);
                if (json == null || !json.has("payload")) {
                    lastError = Lang.t("status.cloud_bad_response");
                    return null;
                }
                String payload = json.get("payload").getAsString();
                String name = json.has("name") && !json.get("name").isJsonNull()
                        ? json.get("name").getAsString()
                        : null;
                return new DownloadResult(payload, name);
            }
            lastError = describeDownloadFailure(response);
            return null;
        } catch (IOException e) {
            lastError = Lang.t("status.cloud_network_error");
            return null;
        }
    }

    public static String download(String code) {
        DownloadResult result = downloadFull(code);
        return result == null ? null : result.payload;
    }

    private static String describeUploadFailure(Response response) {
        if (response.status == 429) {
            JsonObject json = parseObject(response.body);
            String error = json != null && json.has("error")
                    ? json.get("error").getAsString()
                    : "";
            if ("quota_exceeded".equals(error)) {
                int limit = json.has("limit") ? json.get("limit").getAsInt() : 5;
                return Lang.t("status.cloud_quota_exceeded").replace("%d", String.valueOf(limit));
            }
            return Lang.t("status.cloud_rate_limited");
        }
        if (response.status == 400) {
            return Lang.t("status.cloud_bad_payload");
        }
        return Lang.t("status.cloud_server_error").replace("%d", String.valueOf(response.status));
    }

    private static String describeDownloadFailure(Response response) {
        switch (response.status) {
            case 400:
                return Lang.t("status.cloud_bad_code");
            case 404:
                return Lang.t("status.cloud_not_found");
            case 410:
                // The uploader capped the number of downloads and the
                // last one has been used.
                return Lang.t("status.cloud_exhausted");
            case 429:
                return Lang.t("status.cloud_rate_limited");
            default:
                return Lang.t("status.cloud_server_error")
                        .replace("%d", String.valueOf(response.status));
        }
    }

    private static String apiBase() {
        String base = RemoteRulesService.getInstance().getApiBase();
        return base == null || base.isEmpty() ? null : base;
    }

    private static JsonObject parseObject(String raw) {
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private record Response(int status, String body) {}

    private static Response send(String url, String method, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(true);

            if (jsonBody != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = conn.getResponseCode();
            // 4xx/5xx bodies arrive on the error stream, and they are
            // exactly where the reason lives, so read both.
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            return new Response(status, stream == null ? "" : readBody(stream));
        } finally {
            conn.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) {
                out.append(buf, 0, read);
                if (out.length() > MAX_RESPONSE_BYTES) {
                    // A share payload caps at 256 KB server-side; if we
                    // are past double that, something is wrong and we
                    // should stop rather than buffer it all.
                    break;
                }
            }
        }
        return out.toString();
    }

    public static final class DownloadResult {
        public final String payload;
        public final String name;

        public DownloadResult(String payload, String name) {
            this.payload = payload;
            this.name = name;
        }
    }
}
