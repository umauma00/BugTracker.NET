package judge.scenarios;

import static judge.scenarios.S3_EditBugTest.between;
import static judge.scenarios.S3_EditBugTest.dbNow;
import static judge.scenarios.S3_EditBugTest.expect;
import static judge.scenarios.S3_EditBugTest.id;
import static judge.scenarios.S4_AttachmentTest.mediaType;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import judge.core.Db;
import judge.core.Inherited;
import judge.core.Norm;
import judge.core.Scenario;
import judge.core.SourceRef;
import judge.core.Web;

/**
 * S5 — POST api/BugFromEmail (the endpoint btnet_service.exe calls). See judge/specs/S5.md.
 */
class S5_BugFromEmailTest extends Scenario {

    private static final String API = "api/BugFromEmail";
    private static final Timestamp SEED_DATE = Timestamp.valueOf("2020-01-02 03:04:05");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:12-13", why = "[Authorize] on the controller")
    @SourceRef(value = "src/BugTracker.Web/App_Start/Startup.Auth.cs:15-19", why = "cookie auth with LoginPath /default.aspx turns the 401 into a 302 (non-AJAX request)")
    @SourceRef(value = "src/BugTracker.Web/packages.config:22", why = "Microsoft.Owin.Security.Cookies 3.0.0")
    void unauthenticatedPostIsRedirectedToLogin() {
        Web anon = newSession();
        Web.Page r = anon.postJson(API, "{\"FromAddress\":\"a@example.com\",\"ShortDescription\":\"x\",\"Comment\":\"y\"}");
        assertEquals(1, r.redirects().size(), "401 challenge becomes a redirect");
        assertEquals("default.aspx", Norm.relPath(r.redirects().get(0)));
        URI back = anon.resolve("/").resolve(Norm.query(r.redirects().get(0), "ReturnUrl"));
        assertEquals("api/bugfromemail", Norm.relPath(back));
        assertEquals(0, Db.count("select count(*) from bugs"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Models/BugFromEmail.cs:14-15", why = "FromAddress is [Required]")
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:18,242-245", why = "invalid model -> BadRequest(ModelState)")
    void missingFromAddressIsBadRequest() throws Exception {
        loginAdmin(web);
        Web.Page r = web.postJson(API, "{\"ShortDescription\":\"x\",\"Comment\":\"y\"}");
        assertEquals(400, r.status());
        JsonNode body = JSON.readTree(r.text());
        assertEquals("The request is invalid.", body.path("Message").asText());
        JsonNode ms = body.path("ModelState");
        List<String> keys = new java.util.ArrayList<>();
        for (Iterator<String> it = ms.fieldNames(); it.hasNext(); ) keys.add(it.next());
        assertEquals(List.of("bugFromEmail.FromAddress"), keys);
        assertEquals("The FromAddress field is required.", ms.path("bugFromEmail.FromAddress").path(0).asText());
        assertEquals(0, Db.count("select count(*) from bugs"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:93-177", why = "new bug: defaults, insert_bug(from, cc, text/plain, no notifications), Ok(bugid)")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:715-737", why = "get_bug_defaults: pj/ct/pr/udf 0, st 1 ('new')")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:810-878", why = "bugs insert")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:893-933", why = "from != null: type 'received', bugs last-updated = post @now")
    void jsonPostCreatesBugWithDefaults() throws Exception {
        loginAdmin(web);
        int admin = id("admin");
        Timestamp before = dbNow();
        Web.Page r = web.postJson(API, "{\"FromAddress\":\"someone@example.com\",\"CcAddress\":\"cc@example.com\","
                + "\"ShortDescription\":\"Mail bug\",\"Comment\":\"Body text\"}");
        Timestamp after = dbNow();

        assertEquals(200, r.status());
        assertEquals("application/json", mediaType(r));
        assertEquals("1", r.text().trim());

        Map<String, Object> bug = Db.one("select * from bugs");
        expect(bug, "bg_id", 1, "bg_short_desc", "Mail bug", "bg_tags", "", "bg_reported_user", admin,
                "bg_last_updated_user", admin, "bg_project", 0, "bg_org", 1, "bg_category", 0, "bg_priority", 0,
                "bg_status", 1, "bg_assigned_to_user", 0, "bg_user_defined_attribute", 0);
        between(before, after, bug.get("bg_reported_date"), "bg_reported_date");

        Map<String, Object> post = Db.one("select * from bug_posts");
        expect(post, "bp_bug", 1, "bp_type", "received", "bp_user", admin, "bp_comment", "Body text",
                "bp_comment_search", "Body text", "bp_email_from", "someone@example.com", "bp_email_cc", "cc@example.com",
                "bp_email_to", null, "bp_content_type", "text/plain", "bp_hidden_from_external_users", 0,
                "bp_parent", null, "bp_file", null);
        between(before, after, post.get("bp_date"), "bp_date");
        assertEquals(post.get("bp_date"), bug.get("bg_last_updated_date"), "insert_comment sets bg_last_updated_date = @now");
        assertEquals(0, Db.count("select count(*) from queued_notifications"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:114-136", why = "explicit ids are used as given")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:813-816", why = "blank short description -> '[No Description]'")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:893,952-955", why = "empty comment -> no post")
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:20-23,46-51", why = "null ShortDescription/Comment become ''")
    void explicitIdsAndBlankTextCreateBareBug() {
        loginAdmin(web);
        Web.Page r = web.postJson(API, "{\"FromAddress\":\"someone@example.com\",\"ProjectId\":2,\"OrganizationId\":2,"
                + "\"CategoryId\":3,\"PriorityId\":1,\"StatusId\":2,\"AssignedTo\":" + id("developer") + "}");
        assertEquals(200, r.status());
        assertEquals("1", r.text().trim());
        Map<String, Object> bug = Db.one("select * from bugs");
        expect(bug, "bg_short_desc", "[No Description]", "bg_project", 2, "bg_org", 2, "bg_category", 3,
                "bg_priority", 1, "bg_status", 2, "bg_assigned_to_user", id("developer"), "bg_user_defined_attribute", 0);
        assertEquals(bug.get("bg_reported_date"), bug.get("bg_last_updated_date"), "no comment -> no second update");
        assertEquals(0, Db.count("select count(*) from bug_posts"));
    }

    @Test
    @SourceRef(value = "src/btnet_service/POP3Main.cs:697-722", why = "real client: form-urlencoded projectId, fromAddress, shortDescription, message (raw MIME)")
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:31-45,58-68,97-109", why = "MIME: comment = headers + NewLine + body; from/cc/subject from MIME")
    @SourceRef(value = "src/BugTracker.Web/Mail/Mime.cs:70-122,234-256", why = "from address, subject, to/cc, plain-text body, 'Subject: ..\\nTo: ..\\n' header block")
    @SourceRef(value = "src/BugTracker.Web/Mail/email.cs:363-393", why = "auto_reply skipped: project 1 has no pj_pop3_email_from")
    @Inherited("the stored e-mail comment mixes line endings: header lines end with \\n, then Environment.NewLine (\\r\\n) before the body")
    void mimeMessageFromServiceCreatesBug() {
        loginAdmin(web);
        int admin = id("admin");
        String mime = "From: Reporter Person <reporter@example.com>\r\n"
                + "To: support@example.com\r\n"
                + "Subject: Printer is on fire\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=us-ascii\r\n"
                + "\r\n"
                + "It is really on fire.";
        Web.Page r = web.postForm(web.resolve(API), List.of(
                Map.entry("projectId", "1"),
                Map.entry("fromAddress", "Reporter Person <reporter@example.com>"),
                Map.entry("shortDescription", "ignored subject"),
                Map.entry("message", mime)));
        assertEquals(200, r.status());
        assertEquals("1", r.text().trim());

        Map<String, Object> bug = Db.one("select * from bugs");
        expect(bug, "bg_short_desc", "Printer is on fire", "bg_project", 1, "bg_org", 1, "bg_status", 1,
                "bg_reported_user", admin);
        String comment = "Subject: Printer is on fire\nTo: support@example.com\n\r\nIt is really on fire.";
        Map<String, Object> post = Db.one("select * from bug_posts");
        expect(post, "bp_type", "received", "bp_comment", comment, "bp_comment_search", comment,
                "bp_email_from", "reporter@example.com", "bp_email_cc", "", "bp_content_type", "text/plain");
        assertEquals(0, Db.count("select count(*) from bug_posts where bp_type = 'file'"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Mail/Mime.cs:27-68", why = "bug id parsed from '(DO NOT EDIT THIS:1)' in the subject")
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:179-239", why = "existing bug: no status change (StatusResultingFromIncomingEmail=0), received comment, Ok(bugid)")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:915-928", why = "bugs last-updated user/date = the post")
    void mimeReplyWithTrackingIdAppendsComment() {
        loginAdmin(web);
        int admin = id("admin");
        seedBug();
        String subject = "Re: Bug:1 was added - Seeded bug (DO NOT EDIT THIS:1)";
        String mime = "From: reporter@example.com\r\nTo: support@example.com\r\nSubject: " + subject + "\r\n"
                + "Content-Type: text/plain; charset=us-ascii\r\n\r\nThanks, fixed.";
        Web.Page r = web.postForm(web.resolve(API), List.of(
                Map.entry("projectId", "1"),
                Map.entry("fromAddress", "reporter@example.com"),
                Map.entry("shortDescription", subject),
                Map.entry("message", mime)));
        assertEquals(200, r.status());
        assertEquals("1", r.text().trim());

        assertEquals(1, Db.count("select count(*) from bugs"));
        Map<String, Object> bug = Db.one("select * from bugs where bg_id = 1");
        expect(bug, "bg_short_desc", "Seeded bug", "bg_status", 1, "bg_last_updated_user", admin);
        Map<String, Object> post = Db.one("select * from bug_posts");
        expect(post, "bp_bug", 1, "bp_type", "received", "bp_user", admin,
                "bp_comment", "Subject: " + subject + "\nTo: support@example.com\n\r\nThanks, fixed.",
                "bp_email_from", "reporter@example.com", "bp_email_cc", "");
        assertEquals(post.get("bp_date"), bug.get("bg_last_updated_date"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:72-88", why = "BugId of a missing bug is reset to 0 -> new bug")
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:179-239", why = "BugId of an existing bug (no MIME) -> received comment appended")
    void jsonBugIdAppendsOrFallsBackToNewBug() {
        loginAdmin(web);
        seedBug();
        Web.Page existing = web.postJson(API, "{\"FromAddress\":\"x@example.com\",\"BugId\":1,\"Comment\":\"More info\"}");
        assertEquals(200, existing.status());
        assertEquals("1", existing.text().trim());
        expect(Db.one("select * from bug_posts where bp_bug = 1"), "bp_type", "received", "bp_comment", "More info",
                "bp_email_from", "x@example.com", "bp_email_cc", "");

        Web.Page missing = web.postJson(API, "{\"FromAddress\":\"x@example.com\",\"BugId\":99,"
                + "\"ShortDescription\":\"Orphan reply\",\"Comment\":\"Lost\"}");
        assertEquals(200, missing.status());
        assertEquals("2", missing.text().trim());
        expect(Db.one("select * from bugs where bg_id = 2"), "bg_short_desc", "Orphan reply");
        assertEquals(0, Db.count("select count(*) from bugs where bg_id = 99"));
        expect(Db.one("select * from bug_posts where bp_bug = 2"), "bp_type", "received", "bp_comment", "Lost");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugFromEmailController.cs:154-169", why = "non-MIME attachment stored with parent -1 (not the comment post)")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:304-372", why = "file post + blob; bugs last-updated = file post @now")
    @Inherited("JSON attachments are not linked to the e-mail comment (bp_parent NULL), unlike MIME attachments which get the comment's post id")
    void jsonAttachmentIsStored() throws Exception {
        loginAdmin(web);
        byte[] content = S4_AttachmentTest.payload();
        Web.Page r = web.postJson(API, "{\"FromAddress\":\"x@example.com\",\"ShortDescription\":\"With file\","
                + "\"Comment\":\"see file\",\"Attachment\":\"" + Base64.getEncoder().encodeToString(content) + "\","
                + "\"AttachmentFileName\":\"mail.bin\",\"AttachmentContentType\":\"application/x-judge\","
                + "\"AttachmentDescription\":\"from mail\"}");
        assertEquals(200, r.status());
        assertEquals("1", r.text().trim());
        Map<String, Object> file = Db.one("select * from bug_posts where bp_type = 'file'");
        expect(file, "bp_bug", 1, "bp_file", "mail.bin", "bp_comment", "from mail", "bp_size", content.length,
                "bp_content_type", "application/x-judge", "bp_parent", null, "bp_hidden_from_external_users", 0);
        assertArrayEquals(content, (byte[]) Db.scalar("select bpa_content from bug_post_attachments where bpa_post = ?",
                file.get("bp_id")));
        assertEquals(1, Db.count("select count(*) from bug_posts where bp_type = 'received'"));
        assertEquals(file.get("bp_date"), Db.scalar("select bg_last_updated_date from bugs where bg_id = 1"));
    }

    private void seedBug() {
        int admin = id("admin");
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org,"
                + " bg_category, bg_project, bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date,"
                + " bg_user_defined_attribute) values ('Seeded bug', ?, ?, 1, 3, 1, 1, 1, 0, ?, ?, 0)",
                admin, SEED_DATE, admin, SEED_DATE);
    }
}
