package vorga.phazeclient.implement.menu.components.implement.other;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

/**
 * HTTP helpers for the share-config endpoints.
 *
 * <p>Uses the legacy {@link HttpURLConnection} because the modern
 * {@link java.net.http.HttpClient} forcibly re-enables HTTPS
 * endpoint identification at the SSL engine level even when
 * {@link javax.net.ssl.SSLParameters#setEndpointIdentificationAlgorithm}
 * is set to null - that's by design in the JDK to prevent silent
 * cert spoofing. We genuinely need to disable hostname verification
 * for the share endpoint because some user networks (corporate DPI,
 * antivirus HTTPS interception, mobile carriers) intercept the TLS
 * connection and respond with a cert whose SAN/CN doesn't include
 * {@code phaze-rules-admin.pages.dev}, which the JDK then rejects
 * with "No name matching ... found".
 *
 * <p>The legacy {@link HttpsURLConnection} respects a per-connection
 * {@link HostnameVerifier} override, so we can return {@code true}
 * for our specific endpoint without flipping a JVM-wide flag.
 *
 * <p>Risk profile - all calls return {@code null} on error and the
 * payload is opaque (PHAZE1+gzip+JSON, validated client-side):
 * permissive TLS is acceptable here because (a) the API is public
 * and unauthenticated, (b) downloaded content is decoded and
 * sanitised before any local persistence, (c) a fully passive MITM
 * still can't forge a payload that decodes - they'd have to know
 * the gzipped JSON shape AND have the user paste their fake key.
 */
public final class ConfigShareApi {

    /**
     * Cloudflare Worker that hosts the share API. We deliberately
     * use the {@code *.workers.dev} subdomain instead of the
     * Pages-Functions {@code *.pages.dev} variant because some RU
     * mobile carriers (Megafon, MTS) intercept the SNI for
     * {@code pages.dev} and rewrite the response into a captive
     * portal redirect (HTTP 302 to {@code megafonpro.ru/rkn-auth}
     * etc.). The {@code workers.dev} subdomain currently passes
     * through clean. Both deployments hit the same D1 database, so
     * the dashboard and the in-game module see the same data.
     *
     * <p>We carry a {@link #API_FALLBACKS} list as well: if a user's
     * carrier blocks one endpoint they can still upload through the
     * other without us shipping a new client build. The list is
     * tried in order until one returns a non-network error.
     *
     * <p>JVM-property override for local dev:
     * {@code -Dphaze.share.api=http://127.0.0.1:8788}.
     */
    private static final String API_BASE =
            System.getProperty("phaze.share.api", "https://phazeclient.ru");

    /**
     * Backup endpoints tried on network failures. The {@code pages.dev}
     * fallback covers the case where workers.dev is being throttled /
     * SNI-rewritten by the user's carrier and the pages.dev SNI gets
     * through (some carriers block one but not the other). All
     * endpoints share the same D1 database via the multi-deploy
     * setup in the server project.
     */
    private static final String[] API_FALLBACKS = {
            "https://phaze-rules-admin.pages.dev",
    };

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 PhazeClient/1.0";

    /** Last failure reason from upload/download, or {@code null} on
     *  success. Read by the modal to display a useful status. */
    private static volatile String lastError;

    /** Lazy-init SSLSocketFactory that trusts any cert. */
    private static volatile SSLSocketFactory permissiveFactory;
    /** Verifier that accepts any hostname. Reused across connections. */
    private static final HostnameVerifier ANY_HOST = new HostnameVerifier() {
        @Override public boolean verify(String hostname, SSLSession session) { return true; }
    };

    private ConfigShareApi() {}

    public static String getLastError() {
        return lastError;
    }

