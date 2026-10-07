import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** One-time operator provisioning for the public interview database. */
public final class DemoAccounts {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--confirm-public-demo")) {
            throw new IllegalArgumentException("Pass --confirm-public-demo for the interview database only.");
        }
        try (Connection db = DriverManager.getConnection(
                required("DEMO_DATABASE_URL"), required("DEMO_DATABASE_USER"), required("DEMO_DATABASE_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                provision(db, "1234", "1234", "MEMBER", "Demo Member");
                provision(db, "12345", "12345", "ADMIN", "Demo Administrator");
                db.commit();
                System.out.println("Public demo accounts provisioned: 1234 (MEMBER), 12345 (ADMIN).");
            } catch (Exception error) {
                db.rollback();
                throw error;
            }
        }
    }

    private static void provision(Connection db, String username, String password, String role, String displayName)
            throws Exception {
        UUID id = null;
        boolean needsUpdate = false;
        try (var query = db.prepareStatement(
                "SELECT id, role, status, password_hash FROM users WHERE username = ? FOR UPDATE")) {
            query.setString(1, username);
            try (var row = query.executeQuery()) {
                if (row.next()) {
                    id = row.getObject("id", UUID.class);
                    if (!role.equals(row.getString("role"))) {
                        throw new IllegalStateException("Account " + username + " already has another role; no changes made.");
                    }
                    needsUpdate = !"ACTIVE".equals(row.getString("status"))
                            || !ENCODER.matches(password, row.getString("password_hash"));
                }
            }
        }
        if (id == null) {
            try (var insert = db.prepareStatement(
                    "INSERT INTO users (username, password_hash, role, status, display_name) VALUES (?, ?, ?, 'ACTIVE', ?)")) {
                insert.setString(1, username);
                insert.setString(2, ENCODER.encode(password));
                insert.setString(3, role);
                insert.setString(4, displayName);
                insert.executeUpdate();
            }
        } else if (needsUpdate) {
            try (var update = db.prepareStatement(
                    "UPDATE users SET password_hash = ?, status = 'ACTIVE', token_version = token_version + 1 WHERE id = ?")) {
                update.setString(1, ENCODER.encode(password));
                update.setObject(2, id);
                update.executeUpdate();
            }
            try (var revoke = db.prepareStatement("DELETE FROM refresh_tokens WHERE user_id = ?")) {
                revoke.setObject(1, id);
                revoke.executeUpdate();
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
