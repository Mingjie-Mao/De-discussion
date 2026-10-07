package com.example.myapplication;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import backend.BackendException;
import backend.BackendModerationGateway;
import backend.BackendRuntime;

/**
 * Machine translations of server posts and comments for a Chinese interface.
 *
 * <p>Display only. Screens ask for the text they are about to show; anything not
 * yet translated is shown as written and queued, and the batch that comes back
 * refreshes the screens observing {@link ServerFeatures}. Editing, reporting and
 * search keep reading the author's own text.
 */
final class ContentTranslations {
    static final String LANGUAGE = "zh-CN";
    private static final int MAX_POSTS = 50;
    private static final int MAX_COMMENTS = 100;
    private static final long RETRY_MS = 5_000;
    private static final int MAX_AUTO_RETRIES = 6;

    /** A translation is only valid for the exact text it was made from. */
    private record Entry(String source, String title, String body) {}

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<UUID, Entry> posts = new HashMap<>();
    private static final Map<UUID, Entry> comments = new HashMap<>();
    private static final Map<UUID, String> wantedPosts = new LinkedHashMap<>();
    private static final Map<UUID, String> wantedComments = new LinkedHashMap<>();
    /** Source text last asked about, and when it may be asked about again. */
    private static final Map<String, Long> asked = new HashMap<>();
    private static final Set<UUID> showingOriginal = new HashSet<>();
    private static final TranslationRequestState requests = new TranslationRequestState();
    private static final Map<UUID, String> retryPosts = new LinkedHashMap<>();
    private static final Map<UUID, String> retryComments = new LinkedHashMap<>();
    private static final Map<String, Integer> retries = new HashMap<>();
    private static Context application;
    private static final Runnable FLUSH = ContentTranslations::flush;
    private static final Runnable RETRY = () -> {
        if (!active(application) || !ServerFeatures.hasObservers()) return;
        wantedPosts.putAll(retryPosts); wantedComments.putAll(retryComments);
        retryPosts.clear(); retryComments.clear();
        flush();
    };
    private static boolean scheduled;

    private ContentTranslations() {}

    static String postTitle(Context context, UUID postId, String title, String body) {
        Entry entry = lookupPost(context, postId, title, body);
        return entry == null || entry.title() == null ? title : entry.title();
    }

    static String postBody(Context context, UUID postId, String title, String body) {
        Entry entry = lookupPost(context, postId, title, body);
        return entry == null ? body : entry.body();
    }

    static String comment(Context context, UUID postId, UUID commentId, String body) {
        if (!active(context) || body == null || body.isBlank() || showingOriginal.contains(postId)) return body;
        Entry entry = comments.get(commentId);
        if (entry != null && entry.source().equals(body)) return entry.body();
        want(wantedComments, "c", commentId, body);
        return body;
    }

    /** Whether the post, or any comment under it shown so far, is displayed translated. */
    static boolean isTranslated(Context context, UUID postId, String title, String body) {
        if (!active(context)) return false;
        Entry entry = posts.get(postId);
        return entry != null && entry.source().equals(source(title, body));
    }

    static boolean isShowingOriginal(UUID postId) {
        return showingOriginal.contains(postId);
    }

    static void toggleOriginal(UUID postId) {
        if (!showingOriginal.remove(postId)) showingOriginal.add(postId);
        ServerFeatures.refreshObservers();
    }

    private static Entry lookupPost(Context context, UUID postId, String title, String body) {
        if (!active(context) || showingOriginal.contains(postId)) return null;
        String source = source(title, body);
        Entry entry = posts.get(postId);
        if (entry != null && entry.source().equals(source)) return entry;
        want(wantedPosts, "p", postId, source);
        return null;
    }

    private static boolean active(Context context) {
        String scope = "";
        if (context != null) {
            application = context.getApplicationContext();
            BackendRuntime runtime = BackendRuntime.from(context);
            if (LANGUAGE.equals(UiPreferences.getLanguageTag(context))
                    && UiPreferences.isLoggedIn(context) && runtime.config().isEnabled()) {
                var session = AccountProfileSync.session(context);
                scope = runtime.config().baseUrl() + "|" + session.userId() + "|"
                        + session.generation() + "|" + UiPreferences.lastLoginWasAdmin(context);
            }
        }
        if (requests.selectScope(scope)) {
            main.removeCallbacks(FLUSH); main.removeCallbacks(RETRY);
            scheduled = false;
            posts.clear();
            comments.clear();
            wantedPosts.clear();
            wantedComments.clear();
            asked.clear();
            showingOriginal.clear();
            retryPosts.clear(); retryComments.clear(); retries.clear();
        }
        return !scope.isEmpty();
    }

