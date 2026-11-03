package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import judge.core.Db;
import judge.core.Form;
import judge.core.Inherited;
import judge.core.JudgeConfig;
import judge.core.Norm;
import judge.core.Scenario;
import judge.core.SourceRef;
import judge.core.Web;

/**
 * S2 — bug list page (bugs.aspx) and the JSON query API (api/BugQuery) it calls.
 * See judge/specs/S2.md.
 */
class S2_BugListTest extends Scenario {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Column order of the database project's "all bugs" query (Script.PostDeployment.sql:12-28). */
    private static final List<String> ALL_BUGS_COLUMNS = List.of(
            "$COLOR", "id", "$FLAG", "desc", "project", "organization", "category", "reported by",
            "reported on", "priority", "assigned to", "status", "last updated by", "last updated on");

    private static final String BUG1 = """
            {"$COLOR":"#ff9999","id":1,"$FLAG":0,"desc":"charlie","project":"project 1","organization":"org1",
             "category":"bug","reported by":"admin","reported on":"2024-01-15T00:00:00","priority":"high",
             "assigned to":"developer","status":"new","last updated by":"admin","last updated on":"2024-01-20T00:00:00"}""";
    private static final String BUG2 = """
            {"$COLOR":"#ffdddd","id":2,"$FLAG":0,"desc":"alpha","project":"project 2","organization":"developers",
             "category":"enhancement","reported by":"developer","reported on":"2024-02-10T00:00:00","priority":"med",
             "assigned to":"","status":"in progress","last updated by":"","last updated on":null}""";
    private static final String BUG3 = """
            {"$COLOR":"#ffffff","id":3,"$FLAG":0,"desc":"bravo","project":"project 1","organization":"org1",
             "category":"task","reported by":"admin","reported on":"2024-03-05T00:00:00","priority":"low",
             "assigned to":"tester","status":"closed","last updated by":"developer","last updated on":"2024-03-06T00:00:00"}""";

