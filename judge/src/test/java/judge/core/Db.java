package judge.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * (src/BugTracker.Web/setup.sql — drops and recreates every table; no GO
     * separators, so it runs as one batch), plus two seed patches. Both patches
     * are judge decisions, listed in judge/README.md ("Seed patches"):
     *
     * <ul>
     * <li><b>P1</b> setup.sql:168 inserts the admin row with an explicit us_id
     *     into an identity column (setup.sql:135) without SET IDENTITY_INSERT, which
     *     SQL Server rejects — admin would be missing and every other user id
     *     would shift. The statement is wrapped in IDENTITY_INSERT ON/OFF.</li>
     * <li><b>P2</b> The EF model maps queries.qu_columns
     *     (Models/Mapping/queryMap.cs:29), which setup.sql never creates; the
     *     database project does (BugTracker.Database/dbo/Tables/queries.sql:8) and
     *     its Script.PostDeployment.sql fills it. Both are applied.</li>
     * </ul>
     */
    public static void reset() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            String sql = Files.readString(JudgeConfig.seedSql(), StandardCharsets.UTF_8).replace("\uFEFF", "");
            int i = sql.indexOf("INSERT [dbo].[users] ([us_id]");
            if (i < 0) throw new IllegalStateException("P1: admin insert not found in seed script");
            int j = sql.indexOf('\n', i);
            sql = sql.substring(0, i) + "SET IDENTITY_INSERT [dbo].[users] ON\n" + sql.substring(i, j)
                    + "\nSET IDENTITY_INSERT [dbo].[users] OFF" + sql.substring(j);
            s.execute(sql);
            s.execute("alter table queries add qu_columns nvarchar(max) not null default 'ColumnsNeeded'");
            Path post = JudgeConfig.seedSql().toAbsolutePath().getParent().getParent()
                    .resolve("BugTracker.Database").resolve("Script.PostDeployment.sql");
            s.execute(Files.readString(post, StandardCharsets.UTF_8).replace("\uFEFF", ""));
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
