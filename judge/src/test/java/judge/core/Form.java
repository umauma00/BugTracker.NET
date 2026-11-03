package judge.core;

import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Fills and submits an HTML form the way a browser would, addressing fields by
 * <b>logical name</b> so the same scenario drives both implementations.
 *
 * <p>Logical name = the part of the {@code name} attribute after the last
 * {@code '$'}. WebForms renders {@code <input id="short_desc" runat="server">}
 * inside a master page as {@code name="ctl00$body$short_desc"}; the port renders
 * {@code name="short_desc"}. Both have logical name {@code short_desc}.
 *
 * <p>Every successful control already in the form (hidden fields such as
 * {@code __VIEWSTATE}/{@code __EVENTVALIDATION} or a CSRF token, checked boxes,
 * selected options, text values) is submitted unchanged unless overridden — the
 * judge never needs to know which plumbing fields an implementation uses.
 */
public final class Form {

    private final Web web;
    private final URI action;
    private final String enctype;
    private final boolean getMethod;
    /** Real field name -> value, in document order. Multi-valued fields keep a list. */
    private final Map<String, List<String>> values = new LinkedHashMap<>();
    /** Logical name -> real name. */
    private final Map<String, String> logical = new LinkedHashMap<>();
    /** Submit buttons by logical name -> (real name, value). */
    private final Map<String, Map.Entry<String, String>> buttons = new LinkedHashMap<>();
    private final Map<String, Map.Entry<String, byte[]>> files = new LinkedHashMap<>();
    private final Map<String, String> fileTypes = new LinkedHashMap<>();
    /** Image buttons submit name.x / name.y instead of name=value. */
    private final java.util.Set<String> imageButtons = new java.util.HashSet<>();

    private Form(Web web, Element form, URI pageUri) {
        this.web = web;
        String a = form.attr("action");
        this.action = a.isBlank() ? pageUri : pageUri.resolve(a);
        this.enctype = form.attr("enctype").toLowerCase();
        this.getMethod = form.attr("method").equalsIgnoreCase("get");
        for (Element el : form.select("input, select, textarea, button")) {
            String name = el.attr("name");
            if (name.isBlank() || el.hasAttr("disabled")) continue;
            logical.putIfAbsent(logicalName(name), name);
            String tag = el.tagName();
            String type = el.attr("type").toLowerCase();
            if (tag.equals("button") || type.equals("submit") || type.equals("image")) {
                String v = tag.equals("button") ? el.attr("value") : el.val();
                buttons.putIfAbsent(logicalName(name), new SimpleEntry<>(name, v));
                if (type.equals("image")) imageButtons.add(name);
                continue;
            }
            if (type.equals("file") || type.equals("reset")) continue;
            if (type.equals("checkbox") || type.equals("radio")) {
                values.computeIfAbsent(name, k -> new ArrayList<>());
                if (el.hasAttr("checked")) values.get(name).add(el.hasAttr("value") ? el.val() : "on");
                continue;
            }
            if (tag.equals("select")) {
                List<String> sel = new ArrayList<>();
                for (Element o : el.select("option[selected]")) sel.add(o.hasAttr("value") ? o.attr("value") : o.text());
                if (sel.isEmpty() && !el.hasAttr("multiple")) {
                    Element first = el.selectFirst("option");
                    if (first != null) sel.add(first.hasAttr("value") ? first.attr("value") : first.text());
                }
                values.put(name, sel);
                continue;
            }
            values.computeIfAbsent(name, k -> new ArrayList<>()).add(tag.equals("textarea") ? el.wholeText() : el.val());
        }
    }

    /** The first form on the page (WebForms pages have exactly one). */
    public static Form first(Web web, Web.Page page) {
        Document d = page.html();
        Element f = d.selectFirst("form");
        if (f == null) throw new AssertionError("No <form> on " + page.uri());
        return new Form(web, f, page.uri());
    }

