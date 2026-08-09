package backend;

import android.content.Context;

/** One process-wide backend session and token cache. */
public final class BackendRuntime {
    private static volatile BackendRuntime instance;

    private final BackendConfig config;
    private final BackendModerationGateway moderation;

    private BackendRuntime(Context context) {
        BackendText.init(context);
        KeyValueStore store = new PreferencesStore(context);
        this.config = new BackendConfig(store);
        BackendClient client = new BackendClient(config);
        BackendIdentity identity = new BackendIdentity(store);
        BackendAccounts accounts = new BackendAccounts(client, identity);
        this.moderation = new BackendModerationGateway(
                config, client, accounts, new BackendMappings(store, config));
    }

    public static BackendRuntime from(Context context) {
        BackendRuntime current = instance;
        if (current != null) {
            return current;
        }
        synchronized (BackendRuntime.class) {
            if (instance == null) {
                instance = new BackendRuntime(context.getApplicationContext());
            }
            return instance;
        }
    }

    public BackendConfig config() {
        return config;
    }

    public BackendModerationGateway moderation() {
        return moderation;
    }
}
