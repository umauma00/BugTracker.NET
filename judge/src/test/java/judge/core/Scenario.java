package judge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for every parity scenario. Each test method starts from a freshly
 * reset database and a fresh browser session; scenarios never depend on each
 * other's order.
 *
 * <p>Without a configured target (JUDGE_BASE_URL / JUDGE_DB_URL) the tests are
 * <i>skipped</i>, never passed — a judge that reports green without a target is
 * a green light wired to nothing.
 */
public abstract class Scenario {

    protected Web web;

    @BeforeEach
    void freshWorld() {
        Assumptions.assumeTrue(JudgeConfig.configured(),
                "JUDGE_BASE_URL / JUDGE_DB_URL not set — scenario skipped, not passed");
        Db.reset();
        web = new Web();
    }

    /**
     * Submit the public login form once (default.aspx: fields "user"/"pw",
     * unnamed submit button — src/BugTracker.Web/default.aspx:28,37,41) and
     * return wherever the app lands. No assertion here: what a successful or
     * failed login looks like is pinned by the login scenario (S1), and any
     * priming the original needs is documented there.
     */
    protected Web.Page submitLogin(Web session, String user, String password) {
        Web.Page p = session.get("default.aspx");
        assertEquals(200, p.status(), "login page status");
        return Form.containing(session, p, "pw").set("user", user).set("pw", password).submit(null);
    }

    /**
     * Log in as admin/admin and assert it worked. Fixture, not a scenario.
     *
     * <p>Up to two attempts, by design: every login attempt that reaches
     * check_password_with_db first rewrites user 1's password to "admin" with a
     * fresh salt (src/BugTracker.Web/Security/Authenticate.cs:215), but compares
     * against the row read <i>before</i> that rewrite (:57-65). Whether the very
     * first attempt on a fresh setup.sql database succeeds therefore depends on
     * the seeded hash, which cannot be verified without running the original.
     * The second attempt always compares against a hash of "admin" and must
     * succeed. S1 pins this behavior explicitly; other scenarios only need a
     * logged-in admin. One failed attempt is far below the lockout threshold
     * (FailedLoginAttemptsAllowed, default 10 — Authenticate.cs:91,116), and the
     * success clears the counter (:139-143).
     */
    protected Web.Page loginAdmin(Web session) {
        Web.Page p = submitLogin(session, "admin", "admin");
        if (p.path().equals("default.aspx") && p.redirects().isEmpty()) {
            p = submitLogin(session, "admin", "admin");
        }
        org.junit.jupiter.api.Assertions.assertFalse(p.path().equals("default.aspx") && p.redirects().isEmpty(),
                "admin/admin login failed twice");
        return p;
    }

    /** Fresh session (logged out), same database. */
    protected Web newSession() { return new Web(); }
}
