package judge.scenarios;

import static judge.scenarios.S3_EditBugTest.between;
import static judge.scenarios.S3_EditBugTest.dbNow;
import static judge.scenarios.S3_EditBugTest.expect;
import static judge.scenarios.S3_EditBugTest.id;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import judge.core.Db;
import judge.core.Form;
import judge.core.Norm;
import judge.core.Scenario;
import judge.core.SourceRef;
import judge.core.Web;

/**
 * S4 — attachments: add_attachment.aspx upload, view_attachment.aspx download.
 * See judge/specs/S4.md. Web.config has StoreAttachmentsInDatabase=1, so content
 * lives in bug_post_attachments and is asserted there.
 */
class S4_AttachmentTest extends Scenario {

    private static final Timestamp SEED_DATE = Timestamp.valueOf("2020-01-02 03:04:05");

    /** 256 distinct byte values (incl. 0x00, CR, LF, 0xFF) + ASCII tail: 261 bytes. */
    static byte[] payload() {
        byte[] b = new byte[261];
        for (int i = 0; i < 256; i++) b[i] = (byte) i;
        byte[] tail = "judge".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(tail, 0, b, 256, 5);
        return b;
    }

    // ---------------------------------------------------------------- upload

    @Test
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx:35-74", why = "multipart form: desc, attached_file, submit 'sub'")
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx.cs:162-240", why = "checks, insert_post_attachment(parent -1, notify), success message text")
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx.cs:142-159", why = "message delivered as parent.set_msg('...')")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:254-372", why = "bugs last-updated = @now; bug_posts 'file' row; blob into bug_post_attachments")
    @SourceRef(value = "src/BugTracker.Web/Web.config", why = "StoreAttachmentsInDatabase=1, MaxUploadSize=5000000, EnableInternalOnlyPosts=0")
    @SourceRef(value = "src/BugTracker.Web/Global.asax.cs:88-92", why = "App_Data\\uploads created at startup, so get_upload_folder() does not throw")
    void uploadStoresFilePostAndBlob() {
        loginAdmin(web);
        int admin = id("admin");
        seedBug(1);
        byte[] content = payload();
        Web.Page p = web.get("add_attachment.aspx?id=1");
        assertEquals(200, p.status());
        Form f = Form.containing(web, p, "attached_file")
                .set("desc", "judge attachment")
                .file("attached_file", "judge.bin", content);
        Timestamp before = dbNow();
        Web.Page r = f.submit("sub");
        Timestamp after = dbNow();

        assertEquals(200, r.status());
        assertTrue(r.text().contains("judge.bin was successfully upload (application/octet-stream), 261 bytes"), r.text());

        Map<String, Object> post = Db.one("select * from bug_posts");
        expect(post, "bp_bug", 1, "bp_type", "file", "bp_user", admin, "bp_file", "judge.bin",
                "bp_comment", "judge attachment", "bp_comment_search", null, "bp_size", 261,
                "bp_content_type", "application/octet-stream", "bp_parent", null,
                "bp_hidden_from_external_users", 0, "bp_email_from", null, "bp_email_to", null, "bp_email_cc", null,
                "bp_original_comment_id", null);
        between(before, after, post.get("bp_date"), "bp_date");

        Map<String, Object> blob = Db.one("select * from bug_post_attachments");
        assertEquals(((Number) post.get("bp_id")).intValue(), ((Number) blob.get("bpa_post")).intValue());
        assertArrayEquals(content, (byte[]) blob.get("bpa_content"), "stored bytes");

        Map<String, Object> bug = Db.one("select * from bugs where bg_id = 1");
        expect(bug, "bg_last_updated_user", admin);
        assertEquals(post.get("bp_date"), bug.get("bg_last_updated_date"), "same @now");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx.cs:191-195", why = "zero-length file -> 'No data was uploaded.'")
    void emptyFileIsRejected() {
        assertRejected("empty.bin", new byte[0], "No data was uploaded.");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx.cs:164-179", why = "browser sends filename=\"\" when no file chosen -> 'Please select file'")
    void noFileChosenIsRejected() {
        assertRejected("", new byte[0], "Please select file");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx.cs:181-189", why = "size > MaxUploadSize -> exact message")
    @SourceRef(value = "src/BugTracker.Web/Web.config", why = "MaxUploadSize=5000000; maxRequestLength=51200 KB lets the request through")
    void oversizeFileIsRejected() {
        assertRejected("big.bin", new byte[5_000_001], "File exceeds maximum allowed length of 5000000.");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/add_attachment.aspx.cs:106-125", why = "ReadOnly/None on the bug's project -> 'You are not allowed to edit this item'; no id -> 'Invalid id.'")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:740-794", why = "get_bug_permission_level from project_user_xref")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:557-572", why = "sanitize_integer(null) returns null")
    void readOnlyUserAndMissingIdCannotUpload() {
        loginAdmin(web);
        seedBug(1);
        Db.exec("insert into project_user_xref (pu_project, pu_user, pu_permission_level) values (1, ?, 1)", id("admin"));
        Web.Page p = web.get("add_attachment.aspx?id=1");
        assertEquals(200, p.status());
        assertTrue(p.text().contains("You are not allowed to edit this item"), p.text());

        Web.Page noId = web.get("add_attachment.aspx");
        assertEquals(200, noId.status());
        assertTrue(noId.text().contains("Invalid id."), noId.text());
        assertEquals(0, Db.count("select count(*) from bug_posts"));
    }

    // ---------------------------------------------------------------- download

    @Test
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:19-58", why = "lookup by id + bug_id; download defaults to 1")
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:109-146", why = "Content-Type = bp_content_type; content-disposition attachment/inline; blob bytes written")
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:1-4,168-169", why = "page literals (newlines, inc.aspx) render after the bytes: trailing whitespace only")
    void downloadReturnsStoredBytes() {
        loginAdmin(web);
        seedBug(1);
        byte[] content = payload();
        seedAttachment("seed.bin", "application/octet-stream", content);

        Web.Page r = web.get("view_attachment.aspx?id=1&bug_id=1");
        assertEquals(200, r.status());
        assertEquals("application/octet-stream", mediaType(r));
        assertEquals("attachment; filename=\"seed.bin\"", r.header("Content-Disposition").orElse(null));
        assertBodyIsContentPlusWhitespace(content, r.body());

        Web.Page inline = web.get("view_attachment.aspx?id=1&bug_id=1&download=0");
        assertEquals(200, inline.status());
        assertEquals("inline; filename=\"seed.bin\"", inline.header("Content-Disposition").orElse(null));
        assertBodyIsContentPlusWhitespace(content, inline.body());
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:111-114", why = "empty bp_content_type -> filename_to_content_type")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1009-1063", why = ".txt -> text/plain")
    void emptyContentTypeFallsBackToExtension() {
        loginAdmin(web);
        seedBug(1);
        byte[] content = "hello judge".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        seedAttachment("notes.txt", "", content);
        Web.Page r = web.get("view_attachment.aspx?id=1&bug_id=1");
        assertEquals(200, r.status());
        assertEquals("text/plain", mediaType(r));
        assertBodyIsContentPlusWhitespace(content, r.body());
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:12-17", why = "no bug_id -> Response.End, empty body")
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:31-36", why = "id/bug_id mismatch -> Response.End, empty body")
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:38-43", why = "permission None -> 'You are not allowed to view this item'")
    @SourceRef(value = "src/BugTracker.Web/view_attachment.aspx:101-107", why = "post without blob or file -> 'File not found:<br>' + name")
    void downloadRefusals() {
        loginAdmin(web);
        seedBug(1);
        seedAttachment("seed.bin", "application/octet-stream", payload());

        Web.Page noBug = web.get("view_attachment.aspx?id=1");
        assertEquals(200, noBug.status());
        assertEquals(0, noBug.body().length, "empty body without bug_id");

        Web.Page wrongBug = web.get("view_attachment.aspx?id=1&bug_id=2");
        assertEquals(200, wrongBug.status());
        assertEquals(0, wrongBug.body().length, "empty body when post is not on that bug");

        // a 'file' post with no blob and no file on disk
        Db.exec("insert into bug_posts (bp_bug, bp_type, bp_user, bp_date, bp_comment, bp_file, bp_size, bp_content_type)"
                + " values (1, 'file', ?, ?, '', 'gone.bin', 3, 'application/octet-stream')", id("admin"), SEED_DATE);
        Web.Page gone = web.get("view_attachment.aspx?id=2&bug_id=1");
        assertEquals(200, gone.status());
        assertTrue(Norm.text(gone.html().text()).contains("File not found: gone.bin"), gone.text());

        Db.exec("insert into project_user_xref (pu_project, pu_user, pu_permission_level) values (1, ?, 0)", id("admin"));
        Web.Page denied = web.get("view_attachment.aspx?id=1&bug_id=1");
        assertEquals(200, denied.status());
        assertTrue(denied.text().contains("You are not allowed to view this item"), denied.text());
        assertTrue(indexOf(denied.body(), payload()) < 0, "content must not be sent");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/BasePage.cs:12-18", why = "unauthenticated -> redirect default.aspx?returnUrl=<RawUrl>")
    void unauthenticatedDownloadRedirectsToLogin() {
        loginAdmin(web);
        seedBug(1);
        seedAttachment("seed.bin", "application/octet-stream", payload());
        Web anon = newSession();
        Web.Page r = anon.get("view_attachment.aspx?id=1&bug_id=1");
        assertEquals(1, r.redirects().size());
        assertEquals("default.aspx", Norm.relPath(r.redirects().get(0)));
        URI back = anon.resolve("/").resolve(Norm.query(r.redirects().get(0), "returnUrl"));
        assertEquals("view_attachment.aspx", Norm.relPath(back));
        assertEquals("1", Norm.query(back, "id"));
        assertEquals("1", Norm.query(back, "bug_id"));
        assertTrue(indexOf(r.body(), payload()) < 0, "content must not be sent");
    }

    // ---------------------------------------------------------------- helpers

    private void assertRejected(String filename, byte[] content, String message) {
        loginAdmin(web);
        seedBug(1);
        Web.Page p = web.get("add_attachment.aspx?id=1");
        Web.Page r = Form.containing(web, p, "attached_file").set("desc", "x")
                .file("attached_file", filename, content).submit("sub");
        assertEquals(200, r.status());
        assertTrue(r.text().contains(message), "expected '" + message + "' in: " + r.text());
        assertEquals(0, Db.count("select count(*) from bug_posts"));
        assertEquals(0, Db.count("select count(*) from bug_post_attachments"));
        assertEquals(SEED_DATE, Db.scalar("select bg_last_updated_date from bugs where bg_id = 1"));
    }

    private void seedBug(int project) {
        int admin = id("admin");
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org,"
                + " bg_category, bg_project, bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date,"
                + " bg_user_defined_attribute) values ('Attachment host', ?, ?, 1, 3, 1, 1, ?, 0, ?, ?, 0)",
                admin, SEED_DATE, project, admin, SEED_DATE);
    }

    /** bug_posts row (bp_id 1 on a fresh DB) + blob. */
    private void seedAttachment(String file, String contentType, byte[] content) {
        Db.exec("insert into bug_posts (bp_bug, bp_type, bp_user, bp_date, bp_comment, bp_file, bp_size, bp_content_type)"
                + " values (1, 'file', ?, ?, 'seeded attachment', ?, ?, ?)", id("admin"), SEED_DATE, file, content.length, contentType);
        Db.exec("insert into bug_post_attachments (bpa_post, bpa_content) values (1, ?)", (Object) content);
    }

    static String mediaType(Web.Page p) {
        return p.header("Content-Type").map(v -> v.split(";")[0].trim().toLowerCase()).orElse(null);
    }

    /**
     * Body = stored bytes, byte for byte, followed only by CR/LF. The original
     * renders view_attachment.aspx's whitespace literals after the blob
     * (see S4.md); their exact count is not judged.
     */
    static void assertBodyIsContentPlusWhitespace(byte[] content, byte[] body) {
        assertTrue(body.length >= content.length, "body shorter than content: " + body.length);
        assertArrayEquals(content, Arrays.copyOf(body, content.length), "body prefix must equal stored bytes");
        for (int i = content.length; i < body.length; i++) {
            assertTrue(body[i] == '\r' || body[i] == '\n', "non-whitespace byte after content at " + i);
        }
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
