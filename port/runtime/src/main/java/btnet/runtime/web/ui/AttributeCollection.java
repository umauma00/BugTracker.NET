package btnet.runtime.web.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * {@code System.Web.UI.AttributeCollection}: the declared/code-set HTML attributes of a control,
 * rendered (attribute-encoded) on its element. Keys case-insensitive, insertion order kept.
 * {@code x.Attributes["onclick"] = v} → {@code x.Attributes().set("onclick", v)} ({@code null}
 * removes); read → {@code get(k)} ({@code null} when absent). The key {@code style} is routed to
 * {@link #CssStyle()} exactly as in .NET.
 */
public final class AttributeCollection {
    private final LinkedHashMap<String, String[]> map = new LinkedHashMap<>();
    private final CssStyleCollection style;

    public AttributeCollection(CssStyleCollection style) {
        this.style = style;
    }

    public String get(String key) {
        if ("style".equalsIgnoreCase(key)) {
            return style.Count() == 0 ? null : style.Value();
        }
        String[] e = map.get(norm(key));
        return e == null ? null : e[1];
    }

    public void set(String key, String value) {
        if ("style".equalsIgnoreCase(key)) {
            style.Value(value);
            return;
        }
        if (value == null) { Remove(key); return; }
        String k = norm(key);
        String[] e = map.get(k);
        if (e != null) e[1] = value; else map.put(k, new String[] {key, value});
    }

    public void Add(String key, String value) { set(key, value); }

    public void Remove(String key) {
        if ("style".equalsIgnoreCase(key)) { style.Clear(); return; }
        map.remove(norm(key));
    }

    public void Clear() { map.clear(); style.Clear(); }

    /** Number of attributes ({@code style} counts as one when non-empty, as .NET). */
    public int Count() { return map.size() + (style.Count() > 0 && !map.containsKey("style") ? 1 : 0); }

    public List<String> Keys() {
        List<String> ks = new ArrayList<>();
        for (String[] e : map.values()) ks.add(e[0]);
        if (style.Count() > 0) ks.add("style");
        return ks;
    }

    public CssStyleCollection CssStyle() { return style; }

    boolean contains(String key) {
        return map.containsKey(norm(key));
    }

    /** Attributes other than {@code style}, original key case, insertion order. */
    List<String[]> entries() {
        List<String[]> out = new ArrayList<>();
        for (String[] e : map.values()) out.add(new String[] {e[0], e[1]});
        return out;
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

    private static String norm(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }
}
