package btnet.runtime.web;

import java.util.concurrent.ConcurrentHashMap;

/**
 * .NET {@code System.Web.Caching.Cache} as used by the source ({@code HttpRuntime.Cache},
 * page {@code Cache}; RULEBOOK §2A, §3 [C10, C14]): a process-wide
 * {@link ConcurrentHashMap}, case-sensitive keys, <b>no expiry</b>.
 * {@code set(k, null)} removes; {@code get(absent)} → {@code null};
 * {@link #addIfAbsent} = .NET {@code Cache.Add} (stores only if absent, returns the
 * existing entry or {@code null}).
 */
public final class Cache {
    private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();

    Cache() {
    }

    public Object get(String key) {
        if (key == null) throw new btnet.runtime.ArgumentNullException("key");
        return map.get(key);
    }

    public void set(String key, Object value) {
        if (key == null) throw new btnet.runtime.ArgumentNullException("key");
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    /** .NET {@code Cache.Insert(key, value[, …])} (the expiry arguments are dropped by translators). */
    public void Insert(String key, Object value) {
        set(key, value);
    }

    /** .NET {@code Cache.Add(key, value, …)}: returns the existing item, or {@code null} if it stored. */
    public Object addIfAbsent(String key, Object value) {
        if (key == null) throw new btnet.runtime.ArgumentNullException("key");
        if (value == null) throw new btnet.runtime.ArgumentNullException("value");
        return map.putIfAbsent(key, value);
    }

    /** .NET {@code Cache.Remove}: returns the removed item or {@code null}. */
    public Object Remove(String key) {
        if (key == null) throw new btnet.runtime.ArgumentNullException("key");
        return map.remove(key);
    }

    public int Count() {
        return map.size();
    }
}
