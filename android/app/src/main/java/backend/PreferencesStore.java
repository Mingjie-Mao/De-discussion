package backend;

import android.content.Context;
import android.content.SharedPreferences;

/** The on-device {@link KeyValueStore}, backed by the app's own preferences file. */
public final class PreferencesStore implements KeyValueStore {
    private static final String PREFS = "moderation_backend";

    private final SharedPreferences preferences;

    public PreferencesStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public String get(String key, String fallback) {
        return preferences.getString(key, fallback);
    }

    @Override
    public void put(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    @Override
    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }
}
