package backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class BackendConfigTest {

    @Test
    public void defaultsToTheEmulatorHostAndOnlineMode() {
        BackendConfig config = new BackendConfig(new MemoryStore());

        assertTrue(config.isEnabled());
        assertEquals("http://10.0.2.2:8080", config.baseUrl());
    }

    @Test
    public void normalisesTheBaseUrlAndRejectsSomethingThatIsNotHttp() {
        BackendConfig config = new BackendConfig(new MemoryStore());

        config.setBaseUrl(" https://moderation.example.test/// ");

        assertEquals("https://moderation.example.test", config.baseUrl());
        assertThrows(IllegalArgumentException.class, () -> config.setBaseUrl("moderation.example.test"));
    }

    @Test
    public void neverPersistsTheAdministratorPassword() {
        MemoryStore store = new MemoryStore();
        BackendConfig config = new BackendConfig(store);

        config.setAdminCredentials("moderator", "correct-horse-battery-staple");

        assertTrue(config.hasAdminCredentials());
        assertEquals("moderator", store.values.get("admin_username"));
        assertFalse(store.values.containsValue("correct-horse-battery-staple"));

        BackendConfig afterProcessRestart = new BackendConfig(store);
        assertFalse(afterProcessRestart.hasAdminCredentials());
    }

    static final class MemoryStore implements KeyValueStore {
        final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }
}