    /** The form containing the field with this logical name. */
    public static Form containing(Web web, Web.Page page, String logicalField) {
        for (Element f : page.html().select("form")) {
            for (Element el : f.select("[name]")) {
                if (logicalName(el.attr("name")).equals(logicalField)) return new Form(web, f, page.uri());
            }
        }
        throw new AssertionError("No form with field '" + logicalField + "' on " + page.uri());
    }

    public static String logicalName(String realName) {
        int i = realName.lastIndexOf('$');
        return i < 0 ? realName : realName.substring(i + 1);
    }

    public boolean has(String logicalField) { return logical.containsKey(logicalField); }

    public String get(String logicalField) {
        List<String> v = values.get(real(logicalField));
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    public List<String> getAll(String logicalField) {
        return List.copyOf(values.getOrDefault(real(logicalField), List.of()));
    }

    /** Set a text/hidden/textarea/single-select value. */
    public Form set(String logicalField, String value) {
        values.put(real(logicalField), new ArrayList<>(List.of(value)));
        return this;
    }

    /** Check or uncheck a checkbox (value as rendered, default "on"). */
    public Form check(String logicalField, boolean on) {
        String n = real(logicalField);
        values.put(n, on ? new ArrayList<>(List.of("on")) : new ArrayList<>());
        return this;
    }

    public Form setAll(String logicalField, List<String> vals) {
        values.put(real(logicalField), new ArrayList<>(vals));
        return this;
    }

    public Form file(String logicalField, String filename, byte[] content) {
        files.put(real(logicalField), new SimpleEntry<>(filename, content));
        fileTypes.remove(real(logicalField));
        return this;
    }

    /** As {@link #file(String, String, byte[])} with an explicit multipart part Content-Type. */
    public Form file(String logicalField, String filename, byte[] content, String contentType) {
        file(logicalField, filename, content);
        fileTypes.put(real(logicalField), contentType);
        return this;
    }

    /** Submit, optionally naming the submit button by logical name (null = no button). */
    public Web.Page submit(String logicalButton) {
        List<Map.Entry<String, String>> fields = new ArrayList<>();
        for (var e : values.entrySet()) for (String v : e.getValue()) fields.add(new SimpleEntry<>(e.getKey(), v));
        if (logicalButton != null) {
            var b = buttons.get(logicalButton);
            if (b == null) throw new AssertionError("No submit button '" + logicalButton + "'; have " + buttons.keySet());
            if (imageButtons.contains(b.getKey())) {
                fields.add(new SimpleEntry<>(b.getKey() + ".x", "0"));
                fields.add(new SimpleEntry<>(b.getKey() + ".y", "0"));
            } else {
                fields.add(new SimpleEntry<>(b.getKey(), b.getValue()));
            }
        }
        if (getMethod) {
            StringBuilder q = new StringBuilder();
            for (var f : fields) {
                q.append(q.length() == 0 ? "" : "&")
                        .append(java.net.URLEncoder.encode(f.getKey(), java.nio.charset.StandardCharsets.UTF_8)).append('=')
                        .append(java.net.URLEncoder.encode(f.getValue() == null ? "" : f.getValue(), java.nio.charset.StandardCharsets.UTF_8));
            }
            String base = action.toString();
            int h = base.indexOf('#');
            if (h >= 0) base = base.substring(0, h);
            int qm = base.indexOf('?');
            if (qm >= 0) base = base.substring(0, qm); // browsers replace the action's query on GET submit
            return web.get(URI.create(q.length() == 0 ? base : base + "?" + q));
        }
        if (!files.isEmpty() || enctype.contains("multipart")) return web.postMultipart(action, fields, files, fileTypes);
        return web.postForm(action, fields);
    }

    private String real(String logicalField) {
        String n = logical.get(logicalField);
        if (n == null) throw new AssertionError("No field '" + logicalField + "'; have " + logical.keySet());
        return n;
    }
}
