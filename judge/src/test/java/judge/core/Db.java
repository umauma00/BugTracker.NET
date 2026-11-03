package judge.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct access to the database both implementations share. The judge reads
 * state after an action and compares it; it only writes during {@link #reset()}
 * and explicit {@link #exec} fixture calls.
 */
public final class Db {
    private Db() {}

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(JudgeConfig.dbUrl(), JudgeConfig.dbUser(), JudgeConfig.dbPassword());
    }

    /**
     * Rebuild the schema and seed data from the original's own setup script
     * (src/BugTracker.Web/setup.sql — drops and recreates every table). The
     * script contains no GO separators, so it runs as one batch.
     */
    public static void reset() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            String sql = Files.readString(JudgeConfig.seedSql(), StandardCharsets.UTF_8);
            s.execute(sql);
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Database reset failed", e);
        }
    }

    /** Run a fixture statement with positional parameters. Returns update count. */
    public static int exec(String sql, Object... params) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    /** All rows, column labels lower-cased, values as JDBC returns them. */
    public static List<Map<String, Object>> rows(String sql, Object... params) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    /** Exactly one row, or the test fails. */
    public static Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> r = rows(sql, params);
        if (r.size() != 1) throw new AssertionError("Expected 1 row, got " + r.size() + " for: " + sql);
        return r.get(0);
    }

    public static Object scalar(String sql, Object... params) {
        Map<String, Object> r = one(sql, params);
        return r.values().iterator().next();
    }

    public static long count(String sql, Object... params) {
        return ((Number) scalar(sql, params)).longValue();
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }
}