    // ------------------------------------------------------------------ API

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugQueryController.cs:9-27", why = "GET api/BugQuery; body {recordsTotal, recordsFiltered, data}")
    @SourceRef(value = "src/BugTracker.Web/App_Start/WebApiConfig.cs:14-18", why = "route api/{controller}/{id}")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:41,55", why = "SELECT t.* -> every query column is a JSON property")
    @SourceRef(value = "src/BugTracker.Database/Script.PostDeployment.sql:12-28", why = "all bugs SQL and column list (aliases = JSON names)")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:400-404,463-467", why = "admin: project-permission predicate only, no org restriction (og_other_orgs_permission_level 2)")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:125-126,170,174,178,244-246,279-280,463-467,483-485", why = "seed names joined into the rows")
    void apiAllBugsQueryReturnsEveryColumnOfEveryRow() throws Exception {
        seed();
        JsonNode r = api(q("queryId", "1", "sortBy", "id", "sortOrder", "DESC", "start", "0", "length", "10", "idOnly", "false"));
        assertEquals(List.of("recordsTotal", "recordsFiltered", "data"), names(r), "response property names");
        assertEquals(3, r.get("recordsTotal").intValue());
        assertTrue(r.get("recordsTotal").isInt() && r.get("recordsFiltered").isInt(), "counts are integers");
        assertEquals(3, r.get("recordsFiltered").intValue());
        List<JsonNode> rows = byId(r.get("data"));
        assertEquals(List.of(JSON.readTree(BUG1), JSON.readTree(BUG2), JSON.readTree(BUG3)), rows);
        for (JsonNode row : rows) assertEquals(ALL_BUGS_COLUMNS, names(row), "property order = query column order");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Database/Script.PostDeployment.sql:31-48", why = "open bugs = bg_status <> 5")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:467-475", why = "permission predicate ANDed into the existing WHERE")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:43,45,88", why = "recordsTotal counts the query's own rows")
    void apiOpenBugsQueryExcludesClosed() throws Exception {
        seed();
        JsonNode r = api(q("queryId", "2", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false"));
        assertEquals(2, r.get("recordsTotal").intValue());
        assertEquals(2, r.get("recordsFiltered").intValue());
        assertEquals(List.of(JSON.readTree(BUG1), JSON.readTree(BUG2)), byId(r.get("data")));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:75-79,103", why = "ORDER BY [sortBy] ASC|DESC, OFFSET start FETCH length decides which rows form the page")
    void apiSortByColumnSelectsThePage() {
        seed();
        // desc values: 1=charlie, 2=alpha, 3=bravo
        assertEquals(Set.of(2, 3), ids(api(q("queryId", "1", "sortBy", "desc", "sortOrder", "ASC", "start", "0", "length", "2", "idOnly", "false"))));
        assertEquals(Set.of(1, 3), ids(api(q("queryId", "1", "sortBy", "desc", "sortOrder", "DESC", "start", "0", "length", "2", "idOnly", "false"))));
        assertEquals(Set.of(3), ids(api(q("queryId", "1", "sortBy", "reported on", "sortOrder", "DESC", "start", "0", "length", "1", "idOnly", "false"))));
        assertEquals(Set.of(1), ids(api(q("queryId", "1", "sortBy", "reported on", "sortOrder", "ASC", "start", "0", "length", "1", "idOnly", "false"))));
    }

    @Test
    @Inherited("Sort direction is compared case-sensitively with \"ASC\"; the page's DataTables client sends lowercase \"asc\", so the list can never sort ascending")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:103", why = "sortDirection == \"ASC\" ? \"ASC\" : \"DESC\"")
    @SourceRef(value = "src/BugTracker.Web/Controls/BugList.ascx:162-166", why = "client sends data.order[0].dir (DataTables: lowercase)")
    void apiLowercaseAscSortsDescending() {
        seed();
        assertEquals(Set.of(1, 3), ids(api(q("queryId", "1", "sortBy", "desc", "sortOrder", "asc", "start", "0", "length", "2", "idOnly", "false"))));
        assertEquals(Set.of(3), ids(api(q("queryId", "1", "sortBy", "id", "sortOrder", "asc", "start", "0", "length", "1", "idOnly", "false"))));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:79,84", why = "OFFSET @offset, FETCH @page_size; length <= 0 -> 5000")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:43,88-89", why = "counts ignore paging")
    void apiPaging() {
        seed();
        JsonNode r = api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "1", "length", "1", "idOnly", "false"));
        assertEquals(Set.of(2), ids(r));
        assertEquals(3, r.get("recordsTotal").intValue());
        assertEquals(3, r.get("recordsFiltered").intValue());
        assertEquals(Set.of(3), ids(api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "2", "length", "5", "idOnly", "false"))));
        JsonNode past = api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "3", "length", "5", "idOnly", "false"));
        assertTrue(past.get("data").isArray() && past.get("data").isEmpty(), "page past the end is an empty array");
        assertEquals(3, past.get("recordsTotal").intValue());
        assertEquals(Set.of(1, 2, 3), ids(api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "-1", "idOnly", "false"))));
        assertEquals(Set.of(1, 2, 3), ids(api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "0", "idOnly", "false"))));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:48-53,63,66-73,106-122,89", why = "filters: [col] = @col joined with AND; recordsFiltered from the filtered count")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:70,131-137", why = "parameter name = column with spaces -> _")
    @SourceRef(value = "src/BugTracker.Web/Controls/BugList.ascx:140-151,163-173", why = "client sends filters as [{Column, Value}] via $.param")
    @SourceRef(value = "src/BugTracker.Web/print_bugs.aspx.cs:62-75", why = "the same filters[i][Column]/[Value] key format, read explicitly")
    void apiFilters() {
        seed();
        JsonNode r = api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "1", "idOnly", "false",
                "filters[0][Column]", "project", "filters[0][Value]", "project 1"));
        assertEquals(3, r.get("recordsTotal").intValue());
        assertEquals(2, r.get("recordsFiltered").intValue());
        assertEquals(Set.of(1), ids(r));

        JsonNode two = api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false",
                "filters[0][Column]", "project", "filters[0][Value]", "project 1",
                "filters[1][Column]", "assigned to", "filters[1][Value]", "tester"));
        assertEquals(3, two.get("recordsTotal").intValue());
        assertEquals(1, two.get("recordsFiltered").intValue());
        assertEquals(Set.of(3), ids(two));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Database/Script.PostDeployment.sql:13,18", why = "isnull(bu_flag,0) [$FLAG] from bug_user for @ME")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:82-83", why = "@ME = current user id")
    @SourceRef(value = "src/BugTracker.Web/Controls/BugList.ascx.cs:38,51-56", why = "$FLAG is a filterable column with values 0/1/2")
    void apiFlagColumnIsPerUserAndFilterable() {
        seed();
        Db.exec("insert into bug_user (bu_bug, bu_user, bu_flag, bu_seen, bu_vote) values (1, 1, 1, 0, 0)");
        Db.exec("insert into bug_user (bu_bug, bu_user, bu_flag, bu_seen, bu_vote) values (2, 2, 2, 0, 0)"); // someone else's flag
        JsonNode r = api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false"));
        List<JsonNode> rows = byId(r.get("data"));
        assertEquals(1, rows.get(0).get("$FLAG").intValue());
        assertEquals(0, rows.get(1).get("$FLAG").intValue());
        assertEquals(0, rows.get(2).get("$FLAG").intValue());
        JsonNode f = api(q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false",
                "filters[0][Column]", "$FLAG", "filters[0][Value]", "1"));
        assertEquals(1, f.get("recordsFiltered").intValue());
        assertEquals(Set.of(1), ids(f));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:41,55", why = "idOnly -> SELECT t.id")
    void apiIdOnlyReturnsOnlyIds() {
        seed();
        JsonNode r = api(q("queryId", "1", "sortBy", "id", "sortOrder", "DESC", "start", "0", "length", "2", "idOnly", "true"));
        assertEquals(3, r.get("recordsTotal").intValue());
        assertEquals(3, r.get("recordsFiltered").intValue());
        for (JsonNode row : r.get("data")) assertEquals(List.of("id"), names(row));
        assertEquals(Set.of(3, 2), ids(r));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugQueryController.cs:14-16,27", why = "unknown query id -> NotFound()")
    @SourceRef(value = "src/BugTracker.Web/btnet/BugQueryExecutor.cs:35-38", why = "sortBy not in the query's columns -> ArgumentException (unhandled -> 500)")
    void apiErrors() {
        seed();
        loginIfNeeded();
        assertEquals(404, web.getJson("api/BugQuery?" + q("queryId", "999", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false")).status());
        assertEquals(500, web.getJson("api/BugQuery?" + q("queryId", "1", "sortBy", "bg_id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false")).status());
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controllers/BugQueryController.cs:6", why = "[Authorize]")
    @SourceRef(value = "src/BugTracker.Web/App_Start/Startup.Auth.cs:15-19", why = "cookie auth with LoginPath /default.aspx turns the 401 into a redirect")
    void apiRequiresLogin() {
        Web anon = newSession();
        Web.Page p = anon.getNoFollow("api/BugQuery?" + q("queryId", "1", "sortBy", "id", "sortOrder", "ASC", "start", "0", "length", "10", "idOnly", "false"));
        assertEquals(302, p.status());
        String loc = p.header("Location").orElseThrow();
        assertEquals("default.aspx", Norm.relPath(p.uri().resolve(loc)));
    }

    // ------------------------------------------------------------------ bugs.aspx

    @Test
    @SourceRef(value = "src/BugTracker.Web/bugs.aspx:22", why = "DropDownList ID=query")
    @SourceRef(value = "src/BugTracker.Web/bugs.aspx.cs:54-77", why = "options = queries for user, text=desc, value=id; default (qu_default=1) selected, or ?queryId")
    @SourceRef(value = "src/BugTracker.Web/Models/Context.cs:42-50", why = "user/org null-or-match, ordered by description")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:747-910", why = "the ten seeded queries, ids 1..10 in insert order, all bugs is the default")
    @SourceRef(value = "src/BugTracker.Web/bugs.aspx.cs:19-20", why = "title AppTitle - PluralBugLabel")
    void bugsPageQueryDropdown() {
        seed();
        loginAdmin(web);
        Web.Page p = web.get("bugs.aspx");
        assertEquals(200, p.status());
        Document d = p.html();
        assertEquals("BugTracker.NET - bugs", Norm.text(d.title()));
        Element sel = selectNamed(d, "query");
        List<String> values = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Element o : sel.select("option")) { values.add(o.attr("value")); texts.add(Norm.text(o.text())); }
        assertEquals(List.of("all bugs", "bugs with attachments", "bugs with related bugs", "checked in bugs - for QA",
                "days in status", "demo last comment as column", "demo use of css classes", "demo votes feature",
                "open bugs", "open bugs assigned to me"), texts);
        assertEquals(List.of("1", "8", "9", "4", "7", "6", "5", "10", "2", "3"), values);
        assertEquals("1", Form.first(web, p).get("query"), "default query selected");

        assertEquals("7", Form.first(web, web.get("bugs.aspx?queryId=7")).get("query"), "queryId parameter selects");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/Controls/BugList.ascx:3-30", why = "table#bug-table: header row of visible columns, filter row of select.table-filter[data-column-name]")
    @SourceRef(value = "src/BugTracker.Web/Controls/BugList.ascx.cs:18-31", why = "display names: $FLAG->flag, $SEEN->new, $VOTE->votes")
    @SourceRef(value = "src/BugTracker.Web/Models/query.cs:17-24", why = "columns from qu_columns, minus $COLOR/$SCORE")
    @SourceRef(value = "src/BugTracker.Web/Controls/BugList.ascx.cs:38-43,45-141", why = "filterable columns and their option lists (names ordered by name)")
    @SourceRef(value = "src/BugTracker.Database/Script.PostDeployment.sql:27,105", why = "qu_columns of all bugs and days in status")
    void bugsPageRendersColumnsAndFilterOptions() {
        seed();
        loginAdmin(web);
        Document d = web.get("bugs.aspx").html();
        List<List<String>> t = Norm.tableById(d, "bug-table");
        assertEquals(List.of("id", "flag", "desc", "project", "organization", "category", "reported by", "reported on",
                "priority", "assigned to", "status", "last updated by", "last updated on"), t.get(0));
        assertEquals(2, t.size(), "server renders header + filter rows only; data comes from the API");

        List<String> filterCols = new ArrayList<>();
        for (Element s : d.select("select.table-filter")) filterCols.add(s.attr("data-column-name"));
        assertEquals(List.of("$FLAG", "project", "organization", "category", "reported by", "priority", "assigned to", "status"), filterCols);

        assertOptions(d, "$FLAG", List.of("", "0", "1", "2"), List.of("[no filter]", "None", "Red", "Green"));
        assertOptions(d, "project", List.of("", "project 1", "project 2"), null);
        assertOptions(d, "organization", List.of("", "client one", "client two", "developers", "org1", "testers"), null);
        assertOptions(d, "category", List.of("", "bug", "enhancement", "question", "task", "ticket"), null);
        assertOptions(d, "priority", List.of("", "high", "low", "med"), null);
        assertOptions(d, "status", List.of("", "checked in", "closed", "in progress", "new", "re-opened"), null);
        List<String> users = List.of("", "admin", "customer1", "customer2", "developer", "email", "guest", "reporter", "tester", "viewer");
        assertOptions(d, "reported by", users, null);
        assertOptions(d, "assigned to", users, null);

        Document d7 = web.get("bugs.aspx?queryId=7").html();
        assertEquals(List.of("id", "desc", "days in status", "status", "last status change", "status date"),
                Norm.tableById(d7, "bug-table").get(0));
        List<String> f7 = new ArrayList<>();
        for (Element s : d7.select("select.table-filter")) f7.add(s.attr("data-column-name"));
        assertEquals(List.of("status"), f7);
    }

    // ------------------------------------------------------------------ fixtures / helpers

    /**
     * Seed: database-project queries (see spec "Schema fixture") + three bugs with fixed values.
     * Bug ids are 1..3: Db.reset() recreated the bugs table, so the identity restarts at 1.
     */
    static void seed() {
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org, bg_category, bg_project,"
                + " bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date)"
                + " values (N'charlie', 1, '2024-01-15T00:00:00', 1, 1, 1, 1, 1, 2, 1, '2024-01-20T00:00:00')");
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org, bg_category, bg_project,"
                + " bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date)"
                + " values (N'alpha', 2, '2024-02-10T00:00:00', 2, 2, 2, 2, 2, NULL, NULL, NULL)");
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_status, bg_priority, bg_org, bg_category, bg_project,"
                + " bg_assigned_to_user, bg_last_updated_user, bg_last_updated_date)"
                + " values (N'bravo', 1, '2024-03-05T00:00:00', 5, 3, 1, 3, 1, 3, 2, '2024-03-06T00:00:00')");
    }

    private JsonNode api(String query) {
        loginIfNeeded();
        Web.Page p = web.getJson("api/BugQuery?" + query);
        assertEquals(200, p.status(), "api/BugQuery?" + query + " -> " + p.text());
        try {
            return JSON.readTree(p.body());
        } catch (IOException e) {
            throw new AssertionError("not JSON: " + p.text(), e);
        }
    }

    private boolean loggedIn;

    private void loginIfNeeded() {
        if (!loggedIn) { loginAdmin(web); loggedIn = true; }
    }

    /** Query string, keys and values percent-encoded the way jQuery.param does (brackets encoded, space %20). */
    private static String q(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<String> names(JsonNode o) {
        List<String> n = new ArrayList<>();
        o.fieldNames().forEachRemaining(n::add);
        return n;
    }

    /** Row order inside a page is not judged (see spec): compare sorted by id. */
    private static List<JsonNode> byId(JsonNode data) {
        assertTrue(data != null && data.isArray(), "data is an array");
        List<JsonNode> rows = new ArrayList<>();
        data.forEach(rows::add);
        rows.sort(Comparator.comparingInt(n -> n.get("id").intValue()));
        return rows;
    }

    private static Set<Integer> ids(JsonNode r) {
        Set<Integer> s = new TreeSet<>();
        for (JsonNode row : r.get("data")) {
            assertTrue(row.get("id").isInt(), "id is an integer");
            s.add(row.get("id").intValue());
        }
        assertEquals(r.get("data").size(), s.size(), "no duplicate ids");
        return s;
    }

    private static Element selectNamed(Document d, String logical) {
        for (Element s : d.select("select[name]")) {
            if (Form.logicalName(s.attr("name")).equals(logical)) return s;
        }
        throw new AssertionError("no select named " + logical);
    }

    private static void assertOptions(Document d, String column, List<String> values, List<String> texts) {
        Element s = null;
        for (Element e : d.select("select.table-filter")) if (e.attr("data-column-name").equals(column)) s = e;
        assertNotNull(s, "filter for " + column);
        List<String> v = new ArrayList<>();
        List<String> t = new ArrayList<>();
        for (Element o : s.select("option")) { v.add(o.attr("value")); t.add(Norm.text(o.text())); }
        assertEquals(values, v, "filter values for " + column);
        List<String> expectTexts = texts;
        if (expectTexts == null) {
            expectTexts = new ArrayList<>(values);
            expectTexts.set(0, "[no filter]");
        }
        assertEquals(expectTexts, t, "filter texts for " + column);
    }

}
