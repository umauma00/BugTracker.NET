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
 * See judge/specs/S8.md. Sanctioned fix F3 (D-003): the port writes the queue row the original
 * could not (syntax error); the tests assert that row.
 */
class S8_NotificationQueueTest extends Scenario {

    // ------------------------------------------------------------------ side effect of edit_bug

    /**
     * Sanctioned fix F3 (RULEBOOK §0.1, D-003): the queue INSERT reads {@code N'not sent'}.
     * Sanctioned fix F4 also applies: the developer subscriber is a non-admin, so
     * {@code Security.GetIdentity} (bug.cs:1165) must succeed for it.
     *
     * <p>Not asserted: qn_retries and qn_last_exception. A worker thread starts after the
     * insert (bug.cs:1198-1203) and, with no SMTP server, bumps them (:1240-1250) at a time
     * the judge cannot predict; qn_status stays {@code not sent} either way.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1178-1192", why = "delete + insert batch, status N'not sent', retries 0, body = print_bug html")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1183", why = "values (getdate(), @bug, @user, N'not sent', 0, @to, @from, @subject, @body, N'')")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1067-1107", why = "qualifying subscriber: subscribed, email, notifications on, active, change level, permission, not the editor")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1116-1148", why = "subject from NotificationSubjectFormat: $THING$ -> 'Bug', $BUGID$, $ACTION$ -> 'updated', $SHORTDESC$ (after the update), $TRACKINGID$ = ' (' + TrackingIdString + bugid + ')'")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1150-1163", why = "body starts <html><base href=\"AbsoluteUrlPrefix\"/>, then print_bug output")
    @SourceRef(value = "src/BugTracker.Web/Web.config:128,164,180,525", why = "AbsoluteUrlPrefix http://127.0.0.1/btnet/, NotificationEmailFrom dont_reply@example.com, NotificationSubjectFormat, TrackingIdString 'DO NOT EDIT THIS:'")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:111-121", why = "GetIdentity for the non-admin subscriber works once project_admin is selected (F4)")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:761-831", why = "bug UPDATE committed, then send_notifications; page answers 'Bug was updated.'")
    @SourceRef(value = "src/BugTracker.Web/Web.config:159", why = "NotificationEmailEnabled = 1")
    void qualifyingSubscriberGetsQueuedNotification() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com' where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        loginAdmin(web);
        java.sql.Timestamp before = S3_EditBugTest.dbNow();
        Web.Page p = editShortDesc("changed once");
        java.sql.Timestamp after = S3_EditBugTest.dbNow();
        assertEquals(200, p.status());
        assertEquals("Bug was updated.", Norm.textById(p.html(), "msg"));
        assertEquals("changed once", Db.scalar("select bg_short_desc from bugs where bg_id = 1"));
        assertQueuedRow(Db.one("select * from queued_notifications"), 2, "developer@example.com", "changed once", before, after);
    }

    /** Sanctioned fix F3 (RULEBOOK §0.1, D-003): the delete in the same batch now runs, and the new row is inserted. */
    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1178-1183", why = "delete from queued_notifications where qn_bug = @bug and qn_to = @to, then insert, in one batch")
    void existingQueueRowIsReplaced() {
        seedBug();
        Db.exec("update users set us_email = N'developer@example.com' where us_id = 2");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 2)");
        seedQueueRow("2024-05-01T10:00:00", 1, 2, "not sent", 1, "old error", "developer@example.com", "old subject");
        loginAdmin(web);
        java.sql.Timestamp before = S3_EditBugTest.dbNow();
        assertEquals(200, editShortDesc("changed twice").status());
        java.sql.Timestamp after = S3_EditBugTest.dbNow();
        List<Map<String, Object>> rows = Db.rows("select * from queued_notifications");
        assertEquals(1, rows.size(), "old row deleted, one new row");
        assertQueuedRow(rows.get(0), 2, "developer@example.com", "changed twice", before, after);
        org.junit.jupiter.api.Assertions.assertNotEquals(1, ((Number) rows.get(0).get("qn_id")).intValue(), "not the old row");
    }

    /** Sanctioned fix F3 (RULEBOOK §0.1, D-003): self-notification reaches the (now valid) INSERT. */
    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1095", why = "editor included when us_send_notifications_to_self = 1")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1183", why = "N'not sent'")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1116-1148", why = "subject format")
    void selfNotificationIsQueued() {
        seedBug();
        Db.exec("update users set us_email = N'admin@example.com', us_send_notifications_to_self = 1 where us_id = 1");
        Db.exec("insert into bug_subscriptions (bs_bug, bs_user) values (1, 1)");
        loginAdmin(web);
        java.sql.Timestamp before = S3_EditBugTest.dbNow();
        Web.Page p = editShortDesc("self");
        java.sql.Timestamp after = S3_EditBugTest.dbNow();
        assertEquals(200, p.status());
        assertQueuedRow(Db.one("select * from queued_notifications"), 1, "admin@example.com", "self", before, after);
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

    /**
     * Sanctioned fix F1 (RULEBOOK §0.1, D-003): a form post without the button click (the rendered
     * {@code user_hit_submit} is still disabled, so it is not posted) is an autopostback and
     * notifies nobody; the same form after the click saves and queues the notification.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100,142-146", why = "did_user_hit_submit_button reads Request[\"user_hit_submit\"]")
    @SourceRef(value = "src/BugTracker.Web/Scripts/Site/EditBug.ts:202-204", why = "script sets #user_hit_submit - the element the port renders")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:133-137", why = "no user_hit_submit: project autopostback only reloads users")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:1178-1183", why = "queue insert after a real save")
    void formPostNeedsTheButtonClickToSaveAndNotify() {
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

        Web.Page clicked = formAfterSubmitButtonClick(web.get("edit_bug.aspx?id=1"), "submit_button")
                .set("short_desc", "saved by click").submit(null);
        assertEquals(200, clicked.status());
        assertEquals("saved by click", Db.scalar("select bg_short_desc from bugs where bg_id = 1"));
        assertEquals(1L, Db.count("select count(*) from queued_notifications"));
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

    /**
     * The row bug.cs:1180-1183 writes for one subscriber of bug 1 after a plain update by admin.
     * qn_retries / qn_last_exception are not asserted (worker thread, see qualifyingSubscriberGetsQueuedNotification).
     */
    private static void assertQueuedRow(Map<String, Object> row, int user, String to, String shortDesc,
            java.sql.Timestamp before, java.sql.Timestamp after) {
        assertEquals(1, ((Number) row.get("qn_bug")).intValue());
        assertEquals(user, ((Number) row.get("qn_user")).intValue());
        assertEquals("not sent", row.get("qn_status"));
        assertEquals(to, row.get("qn_to"));
        assertEquals("dont_reply@example.com", row.get("qn_from"));
        assertEquals("Bug:1 was updated - " + shortDesc + " (DO NOT EDIT THIS:1)", row.get("qn_subject"));
        String body = (String) row.get("qn_body");
        org.junit.jupiter.api.Assertions.assertTrue(
                body.startsWith("<html><base href=\"http://127.0.0.1/btnet/\"/>"), "body starts with the base tag: " + body.substring(0, Math.min(80, body.length())));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains(shortDesc), "body is the printed bug");
        S3_EditBugTest.between(before, after, row.get("qn_date_created"), "qn_date_created");
    }

    private static List<String> without(List<String> row, int col) {
        List<String> r = new java.util.ArrayList<>(row);
        r.remove(col);
        return r;
    }
}
