package backend;

import android.content.Context;

/** One process-wide backend session and token cache. */
public final class BackendRuntime {
    private static volatile BackendRuntime instance;

    private final BackendConfig config;
    private final BackendAdminGateway admin;
    private final BackendUserGateway user;
    private final BackendAccounts accounts;
    private final BackendModerationGateway moderation;
    private final BackendForumGateway forum;
    private final BackendMemberGateway member;

    private BackendRuntime(Context context) {
        BackendText.init(context);
        KeyValueStore store = new PreferencesStore(context);
        this.config = new BackendConfig(store);
        BackendClient client = new BackendClient(config);
        BackendIdentity identity = new BackendIdentity(store);
        BackendMappings mappings = new BackendMappings(store, config);
        this.admin = new BackendAdminGateway(new BackendAdminSession(config, new EncryptedSessionStore(context, "admin")));
        this.user = new BackendUserGateway(new BackendUserSession(config, new EncryptedSessionStore(context, "member")));
        this.accounts = new BackendAccounts(client, identity, mappings, admin.session(), user.session());
        this.moderation = new BackendModerationGateway(client, accounts, mappings);
        this.forum = new BackendForumGateway(client, accounts, mappings);
        this.member = new BackendMemberGateway(client, accounts);
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

    public BackendAdminGateway admin() { return admin; }
    public BackendAccounts accounts() { return accounts; }
    public BackendUserGateway user() { return user; }

    public void bindMemberAccount() {
        if (!user.session().hasSession()) throw new BackendException(401, BackendText.memberLoginRequired());
        accounts.bindMember(java.util.UUID.fromString(user.session().userId()));
    }

    public void bindAdministratorAccount() {
        if (!admin.session().hasSession()) throw new BackendException(401, BackendText.adminLoginRequired());
        accounts.bindAdministrator(java.util.UUID.fromString(admin.session().userId()));
    }

    public BackendConfig config() {
        return config;
    }

    public BackendModerationGateway moderation() {
        return moderation;
    }

    public BackendForumGateway forum() {
        return forum;
    }

    public BackendMemberGateway member() {
        return member;
    }
}
