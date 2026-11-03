package judge.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Normalization rules. Every rule here is a place where the judge deliberately
 * stops looking — so each one is listed in judge/README.md and must be justified
 * there. Adding a rule is a judge change and goes through review.
 *
 * <p>The UI layer is being redesigned (WebForms -> Thymeleaf), so the judge
 * compares <b>data</b>, never markup: visible text of elements addressed by id,
 * table cell text, field values, redirect targets, HTTP status, DB rows.
 */
public final class Norm {
    private Norm() {}

    private static final Pattern WS = Pattern.compile("[\\s\\u00a0]+");

    /** N1: collapse runs of whitespace (incl. &nbsp;) to one space and trim. */
    public static String text(String s) {
        return s == null ? null : WS.matcher(s).replaceAll(" ").strip();
    }

    /** Visible text of the element with this id; fails if absent. */
    public static String textById(Document d, String id) {
        Element e = d.getElementById(id);
        if (e == null) throw new AssertionError("No element #" + id + " in " + d.location());
        return text(e.text());
    }

    /**
     * N4: visible text of a server control by its logical id. The original's
     * Web.config sets pages clientIDMode="AutoID" (src/BugTracker.Web/Web.config:38),
     * so {@code id="msg"} inside a master-page content area renders as
     * {@code ctl00_body_msg} unless the page opts into Static; the port renders
     * {@code msg}. Tries the exact id first, then {@code ctl00_body_<id>}.
     */
    public static String textByLogicalId(Document d, String id) {
        Element e = d.getElementById(id);
        if (e == null) e = d.getElementById("ctl00_body_" + id);
        if (e == null) throw new AssertionError("No element #" + id + " (or #ctl00_body_" + id + ") in " + d.location());
        return text(e.text());
    }

    /**
     * N2: a data table as rows of normalized cell text. Header row(s) — rows of
     * {@code th} only — are returned first. Markup inside cells (links, icons,
     * spans) is reduced to its text.
     */
    public static List<List<String>> table(Element table) {
        List<List<String>> out = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            if (tr.closest("table") != table) continue; // skip nested tables
            List<String> row = new ArrayList<>();
            for (Element c : tr.children()) {
                if (c.tagName().equals("td") || c.tagName().equals("th")) row.add(text(c.text()));
            }
            if (!row.isEmpty()) out.add(row);
        }
        return out;
    }

    public static List<List<String>> tableById(Document d, String id) {
        Element t = d.getElementById(id);
        if (t == null) throw new AssertionError("No table #" + id + " in " + d.location());
        if (!t.tagName().equals("table")) {
            t = t.selectFirst("table");
            if (t == null) throw new AssertionError("#" + id + " contains no table");
        }
        return table(t);
    }

    /**
     * N3: path relative to the base URL, lower-cased, query dropped. The original
     * runs on case-insensitive IIS; "Bugs.aspx" and "bugs.aspx" are one page.
     */
    public static String relPath(URI u) {
        String base = JudgeConfig.baseUrl().getPath();
        String p = u.getPath() == null ? "" : u.getPath();
        if (p.startsWith(base)) p = p.substring(base.length());
        else if (p.startsWith("/")) p = p.substring(1);
        return p.toLowerCase();
    }

    /** Query parameter value from a URI (first occurrence), or null. */
    public static String query(URI u, String key) {
        String q = u.getRawQuery();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            String k = i < 0 ? kv : kv.substring(0, i);
            if (k.equalsIgnoreCase(key)) {
                return i < 0 ? "" : java.net.URLDecoder.decode(kv.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
