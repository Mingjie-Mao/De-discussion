package backend;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The application-facing moderation integration.
 *
 * <p>Reports always reference an existing server row. A missing or deleted
 * target is never recreated from an old UI cache.
 */
public final class BackendModerationGateway {
    private final BackendClient client;
    private final BackendAccounts accounts;
    private final BackendMappings mappings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    BackendModerationGateway(
            BackendClient client,
            BackendAccounts accounts,
            BackendMappings mappings) {
        this.client = client;
        this.accounts = accounts;
        this.mappings = mappings;
    }

    public void fetchStatus(Callback<BackendModerationStatus> callback) {
        run(() -> {
            JSONObject value = client.getObject("/api/moderation/status", null);
            return new BackendModerationStatus(
                    value.optString("configuredEngine", ""),
                    value.optString("activeEngine", ""),
                    value.optString("fallbackEngine", ""),
                    value.optBoolean("configuredEngineAvailable", false),
                    value.optBoolean("llmActive", false));
        }, callback);
    }

    /**
     * File a report against existing server content.
     *
     * <p>Succeeds with the new report's id, or with {@code null} when this
     * account had already reported the same target — see {@link #submitNow}.
     * Either way the content is under review, which is all a caller acts on.
     */
    public void submitReport(
            BackendReportTarget target,
            UUID reporterId,
            Callback<UUID> callback) {
        run(() -> submitNow(target, reporterId), callback);
    }

    private UUID submitNow(BackendReportTarget target, UUID reporterId) throws Exception {
        UUID remoteTargetId = target.targetType() == BackendReportTarget.TargetType.POST
                ? mappings.remotePost(target.localTargetId())
                : mappings.remoteComment(target.localTargetId());
        if (remoteTargetId == null) {
            throw new BackendException(404, "This content is no longer synchronized. Refresh before reporting.");
        }

        JSONObject request = new JSONObject()
                .put("targetType", target.targetType().name())
                .put("targetId", remoteTargetId.toString())
                .put("reason", "OTHER");

        try {
            JSONObject response = memberPost(reporterId, "/api/reports", request);
            return requireUuid(response, "id");
        } catch (BackendException error) {
            if (!error.isConflict()) {
                throw error;
            }
            // The backend already holds this account's report on this target and
            // says so with a 409. That is the report having been filed, not the
            // report failing, and the two must not look the same to the person
            // pressing the button.
            //
            // It is reached in ordinary use: the local reported-flag lives in
            // ModerationTools, which is memory-only here, so restarting the app
            // or switching language clears it while the backend row survives.
            // Treated as a failure, the second attempt would show an error on a
            // report that exists and has a case waiting for review.
            return null;
        }
    }

    private JSONObject memberPost(UUID localUserId, String path, JSONObject body) {
        return accounts.authenticated(localUserId, (server, token) -> server.postObject(path, token, body));
    }

    private <T> void run(Task<T> task, Callback<T> callback) {
        executor.execute(() -> {
            try {
                T value = task.run();
                mainHandler.post(() -> callback.onSuccess(value));
            } catch (BackendException error) {
                mainHandler.post(() -> callback.onError(error));
            } catch (Exception error) {
                BackendException wrapped = new BackendException(BackendText.integrationFailed(), error);
                mainHandler.post(() -> callback.onError(wrapped));
            }
        });
    }

    /**
     * A string field, where a JSON {@code null} counts as absent.
     *
     * <p>{@code optString(name, fallback)} does not do this. It returns the
     * fallback only when the key is missing entirely; a key present and null
     * comes back as the four-character string {@code "null"}. Half of this
     * response is nullable by design — a comment has no title, and a case that
     * has not been analysed has no engine, decision or rationale — so the
     * plain call renders "null" to an administrator as though it were content.
     */
    static String text(JSONObject value, String field, String fallback) {
        if (value.isNull(field)) {
            return fallback;
        }
        String result = value.optString(field, fallback);
        return result.isBlank() ? fallback : result;
    }

    private static UUID requireUuid(JSONObject value, String field) {
        String raw = value.optString(field, "");
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException error) {
            throw new BackendException(0, BackendText.malformedResponse());
        }
    }

    private static String shorten(String value, int limit) {
        String text = value == null ? "" : value.trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private interface Task<T> {
        T run() throws Exception;
    }

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(BackendException error);
    }
}
