package btnet.runtime.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * .NET {@code System.Web.HttpCookie}: {@code Name}, {@code Value}, sub-keys
 * ({@code cookie["k"]} → {@code get("k")} / {@code set("k", v)}), {@code Expires},
 * {@code Path} (default {@code "/"}), {@code Domain}, {@code Secure}, {@code HttpOnly}.
 *
 * <p>Sub-keys follow .NET's {@code HttpValueCollection.FillFromString(value, false)}: the
 * value is split on {@code &}, each part {@code k=v} or (no {@code =}) a value under the
 * {@code null} key; no URL decoding. Setting a sub-key rewrites {@link #Value()} as
 * {@code k1=v1&k2=v2} ({@code default.aspx.cs:80-81} → {@code name=&NTLM=0}).
 */
public class HttpCookie {
    private static final DateTimeFormatter EXPIRES =
        DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss 'GMT'", Locale.US);

    private String name;
    private String value;
    private List<String[]> subkeys; // null until a sub-key is read or written
    private String path = "/";
    private String domain;
    private LocalDateTime expires; // null = DateTime.MinValue (session cookie)
    private boolean secure;
    private boolean httpOnly;

    public HttpCookie() {
    }

    public HttpCookie(String name) {
        this.name = name;
    }

    public HttpCookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String Name() {
        return name;
    }

    public HttpCookie Name(String v) {
        this.name = v;
        return this;
    }

    /** .NET {@code Value}: the sub-keys serialized when any were set, else the raw value. */
    public String Value() {
        if (subkeys != null) return serialize();
        return value;
    }

    public HttpCookie Value(String v) {
        this.value = v;
        this.subkeys = null;
        return this;
    }

    /** .NET {@code HasKeys}. */
    public boolean HasKeys() {
        List<String[]> l = parsed();
        for (String[] kv : l) {
            if (kv[0] != null) return true;
        }
        return false;
    }

    /** Sub-key read {@code cookie["k"]}; values of a repeated key are comma-joined; absent → {@code null}. */
    public String get(String key) {
        StringBuilder sb = null;
        for (String[] kv : parsed()) {
            if (keyEquals(kv[0], key)) {
                if (sb == null) sb = new StringBuilder(kv[1] == null ? "" : kv[1]);
                else sb.append(',').append(kv[1] == null ? "" : kv[1]);
            }
        }
        return sb == null ? null : sb.toString();
    }

    /** Sub-key write {@code cookie["k"] = v} (replaces every value of {@code k}). */
    public HttpCookie set(String key, String v) {
        List<String[]> l = new ArrayList<>(parsed());
        int at = -1;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (keyEquals(l.get(i)[0], key)) {
                at = i;
                l.remove(i);
            }
        }
        if (at < 0) l.add(new String[] {key, v});
        else l.add(at, new String[] {key, v});
        subkeys = l;
        return this;
    }

    private static boolean keyEquals(String a, String b) {
        if (a == null || b == null) return a == null && b == null;
        return HttpApplicationState.fold(a).equals(HttpApplicationState.fold(b));
    }

    private List<String[]> parsed() {
        if (subkeys != null) return subkeys;
        List<String[]> l = new ArrayList<>();
        String s = value;
        if (s != null && !s.isEmpty()) {
            int n = s.length();
            int i = 0;
            while (i < n) {
                int si = i, ti = -1;
                while (i < n) {
                    char ch = s.charAt(i);
                    if (ch == '=') {
                        if (ti < 0) ti = i;
                    } else if (ch == '&') {
                        break;
                    }
                    i++;
                }
                if (ti >= 0) l.add(new String[] {s.substring(si, ti), s.substring(ti + 1, i)});
                else l.add(new String[] {null, s.substring(si, i)});
                if (i == n - 1 && s.charAt(i) == '&') l.add(new String[] {null, ""});
                i++;
            }
        }
        subkeys = l;
        return l;
    }

    private String serialize() {
        StringBuilder sb = new StringBuilder();
        for (String[] kv : subkeys) {
            if (sb.length() > 0) sb.append('&');
            if (kv[0] != null) sb.append(kv[0]).append('=');
            if (kv[1] != null) sb.append(kv[1]);
        }
        return sb.toString();
    }

    public String Path() {
        return path;
    }

    public HttpCookie Path(String v) {
        this.path = v;
        return this;
    }

    public String Domain() {
        return domain;
    }

    public HttpCookie Domain(String v) {
        this.domain = v;
        return this;
    }

    /** {@code null} stands for .NET {@code DateTime.MinValue} (no {@code expires} attribute). */
    public LocalDateTime Expires() {
        return expires;
    }

    public HttpCookie Expires(LocalDateTime v) {
        this.expires = v;
        return this;
    }

    public boolean Secure() {
        return secure;
    }

    public HttpCookie Secure(boolean v) {
        this.secure = v;
        return this;
    }

    public boolean HttpOnly() {
        return httpOnly;
    }

    public HttpCookie HttpOnly(boolean v) {
        this.httpOnly = v;
        return this;
    }

    /**
     * The {@code Set-Cookie} header value, as .NET {@code HttpCookie.GetSetCookieHeader}:
     * {@code name=value; domain=…; expires=ddd, dd-MMM-yyyy HH:mm:ss GMT; path=/; secure; HttpOnly}.
     * {@code Expires} is a local time converted to UTC.
     */
    public String toSetCookieHeader() {
        StringBuilder sb = new StringBuilder();
        if (!Objects.toString(name, "").isEmpty()) sb.append(name).append('=');
        String v = Value();
        if (v != null) sb.append(v);
        if (domain != null && !domain.isEmpty()) sb.append("; domain=").append(domain);
        if (expires != null) {
            sb.append("; expires=").append(EXPIRES.format(
                expires.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)));
        }
        if (path != null && !path.isEmpty()) sb.append("; path=").append(path);
        if (secure) sb.append("; secure");
        if (httpOnly) sb.append("; HttpOnly");
        return sb.toString();
    }

    /** Parses one request {@code Cookie} header pair ({@code name=value}, no decoding). */
    static HttpCookie fromRequestPair(String pair) {
        int eq = pair.indexOf('=');
        if (eq < 0) return new HttpCookie("", pair.trim());
        return new HttpCookie(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
    }
}
