package backend;

import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

/**
 * Signs in as the member account that owns a request.
 *
 * <p>Mirrored content is authored by a backend account per local user rather
 * than by one shared service account. It costs a row each and makes the
 * difference between a {@code BAN} decision meaning something — the account that
 * wrote the content is the account that gets banned, and
 * {@code AccountStateFilter} then rejects its token — and it landing on a
 * bookkeeping identity nobody would notice.
 */
public final class BackendAccounts {

    private final BackendClient client;
    private final BackendIdentity identity;
    private final BackendMappings mappings;
    private final BackendAdminSession admin;
    private final BackendUserSession member;
    private final java.util.Set<UUID> administratorIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Tokens live for the session only; losing them costs one extra login. */
    private final Map<String, String> tokensByUsername = new java.util.concurrent.ConcurrentHashMap<>();

    public BackendAccounts(BackendClient client, BackendIdentity identity, BackendMappings mappings) {
        this(client, identity, mappings, null);
    }

    BackendAccounts(BackendClient client, BackendIdentity identity, BackendMappings mappings, BackendAdminSession admin) {
        this(client, identity, mappings, admin, null);
    }

    BackendAccounts(BackendClient client, BackendIdentity identity, BackendMappings mappings, BackendAdminSession admin, BackendUserSession member) {
        this.client = client;
        this.identity = identity;
        this.mappings = mappings;
        this.admin = admin;
        this.member = member;
    }

    void bindAdministrator(UUID id) {
        administratorIds.add(id);
        mappings.putUser(id, id);
    }

    void bindMember(UUID id) { mappings.putUser(id, id); }

    /** An expired administrator session never falls back to a generated member account. */
    public <T> T authenticated(UUID localId, BackendAdminSession.ClientOperation<T> operation) {
        if (administratorIds.contains(localId)) {
            if (admin == null || !localId.toString().equals(admin.userId())) throw new BackendException(401, BackendText.adminLoginRequired());
            return admin.withClient(operation);
        }
        if (member != null) {
            if (!localId.toString().equals(member.userId())) throw new BackendException(401, BackendText.memberLoginRequired());
            return member.withClient(operation);
        }
        String token = tokenForLocalUser(localId);
        try { return operation.run(client, token); }
        catch (BackendException error) {
            if (error.status() != 401) throw error;
            forgetLocalUser(localId);
            try { return operation.run(client, tokenForLocalUser(localId)); }
            catch (BackendException second) { throw second; }
            catch (Exception second) { throw new BackendException(BackendText.integrationFailed(), second); }
        } catch (Exception error) { throw new BackendException(BackendText.integrationFailed(), error); }
    }

    /** A token for the backend account standing in for this local user. */
    public String tokenForLocalUser(UUID localUserId) {
        if (administratorIds.contains(localUserId)) {
            if (admin == null || !localUserId.toString().equals(admin.userId())) throw new BackendException(401, BackendText.adminLoginRequired());
            return admin.accessToken();
        }
        if (member != null) {
            if (!localUserId.toString().equals(member.userId())) throw new BackendException(401, BackendText.memberLoginRequired());
            return member.accessToken();
        }
        String username = identity.usernameFor(localUserId);
        String password = identity.passwordFor(localUserId);
        String token = tokenFor(username, password);
        if (mappings.remoteUser(localUserId) == null) {
            JSONObject me = client.getObject("/api/users/me", token);
            String rawId = me.optString("id", "");
            try {
                mappings.putUser(localUserId, UUID.fromString(rawId));
            } catch (IllegalArgumentException error) {
                throw new BackendException(0, BackendText.malformedResponse());
            }
        }
        return token;
    }

    public void forget(String username) {
        tokensByUsername.remove(username);
    }

    public void forgetLocalUser(UUID localUserId) {
        forget(identity.usernameFor(localUserId));
    }

    public void forgetAll() {
        tokensByUsername.clear();
    }

    private String tokenFor(String username, String password) {
        String cached = tokensByUsername.get(username);
        if (cached != null) {
            return cached;
        }

        String token = registerOrLogin(username, password);
        tokensByUsername.put(username, token);
        return token;
    }

    /**
     * Registration is tried first and a conflict falls through to logging in.
     *
     * <p>The other order would need a login attempt that is expected to fail as
     * its normal first step, and a failed login is indistinguishable from a wrong
     * password by design — the backend answers both the same way, deliberately.
     */
    private String registerOrLogin(String username, String password) {
        try {
            return tokenFrom(client.postObject("/api/auth/register", null, credentials(username, password)));
        } catch (BackendException e) {
            if (!e.isConflict()) {
                throw e;
            }
            return login(username, password);
        }
    }

    private String login(String username, String password) {
        return tokenFrom(client.postObject("/api/auth/login", null, credentials(username, password)));
    }

    private static JSONObject credentials(String username, String password) {
        try {
            return new JSONObject().put("username", username).put("password", password);
        } catch (Exception e) {
            throw new BackendException("Could not build the sign-in request.", e);
        }
    }

    private static String tokenFrom(JSONObject response) {
        String token = response.optString("accessToken", null);
        if (token == null || token.isEmpty()) {
            throw new BackendException(0, "The backend signed us in but returned no access token.");
        }
        return token;
    }
}
