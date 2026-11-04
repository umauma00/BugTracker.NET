package btnet.runtime.web;

import btnet.runtime.Convert;
import btnet.runtime.NameValueCollection;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * .NET 4.5 {@code System.Web.HttpUtility} (RULEBOOK §2A "Web intrinsics in core code").
 * Byte-exact .NET semantics:
 * <ul>
 * <li>{@link #HtmlEncode(String)}: {@code null → null}; {@code < > " & '} →
 * {@code &lt; &gt; &quot; &amp; &#39;}; U+00A0–U+00FF → {@code &#NNN;}; a surrogate
 * pair → {@code &#<scalar>;}, a lone surrogate → U+FFFD (4.5 {@code Strict}
 * conformance); everything else unchanged.</li>
 * <li>{@link #HtmlDecode(String)}: HTML 4 named entities (+ {@code &apos;}), decimal
 * and hex numeric references; an entity needs its {@code ;}; unknown or invalid ones
 * are kept literally.</li>
 * <li>{@link #UrlEncode(String)}: UTF-8, space → {@code +}, unreserved
 * {@code A-Za-z0-9-_.!*()}, everything else {@code %xx} with <b>lower-case</b> hex.</li>
 * <li>{@link #UrlDecode(String)}: {@code +} → space, {@code %xx} bytes decoded as UTF-8,
 * {@code %uXXXX}, invalid {@code %} sequences kept literally.</li>
 * </ul>
 */
public final class HttpUtility {
    private HttpUtility() {
    }

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    // ------------------------------------------------------------------ HtmlEncode

    /** .NET {@code HttpUtility.HtmlEncode(string)}. */
    public static String HtmlEncode(String s) {
        if (s == null) return null;
        if (s.isEmpty()) return s;
        StringBuilder sb = null;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            String rep = null;
            int consumedExtra = 0;
            switch (ch) {
                case '<': rep = "&lt;"; break;
                case '>': rep = "&gt;"; break;
                case '"': rep = "&quot;"; break;
                case '\'': rep = "&#39;"; break;
                case '&': rep = "&amp;"; break;
                default:
                    if (ch >= 160 && ch < 256) {
                        rep = "&#" + (int) ch + ";";
                    } else if (Character.isSurrogate(ch)) {
                        if (Character.isHighSurrogate(ch) && i + 1 < n && Character.isLowSurrogate(s.charAt(i + 1))) {
                            rep = "&#" + Character.toCodePoint(ch, s.charAt(i + 1)) + ";";
                            consumedExtra = 1;
                        } else {
                            rep = "�";
                        }
                    }
            }
            if (rep != null) {
                if (sb == null) {
                    sb = new StringBuilder(n + 16);
                    sb.append(s, 0, i);
                }
                sb.append(rep);
                i += consumedExtra;
            } else if (sb != null) {
                sb.append(ch);
            }
        }
        return sb == null ? s : sb.toString();
    }

    /**
     * .NET {@code HttpUtility.HtmlEncode(object)}: {@code null → null}; an
     * {@link IHtmlString} is returned unencoded; else {@code HtmlEncode(Convert.ToString(o))}.
     */
    public static String HtmlEncode(Object value) {
        if (value == null) return null;
        if (value instanceof IHtmlString h) return h.ToHtmlString();
        if (value instanceof String s) return HtmlEncode(s);
        return HtmlEncode(Convert.ToString(value));
    }

    /** .NET 4.5 {@code HttpUtility.HtmlAttributeEncode}: {@code " & < '} only. */
    public static String HtmlAttributeEncode(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("&quot;"); break;
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ HtmlDecode

    /** .NET 4.5 {@code HttpUtility.HtmlDecode(string)} ({@code WebUtility.HtmlDecode}, Strict). */
    public static String HtmlDecode(String s) {
        if (s == null) return null;
        if (s.indexOf('&') < 0) return s;
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (ch == '&') {
                int index = indexOfAny(s, i + 1);
                if (index > 0 && s.charAt(index) == ';') {
                    String entity = s.substring(i + 1, index);
                    if (entity.length() > 1 && entity.charAt(0) == '#') {
                        Long parsed = parseCharRef(entity);
                        boolean ok = parsed != null;
                        long v = ok ? parsed : 0;
                        if (ok) {
                            ok = (v < 0xD800) || (0xDFFF < v && v <= 0x10FFFF);
                        }
                        if (ok) {
                            if (v <= 0xFFFF) {
                                sb.append((char) v);
                            } else {
                                sb.appendCodePoint((int) v);
                            }
                            i = index;
                        } else {
                            sb.append('&').append(entity).append(';');
                            i = index;
                        }
                    } else {
                        Character c = ENTITIES.get(entity);
                        if (c != null) {
                            sb.append(c.charValue());
                        } else {
                            sb.append('&').append(entity).append(';');
                        }
                        i = index;
                    }
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static int indexOfAny(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ';' || c == '&') return i;
        }
        return -1;
    }

    /** {@code Int32.TryParse} as .NET's HtmlDecode calls it; {@code null} on failure. */
    private static Long parseCharRef(String entity) {
        if (entity.charAt(1) == 'x' || entity.charAt(1) == 'X') {
            String h = entity.substring(2);
            if (h.isEmpty()) return null; // NumberStyles.AllowHexSpecifier, Int32
            long v = 0;
            for (int k = 0; k < h.length(); k++) {
                int d = Character.digit(h.charAt(k), 16);
                if (d < 0 || h.charAt(k) > 'f') return null;
                v = v * 16 + d;
                if (v > 0xFFFFFFFFL) return null;
            }
            return (long) (int) v; // AllowHexSpecifier: 8 hex digits wrap to a negative Int32
        }
        // NumberStyles.Integer: leading/trailing white space, one leading sign
        String d = entity.substring(1);
        int a = 0, b = d.length();
        while (a < b && isNetWhite(d.charAt(a))) a++;
        while (b > a && isNetWhite(d.charAt(b - 1))) b--;
        if (a >= b) return null;
        boolean neg = false;
        if (d.charAt(a) == '+' || d.charAt(a) == '-') {
            neg = d.charAt(a) == '-';
            a++;
        }
        if (a >= b) return null;
        long v = 0;
        for (int k = a; k < b; k++) {
            char c = d.charAt(k);
            if (c < '0' || c > '9') return null;
            v = v * 10 + (c - '0');
            if (v > 2147483648L) return null;
        }
        if (neg) v = -v;
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) return null;
        return v;
    }

    private static boolean isNetWhite(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }

    // ------------------------------------------------------------------ URL encoding

    /** .NET {@code HttpUtility.UrlEncode(string)} (UTF-8, lower-case hex, space → '+'). */
    public static String UrlEncode(String s) {
        if (s == null) return null;
        byte[] bytes = utf8(s);
        StringBuilder sb = new StringBuilder(bytes.length + 16);
        for (byte bb : bytes) {
            int b = bb & 0xFF;
            char c = (char) b;
            if (isUrlSafeChar(c)) {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('+');
            } else {
                sb.append('%').append(HEX_LOWER[b >> 4]).append(HEX_LOWER[b & 15]);
            }
        }
        return sb.toString();
    }

    static boolean isUrlSafeChar(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        switch (c) {
            case '-': case '_': case '.': case '!': case '*': case '(': case ')':
                return true;
            default:
                return false;
        }
    }

    /**
     * .NET {@code HttpUtility.UrlPathEncode}: the part before {@code ?} gets non-ASCII
     * bytes (and controls, DEL) as UTF-8 {@code %xx} and spaces as {@code %20}; the
     * query part is kept.
     */
    public static String UrlPathEncode(String s) {
        if (s == null || s.isEmpty()) return s;
        int q = s.indexOf('?');
        if (q >= 0) return UrlPathEncode(s.substring(0, q)) + s.substring(q);
        return UrlEncodeNonAscii(s).replace(" ", "%20");
    }

    /** .NET {@code HttpEncoder.UrlEncodeNonAscii} (UTF-8). */
    static String UrlEncodeNonAscii(String s) {
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x7F || c < 0x20) { any = true; break; }
        }
        if (!any) return s;
        StringBuilder sb = new StringBuilder();
        for (byte bb : utf8(s)) {
            int b = bb & 0xFF;
            if (b >= 0x7F || b < 0x20) {
                sb.append('%').append(HEX_LOWER[b >> 4]).append(HEX_LOWER[b & 15]);
            } else {
                sb.append((char) b);
            }
        }
        return sb.toString();
    }

    /** .NET {@code HttpUtility.UrlDecode(string)} (UTF-8). */
    public static String UrlDecode(String s) {
        if (s == null) return null;
        int count = s.length();
        StringBuilder out = new StringBuilder(count);
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        for (int pos = 0; pos < count; pos++) {
            char ch = s.charAt(pos);
            if (ch == '+') {
                ch = ' ';
            } else if (ch == '%' && pos < count - 2) {
                if (s.charAt(pos + 1) == 'u' && pos < count - 5) {
                    int h1 = hex(s.charAt(pos + 2)), h2 = hex(s.charAt(pos + 3)),
                        h3 = hex(s.charAt(pos + 4)), h4 = hex(s.charAt(pos + 5));
                    if (h1 >= 0 && h2 >= 0 && h3 >= 0 && h4 >= 0) {
                        ch = (char) ((h1 << 12) | (h2 << 8) | (h3 << 4) | h4);
                        pos += 5;
                        flush(pending, out);
                        out.append(ch);
                        continue;
                    }
                } else {
                    int h1 = hex(s.charAt(pos + 1)), h2 = hex(s.charAt(pos + 2));
                    if (h1 >= 0 && h2 >= 0) {
                        pending.write((h1 << 4) | h2);
                        pos += 2;
                        continue;
                    }
                }
            }
            if ((ch & 0xFF80) == 0) {
                pending.write(ch);
            } else {
                flush(pending, out);
                out.append(ch);
            }
        }
        flush(pending, out);
        return out.toString();
    }

    private static void flush(ByteArrayOutputStream pending, StringBuilder out) {
        if (pending.size() > 0) {
            out.append(new String(pending.toByteArray(), StandardCharsets.UTF_8));
            pending.reset();
        }
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    /** UTF-8 as .NET's {@code UTF8Encoding} with replacement fallback (lone surrogate → EF BF BD). */
    static byte[] utf8(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            int cp = s.charAt(i);
            if (Character.isHighSurrogate((char) cp) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                cp = Character.toCodePoint((char) cp, s.charAt(++i));
            } else if (Character.isSurrogate((char) cp)) {
                cp = 0xFFFD;
            }
            if (cp < 0x80) {
                o.write(cp);
            } else if (cp < 0x800) {
                o.write(0xC0 | (cp >> 6));
                o.write(0x80 | (cp & 0x3F));
            } else if (cp < 0x10000) {
                o.write(0xE0 | (cp >> 12));
                o.write(0x80 | ((cp >> 6) & 0x3F));
                o.write(0x80 | (cp & 0x3F));
            } else {
                o.write(0xF0 | (cp >> 18));
                o.write(0x80 | ((cp >> 12) & 0x3F));
                o.write(0x80 | ((cp >> 6) & 0x3F));
                o.write(0x80 | (cp & 0x3F));
            }
        }
        return o.toByteArray();
    }

    /**
     * .NET {@code HttpUtility.ParseQueryString(string)} / {@code HttpValueCollection.FillFromString}
     * (url-encoded, UTF-8): a segment without {@code =} is added under the {@code null} key;
     * a trailing {@code &} adds {@code (null, "")}. A leading {@code ?} is skipped.
     */
    public static NameValueCollection ParseQueryString(String query) {
        NameValueCollection c = new NameValueCollection();
        if (query == null) return c;
        String s = query.startsWith("?") ? query.substring(1) : query;
        fillFromString(c, s, true);
        return c;
    }

    static void fillFromString(NameValueCollection c, String s, boolean urlencoded) {
        int l = s.length();
        int i = 0;
        while (i < l) {
            int si = i;
            int ti = -1;
            while (i < l) {
                char ch = s.charAt(i);
                if (ch == '=') {
                    if (ti < 0) ti = i;
                } else if (ch == '&') {
                    break;
                }
                i++;
            }
            String name = null;
            String value;
            if (ti >= 0) {
                name = s.substring(si, ti);
                value = s.substring(ti + 1, i);
            } else {
                value = s.substring(si, i);
            }
            if (urlencoded) {
                c.Add(UrlDecode(name), UrlDecode(value));
            } else {
                c.Add(name, value);
            }
            if (i == l - 1 && s.charAt(i) == '&') {
                c.Add(null, "");
            }
            i++;
        }
    }

    // ------------------------------------------------------------------ entity table

    /** HTML 4.01 entities (252) + {@code apos}, as .NET's {@code HtmlEntities} table. */
    static final Map<String, Character> ENTITIES = new HashMap<>(512);

    static {
        String[] basic = {"quot", "34", "amp", "38", "apos", "39", "lt", "60", "gt", "62"};
        for (int k = 0; k < basic.length; k += 2) ENTITIES.put(basic[k], (char) Integer.parseInt(basic[k + 1]));
        String[] latin1 = ("nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr "
            + "deg plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest "
            + "Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml "
            + "ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig "
            + "agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml "
            + "eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml").split(" ");
        for (int k = 0; k < latin1.length; k++) ENTITIES.put(latin1[k], (char) (160 + k));
        String[] other = (
            "OElig 338 oelig 339 Scaron 352 scaron 353 Yuml 376 fnof 402 circ 710 tilde 732 "
            + "Alpha 913 Beta 914 Gamma 915 Delta 916 Epsilon 917 Zeta 918 Eta 919 Theta 920 Iota 921 Kappa 922 "
            + "Lambda 923 Mu 924 Nu 925 Xi 926 Omicron 927 Pi 928 Rho 929 Sigma 931 Tau 932 Upsilon 933 Phi 934 "
            + "Chi 935 Psi 936 Omega 937 alpha 945 beta 946 gamma 947 delta 948 epsilon 949 zeta 950 eta 951 "
            + "theta 952 iota 953 kappa 954 lambda 955 mu 956 nu 957 xi 958 omicron 959 pi 960 rho 961 sigmaf 962 "
            + "sigma 963 tau 964 upsilon 965 phi 966 chi 967 psi 968 omega 969 thetasym 977 upsih 978 piv 982 "
            + "ensp 8194 emsp 8195 thinsp 8201 zwnj 8204 zwj 8205 lrm 8206 rlm 8207 ndash 8211 mdash 8212 "
            + "lsquo 8216 rsquo 8217 sbquo 8218 ldquo 8220 rdquo 8221 bdquo 8222 dagger 8224 Dagger 8225 "
            + "bull 8226 hellip 8230 permil 8240 prime 8242 Prime 8243 lsaquo 8249 rsaquo 8250 oline 8254 "
            + "frasl 8260 euro 8364 image 8465 weierp 8472 real 8476 trade 8482 alefsym 8501 larr 8592 uarr 8593 "
            + "rarr 8594 darr 8595 harr 8596 crarr 8629 lArr 8656 uArr 8657 rArr 8658 dArr 8659 hArr 8660 "
            + "forall 8704 part 8706 exist 8707 empty 8709 nabla 8711 isin 8712 notin 8713 ni 8715 prod 8719 "
            + "sum 8721 minus 8722 lowast 8727 radic 8730 prop 8733 infin 8734 ang 8736 and 8743 or 8744 cap 8745 "
            + "cup 8746 int 8747 there4 8756 sim 8764 cong 8773 asymp 8776 ne 8800 equiv 8801 le 8804 ge 8805 "
            + "sub 8834 sup 8835 nsub 8836 sube 8838 supe 8839 oplus 8853 otimes 8855 perp 8869 sdot 8901 "
            + "lceil 8968 rceil 8969 lfloor 8970 rfloor 8971 lang 9001 rang 9002 loz 9674 spades 9824 clubs 9827 "
            + "hearts 9829 diams 9830").split(" ");
        for (int k = 0; k < other.length; k += 2) ENTITIES.put(other[k], (char) Integer.parseInt(other[k + 1]));
    }
}
