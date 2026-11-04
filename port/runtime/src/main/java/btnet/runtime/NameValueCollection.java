package btnet.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stands for .NET {@code System.Collections.Specialized.NameValueCollection}: case-insensitive (invariant) keys
 * in insertion order, several values per key; {@link #get(String)} joins them with {@code ","}, {@code null} if
 * the key is absent or has no values. Iterating yields the keys (as .NET {@code foreach}).
 */
public class NameValueCollection implements Iterable<String> {

    private static final class Entry {
        final String key;
        final List<String> values = new ArrayList<>(1);

        Entry(String key) {
            this.key = key;
        }
    }

    private final LinkedHashMap<String, Entry> map = new LinkedHashMap<>();

    public NameValueCollection() {
    }

    /** Copy constructor. */
    public NameValueCollection(NameValueCollection other) {
        for (Entry e : other.map.values()) {
            Entry c = new Entry(e.key);
            c.values.addAll(e.values);
            map.put(norm(e.key), c);
        }
    }

    private static String norm(String k) {
        return k == null ? null : Strings.ToLower(Strings.ToUpper(k));
    }

    /** .NET {@code nvc[name]} read. */
    public String get(String name) {
        Entry e = map.get(norm(name));
        return e == null ? null : join(e.values);
    }

    /** .NET {@code nvc[index]} read. */
    public String get(int index) {
        return join(entryAt(index).values);
    }

    /** .NET {@code Get(name)}. */
    public String Get(String name) {
        return get(name);
    }

    /** .NET {@code Get(index)}. */
    public String Get(int index) {
        return get(index);
    }

    private static String join(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (values.get(i) != null) {
                sb.append(values.get(i));
            }
        }
        return sb.toString();
    }

    private Entry entryAt(int index) {
        if (index < 0 || index >= map.size()) {
            throw new ArgumentOutOfRangeException("index",
                    "Index was out of range. Must be non-negative and less than the size of the collection.");
        }
        Iterator<Entry> it = map.values().iterator();
        for (int i = 0; i < index; i++) {
            it.next();
        }
        return it.next();
    }

    /** .NET {@code nvc[name] = value} / {@code Set(name, value)}: replaces all values (a null value is stored). */
    public void set(String name, String value) {
        Entry e = map.computeIfAbsent(norm(name), k -> new Entry(name));
        e.values.clear();
        e.values.add(value);
    }

    public void Set(String name, String value) {
        set(name, value);
    }

    /** .NET {@code Add(name, value)}: appends; a null value creates the key without adding a value. */
    public void Add(String name, String value) {
        Entry e = map.computeIfAbsent(norm(name), k -> new Entry(name));
        if (value != null) {
            e.values.add(value);
        }
    }

    /** .NET {@code Add(NameValueCollection c)}. */
    public void Add(NameValueCollection c) {
        for (Entry e : c.map.values()) {
            if (e.values.isEmpty()) {
                Add(e.key, null);
            }
            for (String v : e.values) {
                Add(e.key, v);
            }
        }
    }

    /** .NET {@code GetValues(name)}: null if absent or empty. */
    public String[] GetValues(String name) {
        Entry e = map.get(norm(name));
        return e == null || e.values.isEmpty() ? null : e.values.toArray(new String[0]);
    }

    public String[] GetValues(int index) {
        Entry e = entryAt(index);
        return e.values.isEmpty() ? null : e.values.toArray(new String[0]);
    }

    /** .NET {@code GetKey(index)}. */
    public String GetKey(int index) {
        return entryAt(index).key;
    }

    /** .NET {@code AllKeys}. */
    public String[] AllKeys() {
        List<String> keys = Keys();
        return keys.toArray(new String[0]);
    }

    /** .NET {@code Keys} (in insertion order, original casing of the first add). */
    public List<String> Keys() {
        List<String> keys = new ArrayList<>(map.size());
        for (Entry e : map.values()) {
            keys.add(e.key);
        }
        return keys;
    }

    /** .NET {@code Count}. */
    public int Count() {
        return map.size();
    }

    /** .NET {@code Remove(name)}. */
    public void Remove(String name) {
        map.remove(norm(name));
    }

    /** .NET {@code Clear()}. */
    public void Clear() {
        map.clear();
    }

    /** .NET {@code HasKeys()}. */
    public boolean HasKeys() {
        return !map.isEmpty();
    }

    @Override
    public Iterator<String> iterator() {
        return Keys().iterator();
    }

    /** Snapshot as an insertion-ordered map of key → comma-joined value. */
    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Entry e : map.values()) {
            m.put(e.key, join(e.values));
        }
        return m;
    }
}
