package backend;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Member-only notification and appeal operations. */
public final class BackendMemberGateway {
    private final BackendClient client;
    private final BackendAccounts accounts;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    BackendMemberGateway(BackendClient client, BackendAccounts accounts) {
        this.client = client;
        this.accounts = accounts;
    }

    public void fetchNotifications(
            UUID localUserId,
            BackendModerationGateway.Callback<List<NotificationSnapshot>> callback) {
        run(() -> withMemberRetry(localUserId, (server, token) -> {
            JSONArray values = server.getArray("/api/notifications?size=100", token);
            List<NotificationSnapshot> result = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                result.add(new NotificationSnapshot(
                        uuid(value, "id"),
                        value.optString("type", ""),
                        value.optString("title", ""),
                        value.optString("body", ""),
                        nullable(value, "referenceType"),
                        nullableUuid(value, "referenceId"),
                        !value.isNull("readAt"),
                        epoch(value.optString("createdAt", ""))));
            }
            return result;
        }), callback);
    }

    public void markNotificationRead(
            UUID localUserId,
            UUID notificationId,
            BackendModerationGateway.Callback<Void> callback) {
        run(() -> withMemberRetry(localUserId, (server, token) -> {
            server.postObject("/api/notifications/" + notificationId + "/read", token, new JSONObject());
            return null;
        }), callback);
    }

    public void createAppeal(
            UUID localUserId,
            UUID caseId,
            String reason,
            BackendModerationGateway.Callback<AppealSnapshot> callback) {
        run(() -> withMemberRetry(localUserId, (server, token) -> {
            JSONObject request = new JSONObject()
                    .put("caseId", caseId.toString())
                    .put("reason", reason);
            JSONObject value = server.postObject("/api/appeals", token, request);
            return new AppealSnapshot(
                    uuid(value, "id"),
                    uuid(value, "caseId"),
                    value.optString("status", "PENDING"));
        }), callback);
    }

    public void updateDisplayName(
            UUID localUserId,
            String displayName,
            BackendModerationGateway.Callback<Void> callback) {
        run(() -> withMemberRetry(localUserId, (server, token) -> {
            server.patchObject("/api/users/me", token,
                    new JSONObject().put("displayName", displayName));
            return null;
        }), callback);
    }

    private <T> T withMemberRetry(UUID localUserId, MemberCall<T> call) throws Exception {
        return accounts.authenticated(localUserId, call::run);
    }

    private <T> void run(Task<T> task, BackendModerationGateway.Callback<T> callback) {
        executor.execute(() -> {
            try {
                T value = task.run();
                mainHandler.post(() -> callback.onSuccess(value));
            } catch (BackendException error) {
                mainHandler.post(() -> callback.onError(error));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(
                        new BackendException(BackendText.integrationFailed(), error)));
            }
        });
    }

    private static UUID uuid(JSONObject value, String field) {
        try {
            return UUID.fromString(value.optString(field, ""));
        } catch (Exception error) {
            throw new BackendException(0, BackendText.malformedResponse());
        }
    }

    private static UUID nullableUuid(JSONObject value, String field) {
        String raw = nullable(value, field);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception error) {
            throw new BackendException(0, BackendText.malformedResponse());
        }
    }

    private static String nullable(JSONObject value, String field) {
        return value.isNull(field) || value.optString(field, "").isBlank()
                ? null
                : value.optString(field);
    }

    private static long epoch(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private interface Task<T> { T run() throws Exception; }
    private interface MemberCall<T> { T run(BackendClient server, String token) throws Exception; }

    public record NotificationSnapshot(
            UUID id,
            String type,
            String title,
            String body,
            String referenceType,
            UUID referenceId,
            boolean read,
            long createdAt) {}

    public record AppealSnapshot(UUID id, UUID caseId, String status) {}
}
