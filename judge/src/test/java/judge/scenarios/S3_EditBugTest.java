package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
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
 * S3 — create / edit a bug, add a comment (edit_bug.aspx). See judge/specs/S3.md.
 *
 * <p>Key reading: the save path only runs when the request carries
 * {@code user_hit_submit=1} (edit_bug.aspx.cs:100,142-146). The original renders no such
 * element, so its browser POST never saved; sanctioned fix F1 (RULEBOOK §0.1, D-003)
 * specifies the port renders a disabled hidden {@code user_hit_submit} that the submit
 * button enables. Tests that are not about F1 still add the field by hand
 * ({@link #bugForm}) - it is exactly what the browser posts after the click.
 */
class S3_EditBugTest extends Scenario {

    private static final Timestamp SEED_DATE = Timestamp.valueOf("2020-01-02 03:04:05");

    // ---------------------------------------------------------------- GET new

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:175-222", why = "new bug: Create button, load_dropdowns, defaults from pj/ct/pr/st_default")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1590-1666", why = "dropdown queries, ordering and the [no ...] first items")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:853-961", why = "org defaults to user's org; category/priority/status default or 0")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:965-1021", why = "project: Session[project] else pj_default else 0")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1070-1235", why = "assigned_to list: active users of assignable orgs, [not assigned] first")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:125-200", why = "orgs and users seed; admin us_org=1; tester/customers in non-assignable orgs; guest inactive")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:244-280", why = "categories, projects seed (no ct_default/pj_default)")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:463-485", why = "statuses ('new' is st_default=1), priorities (no default)")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:98-108", why = "EnableTags=0 -> tags field permission None -> tags not rendered")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx:191-203", why = "udf rendered only when ShowUserDefinedBugAttribute=1 (Web.config: 0)")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:87-91", why = "internal_only hidden when EnableInternalOnlyPosts=0 (Web.config)")
    void newBugFormHasSourceDefaults() {
        loginAdmin(web);
        Web.Page p = web.get("edit_bug.aspx?id=0");
        assertEquals(200, p.status());
        assertEquals("edit_bug.aspx", p.path());
        Document d = p.html();
        Form f = Form.containing(web, p, "short_desc");

        assertEquals("", f.get("short_desc"));
        assertEquals("0", f.get("project"), "no pj_default and empty Session[project] -> [no project]");
        assertEquals("1", f.get("org"), "admin's org (org1)");
        assertEquals("0", f.get("category"));
        assertEquals("0", f.get("priority"));
        assertEquals("1", f.get("status"), "'new' is the default status");
        assertEquals("0", f.get("assigned_to"));
        assertTrue(f.has("comment"), "comment textarea present");

        assertEquals(List.of("0=[no project]", "1=project 1", "2=project 2"), options(d, "project"));
        assertEquals(List.of("0=[no organization]", "4=client one", "5=client two", "2=developers", "1=org1", "3=testers"),
                options(d, "org"));
        assertEquals(List.of("0=[no category]", "1=bug", "2=enhancement", "4=question", "3=task", "5=ticket"),
                options(d, "category"));
        assertEquals(List.of("0=[no priority]", "1=high", "2=med", "3=low"), options(d, "priority"));
        assertEquals(List.of("0=[no status]", "1=new", "2=in progress", "3=checked in", "4=re-opened", "5=closed"),
                options(d, "status"));
        assertEquals(List.of("0=[not assigned]", id("admin") + "=admin", id("developer") + "=developer",
                id("email") + "=email", id("reporter") + "=reporter", id("viewer") + "=viewer"), options(d, "assigned_to"));

        assertFalse(hasField(d, "tags"), "tags hidden (EnableTags=0)");
        assertFalse(hasField(d, "udf"), "udf hidden (ShowUserDefinedBugAttribute=0)");
        assertFalse(hasField(d, "internal_only"), "internal_only hidden (EnableInternalOnlyPosts=0)");

        Element button = d.getElementById("submit_button");
        assertNotNull(button, "#submit_button");
        assertEquals("Create", button.val());
        assertEquals("", Norm.textById(d, "msg"));
    }

    // ---------------------------------------------------------------- create

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100-119", why = "save path gated on user_hit_submit; get_bug_datarow(0) then validate then do_insert")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:636-651", why = "plain-text comment is HtmlDecode'd, content type text/plain (us_use_fckeditor=0)")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:663-707", why = "insert_bug args, Session[project], Response.Redirect(edit_bug.aspx?id=N)")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:810-878", why = "bugs insert: both dates getdate() in one statement, tags as given ('')")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:882-957", why = "comment post: type comment, bp_email_from null, cc '', internal 0")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:30-147", why = "auto_subscribe: nobody qualifies in seed data")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:532-545", why = "EnableSeen=1: get_bug_datarow inserts bug_user row, also for id 0")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:969-992", why = "next new bug preselects Session[project]")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:160", why = "admin us_use_fckeditor = 0")
    @Inherited("submitting a new bug inserts a bug_user 'seen' row for bug id 0 (get_bug_datarow(0) runs before the insert); comment text is HTML-decoded before storage")
    void createBugRedirectsToItAndWritesRows() {
        loginAdmin(web);
        primeAspNetSession(web); // edit_bug.aspx.cs:32 ViewStateUserKey = Session.SessionID
        int admin = id("admin");
        int developer = id("developer");
        Web.Page p = web.get("edit_bug.aspx?id=0");
        Form f = bugForm(p, true)
                .set("short_desc", "Judge S3 created bug")
                .set("project", "1")
                .set("category", "1")
                .set("priority", "2")
                .set("status", "1")
                .set("assigned_to", String.valueOf(developer))
                .set("comment", "Tom &amp; Jerry");
        Timestamp before = dbNow();
        Web.Page r = f.submit(null);
        Timestamp after = dbNow();

        assertEquals(1, r.redirects().size(), "one redirect after create");
        assertEquals("edit_bug.aspx", Norm.relPath(r.redirects().get(0)));
        assertEquals("1", Norm.query(r.redirects().get(0), "id"));
        assertEquals(200, r.status());
        assertEquals("1", Norm.textById(r.html(), "bugid"));

        Map<String, Object> bug = Db.one("select * from bugs");
        expect(bug, "bg_id", 1, "bg_short_desc", "Judge S3 created bug", "bg_reported_user", admin,
                "bg_last_updated_user", admin, "bg_project", 1, "bg_org", 1, "bg_category", 1, "bg_priority", 2,
                "bg_status", 1, "bg_assigned_to_user", developer, "bg_user_defined_attribute", 0, "bg_tags", "",
                "bg_project_custom_dropdown_value1", null, "bg_project_custom_dropdown_value2", null,
                "bg_project_custom_dropdown_value3", null);
        between(before, after, bug.get("bg_reported_date"), "bg_reported_date");
        assertEquals(bug.get("bg_reported_date"), bug.get("bg_last_updated_date"), "same getdate() in one insert");

        Map<String, Object> post = Db.one("select * from bug_posts");
        expect(post, "bp_bug", 1, "bp_type", "comment", "bp_user", admin, "bp_comment", "Tom & Jerry",
                "bp_comment_search", "Tom & Jerry", "bp_email_from", null, "bp_email_to", null, "bp_email_cc", "",
                "bp_file", null, "bp_size", null, "bp_content_type", "text/plain", "bp_parent", null,
                "bp_original_comment_id", null, "bp_hidden_from_external_users", 0);
        between(before, after, post.get("bp_date"), "bp_date");

        assertEquals(0, Db.count("select count(*) from bug_subscriptions"));
        assertEquals(0, Db.count("select count(*) from queued_notifications"));
        List<Map<String, Object>> seen = Db.rows("select * from bug_user order by bu_bug");
        assertEquals(2, seen.size(), "bug_user rows for bug 0 (submit) and bug 1 (redirect target)");
        for (int i = 0; i < 2; i++) {
            expect(seen.get(i), "bu_bug", i, "bu_user", admin, "bu_flag", 0, "bu_seen", 1, "bu_vote", 0,
                    "bu_seen_datetime", null, "bu_flag_datetime", null, "bu_vote_datetime", null);
        }

        Form next = Form.containing(web, web.get("edit_bug.aspx?id=0"), "short_desc");
        assertEquals("1", next.get("project"), "Session[project] remembered for the next new bug");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:120-131", why = "invalid -> msg 'Bug was not created.'")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1921-1941", why = "short_desc == '' -> short_desc_err 'Short Description is required.'")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1684-1704", why = "did_something_change is true for a new bug (prev_project '' != '0')")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:538-545", why = "get_bug_datarow(0) inserted the bug_user row before validation")
    @Inherited("a rejected new bug still inserts a bug_user 'seen' row for bug id 0")
    void missingShortDescriptionIsRejected() {
        loginAdmin(web);
        primeAspNetSession(web); // edit_bug.aspx.cs:32 ViewStateUserKey = Session.SessionID
        Web.Page p = web.get("edit_bug.aspx?id=0");
        Web.Page r = bugForm(p, true).set("short_desc", "").set("comment", "").submit(null);

        assertEquals(200, r.status());
        assertTrue(r.redirects().isEmpty());
        assertEquals("edit_bug.aspx", r.path());
        assertEquals("Short Description is required.", Norm.textById(r.html(), "short_desc_err"));
        assertEquals("Bug was not created.", Norm.textById(r.html(), "msg"));
        assertEquals(0, Db.count("select count(*) from bugs"));
        assertEquals(0, Db.count("select count(*) from bug_posts"));
        assertEquals(1, Db.count("select count(*) from bug_user where bu_bug = 0 and bu_user = ?", id("admin")));
    }

    /**
     * Sanctioned fix F1 (RULEBOOK §0.1, D-003), part (b)+(c): clicking Create enables the
     * hidden field, so the browser's POST carries user_hit_submit=1 and saves. The original
     * rendered no such field (nothing was ever saved from a browser).
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100-119", why = "user_hit_submit=1 -> validate and do_insert")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:142-146", why = "did_user_hit_submit_button: Request[\"user_hit_submit\"] == \"1\"")
    @SourceRef(value = "src/BugTracker.Web/Scripts/Site/EditBug.js:35-38,188-190", why = "button click disables the button and calls form.submit(); the intended #user_hit_submit element is the one the port renders")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:705", why = "after insert: Response.Redirect(edit_bug.aspx?id=<new id>)")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:187", why = "the new-bug button (#submit_button) is labelled Create")
    void submitButtonClickCreatesBug() {
        loginAdmin(web);
        primeAspNetSession(web); // edit_bug.aspx.cs:32 ViewStateUserKey = Session.SessionID
        Web.Page p = web.get("edit_bug.aspx?id=0");
        Web.Page r = formAfterSubmitButtonClick(p, "submit_button").set("short_desc", "Saved by click")
                .set("project", "1").set("comment", "").submit(null);

        assertEquals(1, r.redirects().size(), "redirect to the new bug: " + r.redirects());
        assertEquals("edit_bug.aspx", Norm.relPath(r.redirects().get(0)));
        assertEquals("1", Norm.query(r.redirects().get(0), "id"));
        assertEquals(200, r.status());
        assertEquals("Saved by click", Db.scalar("select bg_short_desc from bugs where bg_id = 1"));
        assertEquals(1, Db.count("select count(*) from bugs"));
    }

    /** Sanctioned fix F1 (RULEBOOK §0.1, D-003): the same click on an existing bug saves the comment. */
    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100,142-146", why = "save path gated on user_hit_submit == 1")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:813-834", why = "insert_comment, msg 'Bug was updated.'")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:636-651", why = "HtmlDecode of plain-text comment")
    void submitButtonClickUpdatesExistingBug() {
        loginAdmin(web);
        seedBug(1, "Seeded bug");
        Web.Page p = web.get("edit_bug.aspx?id=1");
        Web.Page r = formAfterSubmitButtonClick(p, "submit_button").set("comment", "via button").submit(null);
        assertEquals(200, r.status());
        assertEquals("Bug was updated.", Norm.textById(r.html(), "msg"));
        assertEquals("via button", Db.scalar("select bp_comment from bug_posts where bp_bug = 1 and bp_type = 'comment'"));
    }

    /**
     * Sanctioned fix F1 (RULEBOOK §0.1, D-003), part (a)+(c): a POST that does not click a
     * button (the disabled field is not posted) is a project-dropdown autopostback and saves
     * nothing - the old "browser never saves" test, narrowed to the part that is still true.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100,133-137", why = "without user_hit_submit the POST is handled as project autopostback: load_user_dropdown only")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:142-146", why = "Request[\"user_hit_submit\"] == \"1\" is the only save trigger")
    void projectAutopostbackWithoutButtonClickSavesNothing() {
        loginAdmin(web);
        primeAspNetSession(web); // edit_bug.aspx.cs:32 ViewStateUserKey = Session.SessionID
        Web.Page p = web.get("edit_bug.aspx?id=0");
        Web.Page r = bugForm(p, false).set("short_desc", "Never saved").set("project", "1").set("comment", "").submit(null);

        assertEquals(200, r.status());
        assertTrue(r.redirects().isEmpty(), "no redirect: nothing was created");
        assertEquals("edit_bug.aspx", r.path());
        assertEquals("", Norm.textById(r.html(), "msg"));
        assertEquals(0, Db.count("select count(*) from bugs"));
        assertEquals(0, Db.count("select count(*) from bug_posts"));
        assertEquals(0, Db.count("select count(*) from bug_user"), "get_bug_datarow not reached");
    }

    /**
     * Sanctioned fix F1 (RULEBOOK §0.1, D-003), part (a): "true iff ANY value of user_hit_submit
     * in the query string or the form equals 1". A repeated parameter with values 0 and 1
     * saves; 0 and 2 does not. (Request["x"] on the original joins repeated values as "0,1".)
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:142-146", why = "intent: user hit submit when user_hit_submit == \"1\"; F1(a) widens to any repeated value")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:100-119", why = "save path")
    void anyUserHitSubmitValueEqualToOneSaves() {
        loginAdmin(web);
        seedBug(1, "Seeded bug");
        Web.Page p = web.get("edit_bug.aspx?id=1");
        Web.Page no = bugFormWithUserHitSubmit(p, "0", "2").set("comment", "ignored").submit(null);
        assertEquals(200, no.status());
        assertEquals(0, Db.count("select count(*) from bug_posts"), "values 0 and 2: autopostback, nothing saved");
        Web.Page yes = bugFormWithUserHitSubmit(web.get("edit_bug.aspx?id=1"), "0", "1").set("comment", "kept").submit(null);
        assertEquals(200, yes.status());
        assertEquals("kept", Db.scalar("select bp_comment from bug_posts where bp_type = 'comment'"));
    }

    // ---------------------------------------------------------------- update

    /**
     * Sanctioned fix F2 (RULEBOOK §0.1, D-003): AddChange's INSERT gets its missing closing ")".
     * Intent: one bug_posts row of type 'update' per changed field, text
     * 'Changed <field> from <old> to <new>', user = editor, date = getdate().
     * record_changes visits priority (:1817) before status (:1842), so priority's row has the lower bp_id.
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1743-1748", why = "AddChange: values(@id, @us, getdate(), 'Changed ' + @field + ' from ' + @oldValue + ' to ' + @newValue, 'update') - the row it writes, minus the syntax error")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1813-1820", why = "priority: AddChange(\"priority\", text of prev value, new text); seeded priority 3 = low, form sets 1 = high")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1832-1845", why = "status: AddChange(\"status\", text of prev value, new text); seeded status 1 = new, form sets 2 = in progress")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:745-787", why = "update bugs runs first (own connection, autocommit), tags written as '' ")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:794-800", why = "update happened -> record_changes()")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:813-834", why = "empty comment: insert_comment writes nothing; msg 'Bug was updated.'")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:892-897", why = "insert_comment skips an empty comment")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:308-326", why = "bug_posts columns not named by AddChange keep NULL / default 0")
    @SourceRef(value = "src/BugTracker.Web/Web.config", why = "TrackBugHistory=1, so record_changes does not short-circuit")
    void changingStatusAndPriorityUpdatesBugAndWritesHistory() {
        loginAdmin(web);
        int admin = id("admin");
        seedBug(1, "Seeded bug");
        Web.Page p = web.get("edit_bug.aspx?id=1");
        assertEquals(200, p.status());
        Form f = bugForm(p, true);
        assertEquals("1", f.get("status"));
        assertEquals("3", f.get("priority"));
        f.set("status", "2").set("priority", "1").set("comment", "");
        Timestamp before = dbNow();
        Web.Page r = f.submit(null);
        Timestamp after = dbNow();

        assertEquals(200, r.status());
        assertEquals("Bug was updated.", Norm.textById(r.html(), "msg"));
        Map<String, Object> bug = Db.one("select * from bugs where bg_id = 1");
        expect(bug, "bg_short_desc", "Seeded bug", "bg_status", 2, "bg_priority", 1, "bg_project", 1, "bg_org", 1,
                "bg_category", 1, "bg_assigned_to_user", 0, "bg_user_defined_attribute", 0, "bg_tags", "",
                "bg_last_updated_user", admin, "bg_reported_user", admin, "bg_reported_date", SEED_DATE);
        between(before, after, bug.get("bg_last_updated_date"), "bg_last_updated_date");

        List<Map<String, Object>> posts = Db.rows("select * from bug_posts order by bp_id");
        assertEquals(2, posts.size(), "one history row per changed field, no comment row");
        String[] text = {"Changed priority from low to high", "Changed status from new to in progress"};
        for (int i = 0; i < 2; i++) {
            Map<String, Object> post = posts.get(i);
            expect(post, "bp_bug", 1, "bp_type", "update", "bp_user", admin, "bp_comment", text[i],
                    "bp_comment_search", null, "bp_email_from", null, "bp_email_to", null, "bp_email_cc", null,
                    "bp_file", null, "bp_size", null, "bp_content_type", null, "bp_parent", null,
                    "bp_original_comment_id", null, "bp_hidden_from_external_users", 0);
            between(before, after, post.get("bp_date"), "bp_date");
        }
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:709-787", why = "do_update: bugs row rewritten (same values, tags '' , last-updated user/date)")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:758-770", why = "update list: bg_project, bg_org, bg_category, bg_assigned_to_user, bg_user_defined_attribute come from the form")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:30-147", why = "auto_subscribe: nobody qualifies in seed data -> no bug_subscriptions, no queued_notifications")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1684-1704", why = "a non-empty comment alone counts as a change")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1760-1890", why = "record_changes: no field changed -> no AddChange, no exception")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:813-834", why = "insert_comment, msg 'Bug was updated.', comment box cleared")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:882-957", why = "comment post columns")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:636-651", why = "HtmlDecode of plain-text comment")
    @Inherited("saving a bug rewrites NULL bg_tags to '' even when nothing but a comment was entered; comment text is HTML-decoded")
    void commentOnExistingBugAddsPost() {
        loginAdmin(web);
        int admin = id("admin");
        seedBug(1, "Seeded bug");
        Web.Page p = web.get("edit_bug.aspx?id=1");
        Form f = bugForm(p, true).set("comment", "Judge comment &lt;b&gt; ok");
        Timestamp before = dbNow();
        Web.Page r = f.submit(null);
        Timestamp after = dbNow();

        assertEquals(200, r.status());
        assertTrue(r.redirects().isEmpty(), "update re-renders, no redirect");
        assertEquals("Bug was updated.", Norm.textById(r.html(), "msg"));
        assertEquals("", Norm.text(Form.containing(web, r, "short_desc").get("comment")), "comment box cleared");

        Map<String, Object> bug = Db.one("select * from bugs where bg_id = 1");
        // do_update rewrites project/org/category/assignee/udf from the form (edit_bug.aspx.cs:758-770);
        // with an untouched form they must come back as seeded (see seedBug), not reset to defaults.
        expect(bug, "bg_short_desc", "Seeded bug", "bg_status", 1, "bg_priority", 3, "bg_tags", "",
                "bg_project", 1, "bg_org", 1, "bg_category", 1, "bg_assigned_to_user", 0,
                "bg_user_defined_attribute", 0,
                "bg_reported_user", admin, "bg_reported_date", SEED_DATE,
                "bg_last_updated_user", admin);
        between(before, after, bug.get("bg_last_updated_date"), "bg_last_updated_date");

        Map<String, Object> post = Db.one("select * from bug_posts");
        expect(post, "bp_bug", 1, "bp_type", "comment", "bp_user", admin, "bp_comment", "Judge comment <b> ok",
                "bp_comment_search", "Judge comment <b> ok", "bp_email_from", null, "bp_email_to", null, "bp_email_cc", "",
                "bp_file", null, "bp_size", null,
                "bp_content_type", "text/plain", "bp_hidden_from_external_users", 0, "bp_parent", null);
        between(before, after, post.get("bp_date"), "bp_date");
        assertEquals(0, Db.count("select count(*) from bug_posts where bp_type = 'update'"));
        assertEquals(1, Db.count("select count(*) from bug_posts"));
        assertEquals(0, Db.count("select count(*) from bug_subscriptions"), "auto_subscribe: nobody qualifies in seed data");
        assertEquals(0, Db.count("select count(*) from queued_notifications"));
    }

    // ---------------------------------------------------------------- permissions

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:653-661", why = "pu_permission_level from project_user_xref; admin role does not override")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1525-1558", why = "ReadOnly: submit/comment hidden, every field shown as static text")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1293-1330", why = "static_org / static_short_desc text")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1377-1497", why = "static_category/priority/status/project/assigned_to text = selected item text")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:1024-1066", why = "project 2 re-added to dropdown from prev_project_name")
    void readOnlyPermissionShowsStaticFields() {
        loginAdmin(web);
        seedBug(2, "Seeded bug");
        Db.exec("insert into project_user_xref (pu_project, pu_user, pu_permission_level) values (2, ?, 1)", id("admin"));
        Web.Page p = web.get("edit_bug.aspx?id=1");
        assertEquals(200, p.status());
        Document d = p.html();

        assertEquals("Seeded bug", Norm.textById(d, "static_short_desc"));
        assertEquals("project 2", Norm.textById(d, "static_project"));
        assertEquals("org1", Norm.textById(d, "static_org"));
        assertEquals("bug", Norm.textById(d, "static_category"));
        assertEquals("low", Norm.textById(d, "static_priority"));
        assertEquals("new", Norm.textById(d, "static_status"));
        assertEquals("[not assigned]", Norm.textById(d, "static_assigned_to"));
        for (String field : List.of("short_desc", "comment", "project", "org", "category", "priority", "status", "assigned_to")) {
            assertFalse(hasField(d, field), "read-only page must not offer field " + field);
        }
        assertNull(d.getElementById("submit_button"), "no submit button for read-only");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:235-241", why = "permission None -> display_you_dont_have_permission")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1357-1368", why = "exact message text")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:538-545", why = "seen row is written before the permission check")
    @Inherited("opening a bug you may not view still marks it seen (bug_user row inserted)")
    void noPermissionIsRefused() {
        loginAdmin(web);
        seedBug(2, "Secret seeded bug");
        Db.exec("insert into project_user_xref (pu_project, pu_user, pu_permission_level) values (2, ?, 0)", id("admin"));
        Web.Page p = web.get("edit_bug.aspx?id=1");
        assertEquals(200, p.status());
        String text = Norm.text(p.html().text());
        assertTrue(text.contains("You are not allowed to view this bug"), text);
        assertFalse(text.contains("Secret seeded bug"), "bug data must not leak");
        assertFalse(hasField(p.html(), "short_desc"));
        assertEquals(1, Db.count("select count(*) from bug_user where bu_bug = 1 and bu_user = ?", id("admin")));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:55-58,610-624", why = "non-integer id -> 'Error: Bug ID must be an integer.'")
    @SourceRef(value = "src/BugTracker.Web/edit_bug.aspx.cs:228-232", why = "unknown id -> display_bug_not_found")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1343-1354", why = "'Bug not found:&nbsp;999'")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:538-545", why = "seen row inserted for a bug id that does not exist")
    @Inherited("viewing a nonexistent bug id inserts a bug_user row for that id")
    void invalidAndUnknownIdsAreReported() {
        loginAdmin(web);
        Web.Page bad = web.get("edit_bug.aspx?id=abc");
        assertEquals(200, bad.status());
        assertTrue(Norm.text(bad.html().text()).contains("Error: Bug ID must be an integer."), bad.text());

        Web.Page missing = web.get("edit_bug.aspx?id=999");
        assertEquals(200, missing.status());
        assertTrue(Norm.text(missing.html().text()).contains("Bug not found: 999"), missing.text());
        assertEquals(1, Db.count("select count(*) from bug_user where bu_bug = 999 and bu_user = ?", id("admin")));
        assertEquals(0, Db.count("select count(*) from bugs"));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The bug form, optionally with {@code user_hit_submit=1} added as a hidden
     * field (what the browser posts after the button click enabled the rendered
     * field, F1). Everything else is the form exactly as rendered.
     */
    private Form bugForm(Web.Page p, boolean userHitSubmit) {
        if (!userHitSubmit) return Form.containing(web, p, "short_desc");
        Document d = p.html();
        d.outputSettings().prettyPrint(false);
        Element form = null;
        outer:
        for (Element f : d.select("form")) {
            for (Element el : f.select("[name]")) {
                if (Form.logicalName(el.attr("name")).equals("short_desc")) { form = f; break outer; }
            }
        }
        assertNotNull(form, "form with short_desc");
        form.appendElement("input").attr("type", "hidden").attr("name", "user_hit_submit").attr("value", "1");
        Web.Page patched = new Web.Page(p.status(), p.uri(), p.headers(),
                d.outerHtml().getBytes(StandardCharsets.UTF_8), p.redirects());
        return Form.containing(web, patched, "short_desc");
    }

    /** The bug form with one hidden user_hit_submit input per given value (all posted). */
    private Form bugFormWithUserHitSubmit(Web.Page p, String... values) {
        Document d = p.html();
        d.outputSettings().prettyPrint(false);
        Element form = null;
        outer:
        for (Element f : d.select("form")) {
            for (Element el : f.select("[name]")) {
                if (Form.logicalName(el.attr("name")).equals("short_desc")) { form = f; break outer; }
            }
        }
        assertNotNull(form, "form with short_desc");
        for (String v : values) {
            form.appendElement("input").attr("type", "hidden").attr("name", "user_hit_submit").attr("value", v);
        }
        Web.Page patched = new Web.Page(p.status(), p.uri(), p.headers(),
                d.outerHtml().getBytes(StandardCharsets.UTF_8), p.redirects());
        return Form.containing(web, patched, "short_desc");
    }

    private void seedBug(int project, String desc) {
        int admin = id("admin");
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org,"
                + " bg_category, bg_project, bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date,"
                + " bg_user_defined_attribute) values (?, ?, ?, 1, 3, 1, 1, ?, 0, ?, ?, 0)",
                desc, admin, SEED_DATE, project, admin, SEED_DATE);
    }

    static int id(String username) {
        return ((Number) Db.scalar("select us_id from users where us_username = ?", username)).intValue();
    }

    static Timestamp dbNow() {
        return (Timestamp) Db.scalar("select getdate()");
    }

    static void between(Timestamp before, Timestamp after, Object actual, String what) {
        assertNotNull(actual, what);
        Timestamp t = (Timestamp) actual;
        assertTrue(!t.before(before) && !t.after(after), what + "=" + t + " not within [" + before + ", " + after + "] (DB clock)");
    }

    static void expect(Map<String, Object> row, Object... kv) {
        for (int i = 0; i < kv.length; i += 2) {
            String col = (String) kv[i];
            assertTrue(row.containsKey(col), "no column " + col);
            Object exp = kv[i + 1];
            Object act = row.get(col);
            if (exp instanceof Integer && act instanceof Number n) act = n.intValue();
            assertEquals(exp, act, col);
        }
    }

    static Element field(Document d, String logical) {
        for (Element el : d.select("[name]")) {
            if (Form.logicalName(el.attr("name")).equals(logical)) return el;
        }
        return null;
    }

    static boolean hasField(Document d, String logical) { return field(d, logical) != null; }

    /** Options of a select, as "value=text", in document order. */
    static List<String> options(Document d, String logical) {
        Element sel = field(d, logical);
        assertNotNull(sel, "select " + logical);
        List<String> out = new ArrayList<>();
        for (Element o : sel.select("option")) out.add(o.attr("value") + "=" + Norm.text(o.text()));
        return out;
    }
}