    private static void want(Map<UUID, String> wanted, String kind, UUID id, String source) {
        Long retryAt = asked.get(kind + id + source);
        if (retryAt != null && SystemClock.elapsedRealtime() < retryAt) return;
        if (retries.getOrDefault(kind + id + source, 0) >= MAX_AUTO_RETRIES)
            retries.remove(kind + id + source);
        wanted.put(id, source);
        if (!scheduled && !requests.inFlight()) {
            scheduled = true;
            // Let a whole screen of rows ask before sending one request for all of them.
            main.postDelayed(FLUSH, 150);
        }
    }

    private static void flush() {
        scheduled = false;
        if (!active(application) || requests.inFlight() || (wantedPosts.isEmpty() && wantedComments.isEmpty())) return;
        Map<UUID, String> postBatch = take(wantedPosts, MAX_POSTS);
        Map<UUID, String> commentBatch = take(wantedComments, MAX_COMMENTS);
        long never = Long.MAX_VALUE;
        postBatch.forEach((id, source) -> asked.put("p" + id + source, never));
        commentBatch.forEach((id, source) -> asked.put("c" + id + source, never));
        JSONObject request;
        try {
            request = new JSONObject()
                    .put("language", LANGUAGE)
                    .put("postIds", ids(postBatch))
                    .put("commentIds", ids(commentBatch));
        } catch (Exception impossible) {
            return;
        }
        long ticket = requests.begin();
        ServerFeatures.call("POST", "/api/translations", request, new BackendModerationGateway.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                active(application);
                if (!requests.finish(ticket)) return;
                boolean changed = store(value.optJSONArray("posts"), postBatch, posts, true)
                        | store(value.optJSONArray("comments"), commentBatch, comments, false);
                // Pending texts are asked about again later; the rest needed no
                // translation, being Chinese already, and are not asked about again.
                if (value.optBoolean("pending")) retryLater(postBatch, commentBatch, RETRY_MS);
                if (changed) ServerFeatures.refreshObservers();
                next();
            }
            @Override public void onError(BackendException error) {
                active(application);
                if (!requests.finish(ticket)) return;
                retryLater(postBatch, commentBatch, error.status() == 429 || error.status() == 404 ? 60_000 : RETRY_MS);
                next();
            }
        });
    }

    private static void next() {
        if ((!wantedPosts.isEmpty() || !wantedComments.isEmpty()) && !scheduled) {
            scheduled = true;
            main.post(FLUSH);
        }
    }

    private static void retryLater(Map<UUID, String> postBatch, Map<UUID, String> commentBatch, long delay) {
        queueRetry("p", postBatch, posts, retryPosts, delay);
        queueRetry("c", commentBatch, comments, retryComments, delay);
        if (!retryPosts.isEmpty() || !retryComments.isEmpty()) {
            main.removeCallbacks(RETRY);
            main.postDelayed(RETRY, delay);
        }
    }

    private static void queueRetry(String kind, Map<UUID, String> batch, Map<UUID, Entry> cache,
                                   Map<UUID, String> into, long delay) {
        batch.forEach((id, source) -> {
            if (cache.containsKey(id) && cache.get(id).source().equals(source)) return;
            String key = kind + id + source;
            int count = retries.getOrDefault(key, 0) + 1;
            retries.put(key, count);
            asked.put(key, SystemClock.elapsedRealtime() + (count >= MAX_AUTO_RETRIES ? 60_000 : delay));
            if (count < MAX_AUTO_RETRIES) into.put(id, source);
        });
    }

    private static boolean store(JSONArray items, Map<UUID, String> batch, Map<UUID, Entry> into, boolean post) {
        if (items == null) return false;
        boolean changed = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            UUID id;
            try { id = UUID.fromString(item.optString("id")); } catch (IllegalArgumentException ignored) { continue; }
            String source = batch.get(id);
            if (source == null) continue;
            String title = post && !item.isNull("title") ? item.optString("title") : null;
            into.put(id, new Entry(source, title, item.optString("body")));
            changed = true;
        }
        return changed;
    }

    private static Map<UUID, String> take(Map<UUID, String> wanted, int limit) {
        Map<UUID, String> batch = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, String>> iterator = wanted.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < limit) {
            Map.Entry<UUID, String> next = iterator.next();
            batch.put(next.getKey(), next.getValue());
            iterator.remove();
        }
        return batch;
    }

    private static JSONArray ids(Map<UUID, String> batch) {
        List<String> values = new ArrayList<>();
        for (UUID id : batch.keySet()) values.add(id.toString());
        return new JSONArray(values);
    }

    private static String source(String title, String body) {
        return (title == null ? "" : title) + '\u0000' + (body == null ? "" : body);
    }
}
