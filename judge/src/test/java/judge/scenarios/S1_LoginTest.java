package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import judge.core.Db;
import judge.core.Form;
import judge.core.Inherited;
import judge.core.JudgeConfig;
import judge.core.Norm;
import judge.core.Scenario;
import judge.core.SourceRef;
import judge.core.Web;

/**
 * S1 — login, logoff, page protection, role-dependent menu, API login.
 * Spec: judge/specs/S1.md.
 */
class S1_LoginTest extends Scenario {

    /** Seeded salt of user 1 (setup.sql:168). Any change means the admin password was rewritten. */
    private static final String SEED_ADMIN_SALT = "PmafvZSWFYML3";
    private static final String INVALID = "Invalid User or Password.";

    // ------------------------------------------------------------------ tests

    @Test
    @SourceRef(value = "src/BugTracker.Web/default.aspx:28,37,41,42", why = "fields user, pw, unnamed submit, message span msg")
    @SourceRef(value = "src/BugTracker.Web/default.aspx.cs:27", why = "msg cleared on every load")
    @SourceRef(value = "src/BugTracker.Web/default.aspx.cs:10", why = "[PageAllowAnonymous]: no redirect for anonymous")
    void loginPageIsAnonymousAndHasLoginForm() {
        Web.Page p = web.getNoFollow("default.aspx");
        assertEquals(200, p.status());
        Form f = Form.containing(web, p, "pw");
        assertTrue(f.has("user"), "field user");
        assertEquals("", logicalText(p.html(), "msg"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/default.aspx.cs:127-132", why = "success -> Util.redirect(Request, Response)")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1097-1113", why = "no url param, no mobile param -> Response.Redirect(\"bugs.aspx\")")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:145", why = "us_most_recent_login_datetime = getdate() on success")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:168", why = "seeded us_most_recent_login_datetime 2015-01-13 22:05:16.973")
    void adminLoginLandsOnBugsAndStampsLoginTime() {
        java.sql.Timestamp before = S3_EditBugTest.dbNow();
        Web.Page p = submitLogin(web, "admin", "admin");
        java.sql.Timestamp after = S3_EditBugTest.dbNow();
        assertEquals(1, p.redirects().size(), "exactly one redirect hop: " + p.redirects());
        assertEquals("bugs.aspx", Norm.relPath(p.redirects().get(0)));
        assertEquals("bugs.aspx", p.path());
        assertEquals(200, p.status());
        Object stamp = Db.scalar("select us_most_recent_login_datetime from users where us_id = 1");
        S3_EditBugTest.between(before, after, stamp, "us_most_recent_login_datetime (getdate() on success)");
        assertTrue(loggedIn(web));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:44-48", why = "check_password false -> \"Invalid User or Password.\"")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:220-228", why = "hash mismatch -> false")
    @SourceRef(value = "src/BugTracker.Web/default.aspx.cs:133-136", why = "msg.InnerText = ErrorMessage, no redirect")
    void wrongPasswordShowsMessageAndStaysOnLoginPage() {
        Web.Page p = submitLogin(web, "admin", "definitely-not-the-password");
        assertEquals(200, p.status());
        assertEquals("default.aspx", p.path());
        assertTrue(p.redirects().isEmpty(), "no redirect on failure");
        assertEquals(INVALID, logicalText(p.html(), "msg"));
        assertFalse(loggedIn(web));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:63-69", why = "unknown user -> false before any password work")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:44-48", why = "same message as a wrong password")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:213-215", why = "admin rewrite only happens inside check_password_with_db, not reached")
    void unknownUserGetsSameMessageAndDoesNotTouchAdmin() {
        Map<String, Object> before = adminCredentials();
        Web.Page p = submitLogin(web, "no_such_user_judge", "whatever");
        assertEquals("default.aspx", p.path());
        assertTrue(p.redirects().isEmpty());
        assertEquals(INVALID, logicalText(p.html(), "msg"));
        assertEquals(before, adminCredentials(), "admin us_salt/us_password must be untouched");
        assertEquals(SEED_ADMIN_SALT, before.get("us_salt"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/setup.sql:198-200", why = "seed user 'guest' has us_active = 0")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:71-77", why = "inactive -> false before password check")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:44-48", why = "same message")
    void inactiveUserGetsSameMessageAndDoesNotTouchAdmin() {
        Map<String, Object> before = adminCredentials();
        Web.Page p = submitLogin(web, "guest", "guest");
        assertEquals("default.aspx", p.path());
        assertTrue(p.redirects().isEmpty());
        assertEquals(INVALID, logicalText(p.html(), "msg"));
        assertEquals(before, adminCredentials());
        assertFalse(loggedIn(web));
    }

    /**
     * Sanctioned fix F5 (RULEBOOK §0.1, D-003): the original rewrote user 1's password to
     * "admin" with a new random salt on every login attempt that reached the password
     * comparison; the port does not. Intent: {@code check_password_with_db} only compares.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:213-223", why = "check_password_with_db minus line 215: salt and hash are read from the row, entered password hashed with that salt and compared; nothing is written")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:57-65", why = "row read once, before the comparison")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:136-146", why = "success only stamps us_most_recent_login_datetime")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:168", why = "seeded salt PmafvZSWFYML3 and hash of 'admin'")
    void loginAttemptsNeverRewriteAdminPassword() {
        Map<String, Object> before = adminCredentials();
        assertEquals(SEED_ADMIN_SALT, before.get("us_salt"));
        Web.Page p = submitLogin(web, "admin", "wrong-password");
        assertEquals(INVALID, logicalText(p.html(), "msg"));
        assertEquals(before, adminCredentials(), "failed attempt must not rewrite salt/hash");
        // a correct login, in a fresh session, works on the very first attempt and also leaves the row alone
        Web s2 = newSession();
        Web.Page ok = submitLogin(s2, "admin", "admin");
        assertEquals("bugs.aspx", ok.path());
        assertEquals(before, adminCredentials(), "successful login must not rewrite salt/hash");
        // a failed attempt by another existing user reaches the comparison too (developer: salt '0' -> 500, see below);
        // an inactive/unknown user never reaches it. Row 1 stays the seed in every case.
    }

    @Test
    @Inherited("Seeded non-admin users (plaintext us_password, NULL us_salt) cannot log in: the salt '0' is too short for Rfc2898DeriveBytes and the request fails with HTTP 500")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:170-172", why = "developer: us_password 'admin', us_salt NULL")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:57", why = "isnull(us_salt,0) on an nvarchar column -> '0'")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:216-217", why = "HashString(pw, \"0\"); with F5 (D-003) the admin rewrite on line 215 no longer happens")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:494-499", why = "salt bytes = UTF8(salt+salt) = 2 bytes < 8 -> ArgumentException")
    @SourceRef(value = "src/BugTracker.Web/Global.asax.cs:19-24", why = "Application_Error only logs; error is not handled")
    void seededNonAdminLoginFailsWithServerError() {
        // Only the admin-rewrite half of this test changed (Sanctioned fix F5, RULEBOOK §0.1, D-003); the crash is still inherited.
        Web.Page p = submitLogin(web, "developer", "admin");
        assertEquals(500, p.status());
        assertEquals(SEED_ADMIN_SALT, adminCredentials().get("us_salt"), "F5 (D-003): admin is not rewritten, even though the request crashes");
        assertNull(Db.scalar("select us_most_recent_login_datetime from users where us_username = 'developer'"));
        assertFalse(loggedIn(web));
    }

    /**
     * Sanctioned fix F4 (RULEBOOK §0.1, D-003): the original answered HTTP 500 for every
     * non-admin sign-in because {@code GetIdentity} read a {@code project_admin} column its
     * query never selected. Intent: sign in like an admin does and land where admin lands.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:136-146", why = "password ok -> update_most_recent_login_datetime, return true")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:26-31", why = "then Security.SignIn")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:111-121", why = "us_admin != 1 -> project_admin count decides ProjectAdmin role; every user gets role User")
    @SourceRef(value = "src/BugTracker.Web/default.aspx.cs:127-132", why = "success -> Util.redirect")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1097-1113", why = "no url param -> bugs.aspx, same as admin")
    @SourceRef(value = "src/BugTracker.Web/Controllers/LoginController.cs:11-23", why = "API login: success -> 200 empty")
    void nonAdminWithCorrectPasswordLogsInAndLandsOnBugs() {
        String name = "judge_na_" + suffix();
        copyAdminCredentialsTo(name, 0);
        Web s = newSession();
        java.sql.Timestamp before = S3_EditBugTest.dbNow();
        Web.Page p = submitLogin(s, name, "admin");
        java.sql.Timestamp after = S3_EditBugTest.dbNow();
        assertEquals(1, p.redirects().size(), "exactly one redirect hop: " + p.redirects());
        assertEquals("bugs.aspx", Norm.relPath(p.redirects().get(0)));
        assertEquals("bugs.aspx", p.path());
        assertEquals(200, p.status());
        S3_EditBugTest.between(before, after,
                Db.scalar("select us_most_recent_login_datetime from users where us_username = ?", name),
                "us_most_recent_login_datetime (getdate() on success)");
        assertTrue(loggedIn(s));
        assertEquals(SEED_ADMIN_SALT, adminCredentials().get("us_salt"));

        Web api = newSession();
        Web.Page a = api.postJson("api/login", json(name, "admin"));
        assertEquals(200, a.status());
        assertEquals(0, a.body().length, "Ok() has no content");
        assertTrue(loggedIn(api), "auth cookie usable for pages");
    }

    /**
     * Sanctioned fix F4 (RULEBOOK §0.1, D-003): what a plain (non-admin, non-project-admin) user sees in the menu.
     * Org 1 has og_can_search=1 (default), og_can_use_reports=0, og_can_edit_reports=0.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:20-62", why = "bugs always; search if CanSearch; queries unless Guest (role never granted); reports only Admin/CanUseReports/CanEditReports; admin.aspx only Admin; users.aspx only ProjectAdmin")
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:71-80,87-95", why = "text search form (EnableSearch=1, CanSearch); settings + logoff")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:90-108,125", why = "org1 defaults: og_can_search 1, og_can_use_reports 0, og_can_edit_reports 0")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:111-121", why = "role Admin only if us_admin = 1; ProjectAdmin only with a pu_admin = 1 row")
    void plainUserSeesNoAdminMenuEntries() {
        String name = "judge_pu_" + suffix();
        copyAdminCredentialsTo(name, 0);
        Web s = newSession();
        Web.Page p = submitLogin(s, name, "admin");
        assertEquals("bugs.aspx", p.path());
        Set<String> links = sameSiteLinkPaths(p.html());
        for (String must : new String[] {"bugs.aspx", "search.aspx", "queries.aspx", "edit_self.aspx", "logoff.aspx"}) {
            assertTrue(links.contains(must), "menu link " + must + " missing; have " + links);
        }
        for (String mustNot : new String[] {"admin.aspx", "users.aspx", "reports.aspx", "view_whatsnew.aspx"}) {
            assertFalse(links.contains(mustNot), "menu link " + mustNot + " must be absent; have " + links);
        }
        assertTrue(hasTextSearchForm(p), "navbar text-search form for a user whose org can search");
    }

    /** Sanctioned fix F4 (RULEBOOK §0.1, D-003): the project_admin count decides the ProjectAdmin role. */
    @Test
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:49-62", why = "Admin -> admin.aspx, else ProjectAdmin -> users.aspx")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:117-120", why = "project_admin count > 0 -> role ProjectAdmin")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:413-422", why = "project_user_xref.pu_admin")
    void projectAdminSeesUsersEntryInsteadOfAdmin() {
        String name = "judge_pa_" + suffix();
        int id = insertLoginableUser(name, 0, 1);
        Db.exec("insert into project_user_xref (pu_project, pu_user, pu_permission_level, pu_admin) values (1, ?, 2, 1)", id);
        Web s = newSession();
        Web.Page p = submitLogin(s, name, "admin");
        assertEquals("bugs.aspx", p.path());
        Set<String> links = sameSiteLinkPaths(p.html());
        assertTrue(links.contains("users.aspx"), "project admin gets users.aspx; have " + links);
        assertFalse(links.contains("admin.aspx"), "admin.aspx is Admin only; have " + links);
    }

    /** Sanctioned fix F4 (RULEBOOK §0.1, D-003): a non-admin can now be observed without search permission. */
    @Test
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:71", why = "text search form needs EnableSearch=1 and CanSearch")
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:23", why = "search.aspx link needs CanSearch")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:74", why = "CanSearch claim = og_can_search == 1")
    void userOfOrgWithoutSearchHasNoSearchMenu() {
        Db.exec("insert into orgs (og_name, og_can_search) values (N'judge nosearch', 0)");
        int org = ((Number) Db.scalar("select og_id from orgs where og_name = N'judge nosearch'")).intValue();
        String name = "judge_ns_" + suffix();
        insertLoginableUser(name, 0, org);
        Web s = newSession();
        Web.Page p = submitLogin(s, name, "admin");
        assertEquals("bugs.aspx", p.path());
        Set<String> links = sameSiteLinkPaths(p.html());
        assertFalse(links.contains("search.aspx"), "have " + links);
        assertFalse(hasTextSearchForm(p));
        assertTrue(links.contains("bugs.aspx"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/BasePage.cs:9,12-17", why = "[PageAuthorize(User)] on every BasePage; unauthenticated -> Response.Redirect(default.aspx?returnUrl=UrlEncode(RawUrl))")
    @SourceRef(value = "src/BugTracker.Web/bugs.aspx.cs:9-10", why = "bugs.aspx is a protected BasePage")
    void anonymousProtectedPageRedirectsToLoginWithReturnUrl() {
        String base = JudgeConfig.baseUrl().getPath();
        Web.Page p = web.getNoFollow("bugs.aspx?queryId=1");
        assertLoginRedirect(p, base + "bugs.aspx?queryId=1");

        Web.Page q = web.getNoFollow("logoff.aspx");
        assertLoginRedirect(q, base + "logoff.aspx");
    }

    @Test
    @Inherited("returnUrl set by the protection redirect is never read; login always lands on bugs.aspx")
    @SourceRef(value = "src/BugTracker.Web/BasePage.cs:16", why = "parameter is named returnUrl")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1100-1113", why = "post-login redirect reads 'url' only")
    @SourceRef(value = "src/BugTracker.Web/admin.aspx.cs:9", why = "admin.aspx is protected ([PageAuthorize(Admin)])")
    void loginIgnoresReturnUrl() {
        Web.Page loginPage = web.get("admin.aspx");
        assertEquals("default.aspx", loginPage.path());
        assertEquals(JudgeConfig.baseUrl().getPath() + "admin.aspx", Norm.query(loginPage.uri(), "returnUrl"));
        Web.Page p = loginOn(web, loginPage, "admin", "admin");
        assertEquals("bugs.aspx", p.path());
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1097-1118", why = "local 'url' -> Redirect(url + \"?\" + UrlDecode(qs)); non-local -> bugs.aspx")
    void loginHonoursLocalUrlParameterOnly() {
        String base = JudgeConfig.baseUrl().getPath();
        Web.Page lp = web.get("default.aspx?url=" + enc(base + "admin.aspx") + "&qs=");
        Web.Page p = loginOn(web, lp, "admin", "admin");
        assertEquals("admin.aspx", p.path());
        assertEquals(200, p.status());

        Web s2 = newSession();
        Web.Page lp2 = s2.get("default.aspx?url=" + enc("http://example.com/evil.aspx") + "&qs=");
        Web.Page p2 = loginOn(s2, lp2, "admin", "admin");
        assertEquals("bugs.aspx", p2.path());
        assertEquals(JudgeConfig.baseUrl().getHost(), p2.uri().getHost(), "no open redirect");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/logoff.aspx.cs:12-17", why = "SignOut then Redirect(default.aspx?msg=logged+off)")
    @SourceRef(value = "src/BugTracker.Web/default.aspx.cs:27,69,87-91", why = "msg 'logged off' shows nothing: cleared at :27 and the non-'logged off' branch that sets it is skipped")
    void logoffSignsOutAndRedirectsWithMessageParameter() {
        loginAdmin(web);
        assertTrue(loggedIn(web));
        Web.Page p = web.getNoFollow("logoff.aspx");
        assertEquals(302, p.status());
        URI loc = location(p);
        assertEquals("default.aspx", Norm.relPath(loc));
        assertEquals("logged off", Norm.query(loc, "msg"));
        Web.Page landing = web.get("default.aspx?msg=logged+off");
        assertEquals(200, landing.status());
        assertEquals("", logicalText(landing.html(), "msg"));
        assertFalse(loggedIn(web), "session must be signed out");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:20-62", why = "menu entries and their role conditions")
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:71-80", why = "text search form (EnableSearch=1, og_can_search=1) -> search_text.aspx, field query")
    @SourceRef(value = "src/BugTracker.Web/Controls/MainMenu.ascx:87-95", why = "settings + logoff")
    @SourceRef(value = "src/BugTracker.Web/Web.config:502-505,782,798", why = "CustomMenuLinkLabel only inside a comment; EnableWhatsNewPage=0; EnableSearch=1")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:108", why = "og_can_search default 1")
    void adminSeesAdminMenu() {
        Web.Page p = loginAdmin(web);
        assertEquals("bugs.aspx", p.path());
        Set<String> links = sameSiteLinkPaths(p.html());
        for (String must : new String[] {"bugs.aspx", "search.aspx", "queries.aspx", "reports.aspx", "admin.aspx",
                "edit_self.aspx", "logoff.aspx"}) {
            assertTrue(links.contains(must), "menu link " + must + " missing; have " + links);
        }
        assertFalse(links.contains("users.aspx"), "users.aspx is the project-admin entry; admin gets admin.aspx instead");
        assertFalse(links.contains("view_whatsnew.aspx"), "EnableWhatsNewPage=0");
        assertTrue(hasTextSearchForm(p), "navbar text-search form (search_text.aspx, field query)");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:84-124", why = "count of failures in last FailedLoginAttemptsMinutes; locked when count > allowed, before the password check")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:150-158", why = "each failure is appended")
    @SourceRef(value = "src/BugTracker.Web/Web.config:887-890", why = "FailedLoginAttempts* only in comments -> defaults 10 / 10")
    void elevenFailuresLockTheAccount() {
        String name = "judge_lk_" + suffix();
        copyAdminCredentialsTo(name, 1);
        Web s = newSession();
        for (int i = 0; i < 11; i++) {
            assertEquals(INVALID, logicalText(submitLogin(s, name, "bad" + i).html(), "msg"), "attempt " + i);
        }
        Map<String, Object> adminBefore = adminCredentials();
        Web.Page locked = submitLogin(s, name, "admin");
        assertEquals("default.aspx", locked.path());
        assertTrue(locked.redirects().isEmpty());
        assertEquals(INVALID, logicalText(locked.html(), "msg"));
        assertEquals(adminBefore, adminCredentials(), "locked attempt returns before the admin rewrite");
        assertFalse(loggedIn(s));
    }

    @Test
    @Inherited("Off by one: the lock needs count > FailedLoginAttemptsAllowed, so 10 failures still allow the next correct login")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:116", why = "failed_attempts.Count > failed_attempts_allowed")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:136-143", why = "success clears the list")
    void tenFailuresDoNotLock() {
        String name = "judge_ten_" + suffix();
        copyAdminCredentialsTo(name, 1);
        Web s = newSession();
        for (int i = 0; i < 10; i++) submitLogin(s, name, "bad" + i);
        Web.Page p = submitLogin(s, name, "admin");
        assertEquals("bugs.aspx", p.path());
        assertTrue(loggedIn(s));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/LoginController.cs:11-23", why = "POST api/login: success -> Ok() (200, no body), else 403")
    @SourceRef(value = "src/BugTracker.Web/Models/LoginModel.cs:5-7", why = "JSON properties User, Password")
    @SourceRef(value = "src/BugTracker.Web/App_Start/WebApiConfig.cs:14-18", why = "route api/{controller}")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:13-17", why = "success signs the cookie in")
    void apiLoginSuccessReturns200EmptyAndSignsIn() {
        Web.Page wrong = web.postJson("api/login", json("admin", "not-it"));
        assertEquals(403, wrong.status());
        Web.Page p = web.postJson("api/login", json("admin", "admin"));
        assertEquals(200, p.status());
        assertEquals(0, p.body().length, "Ok() has no content");
        assertTrue(loggedIn(web), "auth cookie usable for pages");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/LoginController.cs:20-23", why = "failure -> StatusCode(Forbidden), no content")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:63-69", why = "unknown user fails")
    void apiLoginFailureReturns403Empty() {
        Web.Page p = web.postJson("api/login", json("no_such_user_judge", "x"));
        assertEquals(403, p.status());
        assertEquals(0, p.body().length);
        assertFalse(loggedIn(web));
        assertEquals(SEED_ADMIN_SALT, adminCredentials().get("us_salt"));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Visible text of a server control by its logical id. WebForms renders
     * {@code id="msg"} inside a master page as {@code ctl00_body_msg}
     * (Web.config pages clientIDMode="AutoID"); the port renders {@code msg}.
     * See S1.md, "Normalization".
     */
    static String logicalText(Document d, String id) {
        return Norm.textByLogicalId(d, id);
    }

    private Web.Page loginOn(Web s, Web.Page page, String user, String pw) {
        return Form.containing(s, page, "pw").set("user", user).set("pw", pw).submit(null);
    }

    private static Map<String, Object> adminCredentials() {
        return Db.one("select us_salt, us_password from users where us_id = 1");
    }

    /** Fixture: a user whose password is "admin" (copy of user 1's seeded salt+hash; F5 keeps it the seed). */
    private static void copyAdminCredentialsTo(String username, int admin) {
        insertLoginableUser(username, admin, 1);
    }

    private static boolean hasTextSearchForm(Web.Page p) {
        for (Element f : p.html().select("form[action]")) {
            if (Norm.relPath(URI.create(f.absUrl("action"))).equals("search_text.aspx") && !f.select("[name=query]").isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** True iff a protected page answers 200; otherwise asserts the 302 to default.aspx (BasePage.cs:16). */
    private static boolean loggedIn(Web s) {
        Web.Page p = s.getNoFollow("bugs.aspx");
        if (p.status() == 200) return true;
        // BasePage.cs:16: anything else must be exactly the redirect to default.aspx
        assertEquals(302, p.status(), "logged-out probe must be a 302, was " + p.status());
        assertEquals("default.aspx", Norm.relPath(location(p)), "logged-out probe must redirect to default.aspx");
        return false;
    }

    private static void assertLoginRedirect(Web.Page p, String expectedReturnUrl) {
        assertEquals(302, p.status(), "status for " + p.uri());
        URI loc = location(p);
        assertEquals("default.aspx", Norm.relPath(loc));
        assertEquals(expectedReturnUrl, Norm.query(loc, "returnUrl"));
    }

    private static URI location(Web.Page p) {
        String l = p.header("Location").orElseThrow(() -> new AssertionError("no Location header"));
        return p.uri().resolve(l);
    }

    private static Set<String> sameSiteLinkPaths(Document d) {
        Set<String> out = new LinkedHashSet<>();
        String host = JudgeConfig.baseUrl().getHost();
        for (Element a : d.select("a[href]")) {
            String abs = a.absUrl("href");
            if (abs.isEmpty()) continue;
            try {
                URI u = URI.create(abs);
                if (!Objects.equals(u.getHost(), host)) continue;
                out.add(Norm.relPath(u));
            } catch (IllegalArgumentException ignored) {
                // javascript:, mailto: etc.
            }
        }
        return out;
    }

    private static String json(String user, String pw) {
        return "{\"User\":\"" + user + "\",\"Password\":\"" + pw + "\"}";
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
