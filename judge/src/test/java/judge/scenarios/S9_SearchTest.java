package judge.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * S9 — SQL search page (search.aspx) and the no-input path of the text search.
 * The Elasticsearch-backed text search is not judged (see judge/specs/S9.md).
 */
class S9_SearchTest extends Scenario {

    private static final List<String> BUG1 = List.of("", "1", "alpha login fails", "admin", "2020-01-15", "admin",
            "2020-01-15", "project 1", "org1", "bug", "high", "developer", "new");
    private static final List<String> BUG2 = List.of("", "2", "beta discount 50% off", "tester", "2020-02-01", "admin",
            "2020-02-02", "project 2", "developers", "enhancement", "med", "", "in progress");

    // ------------------------------------------------------------------ tests

    @Test
    @SourceRef(value = "src/BugTracker.Web/BasePage.cs:9,12-17", why = "search.aspx is a BasePage without PageAllowAnonymous")
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:12", why = "class search : BasePage")
    void anonymousSearchRedirectsToLogin() {
        Web.Page p = web.getNoFollow("search.aspx");
        assertEquals(302, p.status());
        URI loc = p.uri().resolve(p.header("Location").orElseThrow());
        assertEquals("default.aspx", Norm.relPath(loc));
        assertEquals(JudgeConfig.baseUrl().getPath() + "search.aspx", Norm.query(loc, "returnUrl"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:61-89", why = "GET: load drop downs only, no query")
    @SourceRef(value = "src/BugTracker.Web/search.aspx:379-384", why = "dv == null -> no result output")
    @SourceRef(value = "src/BugTracker.Web/search.aspx:217-335", why = "form fields reported_by, category, priority, assigned_to, status, org, project, like, like2, and_or")
    void getShowsFormWithoutResults() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = web.get("search.aspx");
        assertEquals(200, p.status());
        Form f = Form.containing(web, p, "like");
        for (String field : new String[] {"reported_by", "category", "priority", "assigned_to", "status", "org",
                "project", "like2", "and_or"}) {
            assertTrue(f.has(field), "field " + field);
        }
        assertEquals("and", f.get("and_or"), "\"and\" logic preselected (search.aspx:333)");
        assertTrue(resultIds(p.html()).isEmpty());
        assertFalse(Norm.text(p.html().text()).contains("returned by query"));
    }

    /**
     * Sanctioned fix F4 (RULEBOOK §0.1, D-003): a non-admin in an org with og_can_search=1 (org1
     * default) signs in and gets the search form; a non-admin in an org with og_can_search=0 gets
     * the refusal text. (Result contents for non-admins depend on further permission claims and are
     * not judged.)
     */
    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:40-49", why = "Admin or CanSearch -> page; otherwise Response.Write('You are not allowed to use this page.') + Response.End")
    @SourceRef(value = "src/BugTracker.Web/Security/Security.cs:74", why = "CanSearch claim = og_can_search == 1")
    @SourceRef(value = "src/BugTracker.Web/setup.sql:90-108", why = "og_can_search default 1")
    @SourceRef(value = "src/BugTracker.Web/search.aspx:217-335", why = "form fields like, and_or ...")
    void searchPageDependsOnOrgSearchPermissionForNonAdmins() {
        seedBugs();
        insertLoginableUser("judge_can", 0, 1);
        Db.exec("insert into orgs (og_name, og_can_search) values (N'judge nosearch', 0)");
        int org = ((Number) Db.scalar("select og_id from orgs where og_name = N'judge nosearch'")).intValue();
        insertLoginableUser("judge_cannot", 0, org);

        Web can = newSession();
        assertEquals("bugs.aspx", submitLogin(can, "judge_can", "admin").path());
        Web.Page ok = can.get("search.aspx");
        assertEquals(200, ok.status());
        Form f = Form.containing(can, ok, "like");
        assertEquals("and", f.get("and_or"));

        Web cannot = newSession();
        assertEquals("bugs.aspx", submitLogin(cannot, "judge_cannot", "admin").path());
        Web.Page no = cannot.get("search.aspx");
        assertEquals(200, no.status());
        assertTrue(Norm.text(no.html().text()).contains("You are not allowed to use this page."), no.text());
        assertTrue(no.html().select("[name]").stream().noneMatch(e -> Form.logicalName(e.attr("name")).equals("like")),
                "no search form for an org without search permission");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:312-327", why = "desc_clause: bg_short_desc like N'%...%' (DB collation: case-insensitive)")
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:402-607", why = "built-in select (SearchSQL unset), order by bg_id desc")
    @SourceRef(value = "src/BugTracker.Web/Web.config:540-555", why = "SearchSQL only inside a comment")
    @SourceRef(value = "src/BugTracker.Web/Web.config:230,250,486,747", why = "JustDateFormat yyyy-MM-dd, no udf column, usernames not full names, no tags column")
    @SourceRef(value = "src/BugTracker.Web/search.aspx:400-403", why = "admin -> mass-edit form -> display_bugs(true): leading checkbox cell")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug_list.cs:665-860", why = "row: checkbox cell, color column skipped, '' -> &nbsp;, desc links edit_bug.aspx?id=N")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug_list.cs:42-51,877", why = "'N bugs returned by query'")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:273-310", why = "midnight -> JustDateFormat")
    void descriptionSearchIsCaseInsensitiveNewestFirst() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = search(Map.of("like", "alpha"), Map.of());
        assertEquals(List.of(4, 1), resultIds(p.html()));
        assertEquals(BUG1, resultRow(p.html(), 1));
        assertTrue(Norm.text(p.html().text()).contains("2 bugs returned by query"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:312-315", why = "' -> '', [ -> [[], % -> [%], _ -> [_]")
    void likeEscapesSqlWildcards() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = search(Map.of("like", "50%"), Map.of());
        assertEquals(List.of(2), resultIds(p.html()));
        Web.Page q = search(Map.of("like", "discount_50"), Map.of());
        assertEquals(List.of(), resultIds(q.html()));
        Web.Page r = search(Map.of("like", "o'brien"), Map.of());
        assertEquals(200, r.status(), "quote must not break the SQL");
        assertEquals(List.of(), resultIds(r.html()));
    }

    @Test
    @Inherited("A time of exactly 12:00:00 (noon) is treated like midnight and shown as a date only")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:302-305", why = "(Hour == 0 || Hour == 12) && Minute == 0 && Second == 0 -> JustDateFormat")
    void noonTimestampIsShownAsDateOnly() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = search(Map.of("like", "beta"), Map.of());
        assertEquals(BUG2, resultRow(p.html(), 2));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:171-194", why = "build_where joins clauses with 'and ' or 'or ' per radio")
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:198-224", why = "listbox -> column in (...)")
    @SourceRef(value = "src/BugTracker.Web/btnet/util.cs:443-463", why = "permission clause wraps the whole where in parentheses")
    void orLogicUnionsCriteria() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = search(Map.of("like", "gamma", "and_or", "or"), Map.of("status", List.of("1")));
        assertEquals(List.of(4, 3, 1), resultIds(p.html()));
        Web.Page q = search(Map.of("like", "gamma"), Map.of("status", List.of("1")));
        assertEquals(List.of(), resultIds(q.html()), "same criteria with \"and\" logic");
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:198-224,285-299", why = "category / priority / assigned_to listboxes")
    @SourceRef(value = "src/BugTracker.Web/search.aspx.cs:898-902", why = "[not assigned] has value 0")
    @SourceRef(value = "src/BugTracker.Web/btnet/bug.cs:835-849", why = "unassigned bugs are stored with bg_assigned_to_user = 0")
    void listboxCriteriaWithAndLogic() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = search(Map.of(), Map.of("category", List.of("1"), "priority", List.of("2")));
        assertEquals(List.of(4), resultIds(p.html()));
        Web.Page q = search(Map.of(), Map.of("assigned_to", List.of("0")));
        assertEquals(List.of(4, 2), resultIds(q.html()));
        Web.Page r = search(Map.of(), Map.of("project", List.of("1"), "priority", List.of("1", "3")));
        assertEquals(List.of(3, 1), resultIds(r.html()));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search.aspx:429-433", why = "empty result -> '<p>No ' + PluralBugLabel")
    @SourceRef(value = "src/BugTracker.Web/Web.config:462", why = "PluralBugLabel = bugs")
    void noMatchSaysNoBugs() {
        seedBugs();
        loginAdmin(web);
        Web.Page p = search(Map.of("like", "zzz-nothing"), Map.of());
        assertEquals(200, p.status());
        assertEquals(List.of(), resultIds(p.html()));
        // search.aspx:430-432 writes "<p>No " + PluralBugLabel + "<p>": a bare <p> whose own text is exactly "No bugs"
        assertTrue(p.html().select("p").stream().anyMatch(e -> Norm.text(e.ownText()).equals("No bugs")),
                "no <p> reading exactly 'No bugs'");
        assertFalse(Norm.text(p.html().text()).contains("returned by query"));
    }

    @Test
    @SourceRef(value = "src/BugTracker.Web/search_text.aspx.cs:17-27", why = "empty query -> exception message")
    @SourceRef(value = "src/BugTracker.Web/search_text.aspx.cs:35-60", why = "display_exception writes message and ends the response before Elasticsearch is called")
    void textSearchWithoutQueryShowsMessage() {
        loginAdmin(web);
        for (String path : new String[] {"search_text.aspx?query=", "search_text.aspx"}) {
            Web.Page p = web.get(path);
            assertEquals(200, p.status(), path);
            assertTrue(Norm.text(p.html().text()).contains("You forgot to enter something to search for..."), path);
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Four bugs written the way bug.cs:823-849 writes them (assigned 0 when
     * unassigned, last-updated set). Times are midnight or noon so the rendered
     * date never depends on the server culture (AM/PM designator).
     */
    private static void seedBugs() {
        insertBug("alpha login fails", 1, "2020-01-15T00:00:00", 1, "2020-01-15T00:00:00", 1, 1, 1, 1, 1, 2);
        insertBug("beta discount 50% off", 3, "2020-02-01T12:00:00", 1, "2020-02-02T00:00:00", 2, 2, 2, 2, 2, 0);
        insertBug("gamma discount 50 items", 2, "2020-03-10T00:00:00", 2, "2020-03-10T00:00:00", 1, 3, 3, 3, 5, 3);
        insertBug("Alpha uppercase", 1, "2020-04-01T00:00:00", 1, "2020-04-01T00:00:00", 2, 1, 1, 2, 1, 0);
    }

    private static void insertBug(String desc, int reporter, String reported, int lastUser, String lastDate,
                                  int project, int org, int category, int priority, int status, int assigned) {
        Db.exec("insert into bugs (bg_short_desc, bg_reported_user, bg_reported_date, bg_last_updated_user,"
                + " bg_last_updated_date, bg_project, bg_org, bg_category, bg_priority, bg_status, bg_assigned_to_user)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                desc, reporter, reported, lastUser, lastDate, project, org, category, priority, status, assigned);
    }

    /**
     * Fresh search.aspx GET, then a postback the way the Search button does it:
     * its onclick sets hit_submit_button = "1" (search.aspx:140-141,342-344).
     */
    private Web.Page search(Map<String, String> fields, Map<String, List<String>> lists) {
        Form f = Form.containing(web, web.get("search.aspx"), "like");
        if (f.has("hit_submit_button")) f.set("hit_submit_button", "1");
        for (var e : fields.entrySet()) f.set(e.getKey(), e.getValue());
        for (var e : lists.entrySet()) f.setAll(e.getKey(), e.getValue());
        Web.Page p = f.submit(null);
        assertEquals(200, p.status());
        return p;
    }

    /** Bug ids of result rows, in display order: links to edit_bug.aspx?id=N (bug_list.cs:808-813). */
    private static List<Integer> resultIds(Document d) {
        return new ArrayList<>(resultLinks(d).keySet());
    }

    private static Map<Integer, Element> resultLinks(Document d) {
        Map<Integer, Element> out = new LinkedHashMap<>();
        for (Element a : d.select("a[href]")) {
            URI u;
            try { u = URI.create(a.absUrl("href")); } catch (IllegalArgumentException e) { continue; }
            if (!"edit_bug.aspx".equals(Norm.relPath(u))) continue;
            String id = Norm.query(u, "id");
            if (id == null || !id.matches("\\d+")) continue;
            out.putIfAbsent(Integer.valueOf(id), a);
        }
        return out;
    }

    /** Normalized cell texts of the table row holding the result link of bug {@code id}. */
    private static List<String> resultRow(Document d, int id) {
        Element a = resultLinks(d).get(id);
        if (a == null) throw new AssertionError("no result row for bug " + id);
        Element tr = a.closest("tr");
        List<String> row = new ArrayList<>();
        for (Element c : tr.children()) if (c.tagName().equals("td") || c.tagName().equals("th")) row.add(Norm.text(c.text()));
        return row;
    }
}
