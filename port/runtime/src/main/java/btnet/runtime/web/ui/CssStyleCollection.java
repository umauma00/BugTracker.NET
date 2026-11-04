package btnet.runtime.web.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code System.Web.UI.CssStyleCollection}: the inline {@code style} of a control. Keys are
 * case-insensitive and keep insertion order; indexer read {@code Style["display"]} →
 * {@code get("display")} ({@code null} when absent), write → {@code set(k, v)} ({@code null} removes,
 * as .NET). {@link #Value()} renders {@code name:value;} per entry, concatenated (.NET form,
 * {@code display:none;}).
 */
public final class CssStyleCollection {
    /** lower-case key → [original key, value]. */
    private final LinkedHashMap<String, String[]> map = new LinkedHashMap<>();

    public CssStyleCollection() {}

    public String get(String key) {
        String[] e = map.get(norm(key));
        return e == null ? null : e[1];
    }

    public void set(String key, String value) {
        if (value == null) { Remove(key); return; }
        String k = norm(key);
        String[] e = map.get(k);
        if (e != null) e[1] = value; else map.put(k, new String[] {key, value});
    }

    public void Add(String key, String value) { set(key, value); }

    public void Remove(String key) { map.remove(norm(key)); }

    public void Clear() { map.clear(); }

    public int Count() { return map.size(); }

    public List<String> Keys() {
        List<String> ks = new ArrayList<>();
        for (String[] e : map.values()) ks.add(e[0]);
        return ks;
    }

    /** {@code CssStyleCollection.Value} getter: {@code k1:v1;k2:v2;}; {@code ""} when empty. */
    public String Value() {
        StringBuilder sb = new StringBuilder();
        for (String[] e : map.values()) sb.append(e[0]).append(':').append(e[1]).append(';');
        return sb.toString();
    }

    /** {@code CssStyleCollection.Value} setter: clears, then parses {@code name: value; …}. */
    public void Value(String style) {
        map.clear();
        if (style == null) return;
        for (String decl : style.split(";")) {
            int colon = decl.indexOf(':');
            if (colon <= 0) continue;
            String k = decl.substring(0, colon).strip();
            String v = decl.substring(colon + 1).strip();
            if (!k.isEmpty()) set(k, v);
        }
    }

    // ---- view state ----

    List<Object> save() {
        List<Object> out = new ArrayList<>();
        for (String[] e : map.values()) out.add(List.of(e[0], e[1]));
        return out;
    }

    void load(Object saved) {
        map.clear();
        if (saved instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof List<?> p && p.size() == 2) set(String.valueOf(p.get(0)), String.valueOf(p.get(1)));
            }
        }
    }

    Map<String, String> asMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] e : map.values()) m.put(e[0], e[1]);
        return m;
    }

    private static String norm(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }
}
