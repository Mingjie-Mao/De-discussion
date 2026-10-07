package backend;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackendAdministratorIdentityTest {
    @Test public void forumCallsUseTheRealAdministratorIdentityAndNeverCreateMappedCredentials() {
        BackendConfigTest.MemoryStore store = new BackendConfigTest.MemoryStore();
        BackendConfig config = new BackendConfig(store);
        BackendAdminSession session = new BackendAdminSession(config, new BackendAdminSessionTest.Fake());
        session.login("admin", "password");
        BackendMappings mappings = new BackendMappings(store, config);
        BackendAccounts accounts = new BackendAccounts(new BackendClient(config), new BackendIdentity(store), mappings, session);
        UUID id = UUID.fromString(BackendAdminSessionTest.ID); accounts.bindAdministrator(id);
        assertEquals("old", accounts.tokenForLocalUser(id));
        assertEquals("old", accounts.authenticated(id, (client, token) -> token));
        assertEquals(id, mappings.remoteUser(id));
        assertFalse(store.values.keySet().stream().anyMatch(key -> key.startsWith("password_")));
    }
    @Test public void loggedOutAdministratorCannotFallBackToGeneratedMemberIdentity() {
        BackendConfigTest.MemoryStore store = new BackendConfigTest.MemoryStore();
        BackendConfig config = new BackendConfig(store);
        BackendAdminSession session = new BackendAdminSession(config, new BackendAdminSessionTest.Fake());
        session.login("admin", "password");
        BackendAccounts accounts = new BackendAccounts(new BackendClient(config), new BackendIdentity(store), new BackendMappings(store, config), session);
        UUID id = UUID.fromString(BackendAdminSessionTest.ID); accounts.bindAdministrator(id); session.clear();
        assertEquals(401, assertThrows(BackendException.class, () -> accounts.tokenForLocalUser(id)).status());
        assertEquals(401, assertThrows(BackendException.class, () -> accounts.authenticated(id, (client, token) -> token)).status());
        assertFalse(store.values.keySet().stream().anyMatch(key -> key.startsWith("password_")));
    }
    @Test public void administratorForumCallsShareRefreshRotation() {
        BackendConfig config = BackendAdminSessionTest.config(); BackendAdminSessionTest.Fake fake = new BackendAdminSessionTest.Fake();
        BackendAdminSession session = new BackendAdminSession(config, fake); session.login("admin", "password");
        BackendAccounts accounts = new BackendAccounts(new BackendClient(config), new BackendIdentity(new BackendConfigTest.MemoryStore()), new BackendMappings(new BackendConfigTest.MemoryStore(), config), session);
        UUID id = UUID.fromString(BackendAdminSessionTest.ID); accounts.bindAdministrator(id);
        String token = accounts.authenticated(id, (client, access) -> {
            if ("old".equals(access)) throw new BackendException(401, "Expired");
            return access;
        });
        assertEquals("new1", token); assertEquals(1, fake.refreshes.get());
    }
}
