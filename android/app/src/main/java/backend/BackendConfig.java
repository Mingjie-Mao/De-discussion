package backend;

/**
 * Where the moderation backend is and whether to talk to it at all.
 *
 * <p>Enabled by default for the development build, whose default points at the
 * host machine from an Android emulator. Network work is asynchronous and a
 * failed submission is shown honestly rather than silently becoming a local-only
 * report. The switch remains available for the original offline demo.
 */
public final class BackendConfig {

    /**
     * The emulator's route to the host machine. A device on the same network
     * needs the host's LAN address instead, which is why this is editable in
     * Settings rather than compiled in.
     */
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_ADMIN_USERNAME = "admin_username";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";

    /** Administrator secrets are session-only; they are never written to disk. */
    private volatile String sessionAdminPassword = "";

    private final KeyValueStore store;

    public BackendConfig(KeyValueStore store) {
        this.store = store;
        // Remove plaintext credentials written by an earlier unfinished version
        // of the integration. The username is harmless to retain; the password
        // is deliberately re-entered after each process restart.
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

    /**
     * The administrator credentials the app signs in with to read the case
     * queue. Demo-only by construction: shipping administrator credentials to a
     * client is exactly what a real deployment must not do, which is why these
     * are typed in on the device rather than built into the app.
     */
    public String adminUsername() {
        return store.get(KEY_ADMIN_USERNAME, "");
    }

    public String adminPassword() {
        return sessionAdminPassword;
    }

    public void setAdminCredentials(String username, String password) {
        store.put(KEY_ADMIN_USERNAME, username == null ? "" : username.trim());
        sessionAdminPassword = password == null ? "" : password;
    }

    public boolean hasAdminCredentials() {
        return !adminUsername().isEmpty() && !adminPassword().isEmpty();
    }

    static String trimTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
