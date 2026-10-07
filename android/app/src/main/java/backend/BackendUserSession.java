package backend;

import org.json.JSONObject;
import java.util.UUID;

/** Server authentication shared by members and administrators; tokens are persisted only through an encrypted SessionStore. */
public class BackendUserSession {
    @FunctionalInterface
    public interface Transport {
        String call(String origin, String method, String path, String token, JSONObject body);
        default byte[] image(String origin, String path, String token) {
            return new BackendClient(origin).getBytes(path, token);
        }
    }
    private record State(long epoch, String origin, String access, String refresh, String userId, String username) {}
    private final BackendConfig config;
    private final boolean administrator;
    private String displayName = "";
    private JSONObject profile = new JSONObject();
    private long profileFetchedAt;
    private final SessionStore store;
    private final Transport transport;
    private final Object refreshLock = new Object();
    private State state;
    private long epoch;

    public BackendUserSession(BackendConfig config) {
        this(config, false);
    }
    protected BackendUserSession(BackendConfig config, boolean administrator) {
        this(config, (origin, method, path, token, body) -> new BackendClient(origin).request(method, path, token, body), administrator);
    }
    public BackendUserSession(BackendConfig config, Transport transport) {
        this(config, transport, false);
    }
    protected BackendUserSession(BackendConfig config, Transport transport, boolean administrator) {
        this(config, transport, administrator, SessionStore.NONE);
    }
    public BackendUserSession(BackendConfig config, SessionStore store) {
        this(config, (origin, method, path, token, body) -> new BackendClient(origin).request(method, path, token, body), false, store);
    }
    protected BackendUserSession(BackendConfig config, Transport transport, boolean administrator, SessionStore store) {
        this.config = config;
        this.store = store;
        this.transport = transport;
        this.administrator = administrator;
        restore();
    }
    public synchronized boolean hasSession() {
        if (state != null && (!config.isEnabled() || !state.origin.equals(config.baseUrl()))) clear();
        return state != null;
    }
    public synchronized long generation() { return epoch; }
    public BackendUserSession(BackendConfig config, Transport transport, SessionStore store) { this(config, transport, false, store); }
    public synchronized String userId() { return hasSession() ? state.userId : ""; }
    public synchronized String username() { return hasSession() ? state.username : ""; }
    public synchronized String displayName() { return hasSession() ? displayName : ""; }
    public synchronized void clear() { state = null; displayName = ""; profile = new JSONObject(); profileFetchedAt = 0; epoch++; store.clear(); }
    public synchronized long profileFetchedAt() { return profileFetchedAt; }
    public synchronized JSONObject profile() { return parse(profile.toString()); }
    public synchronized void acceptProfile(JSONObject value, long expectedGeneration) {
        State current = requireState();
        if (current.epoch != expectedGeneration || !current.userId.equals(value.optString("id"))) throw expired();
        if (!(administrator ? "ADMIN" : "MEMBER").equals(value.optString("role")) || !"ACTIVE".equals(value.optString("status", "ACTIVE"))) {
            clear(); throw expired();
        }
        profile = parse(value.toString()); profileFetchedAt = System.currentTimeMillis(); displayName = value.optString("displayName", current.username);
        persist(current);
    }
    private void persist(State value) {
        store.write(body("origin", value.origin, "role", administrator ? "ADMIN" : "MEMBER",
                "accessToken", value.access, "refreshToken", value.refresh, "userId", value.userId,
                "username", value.username, "profile", profile, "profileFetchedAt", profileFetchedAt).toString());
    }
    private void restore() {
        String saved = store.read();
        if (saved == null) return;
        try {
            JSONObject value = parse(saved);
            if (!config.isEnabled() || !config.baseUrl().equals(value.optString("origin"))
                    || !(administrator ? "ADMIN" : "MEMBER").equals(value.optString("role"))) {
                clear(); return;
            }
            State restored = tokens(value, epoch, config.baseUrl());
            JSONObject cached = value.optJSONObject("profile");
            if (cached == null || !restored.userId.equals(cached.optString("id"))) { clear(); return; }
            profile = cached; profileFetchedAt = value.optLong("profileFetchedAt", 0); displayName = cached.optString("displayName", restored.username);
            state = restored;
        } catch (RuntimeException corrupted) { clear(); }
    }

