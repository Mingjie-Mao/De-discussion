package backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Seven endpoints' worth of HTTP.
 *
 * <p>{@code HttpURLConnection} and the {@code org.json} that ships with Android
 * rather than Retrofit and OkHttp: this app currently has no networking stack at
 * all, and adding one — client, converter, interceptors — to prove two projects
 * talk to each other would be more infrastructure than integration.
 */
public final class BackendClient {

    /**
     * Short on purpose. A backend that is not running should make a tap feel
     * momentarily unproductive, not make the app look frozen.
     */
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 12000;

    private final java.util.function.Supplier<String> baseUrl;

    public BackendClient(BackendConfig config) {
        this.baseUrl = config::baseUrl;
    }

    /** A fixed origin prevents a settings change from redirecting a privileged request. */
    public BackendClient(String origin) {
        this.baseUrl = () -> origin;
    }

    public JSONObject getObject(String path, String token) {
        return toObject(send("GET", path, token, null));
    }

    public JSONArray getArray(String path, String token) {
        String body = send("GET", path, token, null);
        try {
            return new JSONArray(body);
        } catch (Exception e) {
            throw new BackendException(BackendText.malformedResponse(), e);
        }
    }

    public JSONObject postObject(String path, String token, JSONObject body) {
        return toObject(send("POST", path, token, body == null ? "{}" : body.toString()));
    }

    public JSONObject patchObject(String path, String token, JSONObject body) {
        return toObject(send("PATCH", path, token, body == null ? "{}" : body.toString()));
    }

    public void delete(String path, String token) {
        send("DELETE", path, token, null);
    }

    public JSONObject upload(String path, String token, String fileName, String contentType, byte[] bytes) {
        String boundary = "CampusGuard-" + java.util.UUID.randomUUID();
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try {
            payload.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            payload.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                    + safeFileName(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            payload.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            payload.write(bytes);
            payload.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new BackendException("Could not build the media upload.", impossible);
        }
        return toObject(sendBytes("POST", path, token,
                "multipart/form-data; boundary=" + boundary, payload.toByteArray()));
    }

    public String request(String method, String path, String token, JSONObject body) {
        return send(method, path, token, body == null ? null : body.toString());
    }

    /** Private case evidence is bounded and never follows a redirect with a bearer token. */
    public byte[] getBytes(String path, String token) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl.get() + path).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int status = connection.getResponseCode();
            if (status != 200) throw BackendException.fromResponse(status, read(connection.getErrorStream()));
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 8 * 1024 * 1024) throw new BackendException(0, "Image exceeds 8 MiB.");
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } catch (IOException error) {
            throw new BackendException(BackendText.unreachable(baseUrl.get()), error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject toObject(String body) {
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            throw new BackendException(BackendText.malformedResponse(), e);
        }
    }

    /**
     * @return the response body, for any 2xx
     * @throws BackendException on any other status, or if the request never
     *     arrived
     */
    private String send(String method, String path, String token, String body) {
        for (int attempt = 0; ; attempt++) {
            try { return sendBytes(
                method,
                path,
                token,
                body == null ? null : "application/json; charset=utf-8",
                body == null ? null : body.getBytes(StandardCharsets.UTF_8));
            } catch (BackendException error) {
                if (!"GET".equals(method) || attempt != 0 || !retryableRead(error.status())) throw error;
            }
        }
    }

    static boolean retryableRead(int status) { return status == 0 || status == 502 || status == 503 || status == 504; }

    private String sendBytes(String method, String path, String token, String contentType, byte[] body) {
        long started = System.nanoTime();
        int responseStatus = 0;
        String requestId = "unavailable";
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl.get() + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }

            int status = connection.getResponseCode();
            responseStatus = status;
            requestId = connection.getHeaderField("X-Request-Id");

            // The error stream, not the input stream, carries the body on a 4xx.
            // Reading the wrong one is how a useful problem detail turns into an
            // IOException and then into a misleading "unreachable".
            String responseBody = status >= 200 && status < 300
                    ? read(connection.getInputStream())
                    : read(connection.getErrorStream());

            if (status < 200 || status >= 300) {
                throw BackendException.fromResponse(status, responseBody);
            }

            return responseBody;

        } catch (IOException e) {
            throw new BackendException(BackendText.unreachable(baseUrl.get()), e);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (elapsedMs >= 3000 || responseStatus == 0) {
                // Never log tokens, account IDs, query strings or request bodies.
                android.util.Log.w("BackendNetwork", "method=" + method + " status=" + responseStatus
                        + " route=" + logRoute(path) + " durationMs=" + elapsedMs + " requestId="
                        + requestId);
            }
            if (connection != null) connection.disconnect();
        }
    }

    static String logRoute(String path) {
        String clean = path == null ? "" : path.split("\\?", 2)[0]
                .replaceAll("(?i)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)", "/{id}");
        // Only fixed API templates can reach logs, never names or arbitrary path values.
        return java.util.Set.of("/actuator/health/readiness", "/api/auth/login", "/api/auth/refresh",
                "/api/users/me", "/api/users/{id}", "/api/posts", "/api/posts/{id}",
                "/api/posts/authors/{id}", "/api/posts/{id}/comments", "/api/community/state",
                "/api/community/posts", "/api/community/posts/{id}/vote", "/api/community/comments/{id}/vote",
                "/api/community/posts/{id}/bookmark", "/api/community/users/{id}/follow",
                "/api/translations", "/api/market", "/api/market/trades", "/api/market/leaderboard",
                "/api/admin/moderation-cases", "/api/admin/moderation-cases/{id}", "/api/admin/appeals")
                .contains(clean) ? clean : "other";
    }

    private static String safeFileName(String value) {
        return value == null ? "upload" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
