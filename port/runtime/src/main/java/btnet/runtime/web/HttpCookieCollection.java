package btnet.runtime.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * .NET {@code System.Web.HttpCookieCollection} ({@code Request.Cookies},
 * {@code Response.Cookies}). Lookup by name is case-insensitive; {@code get} of an
 * absent name returns {@code null} on the request collection and <b>creates and adds</b>
 * a new cookie on the response collection ({@code Response.Cookies["user"]["name"] = ""},
 * {@code default.aspx.cs:80}). As in .NET, a cookie added to {@code Response.Cookies}
 * also becomes visible in {@code Request.Cookies}.
 */
public final class HttpCookieCollection implements Iterable<HttpCookie> {
    private final List<HttpCookie> cookies = new ArrayList<>();
    private final boolean responseCollection;
    private final HttpRequest request; // for response → request sync; may be null

    HttpCookieCollection(boolean responseCollection, HttpRequest request) {
        this.responseCollection = responseCollection;
        this.request = request;
    }

    /** {@code Cookies["name"]}. */
    public HttpCookie get(String name) {
        for (HttpCookie c : cookies) {
            if (same(c.Name(), name)) return c;
        }
        if (!responseCollection) return null;
        HttpCookie c = new HttpCookie(name);
        Add(c);
        return c;
    }

    /** {@code Cookies[index]}. */
    public HttpCookie get(int index) {
        return cookies.get(index);
    }

    /** .NET {@code Add}: appends (duplicates allowed). */
    public void Add(HttpCookie cookie) {
        cookies.add(cookie);
        syncToRequest(cookie);
    }

    /** .NET {@code Set}: replaces the first cookie of that name, else adds. */
    public void Set(HttpCookie cookie) {
        for (int i = 0; i < cookies.size(); i++) {
            if (same(cookies.get(i).Name(), cookie.Name())) {
                cookies.set(i, cookie);
                for (int j = cookies.size() - 1; j > i; j--) {
                    if (same(cookies.get(j).Name(), cookie.Name())) cookies.remove(j);
                }
                syncToRequest(cookie);
                return;
            }
        }
        Add(cookie);
    }

    public void Remove(String name) {
        cookies.removeIf(c -> same(c.Name(), name));
    }

    public void Clear() {
        cookies.clear();
    }

    public int Count() {
        return cookies.size();
    }

    public String[] AllKeys() {
        String[] keys = new String[cookies.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = cookies.get(i).Name();
        return keys;
    }

    @Override
    public Iterator<HttpCookie> iterator() {
        return new ArrayList<>(cookies).iterator();
    }

    /** Adds without response → request syncing (request parsing). */
    void addRaw(HttpCookie c) {
        cookies.add(c);
    }

    private void syncToRequest(HttpCookie cookie) {
        if (!responseCollection || request == null) return;
        try {
            request.cookiesNoValidate().replaceFromResponse(cookie);
        } catch (RuntimeException e) {
            // request not available (startup / detached response): nothing to sync
        }
    }

    void replaceFromResponse(HttpCookie cookie) {
        for (int i = 0; i < cookies.size(); i++) {
            if (same(cookies.get(i).Name(), cookie.Name())) {
                cookies.set(i, cookie);
                return;
            }
        }
        cookies.add(cookie);
    }

    private static boolean same(String a, String b) {
        if (a == null || b == null) return a == null && b == null;
        return HttpApplicationState.fold(a).equals(HttpApplicationState.fold(b));
    }
}
