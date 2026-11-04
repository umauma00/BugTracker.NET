package btnet.runtime;

import java.util.Map;

/**
 * .NET {@code Dictionary<K,V>} members whose Java {@link Map} counterparts differ (RULEBOOK §2A, §3 S1-28/S1-29):
 * the indexer, {@code ContainsKey}, {@code Add}. A {@code null} key always throws
 * {@code ArgumentNullException("Value cannot be null.\r\nParameter name: key")}.
 */
public final class Collections2 {
    private Collections2() {
    }

    private static void checkKey(Object k) {
        if (k == null) {
            throw new ArgumentNullException("key");
        }
    }

    /** {@code d[k]} read: absent → KeyNotFoundException("The given key was not present in the dictionary."). */
    public static <K, V> V indexer(Map<K, V> d, K k) {
        checkKey(k);
        if (!d.containsKey(k)) {
            throw new KeyNotFoundException("The given key was not present in the dictionary.");
        }
        return d.get(k);
    }

    /** {@code d[k] = v}: overwrites. */
    public static <K, V> void set(Map<K, V> d, K k, V v) {
        checkKey(k);
        d.put(k, v);
    }

    /** {@code d.ContainsKey(k)}. */
    public static <K, V> boolean ContainsKey(Map<K, V> d, K k) {
        checkKey(k);
        return d.containsKey(k);
    }

    /** {@code d.Add(k, v)}: present → ArgumentException("An item with the same key has already been added."). */
    public static <K, V> void Add(Map<K, V> d, K k, V v) {
        checkKey(k);
        if (d.containsKey(k)) {
            throw new ArgumentException("An item with the same key has already been added.");
        }
        d.put(k, v);
    }
}
