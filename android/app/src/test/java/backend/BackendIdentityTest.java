package backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class BackendIdentityTest {

    @Test
    public void identitiesAreStableAndValidForTheBackendContract() {
        BackendConfigTest.MemoryStore store = new BackendConfigTest.MemoryStore();
        BackendIdentity identity = new BackendIdentity(store);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        String firstUsername = identity.usernameFor(first);

        assertEquals(firstUsername, identity.usernameFor(first));
        assertNotEquals(firstUsername, identity.usernameFor(second));
        assertTrue(firstUsername.matches("[a-zA-Z0-9_]{3,50}"));
        assertEquals(identity.passwordFor(first), identity.passwordFor(first));
        assertTrue(identity.passwordFor(first).length() >= 8);
    }
}
