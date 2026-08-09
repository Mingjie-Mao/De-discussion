package backend;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The application-facing moderation integration.
 *
 * <p>Local content is mirrored lazily when it is first reported. That keeps the
 * existing offline feed intact while ensuring the report points at real backend
 * content, authored by the corresponding local account, so HIDE and BAN have the
 * same meaning on both sides.
 */
public final class BackendModerationGateway {
    private static final int POST_TITLE_LIMIT = 200;
    private static final int POST_BODY_LIMIT = 20_000;
    private static final int COMMENT_BODY_LIMIT = 10_000;

    private final BackendConfig config;
    private final BackendClient client;
    private final BackendAccounts accounts;
    private final BackendMappings mappings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    BackendModerationGateway(
            BackendConfig config,
            BackendClient client,
            BackendAccounts accounts,
            BackendMappings mappings) {
        this.config = config;
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
     * Mirror the content this report points at, then file the report.
     *
     * <p>Succeeds with the new report's id, or with {@code null} when this
     * account had already reported the same target — see {@link #submitNow}.
     * Either way the content is under review, which is all a caller acts on.
     */
    public void submitReport(
            BackendReportTarget target,
            UUID reporterId,
            Callback<UUID> callback) {
        run(() -> submitWithStaleMappingRecovery(target, reporterId), callback);
    }

    /** Cases an engine has judged and a human has not yet ruled on. */
    public void fetchReviewCases(Callback<List<BackendReviewCase>> callback) {
        fetchCases("AWAITING_REVIEW", callback);
    }

    /** Cases that already carry a decision, which an administrator may revise. */
    public void fetchResolvedCases(Callback<List<BackendReviewCase>> callback) {
        fetchCases("RESOLVED", callback);
    }

    public void fetchCases(String status, Callback<List<BackendReviewCase>> callback) {
        run(() -> fetchCasesNow(status), callback);
    }

    public void decide(UUID caseId, String action, String note, Callback<Void> callback) {
        run(() -> {
            JSONObject request = new JSONObject()
                    .put("action", action)
                    .put("note", note == null ? "" : note);
            adminPost("/api/admin/moderation-cases/" + caseId + "/decision", request);
            return null;
        }, callback);
    }

    private UUID submitWithStaleMappingRecovery(BackendReportTarget target, UUID reporterId) throws Exception {
        try {
            return submitNow(target, reporterId);
        } catch (BackendException first) {
            if (!first.isNotFound()) {
                throw first;
            }
            // Most often the backend database was recreated while the app kept
            // its SharedPreferences. Rebuild once; a second 404 is a real error.
            mappings.clear(target);
            return submitNow(target, reporterId);
        }
    }

    private UUID submitNow(BackendReportTarget target, UUID reporterId) throws Exception {
        UUID remotePostId = ensurePost(target.post());
        UUID lastCommentId = null;

        for (BackendReportTarget.CommentSnapshot comment : target.comments()) {
            lastCommentId = ensureComment(remotePostId, lastCommentId, comment);
        }

        UUID remoteTargetId = target.targetType() == BackendReportTarget.TargetType.POST
                ? remotePostId
                : lastCommentId;
        if (remoteTargetId == null) {
            throw new BackendException(0, "A comment report did not contain a comment to mirror.");
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

    private UUID ensurePost(BackendReportTarget.PostSnapshot post) throws Exception {
        UUID existing = mappings.remotePost(post.localId());
        if (existing != null) {
            return existing;
        }

        JSONObject request = new JSONObject()
                .put("forumKey", nonBlank(shorten(post.forumKey(), 50), "de"))
                .put("title", nonBlank(shorten(post.title(), POST_TITLE_LIMIT), "Untitled"))
                .put("body", nonBlank(shorten(post.body(), POST_BODY_LIMIT), "[Image attachment]"));
        JSONObject response = memberPost(post.authorId(), "/api/posts", request);
        UUID remote = requireUuid(response, "id");
        mappings.putPost(post.localId(), remote);
        return remote;
    }

    private UUID ensureComment(
            UUID remotePostId,
            UUID remoteParentId,
            BackendReportTarget.CommentSnapshot comment) throws Exception {
        UUID existing = mappings.remoteComment(comment.localId());
        if (existing != null) {
            return existing;
        }

        JSONObject request = new JSONObject()
                .put("body", nonBlank(shorten(comment.body(), COMMENT_BODY_LIMIT), "[Image attachment]"));
        if (remoteParentId != null) {
            request.put("parentCommentId", remoteParentId.toString());
        }

        JSONObject response = memberPost(
                comment.authorId(), "/api/posts/" + remotePostId + "/comments", request);
        UUID remote = requireUuid(response, "id");
        mappings.putComment(comment.localId(), remote);
        return remote;
    }

    private List<BackendReviewCase> fetchCasesNow(String status) throws Exception {
        if (!config.hasAdminCredentials()) {
            throw new BackendException(0, BackendText.adminCredentialsMissing());
        }

        JSONArray summaries = adminGetArray(
                "/api/admin/moderation-cases?status=" + status + "&size=100");
        List<BackendReviewCase> result = new ArrayList<>();

        for (int i = 0; i < summaries.length(); i++) {
            JSONObject summary = summaries.getJSONObject(i);
            UUID caseId = requireUuid(summary, "id");
            JSONObject detail = adminGetObject("/api/admin/moderation-cases/" + caseId);
            JSONObject moderationCase = detail.getJSONObject("moderationCase");
            JSONObject content = detail.optJSONObject("content");
            UUID remoteTarget = requireUuid(moderationCase, "targetId");

            result.add(new BackendReviewCase(
                    caseId,
                    remoteTarget,
                    mappings.localForRemote(remoteTarget),
                    text(moderationCase, "targetType", ""),
                    text(moderationCase, "status", ""),
                    moderationCase.optInt("reportCount", 0),
                    text(moderationCase, "engine", ""),
                    text(moderationCase, "recommendedDecision", ""),
                    moderationCase.optDouble("confidence", 0.0),
                    text(moderationCase, "rationale", ""),
                    content == null ? "Reported content" : text(content, "title", "Reported content"),
                    content == null ? BackendText.contentUnavailable() : text(content, "body", ""),
                    text(moderationCase, "createdAt", ""),
                    text(moderationCase, "finalAction", ""),
                    text(moderationCase, "decidedAt", "")));
        }
        return result;
    }

    private JSONObject memberPost(UUID localUserId, String path, JSONObject body) {
        String token = accounts.tokenForLocalUser(localUserId);
        try {
            return client.postObject(path, token, body);
        } catch (BackendException error) {
            if (!error.isUnauthorised()) {
                throw error;
            }
            accounts.forgetLocalUser(localUserId);
            return client.postObject(path, accounts.tokenForLocalUser(localUserId), body);
        }
    }

    private JSONObject adminPost(String path, JSONObject body) {
        String token = adminToken();
        try {
            return client.postObject(path, token, body);
        } catch (BackendException error) {
            if (!error.isUnauthorised()) {
                throw error;
            }
            forgetAdmin();
            return client.postObject(path, adminToken(), body);
        }
    }

    private JSONArray adminGetArray(String path) {
        String token = adminToken();
        try {
            return client.getArray(path, token);
        } catch (BackendException error) {
            if (!error.isUnauthorised()) {
                throw error;
            }
            forgetAdmin();
            return client.getArray(path, adminToken());
        }
    }

    private JSONObject adminGetObject(String path) {
        String token = adminToken();
        try {
            return client.getObject(path, token);
        } catch (BackendException error) {
            if (!error.isUnauthorised()) {
                throw error;
            }
            forgetAdmin();
            return client.getObject(path, adminToken());
        }
    }

    private String adminToken() {
        return accounts.tokenForAdmin(config.adminUsername(), config.adminPassword());
    }

    private void forgetAdmin() {
        accounts.forgetAdmin(config.adminUsername());
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
