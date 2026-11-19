package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

import static cn.axg.platform.Seed.*;

/** Local single application instance. Each committed snapshot and roster share one transaction. */
final class MysqlStateRepository implements StateRepository {
    private final Path files;
    private final String url, user, password;
    private long revision;

    MysqlStateRepository(String path, String url, String user, String password) throws IOException {
        this.files = Path.of(path, "files").toAbsolutePath();
        this.url = url;
        this.user = user;
        this.password = password;
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pbl_state (
                    id TINYINT PRIMARY KEY, revision BIGINT NOT NULL, payload JSON NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            sql.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pbl_users (
                    id VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
                    student_number VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
                    payload JSON NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        } catch (SQLException e) {
            throw new IOException("无法初始化 PBL MySQL 存储，请检查连接信息及数据库权限", e);
        }
        Files.createDirectories(files);
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public Path file(String id, String owner) {
        if (!owner.matches("[A-Za-z0-9_-]{1,60}") || !id.matches("[A-Za-z0-9_-]{1,60}"))
            throw new IllegalArgumentException("无效的附件存储标识");
        return files.resolve(owner).resolve(id);
    }

    public ObjectNode load() throws IOException {
        ObjectNode state = o("version", Migrations.VERSION, "users", a(), "courses", a(), "classes", a(),
            "teachingClasses", a(), "enrollments", a(), "tasks", a(), "projects", a(), "notifications", a(), "history", a());
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (Statement sql = connection.createStatement()) {
                try (ResultSet row = sql.executeQuery("SELECT revision,payload FROM pbl_state WHERE id=1")) {
                    if (row.next()) {
                        revision = row.getLong(1);
                        state = (ObjectNode) M.readTree(row.getString(2));
                    }
                }
                var users = a();
                try (ResultSet rows = sql.executeQuery("SELECT payload FROM pbl_users ORDER BY id")) {
                    while (rows.next()) users.add(M.readTree(rows.getString(1)));
                }
                state.set("users", users);
                connection.commit();
            }
        } catch (SQLException e) {
            throw new IOException("无法读取 MySQL 业务数据", e);
        }
        return state;
    }

    public void save(ObjectNode state) throws IOException {
        ObjectNode snapshot = state.deepCopy();
        JsonNode users = snapshot.remove("users");
        long next = revision + 1;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                String update = revision == 0 ? "INSERT INTO pbl_state(revision,payload,id) VALUES(?,?,1)"
                    : "UPDATE pbl_state SET revision=?,payload=? WHERE id=1 AND revision=?";
                try (PreparedStatement sql = connection.prepareStatement(update)) {
                    sql.setLong(1, next);
                    sql.setString(2, snapshot.toString());
                    if (revision > 0) sql.setLong(3, revision);
                    if (sql.executeUpdate() != 1) throw new IOException("数据库状态已被其他服务更新，请重启当前服务");
                }
                // Accounts are never physically deleted. The primary/unique keys enforce identity.
                try (PreparedStatement sql = connection.prepareStatement(
                    "INSERT INTO pbl_users(id,student_number,payload) VALUES(?,?,?) ON DUPLICATE KEY UPDATE payload=IF(id=VALUES(id),VALUES(payload),NULL)")) {
                    for (JsonNode account : users) {
                        sql.setString(1, account.path("id").asText());
                        String number = account.path("number").asText("");
                        sql.setString(2, number.isBlank() ? null : number);
                        sql.setString(3, account.toString());
                        sql.addBatch();
                    }
                    sql.executeBatch();
                }
                connection.commit();
                revision = next;
            } catch (SQLException | IOException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("无法保存 MySQL 业务数据", e);
        }
    }
}
