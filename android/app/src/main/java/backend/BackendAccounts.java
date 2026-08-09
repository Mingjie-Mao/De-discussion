package backend;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

/**
 * Signs in as whichever account a request needs to be made by.
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

    /** Tokens live for the session only; losing them costs one extra login. */
    private final Map<String, String> tokensByUsername = new HashMap<>();

    public BackendAccounts(BackendClient client, BackendIdentity identity) {
        this.client = client;
        this.identity = identity;
    }

    /** A token for the backend account standing in for this local user. */
    public String tokenForLocalUser(UUID localUserId) {
        String username = identity.usernameFor(localUserId);
        String password = identity.passwordFor(localUserId);
        return tokenFor(username, password, true);
    }

    /** A token for the administrator whose credentials were entered in Settings. */
    public String tokenForAdmin(String username, String password) {
        return tokenFor(username, password, false);
    }

    public void forget(String username) {
        tokensByUsername.remove(username);
    }

    public void forgetLocalUser(UUID localUserId) {
        forget(identity.usernameFor(localUserId));
    }

    public void forgetAdmin(String username) {
        forget(username);
    }

    public void forgetAll() {
        tokensByUsername.clear();
    }

    private String tokenFor(String username, String password, boolean mayRegister) {
        String cached = tokensByUsername.get(username);
        if (cached != null) {
            return cached;
        }

        String token = mayRegister ? registerOrLogin(username, password) : login(username, password);
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
