package btnet.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Stands for .NET {@code System.Collections.Specialized.StringDictionary} [J-C4]: keys lower-cased with
 * {@code Locale.ROOT} inside every member; {@code get(absent)} → {@code null}; {@code set(k, null)} stores null.
 * A null key → ArgumentNullException("key").
 */
public class StringDictionary {
    private final LinkedHashMap<String, String> map = new LinkedHashMap<>();

    public StringDictionary() {
    }

    private static String key(String k) {
        if (k == null) {
            throw new ArgumentNullException("key");
        }
        return k.toLowerCase(Locale.ROOT);
    }

    public String get(String key) {
        return map.get(key(key));
    }

    public void set(String key, String value) {
        map.put(key(key), value);
    }

    /** .NET {@code Add} (Hashtable.Add): present → ArgumentException("Item has already been added. …"). */
    public void Add(String key, String value) {
        String k = key(key);
        if (map.containsKey(k)) {
            throw new ArgumentException("Item has already been added. Key in dictionary: '" + k
                    + "'  Key being added: '" + k + "'");
        }
        map.put(k, value);
    }

    public boolean ContainsKey(String key) {
        return map.containsKey(key(key));
    }

    public boolean ContainsValue(String value) {
        return map.containsValue(value);
    }

    public void Remove(String key) {
        map.remove(key(key));
    }

    public int Count() {
        return map.size();
    }

    /** .NET {@code Keys} (lower-cased). */
    public List<String> Keys() {
        return new ArrayList<>(map.keySet());
    }

    public List<String> Values() {
        return new ArrayList<>(map.values());
    }

    public void Clear() {
        map.clear();
    }
}
