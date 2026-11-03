package judge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * <p>One attempt, no priming. The original rewrote user 1's password to
     * "admin" on every login attempt (Authenticate.cs:215), which is why this
     * fixture used to retry; sanctioned fix F5 (RULEBOOK §0.1, D-003) removed
     * that statement, so the fixture now relies on the seeded row alone:
     * setup.sql:168 stores salt {@code PmafvZSWFYML3} and a hash that is
     * {@code HashString("admin", "PmafvZSWFYML3")} (the acceptance vector in
     * RULEBOOK §2 B7). The fixture therefore needs no database write and no
     * hash computed by the judge (a judge-side PBKDF2 would have to reproduce
     * .NET's lossy {@code Encoding.UTF8.GetString} exactly). If the first
     * attempt fails, the port's password hash does not meet the acceptance
     * vector, and every scenario fails loudly here instead of in a confusing
     * place.
     */
    protected Web.Page loginAdmin(Web session) {
        Web.Page p = submitLogin(session, "admin", "admin");
        org.junit.jupiter.api.Assertions.assertFalse(p.path().equals("default.aspx") && p.redirects().isEmpty(),
                "admin/admin login failed: the seeded admin hash (setup.sql:168) must verify 'admin' - see RULEBOOK B7 acceptance vector");
        return p;
    }

    /**
     * Fixture: insert a user whose password is "admin" by copying user 1's seeded
     * {@code us_salt}/{@code us_password} (the hash does not depend on the username;
     * with F5 user 1's row is never rewritten, so it is always the seed). Returns the
     * new user's id. {@code usAdmin} 0/1; the user is active, in {@code org}, default
     * query 1. The login cannot be tried before F4 (non-admin sign-in crashed).
     */
    protected static int insertLoginableUser(String username, int usAdmin, int org) {
        Db.exec("insert into users (us_username, us_salt, us_password, us_admin, us_org, us_default_query) "
                + "select ?, us_salt, us_password, ?, ?, 1 from users where us_id = 1", username, usAdmin, org);
        return ((Number) Db.scalar("select us_id from users where us_username = ?", username)).intValue();
    }

    /**
     * Simulates a click on the page's submit button, for sanctioned fix F1
     * (RULEBOOK §0.1, D-003): asserts the form renders the hidden
     * {@code user_hit_submit} field disabled with value 1, asserts the named submit button
     * carries the exact inline {@code onclick} that enables it, then removes the
     * {@code disabled} attribute (what that onclick does in a browser, before EditBug.js
     * submits the form) and returns the form as the browser would now post it.
     */
    protected Form formAfterSubmitButtonClick(Web.Page p, String buttonId) {
        org.jsoup.nodes.Document d = p.html();
        d.outputSettings().prettyPrint(false);
        org.jsoup.nodes.Element hidden = d.getElementById("user_hit_submit");
        assertNotNull(hidden, "F1: form must render #user_hit_submit");
        assertEquals("user_hit_submit", hidden.attr("name"));
        assertEquals("hidden", hidden.attr("type").toLowerCase());
        assertEquals("1", hidden.attr("value"));
        assertTrue(hidden.hasAttr("disabled"), "F1: #user_hit_submit starts disabled");
        org.jsoup.nodes.Element button = d.getElementById(buttonId);
        assertNotNull(button, "#" + buttonId);
        assertEquals("document.getElementById('user_hit_submit').disabled=false;", button.attr("onclick").trim(),
                "F1: inline onclick on #" + buttonId);
        hidden.removeAttr("disabled");
        Web.Page patched = new Web.Page(p.status(), p.uri(), p.headers(),
                d.outerHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8), p.redirects());
        return Form.containing(web, patched, "short_desc");
    }

    /**
     * Makes the ASP.NET session non-empty. Fixture, not a scenario. Call it after
     * login and before the first postback to any page that sets
     * {@code ViewStateUserKey = Session.SessionID} (add_attachment.aspx.cs:15,
     * edit_bug.aspx.cs:32, edit_user.aspx.cs:20, ...). An empty InProc session is
     * issued a new SessionID on every request until something is stored in it, so
     * the postback would be validated against a different key than the page was
     * rendered with and fail the view-state MAC (HTTP 500 on the original).
     * search.aspx stores {@code Session["bugs"]} on a search postback
     * (search.aspx.cs:620), which makes the session id stick. Read-only; harmless
     * for the port. (Reading of ASP.NET session-cookie mechanics - to be confirmed
     * when the original runs.)
     */
    protected void primeAspNetSession(Web session) {
        Form f = Form.containing(session, session.get("search.aspx"), "like");
        if (f.has("hit_submit_button")) f.set("hit_submit_button", "1");
        Web.Page p = f.submit(null);
        assertEquals(200, p.status(), "session priming search");
    }

    /** Fresh session (logged out), same database. */
    protected Web newSession() { return new Web(); }
}
