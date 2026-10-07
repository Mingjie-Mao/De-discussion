package backend;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Backend-first forum writes plus feed/thread synchronization. */
public final class BackendForumGateway {
    private final BackendClient client;
    private final BackendAccounts accounts;
    private final BackendMappings mappings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService commentsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService profileExecutor = Executors.newSingleThreadExecutor();
    public static final String PROFILE_CURSOR_KEY = "author";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    BackendForumGateway(BackendClient client, BackendAccounts accounts, BackendMappings mappings) {
        this.client = client;
        this.accounts = accounts;
        this.mappings = mappings;
    }

    public void fetchFeed(String forumKey, BackendModerationGateway.Callback<List<PostSnapshot>> callback) {
        fetchFeedPage(forumKey, null, new BackendModerationGateway.Callback<>() {
            @Override public void onSuccess(FeedPage page) { callback.onSuccess(page.items()); }
            @Override public void onError(BackendException error) { callback.onError(error); }
        });
    }

    public void fetchFeedPage(String forumKey, String cursor, BackendModerationGateway.Callback<FeedPage> callback) {
        run(() -> {
            String encoded = URLEncoder.encode(forumKey, StandardCharsets.UTF_8.name());
            String path = "/api/posts?forum=" + encoded + "&size=100";
            if (cursor != null && !cursor.isBlank()) path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8.name());
            JSONObject page = client.getObject(path, null);
            JSONArray items = page.optJSONArray("items");
            List<PostSnapshot> result = new ArrayList<>();
            if (items != null) for (int i = 0; i < items.length(); i++) result.add(post(items.getJSONObject(i)));
            return new FeedPage(result, page.optBoolean("hasMore", false), nullable(page, "nextCursor"));
        }, callback);
    }

    public void fetchThread(UUID postId, BackendModerationGateway.Callback<List<CommentSnapshot>> callback) {
        fetchThreadPage(postId, null, new BackendModerationGateway.Callback<>() {
            @Override public void onSuccess(CommentPage page) { callback.onSuccess(page.items()); }
            @Override public void onError(BackendException error) { callback.onError(error); }
        });
    }

    /** One bounded author page across all forums; further pages require a user tap. */
    public void fetchProfilePage(UUID userId, java.util.Map<String, String> cursors,
                                 BackendModerationGateway.Callback<ProfilePage> callback) {
        java.util.Map<String, String> requested = new java.util.LinkedHashMap<>(cursors);
        run(profileExecutor, () -> {
            List<PostSnapshot> ownPosts = new ArrayList<>();
            java.util.Map<String, String> next = new java.util.LinkedHashMap<>();
            String path = "/api/posts/authors/" + userId + "?size=30";
            String requestedCursor = requested.get(PROFILE_CURSOR_KEY);
            if (requestedCursor != null) path += "&cursor=" + URLEncoder.encode(requestedCursor, StandardCharsets.UTF_8.name());
            JSONObject page = client.getObject(path, null);
            JSONArray items = page.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                PostSnapshot item = post(items.getJSONObject(i));
                if (userId.equals(item.authorId())) ownPosts.add(item);
            }
            String cursor = nullable(page, "nextCursor");
            if (page.optBoolean("hasMore", false) && cursor != null) next.put(PROFILE_CURSOR_KEY, cursor);
            return new ProfilePage(ownPosts, next);
        }, callback);
    }

    public void fetchThreadPage(UUID postId, String cursor,
                                BackendModerationGateway.Callback<CommentPage> callback) {
        run(commentsExecutor, () -> {
            String path = "/api/posts/" + postId + "/comments?size=100";
            if (cursor != null && !cursor.isBlank()) {
                path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8.name());
            }
            JSONObject page = client.getObject(path, null);
            JSONArray items = page.optJSONArray("items");
            List<CommentSnapshot> result = new ArrayList<>();
            if (items != null) for (int i = 0; i < items.length(); i++) flatten(items.getJSONObject(i), result);
            return new CommentPage(result, page.optBoolean("hasMore", false),
                    nullable(page, "nextCursor"));
        }, callback);
    }

    public void createPost(UUID localUserId, String forumKey, String title, String body, MediaUpload media,
                           BackendModerationGateway.Callback<PostSnapshot> callback) {
        createPost(localUserId,forumKey,title,body,media,"Study",callback);
    }
    public void createPost(UUID localUserId,String forumKey,String title,String body,MediaUpload media,String category,
                           BackendModerationGateway.Callback<PostSnapshot> callback) {
        run(() -> {
            String token = accounts.tokenForLocalUser(localUserId);
            UUID mediaId = upload(localUserId, token, media);
            JSONObject request = new JSONObject().put("forumKey", forumKey).put("title", title).put("body", body).put("category",category);
            if (mediaId != null) request.put("mediaId", mediaId.toString());
            JSONObject response = withMemberRetry(localUserId, token,
                    (server, next) -> server.postObject("/api/posts", next, request));
            PostSnapshot created = post(response);
            mappings.putPost(created.id(), created.id());
            return created.withAuthor(localUserId);
        }, callback);
    }

    public void updatePost(UUID localUserId, UUID localPostId, String title, String body, MediaUpload media, String retainedMediaUrl,
                           BackendModerationGateway.Callback<PostSnapshot> callback) {
        updatePost(localUserId,localPostId,title,body,media,retainedMediaUrl,null,callback);
    }
    public void updatePost(UUID localUserId,UUID localPostId,String title,String body,MediaUpload media,String retainedMediaUrl,String category,
                           BackendModerationGateway.Callback<PostSnapshot> callback) {
        run(() -> {
            UUID remoteId = remotePost(localPostId);
            String token = accounts.tokenForLocalUser(localUserId);
            UUID mediaId = upload(localUserId, token, media);
            if (media == null) mediaId = mediaId(retainedMediaUrl);
            JSONObject request = new JSONObject().put("title", title).put("body", body).put("category",category);
            if (mediaId != null) request.put("mediaId", mediaId.toString());
            JSONObject response = withMemberRetry(localUserId, token,
                    (server, next) -> server.patchObject("/api/posts/" + remoteId, next, request));
            return post(response).withAuthor(localUserId);
        }, callback);
    }

    /** An existing backend attachment is retained when editing only the text. */
    static UUID mediaId(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String path = java.net.URI.create(url).getPath();
            if (path == null || !path.matches("/api/media/[a-fA-F0-9-]{36}")) return null;
            return UUID.fromString(path.substring("/api/media/".length()));
        } catch (IllegalArgumentException ignored) { return null; }
    }

    public void deletePost(UUID localUserId, UUID localPostId, BackendModerationGateway.Callback<Void> callback) {
        run(() -> {
            UUID remoteId = remotePost(localPostId);
            String token = accounts.tokenForLocalUser(localUserId);
            withMemberRetry(localUserId, token, (server, next) -> { server.delete("/api/posts/" + remoteId, next); return null; });
            return null;
        }, callback);
    }

    public void createComment(UUID localUserId, UUID localPostId, UUID localParentId, String body, MediaUpload media,
                              BackendModerationGateway.Callback<CommentSnapshot> callback) {
        run(() -> {
            UUID remotePostId = remotePost(localPostId);
            UUID remoteParentId = localParentId == null ? null : remoteComment(localParentId);
            String token = accounts.tokenForLocalUser(localUserId);
            UUID mediaId = upload(localUserId, token, media);
            JSONObject request = new JSONObject().put("body", body);
            if (remoteParentId != null) request.put("parentCommentId", remoteParentId.toString());
            if (mediaId != null) request.put("mediaId", mediaId.toString());
            JSONObject response = withMemberRetry(localUserId, token,
                    (server, next) -> server.postObject("/api/posts/" + remotePostId + "/comments", next, request));
            CommentSnapshot created = comment(response);
            mappings.putComment(created.id(), created.id());
            return created.withAuthor(localUserId);
        }, callback);
    }

    private UUID upload(UUID localUserId, String token, MediaUpload media) {
        if (media == null || media.bytes() == null || media.bytes().length == 0) return null;
        JSONObject response = withMemberRetry(localUserId, token,
                (server, next) -> server.upload("/api/media", next, media.fileName(), media.contentType(), media.bytes()));
        return uuid(response, "id");
    }

    private UUID remotePost(UUID localId) {
        UUID remote = mappings.remotePost(localId);
        if (remote == null) throw new BackendException(0, "This post has not been synchronized. Refresh the forum and try again.");
        return remote;
    }

    private UUID remoteComment(UUID localId) {
        UUID remote = mappings.remoteComment(localId);
        if (remote == null) throw new BackendException(0, "This comment has not been synchronized. Refresh the thread and try again.");
        return remote;
    }

    public PostSnapshot post(JSONObject value) {
        UUID remoteId = uuid(value, "id");
        JSONObject author = value.optJSONObject("author");
        UUID remoteAuthor = author == null ? null : uuid(author, "id");
        UUID localAuthor = remoteAuthor;
        mappings.putPost(remoteId, remoteId);
        return new PostSnapshot(remoteId, localAuthor,
                author == null ? "unknown" : author.optString("displayName", author.optString("username", "unknown")),
                value.optString("forumKey", ""), value.optString("title", ""), value.optString("body", ""),
                nullable(value, "mediaUrl"), epoch(value.optString("createdAt", "")),value.optString("category","Study"),value.isNull("pinRank")?null:value.optInt("pinRank"),author==null?null:nullable(author,"avatarUrl"),author==null?0:author.optInt("avatarColor"));
    }

    private void flatten(JSONObject value, List<CommentSnapshot> output) {
        CommentSnapshot snapshot = comment(value);
        output.add(snapshot);
        JSONArray replies = value.optJSONArray("replies");
        if (replies != null) for (int i = 0; i < replies.length(); i++) flatten(replies.optJSONObject(i), output);
    }

    private CommentSnapshot comment(JSONObject value) {
        UUID remoteId = uuid(value, "id");
        JSONObject author = value.optJSONObject("author");
        UUID remoteAuthor = author == null ? null : uuid(author, "id");
        UUID localAuthor = remoteAuthor;
        mappings.putComment(remoteId, remoteId);
        return new CommentSnapshot(remoteId, nullableUuid(value, "parentCommentId"), localAuthor,
                author == null ? "unknown" : author.optString("displayName", author.optString("username", "unknown")),
                value.optString("body", ""), nullable(value, "mediaUrl"), epoch(value.optString("createdAt", "")),author==null?null:nullable(author,"avatarUrl"),author==null?0:author.optInt("avatarColor"));
    }

    public record AuthoredComment(PostSnapshot post,CommentSnapshot comment) {}
    public record AuthoredCommentPage(List<AuthoredComment> items,boolean hasMore,String nextCursor) {}
    public void fetchAuthoredComments(UUID userId,String cursor,BackendModerationGateway.Callback<AuthoredCommentPage> callback) {
        fetchAuthoredComments(userId,userId,cursor,callback);
    }
    public void fetchAuthoredComments(UUID userId,UUID actingUser,String cursor,BackendModerationGateway.Callback<AuthoredCommentPage> callback) {
        run(commentsExecutor,()->{
            String path="/api/community/users/"+userId+"/comments?size=30";
            if(cursor!=null) path+="&cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8.name());
            final String endpoint=path;
            JSONObject page=accounts.authenticated(actingUser,(server,token)->server.getObject(endpoint,token));
            var items=new ArrayList<AuthoredComment>(); JSONArray rows=page.optJSONArray("items");
            if(rows!=null) for(int i=0;i<rows.length();i++) { var row=rows.getJSONObject(i); items.add(new AuthoredComment(post(row.getJSONObject("post")),comment(row))); }
            return new AuthoredCommentPage(items,page.optBoolean("hasMore"),nullable(page,"nextCursor"));
        },callback);
    }
    public void fetchPost(UUID postId,BackendModerationGateway.Callback<PostSnapshot> callback) {
        run(()->post(client.getObject("/api/posts/"+postId,null)),callback);
    }

    private <T> T withMemberRetry(UUID localUserId, String token, MemberCall<T> call) {
        return accounts.authenticated(localUserId, call::run);
    }

    private <T> void run(Task<T> task, BackendModerationGateway.Callback<T> callback) {
        run(executor, task, callback);
    }
    private <T> void run(ExecutorService queue, Task<T> task, BackendModerationGateway.Callback<T> callback) {
        queue.execute(() -> {
            try { T value = task.run(); mainHandler.post(() -> callback.onSuccess(value)); }
            catch (BackendException error) { mainHandler.post(() -> callback.onError(error)); }
            catch (Exception error) { mainHandler.post(() -> callback.onError(new BackendException(BackendText.integrationFailed(), error))); }
        });
    }

    private static UUID uuid(JSONObject value, String field) {
        try { return UUID.fromString(value.optString(field, "")); }
        catch (Exception error) { throw new BackendException(0, BackendText.malformedResponse()); }
    }
    private static UUID nullableUuid(JSONObject value, String field) {
        String raw = nullable(value, field); return raw == null ? null : UUID.fromString(raw);
    }
    private static String nullable(JSONObject value, String field) {
        return value == null || value.isNull(field) || value.optString(field, "").isBlank() ? null : value.optString(field);
    }
    private static long epoch(String value) {
        try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { return System.currentTimeMillis(); }
    }

    private interface Task<T> { T run() throws Exception; }
    private interface MemberCall<T> { T run(BackendClient server, String token); }

    public record MediaUpload(String fileName, String contentType, byte[] bytes) {}
    public record PostSnapshot(UUID id, UUID authorId, String authorName, String forumKey, String title,
                               String body, String mediaUrl, long createdAt, String category, Integer pinRank, String avatarUrl, int avatarColor) {
        public PostSnapshot(UUID id,UUID authorId,String authorName,String forumKey,String title,String body,String mediaUrl,long createdAt,String category,Integer pinRank) {
            this(id,authorId,authorName,forumKey,title,body,mediaUrl,createdAt,category,pinRank,null,0);
        }
        public PostSnapshot(UUID id,UUID authorId,String authorName,String forumKey,String title,String body,String mediaUrl,long createdAt) {
            this(id,authorId,authorName,forumKey,title,body,mediaUrl,createdAt,"Study",null);
        }
        PostSnapshot withAuthor(UUID id) { return new PostSnapshot(this.id,id,authorName,forumKey,title,body,mediaUrl,createdAt,category,pinRank,avatarUrl,avatarColor); }
    }
    public record CommentSnapshot(UUID id, UUID parentId, UUID authorId, String authorName, String body,
                                  String mediaUrl, long createdAt, String avatarUrl, int avatarColor) {
        public CommentSnapshot(UUID id, UUID parentId, UUID authorId, String authorName, String body, String mediaUrl, long createdAt) {
            this(id,parentId,authorId,authorName,body,mediaUrl,createdAt,null,0);
        }
        CommentSnapshot withAuthor(UUID id) { return new CommentSnapshot(this.id, parentId, id, authorName, body, mediaUrl, createdAt,avatarUrl,avatarColor); }
    }
    public record CommentPage(List<CommentSnapshot> items, boolean hasMore, String nextCursor) {}
    public record FeedPage(List<PostSnapshot> items, boolean hasMore, String nextCursor) {}
    public record ProfilePage(List<PostSnapshot> items, java.util.Map<String, String> cursors) {}
}
