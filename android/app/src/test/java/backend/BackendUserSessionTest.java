package backend;

import org.json.JSONObject;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class BackendUserSessionTest {
    static class MemberTransport extends BackendAdminSessionTest.Fake {
        String endpoint;
        MemberTransport() { role = "MEMBER"; }
        @Override public String call(String origin, String method, String path, String token, JSONObject body) {
            if (path.equals("/api/auth/register")) { endpoint = path; password = body.optString("password"); return BackendAdminSessionTest.tokens("old", "r1"); }
            if (path.equals("/api/users/me")) return BackendUserSession.body("id", BackendAdminSessionTest.ID, "role", role, "status", "ACTIVE", "displayName", "Campus Buddy").toString();
            return super.call(origin, method, path, token, body);
        }
    }
    @Test public void registrationVerifiesRealMemberAndKeepsDisplayName() {
        MemberTransport transport = new MemberTransport();
        BackendUserSession session = new BackendUserSession(BackendAdminSessionTest.config(), transport);
        session.register("member", " spaces matter ");
        assertEquals("/api/auth/register", transport.endpoint);
        assertEquals(" spaces matter ", transport.password);
        assertEquals("Campus Buddy", session.displayName());
        assertEquals(BackendAdminSessionTest.ID, session.userId());
        assertTrue(session.hasSession());
    }
    @Test public void adminCannotEnterThroughMemberLogin() {
        MemberTransport transport = new MemberTransport(); transport.role = "ADMIN";
        BackendUserSession session = new BackendUserSession(BackendAdminSessionTest.config(), transport);
        assertEquals(403, assertThrows(BackendException.class, () -> session.login("admin", "password")).status());
        assertFalse(session.hasSession());
    }
    @Test public void memberCannotUsePrivilegedApiOrReadEvidenceImages() {
        BackendUserSession session = new BackendUserSession(BackendAdminSessionTest.config(), new MemberTransport());
        session.login("member", "password");
        assertThrows(IllegalArgumentException.class, () -> session.request("GET", BackendAdminSessionTest.CASE, null));
        assertThrows(BackendException.class, () -> session.image("/api/admin/moderation-cases/11111111-1111-1111-1111-111111111111/media/11111111-1111-1111-1111-111111111111"));
    }
    @Test public void memberWritesRotateRefreshAndNeverUseGeneratedCredentials() {
        BackendConfigTest.MemoryStore store = new BackendConfigTest.MemoryStore();
        BackendConfig config = new BackendConfig(store);
        MemberTransport transport = new MemberTransport(); BackendUserSession session = new BackendUserSession(config, transport);
        session.login("member", "password");
        BackendAccounts accounts = new BackendAccounts(new BackendClient(config), new BackendIdentity(store), new BackendMappings(store, config), null, session);
        UUID id = UUID.fromString(session.userId()); accounts.bindMember(id);
        assertEquals("old", accounts.tokenForLocalUser(id));
        assertEquals("new1", accounts.authenticated(id, (client, token) -> {
            if (token.equals("old")) throw new BackendException(401, "Expired");
            return token;
        }));
        assertEquals(1, transport.refreshes.get());
        assertEquals(401, assertThrows(BackendException.class, () -> accounts.tokenForLocalUser(UUID.randomUUID())).status());
        session.clear();
        assertEquals(401, assertThrows(BackendException.class, () -> accounts.tokenForLocalUser(id)).status());
        assertFalse(store.values.keySet().stream().anyMatch(key -> key.startsWith("password_")));
    }
    @Test public void mediaUrlRetainsOnlyAnExistingBackendAttachmentId() {
        String id = BackendAdminSessionTest.ID;
        assertEquals(UUID.fromString(id), BackendForumGateway.mediaId("https://example.test/api/media/" + id + "?v=2"));
        assertEquals(UUID.fromString(id), BackendForumGateway.mediaId("/api/media/" + id + "?v=2"));
        assertNull(BackendForumGateway.mediaId("content://local/image"));
        assertNull(BackendForumGateway.mediaId("/api/media/invalid"));
        assertNull(BackendForumGateway.mediaId(null));
    }
    @Test public void ordinaryPermissionDenialDoesNotSignMemberOutOrRefresh() {
        MemberTransport transport = new MemberTransport();
        BackendUserSession session = new BackendUserSession(BackendAdminSessionTest.config(), transport);
        session.login("member", "password");
        assertEquals(403, assertThrows(BackendException.class, () -> session.withClient((client, token) -> {
            throw new BackendException(403, "Only the author may edit.");
        })).status());
        assertTrue(session.hasSession());
        assertEquals(0, transport.refreshes.get());
    }
}