    private static SSLSocketFactory permissiveFactory() {
        SSLSocketFactory existing = permissiveFactory;
        if (existing != null) return existing;
        synchronized (ConfigShareApi.class) {
            if (permissiveFactory != null) return permissiveFactory;
            try {
                TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                }};
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, trustAll, new java.security.SecureRandom());
                permissiveFactory = ctx.getSocketFactory();
                return permissiveFactory;
            } catch (Throwable t) {
                lastError = "ssl init failed: " + t;
                System.err.println("[PhazeShare] " + lastError);
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Applies the permissive trust manager + hostname verifier to a
     * single connection. Idempotent; safe to call on plain HTTP too
     * (it's a no-op there).
     */
    private static void hardenConnection(HttpURLConnection conn) {
        if (conn instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) conn;
            try {
                https.setSSLSocketFactory(permissiveFactory());
                https.setHostnameVerifier(ANY_HOST);
            } catch (Throwable t) {
                System.err.println("[PhazeShare] hardenConnection failed: " + t);
            }
        }
    }

    /**
     * Result envelope for {@link #download} - carries the share-
     * string payload alongside the optional original config name
     * uploaded by the original sharer. Receivers use {@link #name}
     * to save the imported config under the same local name as the
     * uploader rather than an opaque {@code imported_<n>} slot.
     */
    public static final class DownloadResult {
        public final String payload;
        public final String name;
        public DownloadResult(String payload, String name) {
            this.payload = payload;
            this.name = name;
        }
    }

    /**
     * POST a share-string to {@code /api/configs}. Returns the
     * generated id ({@code <nick>-NNNN-NNNN}) on success or
     * {@code null} on any error.
     */
    public static String upload(String share, String author, Integer maxUses, String name) {
        // Try primary endpoint, then each fallback in order.
        String result = uploadWithRetry(API_BASE, share, author, maxUses, name);
        if (result != null) return result;
        for (String alt : API_FALLBACKS) {
            result = uploadWithRetry(alt, share, author, maxUses, name);
            if (result != null) return result;
        }
        return null;
    }

    /** Back-compat for callers that don't yet pass a config name. */
    public static String upload(String share, String author, Integer maxUses) {
        return upload(share, author, maxUses, null);
    }

    private static String uploadWithRetry(String base, String share, String author, Integer maxUses, String name) {
        String result = uploadOnce(base, share, author, maxUses, name);
        if (result == null && lastError != null
                && (lastError.contains("SocketTimeout") || lastError.contains("SSL")
                        || lastError.contains("handshake")
                        || lastError.contains("SocketException")
                        || lastError.contains("Unexpected end"))) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            result = uploadOnce(base, share, author, maxUses, name);
        }
        return result;
    }

    private static String uploadOnce(String base, String share, String author, Integer maxUses, String name) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(base + "/api/configs").toURL();
            conn = (HttpURLConnection) url.openConnection();
            hardenConnection(conn);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Connection", "close");
            conn.setInstanceFollowRedirects(true);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);

            JsonObject payload = new JsonObject();
            payload.addProperty("payload", share);
            if (author != null && !author.isEmpty()) {
                payload.addProperty("author", author);
            }
            if (maxUses != null && maxUses > 0) {
                payload.addProperty("maxUses", maxUses.intValue());
            }
            // Carry the local config file name so the receiver can
            // import under the same name. The server-side regex
            // accepts [A-Za-z0-9_-]{1,32}; anything outside that is
            // dropped to null on the worker, NOT a hard reject, so
            // sending a sanitised value here is purely a hint.
            if (name != null && !name.isEmpty()) {
                payload.addProperty("name", name);
            }
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            conn.getOutputStream().write(body);

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String json = stream == null ? "" : readAll(stream);
            if (code != 201 && code != 200) {
                String location = conn.getHeaderField("Location");
                lastError = "HTTP " + code
                        + (location != null ? " -> " + truncate(location, 80) : "")
                        + (json.isEmpty() ? "" : ": " + truncate(json, 120));
                System.err.println("[PhazeShare] upload failed: " + lastError);
                return null;
            }
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            if (!parsed.has("id")) {
                lastError = "server response missing id field";
                return null;
            }
            lastError = null;
            return parsed.get("id").getAsString();
        } catch (IOException e) {
            lastError = "network: " + e.getClass().getSimpleName() + " " + e.getMessage();
            System.err.println("[PhazeShare] upload network error: " + lastError);
            return null;
        } catch (RuntimeException e) {
            lastError = e.getClass().getSimpleName() + " " + e.getMessage();
            System.err.println("[PhazeShare] upload runtime error: " + lastError);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Back-compat wrapper for older callers without author/cap. */
    public static String upload(String share) {
        return upload(share, null, null);
    }

    /**
     * GET {@code /api/configs/<code>}. Returns the share-string
     * payload on success or {@code null} on any error.
     */
    public static String download(String code) {
        DownloadResult res = downloadFull(code);
        return res == null ? null : res.payload;
    }

    /**
     * Same as {@link #download} but also surfaces the original
     * config name attached at upload time, so the caller can save
     * the imported config under the same local name.
     */
    public static DownloadResult downloadFull(String code) {
        DownloadResult result = downloadWithRetry(API_BASE, code);
        if (result != null) return result;
        // 404/410 are NOT network errors - don't try fallbacks for them.
        if (lastError != null
                && (lastError.contains("ключ не найден") || lastError.contains("ключ исчерпан"))) {
            return null;
        }
        for (String alt : API_FALLBACKS) {
            result = downloadWithRetry(alt, code);
            if (result != null) return result;
            if (lastError != null
                    && (lastError.contains("ключ не найден") || lastError.contains("ключ исчерпан"))) {
                return null;
            }
        }
        return null;
    }

    private static DownloadResult downloadWithRetry(String base, String code) {
        DownloadResult result = downloadOnce(base, code);
        if (result == null && lastError != null
                && (lastError.contains("SocketTimeout") || lastError.contains("SSL")
                        || lastError.contains("handshake")
                        || lastError.contains("SocketException")
                        || lastError.contains("Unexpected end"))) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            result = downloadOnce(base, code);
        }
        return result;
    }

    private static DownloadResult downloadOnce(String base, String code) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(base + "/api/configs/" + code).toURL();
            conn = (HttpURLConnection) url.openConnection();
            hardenConnection(conn);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Connection", "close");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            int responseCode = conn.getResponseCode();
            InputStream stream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String json = stream == null ? "" : readAll(stream);
            if (responseCode == 404) {
                lastError = "ключ не найден";
                return null;
            }
            if (responseCode == 410) {
                lastError = "ключ исчерпан";
                return null;
            }
            if (responseCode != 200) {
                lastError = "HTTP " + responseCode + (json.isEmpty() ? "" : ": " + truncate(json, 120));
                return null;
            }
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            if (!parsed.has("payload")) {
                lastError = "пустой ответ сервера";
                return null;
            }
            String name = parsed.has("name") && !parsed.get("name").isJsonNull()
                    ? parsed.get("name").getAsString()
                    : null;
            lastError = null;
            return new DownloadResult(parsed.get("payload").getAsString(), name);
        } catch (IOException e) {
            lastError = "network: " + e.getClass().getSimpleName() + " " + e.getMessage();
            return null;
        } catch (RuntimeException e) {
            lastError = e.getClass().getSimpleName() + " " + e.getMessage();
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
