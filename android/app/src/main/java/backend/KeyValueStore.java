package backend;

/**
 * The small slice of persistence this package needs.
 *
 * <p>An interface rather than a direct {@code SharedPreferences} call so that
 * the logic built on it — username derivation, mirror bookkeeping — can be
 * tested on a plain JVM. Android's framework classes are stubs off-device and
 * throw as soon as they are touched, so anything reaching them directly is
 * effectively untestable without an emulator.
 */
public interface KeyValueStore {

    String get(String key, String fallback);

    void put(String key, String value);

    void remove(String key);
}
