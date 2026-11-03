package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * S6 — admin CRUD: users, projects, categories. Spec: judge/specs/S6.md.
 */
class S6_AdminCrudTest extends Scenario {

    /** setup.sql seeds 9 users (admin + 8 at :170-200). */
    private static final long SEED_USERS = 9;

    // ------------------------------------------------------------------ tests

    @Test
    @SourceRef(value = "src/BugTracker.Web/admin.aspx:75-94", why = "admin menu links")
    @SourceRef(value = "src/BugTracker.Web/admin.aspx.cs:9", why = "[PageAuthorize(Admin)]")
    void adminPageListsAdminSections() {
        loginAdmin(web);
        Web.Page p = web.get("admin.aspx");
        assertEquals(200, p.status());
        assertEquals("admin.aspx", p.path());
        Set<String> links = sameSiteLinkPaths(p.html());
        for (String must : new String[] {"users.aspx", "orgs.aspx", "projects.aspx", "categories.aspx",
                "priorities.aspx", "statuses.aspx", "udfs.aspx", "customfields.aspx", "notifications.aspx",
                "edit_custom_html.aspx", "backup_db.aspx", "manage_logs.aspx"}) {
            assertTrue(links.contains(must), must + " missing; have " + links);
        }
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/BasePage.cs:12-17,21-31", why = "not authorized -> 302 default.aspx?returnUrl=UrlEncode(RawUrl)")
    @SourceRef(value = "src/BugTracker.Web/admin.aspx.cs:9", why = "Admin")
    @SourceRef(value = "src/BugTracker.Web/users.aspx.cs:10", why = "Admin, ProjectAdmin")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:10", why = "Admin")
    @SourceRef(value = "src/BugTracker.Web/delete_user.aspx.cs:9", why = "Admin, ProjectAdmin")
    @SourceRef(value = "src/BugTracker.Web/edit_project.aspx.cs:11", why = "Admin")
    @SourceRef(value = "src/BugTracker.Web/edit_category.aspx.cs:9", why = "Admin")
    @SourceRef(value = "src/BugTracker.Web/projects.aspx.cs:9", why = "Admin")
    @SourceRef(value = "src/BugTracker.Web/categories.aspx.cs:9", why = "Admin, ProjectAdmin")
    void anonymousAdminPagesRedirectToLogin() {
        String base = JudgeConfig.baseUrl().getPath();
        for (String page : new String[] {"admin.aspx", "users.aspx", "edit_user.aspx?id=2", "delete_user.aspx?id=2",
                "edit_project.aspx", "projects.aspx", "edit_category.aspx", "categories.aspx"}) {
            Web.Page p = web.getNoFollow(page);
            assertEquals(302, p.status(), page);
            URI loc = p.uri().resolve(p.header("Location").orElseThrow());
            assertEquals("default.aspx", Norm.relPath(loc), page);
            assertEquals(base + page, Norm.query(loc, "returnUrl"), page);
        }
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/BasePage.cs:12-17", why = "redirect happens in OnPreInit, before Page_Load's delete")
    @SourceRef(value = "src/BugTracker.Web/delete_user.aspx.cs:42-57", why = "the delete itself")
    void anonymousDeletePostDeletesNothing() {
        Web.Page p = web.postForm(web.resolve("delete_user.aspx?id=7"), List.of(Map.entry("row_id", "7")));
        assertFalse(p.redirects().isEmpty(), "must be redirected");
        assertEquals("default.aspx", Norm.relPath(p.redirects().get(0)));
        assertEquals(SEED_USERS, Db.count("select count(1) from users"));
        assertEquals(2, Db.count("select count(1) from project_user_xref where pu_user = 7"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:63-74,254-262", why = "new-user defaults: popups, own/reported auto-subscribe, notif=4, bugs_per_page 10, active, notifications")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:491-528", why = "column values incl. us_forced_project from dropdown ('0'), bool_to_string")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:538-598", why = "insert, then update_user_password, then project xref, then Server.Transfer(users.aspx)")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:722-777", why = "all grid radios at DefaultPermissionLevel -> no project_user_xref rows")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:502-515,533-542", why = "salted hash; salt 10..99 alnum chars")
    @SourceRef(value = "src/BugTracker.Web/users.aspx:34-61", why = "user list columns")
    @SourceRef(value = "src/BugTracker.Web/btnet/sortable_html_table.cs:82,191-240", why = "table#mytable, empty cell -> &nbsp;")
    void createUserWritesRowAndShowsItInList() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx"), "username");
        assertEquals("10", f.get("bugs_per_page"));
        f.set("username", "judge1").set("pw", "Secret99").set("confirm_pw", "Secret99")
                .set("firstname", "Judy").set("lastname", "Gee").set("email", "judy@example.com")
                .set("org", "2").set("query", "1").set("signature", "");
        Web.Page p = f.submit("sub");
        assertEquals(200, p.status());

        Map<String, Object> u = Db.one("select * from users where us_username = 'judge1'");
        assertNum(10, u.get("us_id"));
        assertEquals("Judy", u.get("us_firstname"));
        assertEquals("Gee", u.get("us_lastname"));
        assertEquals("judy@example.com", u.get("us_email"));
        assertNum(0, u.get("us_admin"));
        assertNum(1, u.get("us_default_query"));
        assertNum(1, u.get("us_enable_notifications"));
        assertNum(0, u.get("us_auto_subscribe"));
        assertNum(1, u.get("us_auto_subscribe_own_bugs"));
        assertNum(1, u.get("us_auto_subscribe_reported_bugs"));
        assertNum(0, u.get("us_send_notifications_to_self"));
        assertNum(1, u.get("us_active"));
        assertNum(10, u.get("us_bugs_per_page"));
        assertNum(0, u.get("us_forced_project"));
        assertNum(4, u.get("us_reported_notifications"));
        assertNum(4, u.get("us_assigned_notifications"));
        assertNum(4, u.get("us_subscribed_notifications"));
        assertEquals("", u.get("us_signature"));
        assertNum(0, u.get("us_use_fckeditor"));
        assertNum(1, u.get("us_enable_bug_list_popups"));
        assertNum(1, u.get("us_created_user"));
        assertNum(2, u.get("us_org"));
        assertNull(u.get("us_most_recent_login_datetime"));
        assertNull(u.get("password_reset_key"));
        String salt = (String) u.get("us_salt");
        assertNotNull(salt);
        assertTrue(salt.matches("[A-Za-z0-9]{10,99}"), "salt " + salt);
        assertNotNull(u.get("us_password"));
        assertNotEquals("Secret99", u.get("us_password"), "password must be hashed");
        assertEquals(0, Db.count("select count(1) from project_user_xref where pu_user = 10"));

        assertEquals(List.of("10", "edit", "copy", "delete", "judge1", "Judy Gee", "developers", "judy@example.com",
                "N", "N", "Y", "N", "", "all bugs", "Y", "", "admin"), userRow(p.html(), "judge1"));
    }

    @Test
    @Inherited("Save is a Server.Transfer: the browser stays on edit_user.aspx (no redirect), so a refresh re-posts")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:598", why = "Server.Transfer(\"users.aspx\")")
    @SourceRef(value = "src/BugTracker.Web/edit_category.aspx.cs:139", why = "Server.Transfer(\"categories.aspx\")")
    void saveRendersListWithoutRedirect() {
        loginAdmin(web);
        primeAspNetSession();
        Web.Page p = Form.containing(web, web.get("edit_category.aspx"), "name")
                .set("name", "judgecat").set("sort_seq", "1").submit("sub");
        assertTrue(p.redirects().isEmpty(), "no redirect: " + p.redirects());
        assertEquals("edit_category.aspx", p.path());
        assertNotNull(p.html().getElementById("categories-table"), "list rendered in the same response");
    }

    @Test
    @Inherited("On create, Page_Load re-checks enable_popups / auto_subscribe_own / auto_subscribe_reported after post data is loaded, so unchecking them has no effect")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:63-74", why = "id == 0: Checked = true on every request, including the postback")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:393-396", why = "on_update runs inside the same Page_Load, after that")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:498,507-508", why = "values written")
    void createUserIgnoresUncheckedDefaultFlags() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx"), "username");
        f.set("username", "judge2").set("pw", "Secret99").set("confirm_pw", "Secret99")
                .set("org", "1").set("query", "1").set("signature", "")
                .check("enable_popups", false).check("auto_subscribe_own", false).check("auto_subscribe_reported", false)
                .check("enable_notifications", false);
        f.submit("sub");
        Map<String, Object> u = Db.one("select * from users where us_username = 'judge2'");
        assertNum(1, u.get("us_enable_bug_list_popups"));
        assertNum(1, u.get("us_auto_subscribe_own_bugs"));
        assertNum(1, u.get("us_auto_subscribe_reported_bugs"));
        assertNum(0, u.get("us_enable_notifications")); // not forced: set only on GET (:260)
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:517-520", why = "admin may create admins")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:594", why = "update_user_password(id, pw)")
    @SourceRef(value = "src/BugTracker.Web/Security/Authenticate.cs:213-223", why = "login compares HashString(pw, us_salt) with us_password")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1097-1108", why = "lands on bugs.aspx")
    void createdAdminUserCanLogInWithChosenPassword() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx"), "username");
        f.set("username", "judgeadm").set("pw", "Secret99").set("confirm_pw", "Secret99")
                .set("org", "1").set("query", "1").set("signature", "").check("admin", true);
        f.submit("sub");
        assertNum(1, Db.scalar("select us_admin from users where us_username = 'judgeadm'"));

