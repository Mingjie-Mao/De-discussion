package backend;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * Names and passwords for the backend accounts that stand in for this demo's
 * local users.
 *
 * <p>The local users are fictions seeded by {@code AppData}: their display names
 * may be Chinese, may contain spaces, and are not unique in any way the backend
 * would recognise. The backend requires {@code [a-zA-Z0-9_]{3,50}}. So a name is
 * derived from identifiers rather than from anything a person typed — hex only,
 * fixed length, and nothing that can be broken by renaming a local user.
 *
 * <p>The install id is the other half. Passwords are generated once and kept
 * only on the device, so a reinstall loses them; without an install id the
 * regenerated account name would collide with a row still sitting in a backend
 * database, and every request for that user would fail on a password nobody has
 * any more. A fresh install is a fresh set of accounts instead.
 */
public final class BackendIdentity {

    static final String KEY_INSTALL_ID = "install_id";
    private static final String KEY_PASSWORD_PREFIX = "password_";

    /** Comfortably over the backend's 8 character minimum. */
    private static final int PASSWORD_BYTES = 24;

    private final KeyValueStore store;
    private final SecureRandom random = new SecureRandom();

    public BackendIdentity(KeyValueStore store) {
        this.store = store;
    }

    /** Generated on first use and stable until the app's data is cleared. */
    public String installId() {
        String existing = store.get(KEY_INSTALL_ID, null);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        String generated = shortHex(UUID.randomUUID());
        store.put(KEY_INSTALL_ID, generated);
        return generated;
    }

    /** For example {@code de_72ac91f3_a81f39c0}: 20 characters, always valid. */
    public String usernameFor(UUID localUserId) {
        return "de_" + installId() + "_" + shortHex(localUserId);
    }

    /**
     * The password for a local user's backend account, generated on first ask.
     *
     * <p>The local demo password is {@code 1234}, which the backend rejects as
     * too short, and reusing a login people are told to type would be worse than
     * pointless anyway. These are machine credentials for machine accounts.
     */
    public String passwordFor(UUID localUserId) {
        String key = KEY_PASSWORD_PREFIX + shortHex(localUserId);

        String existing = store.get(key, null);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        byte[] bytes = new byte[PASSWORD_BYTES];
        random.nextBytes(bytes);

        StringBuilder generated = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            generated.append(String.format(Locale.ROOT, "%02x", b));
        }

        store.put(key, generated.toString());
        return generated.toString();
    }

    private static String shortHex(UUID id) {
        return id.toString().replace("-", "").substring(0, 8);
    }
}
