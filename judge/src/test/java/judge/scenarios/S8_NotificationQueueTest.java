package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import judge.core.Db;
import judge.core.Form;
import judge.core.Inherited;
import judge.core.Norm;
import judge.core.Scenario;
import judge.core.SourceRef;
import judge.core.Web;

/**
 * S8 — notification queue (queued_notifications) as a side effect of editing a bug,
 * and the admin pages notifications.aspx / edit_queued_notifications.aspx.
 * See judge/specs/S8.md — in short: the original can never write a queue row.
 */
class S8_NotificationQueueTest extends Scenario {

    // ------------------------------------------------------------------ side effect of edit_bug

    @Test
    @Inherited("The queue INSERT has a stray quote (N''not sent') so the batch never compiles; any edit with a qualifying "
            + "subscriber ends in an unhandled SqlException (500) after the bug row was already saved, and nothing is queued. "
            + "For a non-admin subscriber Security.GetIdentity also fails first (selects no project_admin column).")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1178-1192", why = "delete+insert batch with N''not sent' (syntax error)")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1067-1107", why = "qualifying subscriber: subscribed, email, notifications on, active, change level, permission, not the editor")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1165", why = "GetIdentity(subscriber) before the insert")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:21-35,117", why = "dr[\"project_admin\"] not selected -> throws for non-admins")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:761-831", why = "bug UPDATE committed, then send_notifications (not caught)")
    @SourceRef(value = "src/BugTracker.Web/Web.config:159", why = "NotificationEmailEnabled = 1")
    void qualifyingSubscriberMakesEditFailAndQueuesNothing() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com' where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        loginAdmin(web);
        Web.Page p = editShortDesc("changed once");
        assertEquals(500, p.status());
        assertEquals(0L, Db.count("select count(*) from queued_notifications"));
        assertEquals("changed once", Db.scalar("select bg_short_desc from bugs where bg_id = 1"));
    }

    @Test
    @Inherited("Because the whole delete+insert batch fails to compile, an older queued row for the same bug and recipient is not replaced")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1178-1183", why = "delete ... where qn_bug = @bug and qn_to = @to is in the same failing batch")
    void existingQueueRowIsNotReplaced() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com' where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        seedQueueRow("2024-05-01T10:00:00", 1, 2, "not sent", 1, "old error", "developer@example.com", "old subject");
        loginAdmin(web);
        assertEquals(500, editShortDesc("changed twice").status());
        List<Map<String, Object>> rows = Db.rows("select qn_id, qn_bug, qn_user, qn_status, qn_retries, qn_last_exception, qn_to, qn_subject from queued_notifications");
        assertEquals(1, rows.size());
        assertEquals(Map.of("qn_id", 1, "qn_bug", 1, "qn_user", 2, "qn_status", "not sent", "qn_retries", 1,
                "qn_last_exception", "old error", "qn_to", "developer@example.com", "qn_subject", "old subject"), rows.get(0));
    }

    @Test
    @Inherited("Self-notification reaches the same broken INSERT: 500, nothing queued")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1095", why = "editor included when us_send_notifications_to_self = 1")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:111-114", why = "admin subscriber: GetIdentity succeeds, so the failure is the INSERT itself")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1183", why = "N''not sent'")
    void selfNotificationAlsoFails() {
        seedBug();
        Db.exec("update users set us_email = N'admin@example.com', us_send_notifications_to_self = 1 where us_id = 1");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 1)");
        loginAdmin(web);
        assertEquals(500, editShortDesc("self").status());
        assertEquals(0L, Db.count("select count(*) from queued_notifications"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1076", why = "us_enable_notifications = 1 required")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1107", why = "no subscriber rows -> nothing queued, no error")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:832", why = "msg 'Bug was updated.'")
    void subscriberWithNotificationsDisabledGetsNothing() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com', us_enable_notifications = 0 where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        assertQuietSuccess();
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1095", why = "the editor is excluded unless us_send_notifications_to_self = 1")
    void editorIsNotNotifiedByDefault() {
        seedBug();
        Db.exec("update users set us_email = N'admin@example.com' where us_id = 1");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 1)");
        assertQuietSuccess();
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1075,1079", why = "us_email not null and <> ''")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:170-172", why = "developer seeded without e-mail")
    void subscriberWithoutEmailGetsNothing() {
        seedBug();
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        assertQuietSuccess();
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1080-1083", why = "change level 4 (plain update) must be <= us_subscribed_notifications")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1013-1029", why = "no status/assignee change -> level 4")
    void subscriberBelowChangeLevelGetsNothing() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com', us_subscribed_notifications = 3 where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        assertQuietSuccess();
    }

    @Test
    @Inherited("edit_bug only saves when user_hit_submit=1 arrives, but no element of that name is rendered; a plain form post saves nothing and notifies nobody")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100,142-146", why = "did_user_hit_submit_button reads Request[\"user_hit_submit\"]")
    @SourceRef(value = "src/BugTracker.Web/Scripts/Site/EditBug.ts:202-204", why = "script sets #user_hit_submit, which edit_bug.aspx never renders")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx:236-270", why = "form fields: no user_hit_submit")
    void plainFormPostNeitherSavesNorNotifies() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com' where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        loginAdmin(web);
        Web.Page form = web.get("edit_bug.aspx?id=1");
        assertEquals(200, form.status());
        Web.Page p = Form.containing(web, form, "short_desc").set("short_desc", "never saved").submit(null);
        assertEquals(200, p.status());
        assertEquals("notify me", Db.scalar("select bg_short_desc from bugs where bg_id = 1"));
        assertEquals(0L, Db.count("select count(*) from queued_notifications"));
    }

    // ------------------------------------------------------------------ notifications.aspx

    @Test
    @SourceRef(value = "src/BugTracker.Web/notifications.aspx:36-63", why = "table#notifications-table columns and cells (<%= %>, unencoded)")
    @SourceRef(value = "src/BugTracker.Web/notifications.aspx.cs:27", why = "all rows ordered by qn_id")
    void queuePageListsRows() {
        seedQueueRow("2024-05-02T11:00:00", 7, 3, "held", 3, "smtp down", "tester@example.com", "s2");
        seedQueueRow("2024-05-01T10:00:00", 5, 2, "not sent", 0, "", "developer@example.com", "s1");
        loginAdmin(web);
        Web.Page p = web.get("notifications.aspx");
        assertEquals(200, p.status());
        List<List<String>> t = Norm.tableById(p.html(), "notifications-table");
        assertEquals(List.of("id", "data created", "to", "bug", "status", "retries", "last error"), t.get(0));
        assertEquals(3, t.size());
        // column 1 (DateTime.ToString(), server culture) is not judged
        assertEquals(List.of("1", "tester@example.com", "7", "held", "3", "smtp down"), without(t.get(1), 1));
        assertEquals(List.of("2", "developer@example.com", "5", "not sent", "0", ""), without(t.get(2), 1));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_queued_notifications.aspx:24-28,42", why = "actn=delete removes 'not sent' rows, then redirects to notifications.aspx")
    @SourceRef(value = "src/BugTracker.Web/edit_queued_notifications.aspx:18", why = "no ses parameter + no session_cookie (never set) passes the check")
    void deleteRemovesOnlyUnsent() {
        seedQueueRow("2024-05-01T10:00:00", 5, 2, "not sent", 3, "x", "developer@example.com", "s1");
        seedQueueRow("2024-05-02T11:00:00", 7, 3, "held", 3, "y", "tester@example.com", "s2");
        loginAdmin(web);
        Web.Page p = web.get("edit_queued_notifications.aspx?actn=delete");
        assertEquals(1, p.redirects().size());
        assertEquals("notifications.aspx", p.path());
        assertEquals(List.of(Map.of("qn_id", 2, "qn_status", "held")), Db.rows("select qn_id, qn_status from queued_notifications"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_queued_notifications.aspx:29-33,42", why = "actn=reset sets qn_retries = 0 on 'not sent' rows only")
    void resetZeroesRetriesOfUnsentOnly() {
        seedQueueRow("2024-05-01T10:00:00", 5, 2, "not sent", 3, "x", "developer@example.com", "s1");
        seedQueueRow("2024-05-02T11:00:00", 7, 3, "held", 3, "y", "tester@example.com", "s2");
        loginAdmin(web);
        Web.Page p = web.get("edit_queued_notifications.aspx?actn=reset");
        assertEquals("notifications.aspx", p.path());
        assertEquals(List.of(Map.of("qn_id", 1, "qn_retries", 0, "qn_last_exception", "x"),
                        Map.of("qn_id", 2, "qn_retries", 3, "qn_last_exception", "y")),
                Db.rows("select qn_id, qn_retries, qn_last_exception from queued_notifications order by qn_id"));
    }

    @Test
    @Inherited("The 'Delete unsent notifications' link carries a stray ' %>' in actn, so following it deletes nothing")
    @SourceRef(value = "src/BugTracker.Web/notifications.aspx:21", why = "href=\"edit_queued_notifications.aspx?actn=delete %>\"")
    @SourceRef(value = "src/BugTracker.Web/edit_queued_notifications.aspx:24,42", why = "actn must equal 'delete'; otherwise just redirect")
    void renderedDeleteLinkDeletesNothing() {
        seedQueueRow("2024-05-01T10:00:00", 5, 2, "not sent", 3, "x", "developer@example.com", "s1");
        loginAdmin(web);
        Document d = web.get("notifications.aspx").html();
        Element link = null;
        for (Element a : d.select("a[href]")) if (Norm.text(a.text()).equals("Delete unsent notifications")) link = a;
        assertNotNull(link, "Delete unsent notifications link");
        // percent-encode as a browser would before sending (space, %, > are not legal in a URI)
        String href = link.attr("href").replace("%", "%25").replace(" ", "%20").replace(">", "%3E");
        Web.Page p = web.get(href);
        assertEquals("notifications.aspx", p.path());
        assertEquals(1L, Db.count("select count(*) from queued_notifications where qn_status = N'not sent'"));
    }

    @Test
    @Inherited("A ses parameter is compared with Session[\"session_cookie\"], which nothing ever sets: any ses value blocks the action")
    @SourceRef(value = "src/BugTracker.Web/edit_queued_notifications.aspx:18-22", why = "message + Response.End")
    void sesParameterBlocksAction() {
        seedQueueRow("2024-05-01T10:00:00", 5, 2, "not sent", 3, "x", "developer@example.com", "s1");
        loginAdmin(web);
        Web.Page p = web.get("edit_queued_notifications.aspx?actn=delete&ses=abc");
        assertEquals(200, p.status());
        assertEquals("session in URL doesn't match session cookie", Norm.text(p.text()));
        assertEquals(1L, Db.count("select count(*) from queued_notifications"));
    }

    // ------------------------------------------------------------------ helpers

    /** Bug 1: project 1, org 1, unassigned, reported/last updated by admin, fixed dates. */
    private static void seedBug() {
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org, bg_category, bg_project,"
                + " bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date)"
                + " values (N'notify me', 1, '2024-01-15T00:00:00', 1, 2, 1, 1, 1, NULL, 1, '2024-01-15T00:00:00')");
    }

    private static void seedQueueRow(String created, int bug, int user, String status, int retries, String ex, String to, String subject) {
        Db.exec("insert into queued_notifications (qn_date_created, qn_bug, qn_user, qn_status, qn_retries, qn_last_exception, qn_to, qn_from, qn_subject, qn_body)"
                + " values (?, ?, ?, ?, ?, ?, ?, N'dont_reply@example.com', ?, N'<html>body</html>')",
                java.sql.Timestamp.valueOf(created.replace('T', ' ')), bug, user, status, retries, ex, to, subject);
    }

    /**
     * Change only the short description through the edit form. The query string carries
     * user_hit_submit=1 (Request[] reads the query string too, edit_bug.aspx.cs:144) and WebForms
     * posts back to the same URL, so the save path runs; see spec for why.
     */
    private Web.Page editShortDesc(String desc) {
        Web.Page form = web.get("edit_bug.aspx?id=1&user_hit_submit=1");
        assertEquals(200, form.status());
        return Form.containing(web, form, "short_desc").set("short_desc", desc).submit(null);
    }

    private void assertQuietSuccess() {
        loginAdmin(web);
        Web.Page p = editShortDesc("changed quietly");
        assertEquals(200, p.status());
        assertEquals("Bug was updated.", Norm.textById(p.html(), "msg"));
        assertEquals("changed quietly", Db.scalar("select bg_short_desc from bugs where bg_id = 1"));
        assertEquals(0L, Db.count("select count(*) from queued_notifications"));
    }

    private static List<String> without(List<String> row, int col) {
        List<String> r = new java.util.ArrayList<>(row);
        r.remove(col);
        return r;
    }
}
