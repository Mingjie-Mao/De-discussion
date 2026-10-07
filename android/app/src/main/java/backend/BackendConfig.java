package backend;

/**
 * Where the moderation backend is and whether to talk to it at all.
 *
 * <p>Enabled by default and connected to the hosted demonstration API. Network work is asynchronous and a
 * failed submission is shown honestly rather than silently becoming a local-only
 * report. The app always enables the connection when signing in.
 */
public final class BackendConfig {

    /**
     * The hosted demo works on physical devices too. Local development can
     * override this with http://10.0.2.2:8080 in a debug build.
     */
    public static final String DEFAULT_BASE_URL = "https://p01--de-moderation-api--z48dx52bgz5k.code.run";
    public static final String RENDER_FALLBACK_URL = "https://de-moderation-api-demo.onrender.com";
    private static final String KEY_DEMO_HOST_MIGRATED = "northflank_url_migrated_v1";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BASE_URL = "base_url";
    private static final String LEGACY_ADMIN_USERNAME = "admin_username";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";

    private final KeyValueStore store;

    public BackendConfig(KeyValueStore store) {
        this.store = store;
        if (!Boolean.parseBoolean(store.get(KEY_DEMO_HOST_MIGRATED, "false"))) {
            String saved = trimTrailingSlash(store.get(KEY_BASE_URL, "").trim());
            if (RENDER_FALLBACK_URL.equals(saved)) store.put(KEY_BASE_URL, DEFAULT_BASE_URL);
            // Preserve custom hosts, and permit a later explicit rollback to Render.
            store.put(KEY_DEMO_HOST_MIGRATED, "true");
        }
        // Administrator passwords are never persisted. Scrub the plaintext
        // credentials left by earlier demo builds during startup.
        store.remove(LEGACY_ADMIN_USERNAME);
        store.remove(KEY_ADMIN_PASSWORD);
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(store.get(KEY_ENABLED, "true"));
    }

    public void setEnabled(boolean enabled) {
        store.put(KEY_ENABLED, Boolean.toString(enabled));
    }

    public String baseUrl() {
        String value = store.get(KEY_BASE_URL, DEFAULT_BASE_URL);
        return value == null || value.trim().isEmpty() ? DEFAULT_BASE_URL : trimTrailingSlash(value.trim());
    }

    public void setBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : trimTrailingSlash(baseUrl.trim());
        if (value.isEmpty()) {
            store.put(KEY_BASE_URL, "");
            return;
        }
        if (!(value.startsWith("https://") || value.startsWith("http://"))) {
            throw new IllegalArgumentException("Backend URL must start with http:// or https://.");
        }
        store.put(KEY_BASE_URL, value);
    }

    static String trimTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
