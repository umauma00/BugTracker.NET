package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import judge.core.Db;
import judge.core.Inherited;
import judge.core.Norm;
import judge.core.Scenario;
import judge.core.SourceRef;
import judge.core.Web;

/**
 * S7 — print one bug, print a list (table and details), export to Excel (CSV).
 * Fixtures are S2's (three bugs, database-project queries). See judge/specs/S7.md.
 */
class S7_PrintExportTest extends Scenario {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    /** Marks a cell whose text is not judged (culture-dependent DateTime.ToString()). */
    private static final String ANY = "\u0000any";

    private static final List<List<String>> FIELDS_BUG1 = List.of(
            List.of("Last changed by", "admin"), List.of("Reported By", "admin"), List.of("Reported On", "2024-01-15"),
            List.of("Project", "project 1"), List.of("Organization", "org1"), List.of("Category", "bug"),
            List.of("Priority", "high"), List.of("Assigned", "developer"), List.of("Status", "new"));
    private static final List<List<String>> FIELDS_BUG2 = List.of(
            List.of("Last changed by", ""), List.of("Reported By", "developer"), List.of("Reported On", "2024-02-10"),
            List.of("Project", "project 2"), List.of("Organization", "developers"), List.of("Category", "enhancement"),
            List.of("Priority", "med"), List.of("Assigned", "[not assigned]"), List.of("Status", "in progress"));
    private static final List<List<String>> FIELDS_BUG3 = List.of(
            List.of("Last changed by", "developer"), List.of("Reported By", "admin"), List.of("Reported On", "2024-03-05"),
            List.of("Project", "project 1"), List.of("Organization", "org1"), List.of("Category", "task"),
            List.of("Priority", "low"), List.of("Assigned", "tester"), List.of("Status", "closed"));

    // ------------------------------------------------------------------ print_bugs.aspx (list)

    @Test
    @SourceRef(value = "src/BugTracker.Web/print_bugs.aspx.cs:31-57", why = "runs BugQueryExecutor with queryId/start/length/sortBy/sortOrder/filters, html unless format=excel")
    @SourceRef(value = "src/BugTracker.Web/print_bugs.aspx.cs:83-103", why = "header cells from column 1 on ($COLOR skipped), $FLAG->flag")
    @SourceRef(value = "src/BugTracker.Web/print_bugs.aspx.cs:105-160", why = "cells: flag as empty span, empty value -> &nbsp;, else HtmlEncode(ToString())")
    @SourceRef(value = "src/BugTracker.Web/bugs.aspx:70-80", why = "the page opens print_bugs.aspx?<query params>&start=0&length=-1")
    void printListRendersQueryColumnsAndCells() {
        S2_BugListTest.seed();
        loginAdmin(web);
        Web.Page p = web.get("print_bugs.aspx?" + q("queryId", "1", "sortBy", "id", "sortOrder", "DESC", "start", "0", "length", "-1"));
        assertEquals(200, p.status());
        Element table = p.html().selectFirst("table");
        assertNotNull(table, "print_bugs renders one table");
        List<List<String>> t = Norm.table(table);
        assertEquals(List.of("id", "flag", "desc", "project", "organization", "category", "reported by", "reported on",
                "priority", "assigned to", "status", "last updated by", "last updated on"), t.get(0));
        assertCells(List.of(
                List.of("1", "", "charlie", "project 1", "org1", "bug", "admin", ANY, "high", "developer", "new", "admin", ANY),
                List.of("2", "", "alpha", "project 2", "developers", "enhancement", "developer", ANY, "med", "", "in progress", "", ""),
                List.of("3", "", "bravo", "project 1", "org1", "task", "admin", ANY, "low", "tester", "closed", "developer", ANY)),
                sortedById(t.subList(1, t.size())));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/print_bugs.aspx.cs:36,62-75", why = "filters read from filters[i][Column]/filters[i][Value]")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:48-53,63,75-79,84,101-104,106-122", why = "filter + sort + page")
    void printListHonorsFilterSortAndPaging() {
        S2_BugListTest.seed();
        loginAdmin(web);
        Web.Page p = web.get("print_bugs.aspx?" + q("queryId", "1", "sortBy", "desc", "sortOrder", "ASC", "start", "0", "length", "1",
                "filters[0][Column]", "project", "filters[0][Value]", "project 1"));
        List<List<String>> t = Norm.table(p.html().selectFirst("table"));
        assertEquals(2, t.size(), "header + one row");
        assertEquals("3", t.get(1).get(0), "project 1 bugs are charlie(1), bravo(3); first by desc ASC is bravo");
        assertEquals("bravo", t.get(1).get(2));
    }

    // ------------------------------------------------------------------ export (format=excel)

