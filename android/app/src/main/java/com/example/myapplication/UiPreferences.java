package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class UiPreferences {
    private static final String PREFS = "ui_preferences";
    private static final String KEY_UID = "profile_uid";
    private static final String KEY_NICKNAME = "profile_nickname";
    private static final String KEY_AVATAR_INDEX = "profile_avatar_index";
    private static final String KEY_LANGUAGE_TAG = "language_tag";
    private static final String KEY_DARK_THEME = "dark_theme";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_SESSION_ADMIN = "session_admin";
    private static final String KEY_AVATAR_IMAGE_URI = "avatar_image_uri";

    private static final String DEFAULT_UID = "uid_2100_001";
    private static final String DEFAULT_LANGUAGE_TAG = "en";
    private static final int[] GOOGLE_COLORS = {
            Color.rgb(205, 220, 255),
            Color.rgb(208, 234, 236),
            Color.rgb(135, 228, 215),
            Color.rgb(179, 239, 162),
            Color.rgb(255, 228, 126),
            Color.rgb(255, 213, 190),
            Color.rgb(255, 204, 188),
            Color.rgb(255, 206, 216),
            Color.rgb(255, 198, 238),
            Color.rgb(228, 205, 255)
    };

    private UiPreferences() {
    }

    public static boolean handleExpiredSession(android.app.Activity activity, backend.BackendException error) {
        if (!handleExpiredSession((Context) activity, error)) return false;
        activity.finish();
        return true;
    }

    public static boolean handleExpiredSession(Context context, backend.BackendException error) {
        if (!error.isUnauthorised() || isLoggedIn(context)) return false;
        // Concurrent failed requests may arrive after the first redirect.
        if (!prefs(context).getBoolean(KEY_LOGGED_IN, false)) return true;
        clearLoginSession(context);
        android.content.Intent login = new android.content.Intent(context, LoginActivity.class);
        login.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(login);
        return true;
    }

    public static void applyAppearance(Context context) {
        AppCompatDelegate.setDefaultNightMode(isDarkTheme(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        if(android.os.Build.VERSION.SDK_INT >= 33) {
            // AppCompat 1.6 looks up an active delegate on API 33+. There may be
            // none yet during a restored account's cold start; use the context directly.
            android.app.LocaleManager manager=context.getSystemService(android.app.LocaleManager.class);
            android.os.LocaleList locales=android.os.LocaleList.forLanguageTags(getLanguageTag(context));
            if(manager!=null&&!locales.equals(manager.getApplicationLocales())) manager.setApplicationLocales(locales);
        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(getLanguageTag(context)));
    }

    public static void applyServerProfile(Context context, org.json.JSONObject profile) {
        if (!profile.has("id")) return;
        String avatar = profile.isNull("avatarUrl") ? null : profile.optString("avatarUrl", null);
        if (avatar != null && avatar.startsWith("/api/media/")) avatar = backend.BackendRuntime.from(context).config().baseUrl() + avatar;
        prefs(context).edit().putString(KEY_UID, profile.optString("id"))
                .putString(KEY_NICKNAME, profile.optString("displayName", profile.optString("username")))
                .putInt(KEY_AVATAR_INDEX, profile.optInt("avatarColor", 0))
                .putString(KEY_AVATAR_IMAGE_URI, avatar)
                .putString(KEY_LANGUAGE_TAG, profile.optString("languageTag", "en"))
                .putBoolean(KEY_DARK_THEME, "dark".equals(profile.optString("theme", "light"))).commit();
    }

    public static String getProfileUid(Context context) {
        return prefs(context).getString(KEY_UID, DEFAULT_UID);
    }

    public static void setProfileUid(Context context, String uid) {
        prefs(context).edit().putString(KEY_UID, uid).apply();
    }

    public static String getProfileNickname(Context context) {
        return prefs(context).getString(KEY_NICKNAME, defaultNickname());
    }

    public static void setProfileNickname(Context context, String nickname) {
        prefs(context).edit().putString(KEY_NICKNAME, nickname).apply();
    }

    public static int getAvatarIndex(Context context) {
        return prefs(context).getInt(KEY_AVATAR_INDEX, 0);
    }

    public static void setAvatarIndex(Context context, int avatarIndex) {
        prefs(context).edit().putInt(KEY_AVATAR_INDEX, avatarIndex).commit();
    }

    public static int getAvatarColor(Context context) {
        return getGoogleColor(getAvatarIndex(context));
    }

    public static int getGoogleColor(int colorIndex) {
        return GOOGLE_COLORS[normalizeColorIndex(colorIndex)];
    }

    public static int getGoogleColorCount() {
        return GOOGLE_COLORS.length;
    }

    public static String getLanguageTag(Context context) {
        return prefs(context).getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG);
    }

    public static void setLanguageTag(Context context, String languageTag) {
        prefs(context).edit().putString(KEY_LANGUAGE_TAG, languageTag).commit();
    }

    public static boolean isDarkTheme(Context context) {
        return prefs(context).getBoolean(KEY_DARK_THEME, false);
    }

    public static void setDarkTheme(Context context, boolean darkTheme) {
        prefs(context).edit().putBoolean(KEY_DARK_THEME, darkTheme).commit();
    }

    public static boolean isLoggedIn(Context context) {
        return prefs(context).getBoolean(KEY_LOGGED_IN, false)
                && (prefs(context).getBoolean(KEY_SESSION_ADMIN, false)
                    ? backend.BackendRuntime.from(context).admin().session().hasSession()
                    : backend.BackendRuntime.from(context).user().session().hasSession());
    }

    public static boolean lastLoginWasAdmin(Context context) {
        return prefs(context).getBoolean(KEY_SESSION_ADMIN, false);
    }

    public static boolean isAdminSession(Context context) {
        return prefs(context).getBoolean(KEY_SESSION_ADMIN, false)
                && backend.BackendRuntime.from(context).admin().session().hasSession();
    }

    public static void setLoginSession(Context context, boolean adminSession) {
        prefs(context).edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putBoolean(KEY_SESSION_ADMIN, adminSession)
                .commit();
    }

    public static String getAvatarImageUri(Context context) {
        return prefs(context).getString(KEY_AVATAR_IMAGE_URI, null);
    }

    public static void setAvatarImageUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_AVATAR_IMAGE_URI, uri).apply();
    }

    public static void clearLoginSession(Context context) {
        backend.BackendRuntime.from(context).admin().clear();
        backend.BackendRuntime.from(context).user().clear();
        prefs(context).edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .remove(KEY_SESSION_ADMIN)
                .commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String defaultNickname() {
        return java.util.Locale.getDefault().getLanguage().equals("zh") ? "校园伙伴" : "Campus Buddy";
    }

    private static int normalizeColorIndex(int colorIndex) {
        if (colorIndex < 0) {
            return 0;
        }
        if (colorIndex >= GOOGLE_COLORS.length) {
            return GOOGLE_COLORS.length - 1;
        }
        return colorIndex;
    }
}
