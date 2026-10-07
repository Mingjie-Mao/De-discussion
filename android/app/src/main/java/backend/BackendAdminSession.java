package backend;

/** Administrator authentication uses the shared session with an enforced ADMIN role. */
public final class BackendAdminSession extends BackendUserSession {
    public BackendAdminSession(BackendConfig config) { super(config, true); }
    public BackendAdminSession(BackendConfig config, SessionStore store) {
        super(config, (origin, method, path, token, body) -> new BackendClient(origin).request(method, path, token, body), true, store);
    }
    public BackendAdminSession(BackendConfig config, Transport transport) { super(config, transport, true); }
}
