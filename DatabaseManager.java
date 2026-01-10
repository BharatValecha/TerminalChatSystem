import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

public class DatabaseManager {
    // Use the server machine's IP for DB too (same machine as MySQL)
    private static final String DB_HOST = "localhost"; // change to your server IP
    private static final String DB_URL = 
    "jdbc:mysql://" + DB_HOST + ":3306/chat_app?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "chatapp";
    private static final String DB_PASSWORD = "chatapp123";

    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver not found", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.out.println("DB connection failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("DB error (userExists): " + e.getMessage());
            return false;
        }
    }

    public static boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.length() < 6) {
            return false;
        }
        String hash = BCrypt.hashpw(password, BCrypt.gensalt()); // salted hash [web:18][web:21]

        String sql = "INSERT INTO users(username, password_hash) VALUES(?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            // duplicate username
            return false;
        } catch (SQLException e) {
            System.out.println("DB error (registerUser): " + e.getMessage());
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;
            String hash = rs.getString("password_hash");
            boolean ok = BCrypt.checkpw(password, hash); // verify [web:18][web:21]
            if (ok) {
                updateLastLogin(username);
            }
            return ok;
        } catch (SQLException e) {
            System.out.println("DB error (authenticateUser): " + e.getMessage());
            return false;
        }
    }

    private static void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("DB error (updateLastLogin): " + e.getMessage());
        }
    }
}
