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
    private static final int READ_TIMEOUT_MS = 8000;

    private final BackendConfig config;

    public BackendClient(BackendConfig config) {
        this.config = config;
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
        HttpURLConnection connection = null;
        try {
            URL url = new URL(config.baseUrl() + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }

            int status = connection.getResponseCode();

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
            throw new BackendException(BackendText.unreachable(config.baseUrl()), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