    public void login(String username, String password) {
        authenticate("/api/auth/login", username, password);
    }
    public void register(String username, String password) {
        if (administrator) throw new BackendException(403, BackendText.adminRoleRequired());
        authenticate("/api/auth/register", username, password);
    }
    private void authenticate(String path, String username, String password) {
        final long attempt;
        final String origin;
        synchronized (this) {
            clear();
            attempt = epoch;
            if (!config.isEnabled()) throw expired();
            origin = config.baseUrl();
        }
        JSONObject credentials = body("username", username, "password", password);
        JSONObject tokens = parse(transport.call(origin, "POST", path, null, credentials));
        State candidate = tokens(tokens, attempt, origin);
        JSONObject profile = parse(transport.call(origin, "GET", "/api/users/me", candidate.access, null));
        if (!(administrator ? "ADMIN" : "MEMBER").equals(profile.optString("role")) || !"ACTIVE".equals(profile.optString("status", "ACTIVE"))
                || !candidate.userId.equals(profile.optString("id"))) {
            throw new BackendException(403, administrator ? BackendText.adminRoleRequired() : BackendText.memberRoleRequired());
        }
        synchronized (this) {
            if (epoch != attempt || !origin.equals(config.baseUrl()) || !config.isEnabled()) throw expired();
            this.profile = profile;
            this.profileFetchedAt = System.currentTimeMillis();
            displayName = profile.optString("displayName", candidate.username);
            persist(candidate);
            state = candidate;
        }
    }

    /** Forum/profile calls keep the signed-in account, origin and refresh policy. */
    public <T> T withClient(ClientOperation<T> operation) {
        return authenticated(s -> {
            try { return operation.run(new BackendClient(s.origin), s.access); }
            catch (BackendException error) { throw error; }
            catch (Exception error) { throw new BackendException(BackendText.integrationFailed(), error); }
        });
    }
    public synchronized String accessToken() { return requireState().access; }
    public interface ClientOperation<T> { T run(BackendClient client, String token) throws Exception; }

    /** Retry only a 401 once. A 403 is a permission denial, never a refresh trigger. */
    public String request(String method, String path, JSONObject body) {
        if (!administrator || !path.startsWith("/api/admin/") || path.contains("..")) throw new IllegalArgumentException("Admin API path required.");
        return authenticated(s -> transport.call(s.origin, method, path, s.access, body));
    }
    public byte[] image(String path) {
        if (!administrator || path == null || !path.matches("/api/admin/moderation-cases/[a-fA-F0-9-]{36}/media/[a-fA-F0-9-]{36}")) {
            throw new BackendException(0, BackendText.malformedResponse());
        }
        return authenticated(s -> transport.image(s.origin, path, s.access));
    }
    private <T> T authenticated(Operation<T> operation) {
        State first = requireState();
        try {
            T value = operation.run(first);
            checkCurrent(first);
            return value;
        } catch (BackendException error) {
            if (error.status() != 401) {
                if (administrator && error.status() == 403) invalidate(first);
                throw error;
            }
        }
        State next = refresh(first);
        try {
            T value = operation.run(next);
            checkCurrent(next);
            return value;
        } catch (BackendException error) {
            if (error.status() == 401 || (administrator && error.status() == 403)) invalidate(next);
            throw error;
        }
    }
    private State refresh(State failed) {
        synchronized (refreshLock) {
            State current = requireState();
            if (current.epoch != failed.epoch) throw expired();
            if (!current.access.equals(failed.access)) return current;
            try {
                JSONObject response = parse(transport.call(current.origin, "POST", "/api/auth/refresh", null,
                        body("refreshToken", current.refresh)));
                State rotated = tokens(response, current.epoch, current.origin);
                if (!rotated.userId.equals(current.userId)) {
                    invalidate(current);
                    throw expired();
                }
                synchronized (this) {
                    checkCurrent(current);
                    try { persist(rotated); }
                    catch (BackendException failure) { clear(); throw failure; }
                    state = rotated;
                }
                return rotated;
            } catch (BackendException error) {
                // Transient network / server errors leave the session available for retry.
                if (error.isUnauthorised()) invalidate(current);
                throw error;
            }
        }
    }
    private synchronized State requireState() {
        if (!hasSession()) throw expired();
        return state;
    }
    private synchronized void checkCurrent(State expected) {
        State current = requireState();
        if (current.epoch != expected.epoch) throw expired();
    }
    private synchronized void invalidate(State expected) {
        if (state != null && state.epoch == expected.epoch) clear();
    }
    private static State tokens(JSONObject response, long epoch, String origin) {
        String access = response.optString("accessToken", "");
        String refresh = response.optString("refreshToken", "");
        String id = response.optString("userId", "");
        try { UUID.fromString(id); } catch (IllegalArgumentException error) { throw new BackendException(0, BackendText.malformedResponse()); }
        if (access.trim().isEmpty() || refresh.trim().isEmpty() || "null".equals(access) || "null".equals(refresh))
            throw new BackendException(0, BackendText.malformedResponse());
        return new State(epoch, origin, access, refresh, id, response.optString("username", ""));
    }
    static JSONObject parse(String raw) {
        try { return new JSONObject(raw); } catch (Exception error) { throw new BackendException(BackendText.malformedResponse(), error); }
    }
    public static JSONObject body(Object... fields) {
        try {
            JSONObject result = new JSONObject();
            for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
            return result;
        } catch (Exception error) { throw new IllegalArgumentException("Invalid request body.", error); }
    }
    private BackendException expired() { return new BackendException(401, administrator ? BackendText.adminLoginRequired() : BackendText.memberLoginRequired()); }
    private interface Operation<T> { T run(State state); }
}
