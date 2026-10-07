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
    public void defaultsToTheHostedDemoAndOnlineMode() {
        BackendConfig config = new BackendConfig(new MemoryStore());

        assertTrue(config.isEnabled());
        assertEquals(BackendConfig.DEFAULT_BASE_URL, config.baseUrl());
    }

    @Test
    public void normalisesTheBaseUrlAndRejectsSomethingThatIsNotHttp() {
        BackendConfig config = new BackendConfig(new MemoryStore());

        config.setBaseUrl(" https://moderation.example.test/// ");

        assertEquals("https://moderation.example.test", config.baseUrl());
        assertThrows(IllegalArgumentException.class, () -> config.setBaseUrl("moderation.example.test"));
    }

    @Test
    public void scrubsLegacyAdministratorCredentials() {
        MemoryStore store = new MemoryStore();
        store.put("admin_username", "moderator");
        store.put("admin_password", "correct-horse-battery-staple");

        new BackendConfig(store);

        assertFalse(store.values.containsKey("admin_username"));
        assertFalse(store.values.containsKey("admin_password"));
    }

    @Test
    public void upgradesPreviouslySavedDemoUrlOnceAndAllowsExplicitRollback() {
        MemoryStore store = new MemoryStore();
        store.put("base_url", BackendConfig.RENDER_FALLBACK_URL + "/");
        BackendConfig upgraded = new BackendConfig(store);
        assertEquals(BackendConfig.DEFAULT_BASE_URL, upgraded.baseUrl());
        upgraded.setBaseUrl(BackendConfig.RENDER_FALLBACK_URL);
        assertEquals(BackendConfig.RENDER_FALLBACK_URL, new BackendConfig(store).baseUrl());
    }

    @Test
    public void keepsCustomServerDuringDemoHostUpgrade() {
        MemoryStore store = new MemoryStore();
        store.put("base_url", "http://10.0.2.2:8080");
        assertEquals("http://10.0.2.2:8080", new BackendConfig(store).baseUrl());
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
