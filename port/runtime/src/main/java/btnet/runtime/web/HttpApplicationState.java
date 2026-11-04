package btnet.runtime.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * .NET {@code System.Web.HttpApplicationState} ({@code Application[k]}, RULEBOOK §2A,
 * §3 [C10, C32]). One process-wide instance, {@link ApplicationState#get()}.
 * <ul>
 * <li>Keys are case-insensitive (NameObjectCollectionBase with the invariant
 * ignore-case comparer), insertion order kept for {@link #AllKeys()}.</li>
 * <li>{@code get(absent)} → {@code null}; {@code set(k, null)} removes the key.</li>
 * <li>{@link #Lock()}/{@link #UnLock()} = one {@link ReentrantLock}; writes take the same
 * lock, so a holder of {@code Lock()} excludes other writers (as .NET's writer lock).</li>
 * </ul>
 */
public final class HttpApplicationState {
    private static final class Entry {
        final String key;
        final Object value;

        Entry(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    private final Map<String, Entry> map = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    HttpApplicationState() {
    }

    static String fold(String key) {
        if (key == null) return null;
        char[] cs = key.toCharArray();
        for (int i = 0; i < cs.length; i++) cs[i] = Character.toLowerCase(cs[i]);
        return new String(cs);
    }

    /** {@code Application[key]}; {@code null} when absent. */
    public Object get(String key) {
        synchronized (map) {
            Entry e = map.get(fold(key));
            return e == null ? null : e.value;
        }
    }

    /** {@code Application[index]} (insertion order). */
    public Object get(int index) {
        synchronized (map) {
            return new ArrayList<>(map.values()).get(index).value;
        }
    }

    /** {@code Application[key] = value}; {@code null} removes. */
    public void set(String key, Object value) {
        lock.lock();
        try {
            synchronized (map) {
                String f = fold(key);
                if (value == null) {
                    map.remove(f);
                } else {
                    Entry old = map.get(f);
                    map.put(f, new Entry(old != null ? old.key : key, value));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** .NET {@code Add}: same as the indexer set for an existing key in this port's model. */
    public void Add(String key, Object value) {
        set(key, value);
    }

    public void Remove(String key) {
        set(key, null);
    }

    public void Clear() {
        lock.lock();
        try {
            synchronized (map) {
                map.clear();
            }
        } finally {
            lock.unlock();
        }
    }

    public void RemoveAll() {
        Clear();
    }

    public int Count() {
        synchronized (map) {
            return map.size();
        }
    }

    public String[] AllKeys() {
        synchronized (map) {
            List<String> keys = new ArrayList<>();
            for (Entry e : map.values()) keys.add(e.key);
            return keys.toArray(new String[0]);
        }
    }

    public void Lock() {
        lock.lock();
    }

    public void UnLock() {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}
