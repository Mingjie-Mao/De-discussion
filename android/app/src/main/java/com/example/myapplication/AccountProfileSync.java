package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import backend.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;

/** Serialize per-account PATCHes; apply only a server response from the current session. */
final class AccountProfileSync {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String fetching;
    private static String fetchedScope;
    private static long fetchedAt;
    private AccountProfileSync() {}
    static BackendUserSession session(Context context) {
        BackendRuntime runtime = BackendRuntime.from(context);
        return UiPreferences.lastLoginWasAdmin(context) ? runtime.admin().session() : runtime.user().session();
    }
    static void applyCached(Context context) {
        BackendUserSession session = session(context);
        if (session.hasSession()) UiPreferences.applyServerProfile(context, session.profile());
    }
    static void fetch(Activity activity, Runnable success) {
        String scope = scope(activity);
        long age = System.currentTimeMillis() - session(activity).profileFetchedAt();
        if (age >= 0 && age < 30000) return;
        if (scope.equals(fetching) || (scope.equals(fetchedScope) && System.currentTimeMillis() - fetchedAt < 30000)) return;
        fetching = scope;
        run(activity, null, null, () -> {
            fetching = null; fetchedScope = scope; fetchedAt = System.currentTimeMillis(); success.run();
        }, () -> fetching = null);
    }
    static void update(Activity activity, JSONObject body, Runnable success) {
        run(activity, body, null, success, () -> {});
    }
    static void avatar(Activity activity, Uri uri, Runnable success) {
        run(activity, new JSONObject(), uri, success, () -> {});
    }
    private static String scope(Context context) {
        BackendUserSession session = session(context);
        return BackendRuntime.from(context).config().baseUrl() + "|" + session.userId() + "|" + session.generation() + "|" + UiPreferences.lastLoginWasAdmin(context);
    }
    private static void run(Activity activity, JSONObject body, Uri image, Runnable success, Runnable failure) {
        Context context = activity.getApplicationContext();
        BackendUserSession signed = session(context);
        String scope = scope(context);
        long generation = signed.generation();
        worker.execute(() -> {
            try {
                JSONObject profile = signed.withClient((client, token) -> {
                    if (image != null) {
                        var upload = BackendMedia.readAvatar(context, image);
                        var uploaded = client.upload("/api/media", token, upload.fileName(), upload.contentType(), upload.bytes());
                        body.put("avatarMediaId", uploaded.getString("id"));
                    }
                    return body == null ? client.getObject("/api/users/me", token) : client.patchObject("/api/users/me", token, body);
                });
                if (body != null && (!profile.has("languageTag") || !profile.has("theme") || !profile.has("avatarColor")))
                    throw new BackendException(0, "Account settings require the updated backend. Please retry after deployment.");
                // The session itself validates user identity before persisting the cache.
                if (!scope.equals(scope(context))) return;
                signed.acceptProfile(profile, generation);
                main.post(() -> {
                    if (!scope.equals(scope(context))) return;
                    UiPreferences.applyServerProfile(context, profile);
                    if (!activity.isFinishing() && !activity.isDestroyed()) success.run();
                });
            } catch (Exception error) {
                BackendException wrapped = error instanceof BackendException ? (BackendException)error : new BackendException("Could not sync account settings.", error);
                main.post(() -> {
                    if (!scope.equals(scope(context))) { failure.run(); return; }
                    failure.run();
                    UiPreferences.handleExpiredSession(activity, wrapped);
                    if (!activity.isFinishing() && !activity.isDestroyed()) Toast.makeText(activity, wrapped.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
