package btnet.runtime.net;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.UriFormatException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Stands for .NET {@code System.Uri} (absolute URIs only, .NET 4.5 semantics)
 * [S1-21]. {@code HttpRequest.Url()} returns one; {@link #toString()} is .NET
 * {@code Uri.ToString()} (canonical, safely unescaped form), {@link #AbsoluteUri()}
 * the escaped form.
 *
 * <p>Canonicalisation: scheme and host lower-cased, default port omitted, empty
 * path of a hierarchical URI becomes {@code "/"}, dot segments removed, back
 * slashes in http/https/ftp/file paths become {@code /}, characters that are
 * not legal in a URI are percent-encoded as UTF-8 (upper-case hex), and
 * percent-encoded unreserved characters are decoded (.NET 4.5 normalisation).
 *
 * <p>TODO(port): pin the exact set of escapes that .NET 4.5
 * {@code UriFormat.SafeUnescaped} keeps in {@code ToString()} from the reference
 * source; this implementation keeps {@code % # ? / \} and control characters
 * escaped and decodes every other valid UTF-8 escape.
 */
public final class Uri {

    /** .NET {@code Uri.c_MaxUriBufferSize} (0xFFF0). */
    static final int MAX_URI_BUFFER_SIZE = 0xFFF0;

    private static final String HEX = "0123456789ABCDEF";

    private final String originalString;
    private final String scheme;
    private final boolean hierarchical;
    private final String userInfo; // escaped, null if none
    private final String host;     // lower-case, "" for non-hierarchical
    private final int port;        // explicit port, -1 if none
    private final String path;     // escaped
    private final String query;    // escaped, including '?', or ""
    private final String fragment; // escaped, including '#', or ""

    /**
     * .NET {@code new Uri(string)}: the string must be an absolute URI.
     *
     * @throws ArgumentNullException when {@code uriString} is null
     * @throws UriFormatException    when it is relative or malformed
     */
    public Uri(String uriString) {
        if (uriString == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: uriString");
        }
        this.originalString = uriString;
        String s = trimWhite(uriString);
        int colon = schemeEnd(s);
        if (colon < 0) {
            throw new UriFormatException("Invalid URI: The format of the URI could not be determined.");
        }
        this.scheme = s.substring(0, colon).toLowerCase(Locale.ROOT);
        String rest = s.substring(colon + 1);
        boolean slashy = isSlashScheme(scheme);
        if (slashy) {
            rest = rest.replace('\\', '/');
        }
        if (rest.startsWith("//")) {
            hierarchical = true;
            rest = rest.substring(2);
            int end = firstOf(rest, "/?#");
            String authority = end < 0 ? rest : rest.substring(0, end);
            rest = end < 0 ? "" : rest.substring(end);
            String ui = null;
            int at = authority.lastIndexOf('@');
            if (at >= 0) {
                ui = escape(authority.substring(0, at), false);
                authority = authority.substring(at + 1);
            }
            String h = authority;
            int p = -1;
            int pc = authority.startsWith("[") ? authority.indexOf(':', Math.max(0, authority.indexOf(']'))) : authority.lastIndexOf(':');
            if (pc >= 0) {
                h = authority.substring(0, pc);
                String ps = authority.substring(pc + 1);
                if (!ps.isEmpty()) {
                    if (ps.length() > 5 || !ps.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
                        throw new UriFormatException("Invalid URI: Invalid port specified.");
                    }
                    p = Integer.parseInt(ps);
                    if (p > 65535) {
                        throw new UriFormatException("Invalid URI: Invalid port specified.");
                    }
                }
            }
            if (!scheme.equals("file") && (h.isEmpty() || !validHost(h))) {
                throw new UriFormatException("Invalid URI: The hostname could not be parsed.");
            }
            this.userInfo = ui;
            this.host = h.toLowerCase(Locale.ROOT);
            this.port = p == defaultPort(scheme) ? -1 : p;
        } else {
            if (slashy) {
                // "http:foo" — an http URI without authority
                throw new UriFormatException("Invalid URI: The format of the URI could not be determined.");
            }
            hierarchical = false;
            this.userInfo = null;
            this.host = "";
            this.port = -1;
        }
        int hash = rest.indexOf('#');
        String frag = hash < 0 ? "" : rest.substring(hash);
        rest = hash < 0 ? rest : rest.substring(0, hash);
        int q = rest.indexOf('?');
        String qry = q < 0 ? "" : rest.substring(q);
        String pth = q < 0 ? rest : rest.substring(0, q);
        if (hierarchical) {
            if (pth.isEmpty()) {
                pth = "/";
            }
            pth = removeDotSegments(pth);
        }
        this.path = normalizeEscapes(escape(pth, false));
        this.query = normalizeEscapes(escape(qry, true));
        this.fragment = normalizeEscapes(escape(frag, true));
    }

    // ---------------------------------------------------------------- members

    /** .NET {@code Uri.OriginalString}. */
    public String OriginalString() { return originalString; }

    /** .NET {@code Uri.Scheme} (lower case). */
    public String Scheme() { return scheme; }

    /** .NET {@code Uri.Host} (lower case). */
    public String Host() { return host; }

    /** .NET {@code Uri.Port}: the explicit port, else the scheme default, else -1. */
    public int Port() { return port >= 0 ? port : defaultPort(scheme); }

    /** .NET {@code Uri.IsDefaultPort}. */
    public boolean IsDefaultPort() { return port < 0; }

    /** .NET {@code Uri.IsAbsoluteUri} — always true for this class. */
    public boolean IsAbsoluteUri() { return true; }

    /** .NET {@code Uri.UserInfo} (escaped), {@code ""} if none. */
    public String UserInfo() { return userInfo == null ? "" : userInfo; }

    /** .NET {@code Uri.Authority}: host plus non-default port. */
    public String Authority() { return host + (port >= 0 ? ":" + port : ""); }

    /** .NET {@code Uri.AbsolutePath} (escaped). */
    public String AbsolutePath() { return path; }

    /** .NET {@code Uri.Query}: escaped, including the leading {@code ?}, or {@code ""}. */
    public String Query() { return query; }

    /** .NET {@code Uri.Fragment}: escaped, including {@code #}, or {@code ""}. */
    public String Fragment() { return fragment; }

    /** .NET {@code Uri.PathAndQuery} (escaped). */
    public String PathAndQuery() { return path + query; }

    /** .NET {@code Uri.AbsoluteUri}: the canonical escaped form. */
    public String AbsoluteUri() {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append(':');
        if (hierarchical) {
            sb.append("//");
            if (userInfo != null) {
                sb.append(userInfo).append('@');
            }
            sb.append(Authority());
        }
        sb.append(path).append(query).append(fragment);
        return sb.toString();
    }

    /** The equivalent {@code java.net.URI} (of {@link #AbsoluteUri()}). */
    public java.net.URI toJavaUri() {
        return java.net.URI.create(AbsoluteUri());
    }

    /** .NET {@code Uri.ToString()}: canonical form with safe escapes decoded. */
    @Override
    public String toString() {
        return safeUnescape(AbsoluteUri());
    }

    /** .NET {@code Uri.Equals}: canonical forms equal, fragment ignored. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Uri u)) {
            return false;
        }
        return withoutFragment().equals(u.withoutFragment());
    }

    @Override
    public int hashCode() {
        return withoutFragment().hashCode();
    }

    private String withoutFragment() {
        String a = AbsoluteUri();
        return a.substring(0, a.length() - fragment.length());
    }

    // ---------------------------------------------------------- static helpers

    /**
     * .NET 4.5 {@code Uri.EscapeDataString}: RFC 3986 unreserved characters
     * ({@code A-Z a-z 0-9 - . _ ~}) are kept, everything else is UTF-8
     * percent-encoded with upper-case hex.
     *
     * <p>Strings of {@code 0xFFF0} (65520) characters or more throw
     * {@code UriFormatException("Invalid URI: The Uri string is too long.")}
     * (.NET {@code c_MaxUriBufferSize}). TODO(port): confirm the exact
     * threshold on .NET 4.5 when the original runs (D-001).
     */
    public static String EscapeDataString(String stringToEscape) {
        if (stringToEscape == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: stringToEscape");
        }
        if (stringToEscape.isEmpty()) {
            return "";
        }
        if (stringToEscape.length() >= MAX_URI_BUFFER_SIZE) {
            throw new UriFormatException("Invalid URI: The Uri string is too long.");
        }
        StringBuilder sb = new StringBuilder(stringToEscape.length() + 16);
        int i = 0;
        int n = stringToEscape.length();
        while (i < n) {
            char c = stringToEscape.charAt(i);
            if (isUnreserved(c)) {
                sb.append(c);
                i++;
                continue;
            }
            int len = Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(stringToEscape.charAt(i + 1)) ? 2 : 1;
            appendUtf8Escaped(sb, stringToEscape.substring(i, i + len));
            i += len;
        }
        return sb.toString();
    }

    /** .NET {@code Uri.UnescapeDataString}: decodes valid UTF-8 escapes, keeps the rest. */
    public static String UnescapeDataString(String stringToUnescape) {
        if (stringToUnescape == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: stringToUnescape");
        }
        return unescape(stringToUnescape, "");
    }

    /** .NET {@code Uri.EscapeUriString}: escapes characters not legal anywhere in a URI. */
    public static String EscapeUriString(String stringToEscape) {
        if (stringToEscape == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: stringToEscape");
        }
        if (stringToEscape.length() >= MAX_URI_BUFFER_SIZE) {
            throw new UriFormatException("Invalid URI: The Uri string is too long.");
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < stringToEscape.length()) {
            char c = stringToEscape.charAt(i);
            if (c < 0x80 && (isUnreserved(c) || "!#$&'()*+,/:;=?@[]%".indexOf(c) >= 0)) {
                sb.append(c);
                i++;
            } else {
                int len = Character.isHighSurrogate(c) && i + 1 < stringToEscape.length() ? 2 : 1;
                appendUtf8Escaped(sb, stringToEscape.substring(i, i + len));
                i += len;
            }
        }
        return sb.toString();
    }

    /**
     * Whether {@code s} has the form {@code scheme:...} (.NET would try it as an
     * absolute URI). Used by {@link HttpRequests#newBuilder(String)}.
     */
    static boolean looksAbsolute(String s) {
        String t = trimWhite(s);
        if (schemeEnd(t) >= 0) {
            return true;
        }
        // .NET treats "c:\x" and "\\server\share" as absolute file URIs
        return (t.length() >= 2 && Character.isLetter(t.charAt(0)) && t.charAt(1) == ':')
                || t.startsWith("\\\\");
    }

    static boolean isUnreserved(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }

    static int defaultPort(String scheme) {
        return switch (scheme) {
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            case "ftp" -> 21;
            case "gopher" -> 70;
            case "nntp" -> 119;
            case "telnet" -> 23;
            case "ldap" -> 389;
            default -> -1;
        };
    }

    // ---------------------------------------------------------------- private

    private static boolean isSlashScheme(String scheme) {
        return switch (scheme) {
            case "http", "https", "ftp", "file", "ws", "wss", "gopher", "nntp", "telnet", "ldap", "net.tcp", "net.pipe" -> true;
            default -> false;
        };
    }

    /** .NET white space trimmed by the Uri parser. */
    private static String trimWhite(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && isWhite(s.charAt(a))) a++;
        while (b > a && isWhite(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static boolean isWhite(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085';
    }

    /** Index of the ':' ending a scheme (2+ chars, so "c:" is not a scheme), or -1. */
    private static int schemeEnd(String s) {
        int colon = s.indexOf(':');
        if (colon < 2) {
            return -1;
        }
        if (!isAsciiLetter(s.charAt(0))) {
            return -1;
        }
        for (int i = 1; i < colon; i++) {
            char c = s.charAt(i);
            if (!(isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.')) {
                return -1;
            }
        }
        return colon;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static int firstOf(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean validHost(String h) {
        if (h.startsWith("[")) {
            return h.endsWith("]") && h.length() > 2;
        }
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c <= ' ' || "<>\"{}|\\^`%[]/?#@".indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static String removeDotSegments(String p) {
        if (!p.contains("/.") && !p.startsWith(".")) {
            return p;
        }
        Deque<String> out = new ArrayDeque<>();
        String[] segs = p.split("/", -1); // runtime-internal, not translated code
        for (int i = 0; i < segs.length; i++) {
            String seg = segs[i];
            boolean last = i == segs.length - 1;
            if (seg.equals(".")) {
                if (last) out.addLast("");
            } else if (seg.equals("..")) {
                if (out.size() > 1) out.removeLast();
                if (last) out.addLast("");
            } else {
                out.addLast(seg);
            }
        }
        String r = String.join("/", out);
        return r.startsWith("/") ? r : "/" + r;
    }

    /**
     * Percent-encodes characters that may not appear literally; keeps valid
     * {@code %XX}; a {@code %} not followed by two hex digits becomes {@code %25}.
     */
    private static String escape(String s, boolean allowHash) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                    sb.append('%').append(Character.toUpperCase(s.charAt(i + 1))).append(Character.toUpperCase(s.charAt(i + 2)));
                    i += 3;
                } else {
                    sb.append("%25");
                    i++;
                }
                continue;
            }
            if (c > ' ' && c < 0x7F && "\"<>\\^`{|}".indexOf(c) < 0 && (allowHash || c != '#')) {
                sb.append(c);
                i++;
                continue;
            }
            int len = Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)) ? 2 : 1;
            appendUtf8Escaped(sb, s.substring(i, i + len));
            i += len;
        }
        return sb.toString();
    }

    /** Decodes {@code %XX} of unreserved characters (.NET 4.5 normalisation). */
    private static String normalizeEscapes(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                char d = (char) Integer.parseInt(s.substring(i + 1, i + 3), 16);
                if (isUnreserved(d)) {
                    sb.append(d);
                } else {
                    sb.append(s, i, i + 3);
                }
                i += 2;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String safeUnescape(String s) {
        return unescape(s, "%#?/\\");
    }

    /**
     * Decodes runs of {@code %XX} that form valid UTF-8, except characters in
     * {@code keep} and control characters, which stay escaped.
     */
    private static String unescape(String s, String keep) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '%' || i + 2 >= s.length() || !isHex(s.charAt(i + 1)) || !isHex(s.charAt(i + 2))) {
                sb.append(c);
                i++;
                continue;
            }
            // collect a run of escapes
            int start = i;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (i + 2 < s.length() && s.charAt(i) == '%' && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                bytes.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 3;
            }
            byte[] b = bytes.toByteArray();
            // decode sequence by sequence so one bad byte does not block the run
            int bi = 0;
            int si = start;
            while (bi < b.length) {
                int len = utf8Length(b, bi);
                String decoded = len > 0 ? decodeStrict(b, bi, len) : null;
                if (decoded != null && decoded.chars().noneMatch(ch -> ch < 0x20 || ch == 0x7F || keep.indexOf(ch) >= 0)) {
                    sb.append(decoded);
                    bi += len;
                    si += len * 3;
                } else {
                    int l = Math.max(len, 1);
                    sb.append(s, si, si + l * 3);
                    bi += l;
                    si += l * 3;
                }
            }
        }
        return sb.toString();
    }

    private static int utf8Length(byte[] b, int i) {
        int x = b[i] & 0xFF;
        int len = x < 0x80 ? 1 : x >= 0xF0 && x <= 0xF4 ? 4 : x >= 0xE0 ? 3 : x >= 0xC2 && x < 0xE0 ? 2 : 0;
        return len == 0 || i + len > b.length ? 0 : len;
    }

    private static String decodeStrict(byte[] b, int off, int len) {
        CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return d.decode(ByteBuffer.wrap(b, off, len)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static void appendUtf8Escaped(StringBuilder sb, String chars) {
        CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        byte[] bytes;
        try {
            ByteBuffer bb = enc.encode(CharBuffer.wrap(chars));
            bytes = new byte[bb.remaining()];
            bb.get(bytes);
        } catch (CharacterCodingException e) {
            // a lone surrogate: .NET throws from the escaper
            throw new UriFormatException("Invalid URI: There is an invalid sequence in the string.");
        }
        for (byte x : bytes) {
            int v = x & 0xFF;
            sb.append('%').append(HEX.charAt(v >> 4)).append(HEX.charAt(v & 0xF));
        }
    }
}
