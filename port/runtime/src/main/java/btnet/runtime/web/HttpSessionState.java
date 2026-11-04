package btnet.runtime.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * .NET {@code System.Web.SessionState.HttpSessionState} (InProc, 120 min,
 * {@code Web.config:30}; RULEBOOK §2A {@code Session["k"]}, §3 [C10]) over the servlet
 * {@link HttpSession}.
 * <ul>
 * <li>Keys case-insensitive (as .NET's {@code SessionStateItemCollection}).</li>
 * <li>{@code get(absent)} → {@code null}; {@code set(k, null)} removes the key.</li>
 * <li>The servlet session is created on the first write or {@link #SessionID()} call
 * and then stays stable (unlike ASP.NET's per-request id before the first write —
 * harmless, judge README {@code primeAspNetSession}).</li>
 * <li>{@link #Abandon()} invalidates the session.</li>
 * </ul>
 */
public final class HttpSessionState {
    /** Session timeout in seconds ({@code Web.config:30} {@code timeout="120"}). */
    public static final int TIMEOUT_SECONDS = 120 * 60;

    private static final String PREFIX = "btnet.session:";

    private static final class Entry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final String key;
        final transient Object value;

        Entry(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    private final HttpServletRequest request;

    HttpSessionState(HttpServletRequest request) {
        this.request = request;
    }

    private HttpSession session(boolean create) {
        HttpSession s = request.getSession(false);
        if (s == null && create) {
            s = request.getSession(true);
            s.setMaxInactiveInterval(TIMEOUT_SECONDS);
        }
        return s;
    }

    public Object get(String key) {
        HttpSession s = session(false);
        if (s == null) return null;
        Object o = s.getAttribute(PREFIX + HttpApplicationState.fold(key));
        return o instanceof Entry e ? e.value : null;
    }

    public void set(String key, Object value) {
        String name = PREFIX + HttpApplicationState.fold(key);
        if (value == null) {
            HttpSession s = session(false);
            if (s != null) s.removeAttribute(name);
            return;
        }
        HttpSession s = session(true);
        Object old = s.getAttribute(name);
        String k = old instanceof Entry e ? e.key : key;
        s.setAttribute(name, new Entry(k, value));
    }

    public void Add(String key, Object value) {
        set(key, value);
    }

    public void Remove(String key) {
        set(key, null);
    }

    public void Clear() {
        HttpSession s = session(false);
        if (s == null) return;
        for (String n : Collections.list(s.getAttributeNames())) {
            if (n.startsWith(PREFIX)) s.removeAttribute(n);
        }
    }

    public void RemoveAll() {
        Clear();
    }

    public int Count() {
        return Keys().size();
    }

    public List<String> Keys() {
        List<String> keys = new ArrayList<>();
        HttpSession s = session(false);
        if (s == null) return keys;
        for (String n : Collections.list(s.getAttributeNames())) {
            if (n.startsWith(PREFIX) && s.getAttribute(n) instanceof Entry e) keys.add(e.key);
        }
        return keys;
    }

    /** .NET {@code Session.SessionID}; creates the session if needed. */
    public String SessionID() {
        return session(true).getId();
    }

    public boolean IsNewSession() {
        HttpSession s = session(false);
        return s == null || s.isNew();
    }

    /** .NET {@code Session.Timeout} in minutes. */
    public int Timeout() {
        HttpSession s = session(false);
        return s == null ? TIMEOUT_SECONDS / 60 : s.getMaxInactiveInterval() / 60;
    }

    /** .NET {@code Session.Abandon()}. */
    public void Abandon() {
        HttpSession s = session(false);
        if (s != null) {
            try {
                s.invalidate();
            } catch (IllegalStateException alreadyInvalid) {
                // already invalidated: .NET Abandon is idempotent
            }
        }
    }
}