    @Test
    @SourceRef(value = "src/BugTracker.Web/bugs.aspx:88-90", why = "Export to Excel = print_bugs.aspx?format=excel&...")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1163-1236", why = "headers, BOM, quoted CSV, $COLOR/$FLAG/$SEEN skipped, CRLF")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:273-318", why = "DateTime cells via format_db_date_and_time; midnight -> JustDateFormat; DBNull -> \"\"")
    @SourceRef(value = "src/BugTracker.Web/Web.config:230", why = "JustDateFormat yyyy-MM-dd")
    void exportExcelIsCsvWithBom() {
        S2_BugListTest.seed();
        loginAdmin(web);
        Web.Page p = web.get("print_bugs.aspx?" + q("format", "excel", "queryId", "1", "sortBy", "id", "sortOrder", "DESC", "start", "0", "length", "-1"));
        assertEquals(200, p.status());
        assertExcelHeaders(p);
        List<String> lines = csvLines(p);
        assertEquals("\"id\",\"desc\",\"project\",\"organization\",\"category\",\"reported by\",\"reported on\",\"priority\","
                + "\"assigned to\",\"status\",\"last updated by\",\"last updated on\"", lines.get(0));
        List<String> data = new ArrayList<>(lines.subList(1, lines.size()));
        data.sort(Comparator.naturalOrder()); // row order not judged; ids are 1 digit so lexical = numeric
        assertEquals(List.of(
                "\"1\",\"charlie\",\"project 1\",\"org1\",\"bug\",\"admin\",\"2024-01-15\",\"high\",\"developer\",\"new\",\"admin\",\"2024-01-20\"",
                "\"2\",\"alpha\",\"project 2\",\"developers\",\"enhancement\",\"developer\",\"2024-02-10\",\"med\",\"\",\"in progress\",\"\",\"\"",
                "\"3\",\"bravo\",\"project 1\",\"org1\",\"task\",\"admin\",\"2024-03-05\",\"low\",\"tester\",\"closed\",\"developer\",\"2024-03-06\""),
                data);
    }

    @Test
    @Inherited("A time of exactly 12:00:00 (noon) is treated like midnight and exported as a date without time")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:302-305", why = "(Hour == 0 || Hour == 12) && Minute == 0 && Second == 0 -> JustDateFormat")
    void exportNoonTimestampLosesItsTime() {
        S2_BugListTest.seed();
        Db.exec("update bugs set bg_last_updated_date = '2024-01-20T12:00:00' where bg_id = 1");
        loginAdmin(web);
        Web.Page p = web.get("print_bugs.aspx?" + q("format", "excel", "queryId", "1", "sortBy", "id", "sortOrder", "DESC", "start", "0", "length", "-1",
                "filters[0][Column]", "id", "filters[0][Value]", "1"));
        List<String> lines = csvLines(p);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).endsWith(",\"admin\",\"2024-01-20\""), lines.get(1));
        assertEquals("\"1\",\"charlie\",\"project 1\",\"org1\",\"bug\",\"admin\",\"2024-01-15\",\"high\",\"developer\",\"new\",\"admin\",\"2024-01-20\"", lines.get(1));
    }

    @Test
    @Inherited("print_as_excel always skips column 0 (meant for the row colour), so the tasks export drops the bug id column")
    @SourceRef(value = "src/BugTracker.Web/tasks_all_excel.aspx:16-29", why = "admin or can_view_tasks; get_all_tasks(identity, 0) -> print_as_excel")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1241-1338", why = "columns (all ShowTask* off), order by tsk_sort_sequence, tsk_id")
    @SourceRef(value = "src/BugTracker.Web/Web.config:830-840", why = "ShowTask* = 0")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1183,1195", why = "starts at column 1; <br> in names -> space")
    void tasksExcelExport() {
        S2_BugListTest.seed();
        Db.exec("insert into bug_tasks (tsk_bug, tsk_created_user, tsk_created_date, tsk_last_updated_user, tsk_last_updated_date, tsk_description, tsk_sort_sequence)"
                + " values (1, 1, '2024-04-01T00:00:00', 1, '2024-04-01T00:00:00', N'write tests', 2)");
        Db.exec("insert into bug_tasks (tsk_bug, tsk_created_user, tsk_created_date, tsk_last_updated_user, tsk_last_updated_date, tsk_description, tsk_sort_sequence)"
                + " values (2, 1, '2024-04-01T00:00:00', 1, '2024-04-01T00:00:00', N'fix it', 1)");
        loginAdmin(web);
        Web.Page p = web.get("tasks_all_excel.aspx");
        assertEquals(200, p.status());
        assertExcelHeaders(p);
        assertEquals(List.of(
                "\"description\",\"project\",\"category\",\"status\",\"assigned to\",\"task id\",\"task description\"",
                "\"alpha\",\"project 2\",\"enhancement\",\"in progress\",\"\",\"2\",\"fix it\"",
                "\"charlie\",\"project 1\",\"bug\",\"new\",\"developer\",\"1\",\"write tests\""),
                csvLines(p));
    }

    // ------------------------------------------------------------------ print_bugs2.aspx (details)

    @Test
    @SourceRef(value = "src/BugTracker.Web/print_bugs2.aspx:20-43", why = "one print_bug per row of the query result (id = column 1)")
    @SourceRef(value = "src/BugTracker.Web/print_bugs2.aspx.cs:27-46", why = "title, same query params as print_bugs")
    @SourceRef(value = "src/BugTracker.Web/btnet/print_bug.cs:87-204", why = "bug id / short desc links and the field rows")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:538-545", why = "EnableSeen: each fetched bug is marked seen for the viewer")
    @SourceRef(value = "src/BugTracker.Web/Web.config:766", why = "EnableSeen = 1")
    void printDetailsPrintsEveryBugOfTheQuery() {
        S2_BugListTest.seed();
        loginAdmin(web);
        Web.Page p = web.get("print_bugs2.aspx?" + q("queryId", "1", "sortBy", "id", "sortOrder", "DESC", "start", "0", "length", "-1"));
        assertEquals(200, p.status());
        Document d = p.html();
        assertEquals("BugTracker.NET - print bugs", Norm.text(d.title()));
        assertEquals(Map.of(1, List.of("1", "charlie"), 2, List.of("2", "alpha"), 3, List.of("3", "bravo")), bugLinks(d));
        List<List<List<String>>> tables = fieldTables(d);
        assertEquals(3, tables.size());
        assertEquals(Set.of(FIELDS_BUG1, FIELDS_BUG2, FIELDS_BUG3), new HashSet<>(tables));
        assertEquals(3L, Db.count("select count(*) from bug_user where bu_user = 1 and bu_seen = 1 and bu_flag = 0 and bu_vote = 0"));
    }

    // ------------------------------------------------------------------ print_bug.aspx

    @Test
    @SourceRef(value = "src/BugTracker.Web/print_bug.aspx:22-32,84-90", why = "get_bug_datarow(id) then PrintBug.print_bug(internal_posts=true)")
    @SourceRef(value = "src/BugTracker.Web/btnet/print_bug.cs:87-163", why = "links and field rows; Tags hidden (EnableTags=0 -> tags permission 0), UDF hidden (ShowUserDefinedBugAttribute=0)")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:98-108", why = "tags field permission is None unless EnableTags")
    @SourceRef(value = "src/BugTracker.Web/Web.config:250,486,747", why = "ShowUserDefinedBugAttribute 0, UseFullNames 0, EnableTags 0")
    @SourceRef(value = "src/BugTracker.Web/btnet/print_bug.cs:305-415,1023-1081", why = "posts_table: comments newest first, history ('update') excluded without history_inline cookie")
    @SourceRef(value = "src/BugTracker.Web/btnet/print_bug.cs:579-584", why = "post header: 'on <date>, <how long ago>'")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:687-707", why = "how_long_ago: 'N days ago' for > 1 day")
    @SourceRef(value = "src/BugTracker.Web/Web.config:896", why = "CommentSortOrder desc")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:538-545", why = "marks the bug seen for the viewer")
    void printOneBug() {
        S2_BugListTest.seed();
        Db.exec("insert into bug_posts (bp_bug, bp_type, bp_user, bp_date, bp_comment, bp_comment_search, bp_content_type)"
                + " values (1, 'comment', 1, '2024-01-16T00:00:00', N'first comment', N'first comment', 'text/plain')");
        Db.exec("insert into bug_posts (bp_bug, bp_type, bp_user, bp_date, bp_comment, bp_comment_search, bp_content_type)"
                + " values (1, 'update', 1, '2024-01-16T00:00:00', N'changed desc from \"x\" to \"charlie\"', N'h', 'text/plain')");
        Db.exec("insert into bug_posts (bp_bug, bp_type, bp_user, bp_date, bp_comment, bp_comment_search, bp_content_type)"
                + " values (1, 'comment', 2, '2024-01-17T00:00:00', N'second comment' + char(10) + N'line two', N's', 'text/plain')");
        loginAdmin(web);
        Web.Page p = web.get("print_bug.aspx?id=1");
        assertEquals(200, p.status());
        Document d = p.html();
        assertEquals(Map.of(1, List.of("1", "charlie")), bugLinks(d));
        assertEquals(List.of(FIELDS_BUG1), fieldTables(d));
        String posts = Norm.textById(d, "posts_table");
        String re = "\\Qcomment 3 posted by developer on 2024-01-17, \\E\\d+\\Q days ago second comment line two \\E"
                + "\\Qcomment 1 posted by admin on 2024-01-16, \\E\\d+\\Q days ago first comment\\E";
        assertTrue(Pattern.matches(re, posts), "posts_table text: " + posts);
        Map<String, Object> bu = Db.one("select bu_flag, bu_seen, bu_vote from bug_user where bu_bug = 1 and bu_user = 1");
        assertEquals(Map.of("bu_flag", 0, "bu_seen", 1, "bu_vote", 0), bu);
    }

    @Test
    @Inherited("print_bug title has no space between 'ID' and the number")
    @SourceRef(value = "src/BugTracker.Web/print_bug.aspx:34-36", why = "AppTitle + \" - \" + Bug + \" ID\" + id + \" \" + short_desc")
    void printOneBugTitle() {
        S2_BugListTest.seed();
        loginAdmin(web);
        assertEquals("BugTracker.NET - Bug ID1 charlie", Norm.text(web.get("print_bug.aspx?id=1").html().title()));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/print_bug.aspx:26-31", why = "no row -> display_bug_not_found")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:1343-1354", why = "message text, link to bugs.aspx, Response.End")
    void printMissingBug() {
        S2_BugListTest.seed();
        loginAdmin(web);
        Web.Page p = web.get("print_bug.aspx?id=99");
        assertEquals(200, p.status());
        assertEquals("Bug not found: 99 View bugs", Norm.text(p.html().body().text()));
    }

    // ------------------------------------------------------------------ helpers

    private static String q(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8).replace("+", "%20")).append('=')
              .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static void assertExcelHeaders(Web.Page p) {
        assertEquals("application/ms-excel;charset=utf-8",
                p.header("Content-Type").orElse("").toLowerCase().replace(" ", ""), "content type");
        String cd = p.header("Content-Disposition").orElse("");
        assertTrue(cd.matches("attachment; filename=btnet_\\d{8}_\\d{6}\\.csv"), "content-disposition: " + cd);
    }

    /** Body = UTF-8 BOM + CRLF-terminated lines; returns the lines (without the final empty one). */
    private static List<String> csvLines(Web.Page p) {
        byte[] b = p.body();
        assertTrue(b.length >= 3, "body too short");
        assertArrayEquals(BOM, Arrays.copyOf(b, 3), "UTF-8 preamble");
        String s = new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        assertTrue(s.endsWith("\r\n"), "last line CRLF-terminated");
        assertTrue(!s.replace("\r\n", "").contains("\n"), "no bare LF");
        return List.of(s.substring(0, s.length() - 2).split("\r\n", -1));
    }

    private static List<List<String>> sortedById(List<List<String>> rows) {
        List<List<String>> r = new ArrayList<>(rows);
        r.sort(Comparator.comparingInt(x -> Integer.parseInt(x.get(0))));
        return r;
    }

    private static void assertCells(List<List<String>> expected, List<List<String>> actual) {
        assertEquals(expected.size(), actual.size(), "row count");
        for (int i = 0; i < expected.size(); i++) {
            List<String> e = expected.get(i), a = actual.get(i);
            assertEquals(e.size(), a.size(), "cell count, row " + i);
            for (int j = 0; j < e.size(); j++) {
                if (e.get(j) != ANY) assertEquals(e.get(j), a.get(j), "row " + i + " col " + j);
            }
        }
    }

    /** id -> texts of the links to edit_bug.aspx?id=id, in document order. */
    private static Map<Integer, List<String>> bugLinks(Document d) {
        Pattern pt = Pattern.compile("(?i)(?:^|/)edit_bug\\.aspx\\?id=(\\d+)$");
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        for (Element a : d.select("a[href]")) {
            Matcher mm = pt.matcher(a.attr("href"));
            if (mm.find()) m.computeIfAbsent(Integer.parseInt(mm.group(1)), k -> new ArrayList<>()).add(Norm.text(a.text()));
        }
        return m;
    }

    /** Every table holding a "Last changed by" row, as its two-cell rows (label, value). */
    private static List<List<List<String>>> fieldTables(Document d) {
        List<List<List<String>>> out = new ArrayList<>();
        for (Element t : d.select("table")) {
            List<List<String>> rows = new ArrayList<>();
            boolean isFields = false;
            for (List<String> r : Norm.table(t)) {
                if (r.size() != 2) continue;
                rows.add(r);
                if (r.get(0).equals("Last changed by")) isFields = true;
            }
            if (isFields) out.add(rows);
        }
        return out;
    }
}
