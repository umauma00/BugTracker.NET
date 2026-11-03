package judge.core;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the implementation under judgement lives. Everything comes from the
 * environment so the same harness runs unchanged against the original
 * (IIS / IIS Express) and the port (Spring Boot).
 *
 * <pre>
 * JUDGE_TARGET       old | new                  (label only; never changes an assertion)
 * JUDGE_BASE_URL     e.g. http://localhost:8080/   (must end with '/')
 * JUDGE_DB_URL       jdbc:sqlserver://localhost:1433;databaseName=btnet_judge;encrypt=false
 * JUDGE_DB_USER      sa
 * JUDGE_DB_PASSWORD  ...
 * JUDGE_SEED_SQL     path to the seed script (default: ../src/BugTracker.Web/setup.sql)
 * </pre>
 */
public final class JudgeConfig {
    private JudgeConfig() {}

    public static String target() {
        return env("JUDGE_TARGET", "unset");
    }

    public static URI baseUrl() {
        String u = required("JUDGE_BASE_URL");
        if (!u.endsWith("/")) u = u + "/";
        return URI.create(u);
    }

    public static String dbUrl() { return required("JUDGE_DB_URL"); }
    public static String dbUser() { return env("JUDGE_DB_USER", "sa"); }
    public static String dbPassword() { return env("JUDGE_DB_PASSWORD", ""); }

    public static Path seedSql() {
        Path p = Path.of(env("JUDGE_SEED_SQL", "../src/BugTracker.Web/setup.sql"));
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("Seed script not found: " + p.toAbsolutePath());
        }
        return p;
    }

    /** True when the environment is configured; scenarios are skipped (not passed) otherwise. */
    public static boolean configured() {
        return System.getenv("JUDGE_BASE_URL") != null && System.getenv("JUDGE_DB_URL") != null;
    }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? dflt : v;
    }

    private static String required(String k) {
        String v = System.getenv(k);
        if (v == null || v.isBlank()) throw new IllegalStateException(k + " is not set");
        return v;
    }
}
