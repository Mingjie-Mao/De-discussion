package backend;

import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class BackendAdminSessionTest {
    static final String ID = "11111111-1111-1111-1111-111111111111";
    static final String CASE = "/api/admin/moderation-cases?size=30&page=0";
    static String tokens(String access, String refresh) {
        return BackendAdminSession.body("accessToken", access, "refreshToken", refresh,
                "userId", ID, "username", "admin").toString();
    }
    static String profile(String role) {
        return BackendAdminSession.body("id", ID, "role", role, "status", "ACTIVE").toString();
    }
    static class Fake implements BackendAdminSession.Transport {
        String role = "ADMIN";
        volatile boolean expired;
        int refreshStatus;
        int apiStatus;
        final AtomicInteger refreshes = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        String password;
        String lastRefresh;
        @Override public String call(String origin, String method, String path, String token, JSONObject body) {
            calls.incrementAndGet();
            if (path.equals("/api/auth/login")) { password = body.optString("password"); return tokens("old", "r1"); }
            if (path.equals("/api/users/me")) return profile(role);
            if (path.equals("/api/auth/refresh")) {
                refreshes.incrementAndGet(); lastRefresh = body.optString("refreshToken");
                if (refreshStatus != 0) throw new BackendException(refreshStatus, "Refresh rejected");
                return tokens("new" + refreshes.get(), "r" + (refreshes.get() + 1));
            }
            if (apiStatus != 0) throw new BackendException(apiStatus, "API rejected");
            if (expired && "old".equals(token)) throw new BackendException(401, "Expired");
            return "[]";
        }
    }
    static BackendConfig config() { return new BackendConfig(new BackendConfigTest.MemoryStore()); }
    @Test public void loginVerifiesAdminAndDoesNotTrimThePassword() {
        Fake fake = new Fake(); BackendAdminSession session = new BackendAdminSession(config(), fake);
        session.login("admin", " spaces matter ");
        assertEquals(" spaces matter ", fake.password); assertTrue(session.hasSession()); assertEquals(ID, session.userId());
    }
    @Test public void memberCannotObtainAdminSessionBySelectingAdmin() {
        Fake fake = new Fake(); fake.role = "MEMBER";
        BackendAdminSession session = new BackendAdminSession(config(), fake);
        assertEquals(403, assertThrows(BackendException.class, () -> session.login("member", "password")).status());
        assertFalse(session.hasSession());
    }
    @Test public void wrongCredentialsLeaveNoPrivilegedSession() {
        BackendAdminSession session = new BackendAdminSession(config(), (o, m, p, t, b) -> { throw new BackendException(401, "Invalid credentials"); });
        assertEquals(401, assertThrows(BackendException.class, () -> session.login("admin", "wrong")).status());
        assertFalse(session.hasSession());
    }
    @Test public void expiredAccessRotatesOnceAndRetries() {
        Fake fake = new Fake(); BackendAdminSession session = new BackendAdminSession(config(), fake);
        session.login("admin", "password"); fake.expired = true;
        assertEquals("[]", session.request("GET", CASE, null)); assertEquals(1, fake.refreshes.get()); assertEquals("r1", fake.lastRefresh);
        assertEquals("[]", session.request("GET", CASE, null)); assertEquals(1, fake.refreshes.get());
    }
    @Test public void rejectedRefreshEndsSession() {
        Fake fake = new Fake(); BackendAdminSession session = new BackendAdminSession(config(), fake);
        session.login("admin", "password"); fake.expired = true; fake.refreshStatus = 401;
        assertThrows(BackendException.class, () -> session.request("GET", CASE, null)); assertFalse(session.hasSession());
    }
    @Test public void transientRefreshFailurePreservesSessionForRetry() {
        Fake fake = new Fake(); BackendAdminSession session = new BackendAdminSession(config(), fake);
        session.login("admin", "password"); fake.expired = true; fake.refreshStatus = 503;
        assertThrows(BackendException.class, () -> session.request("GET", CASE, null)); assertTrue(session.hasSession());
        fake.refreshStatus = 0; assertEquals("[]", session.request("GET", CASE, null));
    }
    @Test public void permissionDenialDoesNotTriggerRefresh() {
        Fake fake = new Fake(); BackendAdminSession session = new BackendAdminSession(config(), fake);
        session.login("admin", "password"); fake.apiStatus = 403;
        assertThrows(BackendException.class, () -> session.request("GET", CASE, null));
        assertEquals(0, fake.refreshes.get()); assertFalse(session.hasSession());
    }
    @Test public void second401DoesNotLoop() {
        Fake fake = new Fake(); BackendAdminSession session = new BackendAdminSession(config(), fake);
        session.login("admin", "password"); fake.apiStatus = 401;
        assertThrows(BackendException.class, () -> session.request("GET", CASE, null));
        assertEquals(1, fake.refreshes.get()); assertFalse(session.hasSession());
    }
    @Test public void changedOriginCannotReceivePreviousCredentials() {
        Fake fake = new Fake(); BackendConfig config = config(); BackendAdminSession session = new BackendAdminSession(config, fake);
        session.login("admin", "password"); int before = fake.calls.get(); config.setBaseUrl("https://another.example.test");
        assertThrows(BackendException.class, () -> session.request("GET", CASE, null));
        assertEquals(before, fake.calls.get()); assertFalse(session.hasSession());
    }
    @Test public void disablingOnlineModeEndsAdminSession() {
        BackendConfig config = config(); BackendAdminSession session = new BackendAdminSession(config, new Fake());
        session.login("admin", "password"); config.setEnabled(false); assertFalse(session.hasSession());
    }
    @Test public void privateImageCannotSendTokenToAnExternalUrlOrUnrelatedPath() {
        BackendAdminSession session = new BackendAdminSession(config(), new Fake()); session.login("admin", "password");
        assertThrows(BackendException.class, () -> session.image("https://evil.example/image"));
        assertThrows(BackendException.class, () -> session.image("/api/admin/moderation-cases/../image"));
    }
    @Test public void logoutDuringLoginCannotResurrectSession() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Fake fake = new Fake() {
            @Override public String call(String o, String m, String p, String t, JSONObject b) {
                if (p.equals("/api/users/me")) { entered.countDown(); await(release); }
                return super.call(o, m, p, t, b);
            }
        };
        BackendAdminSession session = new BackendAdminSession(config(), fake);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> login = pool.submit(() -> session.login("admin", "password"));
            assertTrue(entered.await(2, TimeUnit.SECONDS)); session.clear(); release.countDown();
            assertThrows(ExecutionException.class, () -> login.get(2, TimeUnit.SECONDS)); assertFalse(session.hasSession());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test public void concurrent401ResponsesConsumeOneRefreshToken() throws Exception {
        CountDownLatch oldRequests = new CountDownLatch(2);
        Fake fake = new Fake() {
            @Override public String call(String o, String m, String p, String t, JSONObject b) {
                if (p.equals(CASE) && "old".equals(t)) { oldRequests.countDown(); await(oldRequests); }
                return super.call(o, m, p, t, b);
            }
        };
        BackendAdminSession session = new BackendAdminSession(config(), fake); session.login("admin", "password"); fake.expired = true;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> session.request("GET", CASE, null));
            Future<String> second = pool.submit(() -> session.request("GET", CASE, null));
            assertEquals("[]", first.get(3, TimeUnit.SECONDS)); assertEquals("[]", second.get(3, TimeUnit.SECONDS));
            assertEquals(1, fake.refreshes.get());
        } finally { pool.shutdownNow(); }
    }
    static void await(CountDownLatch latch) {
        try { if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }
}
