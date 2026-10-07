package backend;

/** One atomically replaced encrypted snapshot; never stores a password. */
public interface SessionStore {
    String read();
    void write(String snapshot);
    void clear();
    SessionStore NONE = new SessionStore() {
        public String read() { return null; }
        public void write(String snapshot) {}
        public void clear() {}
    };
}