        Web s = newSession();
        Web.Page bad = submitLogin(s, "judgeadm", "Secret98");
        assertEquals("default.aspx", bad.path());
        Web.Page ok = submitLogin(s, "judgeadm", "Secret99");
        assertEquals("bugs.aspx", ok.path());
        assertEquals(200, ok.status());
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:401-477", why = "validation messages")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:669-674", why = "msg 'User was not created.'")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:228", why = "org default item '[select org]'")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx:64-106,434", why = "error cells username_err, pw_err, confirm_pw_err, org_err; span msg")
    void createUserValidationErrors() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx"), "username");
        assertEquals("0", f.get("org"), "[select org] preselected");
        Web.Page p = f.set("username", "").set("pw", "").set("confirm_pw", "").set("signature", "").submit("sub");
        assertEquals(200, p.status());
        Document d = p.html();
        assertEquals("Username is required.", S1_LoginTest.logicalText(d, "username_err"));
        assertEquals("Password is required.", S1_LoginTest.logicalText(d, "pw_err"));
        assertEquals("", S1_LoginTest.logicalText(d, "confirm_pw_err"));
        assertEquals("You must select a org", S1_LoginTest.logicalText(d, "org_err"));
        assertEquals("", S1_LoginTest.logicalText(d, "bugs_per_page_err"));
        assertEquals("User was not created.", S1_LoginTest.logicalText(d, "msg"));
        assertEquals(SEED_USERS, Db.count("select count(1) from users"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:541-545,600-604", why = "existing username -> username_err + msg")
    void createUserDuplicateUsername() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx"), "username");
        Web.Page p = f.set("username", "admin").set("pw", "x").set("confirm_pw", "x").set("org", "1")
                .set("query", "1").set("signature", "").submit("sub");
        assertEquals("User already exists. Choose another username.", S1_LoginTest.logicalText(p.html(), "username_err"));
        assertEquals("User was not created.", S1_LoginTest.logicalText(p.html(), "msg"));
        assertEquals(SEED_USERS, Db.count("select count(1) from users"));
        assertEquals(1, Db.count("select count(1) from users where us_username = 'admin'"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:172-197,290-352", why = "form prefilled from the row; isnull(us_bugs_per_page,10), isnull(us_forced_project,0)")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:608-659", why = "update; password only when pw != ''")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:170-172", why = "developer: Al Kaline, org 2, default query 2, us_password 'admin', no salt")
    void editUserUpdatesRowAndKeepsPasswordWhenBlank() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx?id=2"), "username");
        assertEquals("developer", f.get("username"));
        assertEquals("Al", f.get("firstname"));
        assertEquals("Kaline", f.get("lastname"));
        assertEquals("10", f.get("bugs_per_page"));
        assertEquals("2", f.get("org"));
        assertEquals("2", f.get("query"));
        assertEquals("0", f.get("forced_project"));
        assertEquals("", f.get("pw"));
        f.set("firstname", "Alan").set("email", "al@example.com").set("signature", "");
        Web.Page p = f.submit("sub");
        assertEquals(200, p.status());

        Map<String, Object> u = Db.one("select * from users where us_id = 2");
        assertEquals("developer", u.get("us_username"));
        assertEquals("Alan", u.get("us_firstname"));
        assertEquals("Kaline", u.get("us_lastname"));
        assertEquals("al@example.com", u.get("us_email"));
        assertEquals("admin", u.get("us_password"), "blank pw leaves password untouched");
        assertNull(u.get("us_salt"));
        assertNum(10, u.get("us_bugs_per_page"));
        assertNum(0, u.get("us_forced_project"));
        assertEquals("", u.get("us_signature"));
        assertNum(2, u.get("us_default_query"));
        assertNum(2, u.get("us_org"));
        assertNum(0, u.get("us_admin"));
        assertNum(1, u.get("us_active"));
        assertNum(1, u.get("us_enable_notifications"));
        assertNum(0, u.get("us_send_notifications_to_self"));
        assertNum(0, u.get("us_auto_subscribe"));
        assertNum(0, u.get("us_auto_subscribe_own_bugs"));
        assertNum(0, u.get("us_auto_subscribe_reported_bugs"));
        assertNum(1, u.get("us_enable_bug_list_popups"));
        assertNum(0, u.get("us_use_fckeditor"));
        assertNum(4, u.get("us_reported_notifications"));
        assertNum(1, u.get("us_created_user"));
        assertEquals(0, Db.count("select count(1) from project_user_xref where pu_user = 2"));
        assertEquals(List.of("2", "edit", "copy", "delete", "developer", "Alan Kaline", "developers", "al@example.com",
                "N", "N", "Y", "N", "", "all bugs", "Y", "", "admin"), userRow(p.html(), "developer"));
    }

    @Test
    @Inherited("Saving a user never stores permission level 3 (reporter): the level-3 UPDATE is built with String.Format but never appended, after all rows were reset to DefaultPermissionLevel")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:429-430", why = "user 8 'reporter': project 1 level 3, project 2 level 0")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx:388-398", why = "grid radios, value 3 = reporter")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:794-800", why = "reset every row to @dpl")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:853-875", why = "level 0 re-applied")
    @SourceRef(value = "src/BugTracker.Web/edit_user.aspx.cs:926-947", why = "level 3 statement discarded")
    void savingReporterUserDowngradesReporterPermission() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_user.aspx?id=8"), "username");
        assertEquals("reporter", f.get("username"));
        assertEquals("3", f.get("permissions"), "first grid row (project 1) shows reporter");
        f.set("signature", "").submit("sub");
        assertNum(2, Db.scalar("select pu_permission_level from project_user_xref where pu_user = 8 and pu_project = 1"));
        assertNum(0, Db.scalar("select pu_permission_level from project_user_xref where pu_user = 8 and pu_project = 2"));
        assertEquals(2, Db.count("select count(1) from project_user_xref where pu_user = 8"));
        assertNum(1, Db.scalar("select us_forced_project from users where us_id = 8"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/delete_user.aspx.cs:59-93", why = "GET: confirm text, row_id")
    @SourceRef(value = "src/BugTracker.Web/delete_user.aspx.cs:42-57", why = "POST: deletes user + xref rows, Server.Transfer(users.aspx)")
    @SourceRef(value = "src/BugTracker.Web/delete_user.aspx:13-14", why = "confirm_href, row_id")
    @SourceRef(value = "src/BugTracker.Web/users.aspx:59-61,128-135", why = "list includes inactive users, ordered by username")
    void deleteUserRemovesUserAndProjectRows() {
        loginAdmin(web);
        primeAspNetSession();
        Web.Page confirm = web.get("delete_user.aspx?id=7");
        assertEquals(200, confirm.status());
        assertEquals("confirm delete of \"viewer\"", S1_LoginTest.logicalText(confirm.html(), "confirm_href"));
        Form f = Form.containing(web, confirm, "row_id");
        assertEquals("7", f.get("row_id"));
        Web.Page p = f.submit(null);
        assertEquals(200, p.status());
        assertEquals(0, Db.count("select count(1) from users where us_id = 7"));
        assertEquals(SEED_USERS - 1, Db.count("select count(1) from users"));
        assertEquals(0, Db.count("select count(1) from project_user_xref where pu_user = 7"));
        assertEquals(4, Db.count("select count(1) from project_user_xref"));
        assertEquals(List.of("admin", "customer1", "customer2", "developer", "email", "guest", "reporter", "tester"),
                userColumn(p.html(), 4));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/delete_user.aspx.cs:64-83", why = "referenced by a bug -> message, Response.End")
    void deleteUserReferencedByBugIsRefused() {
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org,"
                + " bg_category, bg_project, bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date)"
                + " values ('judge bug', 2, '20200115', 1, 1, 1, 1, 1, 0, 2, '20200115')");
        loginAdmin(web);
        Web.Page p = web.get("delete_user.aspx?id=2");
        assertEquals(200, p.status());
        assertTrue(Norm.text(p.html().text()).contains(
                "You can't delete user \"developer\" because some bugs or bug posts still reference it."), p.text());
        assertEquals(SEED_USERS, Db.count("select count(1) from users"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_project.aspx.cs:36-50", why = "new: default_user '' = 0, active checked")
    @SourceRef(value = "src/BugTracker.Web/edit_project.aspx.cs:202-290", why = "insert with every column from the form")
    void createProjectWritesRow() {
        loginAdmin(web);
        primeAspNetSession();
        Form f = Form.containing(web, web.get("edit_project.aspx"), "name");
        assertEquals("0", f.get("default_user"));
        f.set("name", "judgeproj").set("default_user", "2").set("desc", "judge desc")
                .set("custom_dropdown_values1", "").set("custom_dropdown_values2", "").set("custom_dropdown_values3", "");
        Web.Page p = f.submit("sub");
        assertEquals(200, p.status());
        Map<String, Object> r = Db.one("select * from projects where pj_name = 'judgeproj'");
        assertNum(3, r.get("pj_id"));
        assertNum(1, r.get("pj_active"));
        assertNum(2, r.get("pj_default_user"));
        assertNum(0, r.get("pj_default"));
        assertNum(0, r.get("pj_auto_assign_default_user"));
        assertNum(0, r.get("pj_auto_subscribe_default_user"));
        assertNum(0, r.get("pj_enable_pop3"));
        assertEquals("", r.get("pj_pop3_username"));
        assertEquals("", r.get("pj_pop3_password"));
        assertEquals("", r.get("pj_pop3_email_from"));
        assertEquals("judge desc", r.get("pj_description"));
        for (int i = 1; i <= 3; i++) {
            assertNum(0, r.get("pj_enable_custom_dropdown" + i));
            assertEquals("", r.get("pj_custom_dropdown_label" + i));
            assertEquals("", r.get("pj_custom_dropdown_values" + i));
        }
        assertEquals(List.of("3", "judgeproj", "Y", "N", "N", "N", "N", "", "", "N", ""),
                bodyRows(p.html(), "projects-table").get(0));
    }

    @Test
    @Inherited("projects list 'default user' column shows Y when pj_default_user == 1 (user id 1), not when a default user is set")
    @SourceRef(value = "src/BugTracker.Web/projects.aspx:44-53", why = "project.DefaultUser == 1 ? \"Y\" : \"N\"")
    @SourceRef(value = "src/BugTracker.Web/projects.aspx.cs:21-24", why = "ordered by name")
    void projectsListDefaultUserColumnQuirk() {
        Db.exec("update projects set pj_default_user = 1 where pj_id = 1");
        Db.exec("update projects set pj_default_user = 2 where pj_id = 2");
        loginAdmin(web);
        Web.Page p = web.get("projects.aspx");
        assertEquals(List.of(
                List.of("1", "project 1", "Y", "Y", "N", "N", "N", "", "", "N", ""),
                List.of("2", "project 2", "Y", "N", "N", "N", "N", "", "", "N", "")),
                bodyRows(p.html(), "projects-table"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_category.aspx.cs:114-139", why = "insert ct_name, ct_sort_seq, ct_default")
    @SourceRef(value = "src/BugTracker.Web/categories.aspx.cs:23-26", why = "list ordered by name")
    @SourceRef(value = "src/BugTracker.Web/categories.aspx:17-33", why = "table#categories-table")
    void createCategoryWritesRowAndListsIt() {
        loginAdmin(web);
        primeAspNetSession();
        Web.Page p = Form.containing(web, web.get("edit_category.aspx"), "name")
                .set("name", "judgecat").set("sort_seq", "7").submit("sub");
        Map<String, Object> r = Db.one("select * from categories where ct_name = 'judgecat'");
        assertNum(6, r.get("ct_id"));
        assertNum(7, r.get("ct_sort_seq"));
        assertNum(0, r.get("ct_default"));
        List<String> names = new ArrayList<>();
        for (List<String> row : bodyRows(p.html(), "categories-table")) names.add(row.get(0));
        assertEquals(List.of("bug", "enhancement", "judgecat", "question", "task", "ticket"), names);
    }

    @Test
    @Inherited("An empty sort sequence reports 'must be an integer': the 'required' message is overwritten by the is_int check")
    @SourceRef(value = "src/BugTracker.Web/edit_category.aspx.cs:72-108", why = "messages; second check overwrites the first")
    @SourceRef(value = "src/BugTracker.Web/edit_category.aspx.cs:142-147", why = "msg 'Category was not created.'")
    @SourceRef(value = "src/BugTracker.Web/edit_category.aspx:19,32,44", why = "name_err, sort_seq_err, msg")
    void categoryValidationErrors() {
        loginAdmin(web);
        primeAspNetSession();
        Web.Page p = Form.containing(web, web.get("edit_category.aspx"), "name")
                .set("name", "").set("sort_seq", "").submit("sub");
        Document d = p.html();
        assertEquals("Description is required.", S1_LoginTest.logicalText(d, "name_err"));
        assertEquals("Sort Sequence must be an integer.", S1_LoginTest.logicalText(d, "sort_seq_err"));
        assertEquals("Category was not created.", S1_LoginTest.logicalText(d, "msg"));
        assertEquals(5, Db.count("select count(1) from categories"));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Makes the ASP.NET session non-empty before any page that sets
     * ViewStateUserKey = Session.SessionID (edit_user.aspx.cs:20 etc.). An empty
     * InProc session gets a fresh id on each request, which would fail the
     * postback's view-state check. search.aspx stores Session["bugs"] on a
     * search postback (search.aspx.cs:620). Read-only; harmless for the port.
     */
    private void primeAspNetSession() {
        Form f = Form.containing(web, web.get("search.aspx"), "like");
        if (f.has("hit_submit_button")) f.set("hit_submit_button", "1");
        Web.Page p = f.submit(null);
        assertEquals(200, p.status(), "session priming search");
    }

    private static void assertNum(long expected, Object actual) {
        assertNotNull(actual, "expected " + expected + " but was NULL");
        assertEquals(expected, ((Number) actual).longValue());
    }

    /** Data rows (rows containing td) of the table with this id. */
    static List<List<String>> bodyRows(Document d, String id) {
        Element t = d.getElementById(id);
        if (t == null) throw new AssertionError("No table #" + id);
        List<List<String>> out = new ArrayList<>();
        for (Element tr : t.select("tr")) {
            if (tr.closest("table") != t || tr.select("> td").isEmpty()) continue;
            // sortable_html_table.cs:109-127 writes its heading row as <td class=datah>
            if (tr.select("> td").size() == tr.select("> td.datah").size()) continue;
            List<String> row = new ArrayList<>();
            for (Element c : tr.children()) if (c.tagName().equals("td") || c.tagName().equals("th")) row.add(Norm.text(c.text()));
            out.add(row);
        }
        return out;
    }

    private static List<String> userRow(Document d, String username) {
        for (List<String> r : bodyRows(d, "mytable")) if (r.size() > 4 && r.get(4).equals(username)) return r;
        throw new AssertionError("no row for " + username + " in #mytable");
    }

    private static List<String> userColumn(Document d, int col) {
        List<String> out = new ArrayList<>();
        for (List<String> r : bodyRows(d, "mytable")) out.add(r.get(col));
        return out;
    }

    private static Set<String> sameSiteLinkPaths(Document d) {
        Set<String> out = new LinkedHashSet<>();
        String host = JudgeConfig.baseUrl().getHost();
        for (Element a : d.select("a[href]")) {
            try {
                URI u = URI.create(a.absUrl("href"));
                if (Objects.equals(u.getHost(), host)) out.add(Norm.relPath(u));
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }
}
