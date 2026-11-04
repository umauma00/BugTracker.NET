package btnet.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stands for .NET {@code System.String} members whose Java equivalents differ (RULEBOOK §2A Strings, §3):
 * C# concatenation ({@link #cs}), composite formatting ({@link #Format}), {@code Split}, {@code Trim*},
 * {@code IndexOf} overloads, {@code string.Join}, culture comparison, length-preserving case mapping.
 */
public final class Strings {
    private Strings() {
    }

    /**
     * .NET {@code StringComparer.CurrentCulture} for en-US [C20, C31, S2-1]: the Windows NLS emulation
     * {@link NlsComparer} ({@code "in progress" < "inactive" < "info"}; the space is not ignorable);
     * {@code null} sorts first.
     */
    public static final Comparator<String> CULTURE_COMPARER = NlsComparer.INSTANCE;

    /** .NET {@code string.Empty}. */
    public static final String Empty = "";

    // ------------------------------------------------------------------ concatenation / conversion

    /** C# string-concatenation operand semantics: {@code null}/DBNull → {@code ""}, else {@link Convert#ToString(Object)}. */
    public static String cs(Object o) {
        if (o == null || o == DBNull.Value) {
            return "";
        }
        if (o instanceof String s) {
            return s;
        }
        return Convert.ToString(o);
    }

    /** {@code o.ToString()} on a receiver that may be null [J-C17]: NPE (≈ NullReferenceException) on null. */
    public static String ToStringStrict(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return Convert.ToString(o);
    }

    public static boolean IsNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean IsNullOrWhiteSpace(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isWhiteSpace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** .NET {@code Char.IsWhiteSpace}. */
    public static boolean isWhiteSpace(char c) {
        return (c >= 0x09 && c <= 0x0D) || c == 0x20 || c == 0x85 || c == 0xA0 || c == 0x1680
                || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F
                || c == 0x3000;
    }

    // ------------------------------------------------------------------ composite formatting

    /** .NET {@code string.Format(format, args)} with en-US. {@code java.lang.String.format} is banned. */
    public static String Format(String format, Object... args) {
        return Format(CultureInfo.EN_US, format, args);
    }

    /**
     * .NET {@code string.Format(provider, format, args)}: items {@code {index[,alignment][:format]}},
     * {@code {{}/{@code }}} escapes; a null argument formats as {@code ""}; bad syntax →
     * FormatException("Input string was not in a correct format."); index outside args →
     * FormatException("Index (zero based) must be greater than or equal to zero and less than the size of the
     * argument list.").
     */
    public static String Format(CultureInfo provider, String format, Object... args) {
        if (format == null) {
            throw new ArgumentNullException("format");
        }
        if (args == null) {
            args = new Object[] {null};
        }
        CultureInfo ci = provider == null ? CultureInfo.EN_US : provider;
        StringBuilder sb = new StringBuilder();
        int n = format.length();
        int i = 0;
        while (i < n) {
            char c = format.charAt(i);
            if (c == '}') {
                if (i + 1 < n && format.charAt(i + 1) == '}') {
                    sb.append('}');
                    i += 2;
                    continue;
                }
                throw badFormat();
            }
            if (c != '{') {
                sb.append(c);
                i++;
                continue;
            }
            if (i + 1 < n && format.charAt(i + 1) == '{') {
                sb.append('{');
                i += 2;
                continue;
            }
            i++;
            // index
            if (i >= n || format.charAt(i) < '0' || format.charAt(i) > '9') {
                throw badFormat();
            }
            int index = 0;
            while (i < n && format.charAt(i) >= '0' && format.charAt(i) <= '9') {
                index = index * 10 + (format.charAt(i) - '0');
                if (index >= 1_000_000) {
                    throw badFormat();
                }
                i++;
            }
            while (i < n && format.charAt(i) == ' ') {
                i++;
            }
            int width = 0;
            boolean left = false;
            if (i < n && format.charAt(i) == ',') {
                i++;
                while (i < n && format.charAt(i) == ' ') {
                    i++;
                }
                if (i < n && format.charAt(i) == '-') {
                    left = true;
                    i++;
                }
                if (i >= n || format.charAt(i) < '0' || format.charAt(i) > '9') {
                    throw badFormat();
                }
                while (i < n && format.charAt(i) >= '0' && format.charAt(i) <= '9') {
                    width = width * 10 + (format.charAt(i) - '0');
                    if (width >= 1_000_000) {
                        throw badFormat();
                    }
                    i++;
                }
            }
            while (i < n && format.charAt(i) == ' ') {
                i++;
            }
            String itemFormat = null;
            if (i < n && format.charAt(i) == ':') {
                i++;
                StringBuilder fb = new StringBuilder();
                while (true) {
                    if (i >= n) {
                        throw badFormat();
                    }
                    char fc = format.charAt(i);
                    if (fc == '{') {
                        if (i + 1 < n && format.charAt(i + 1) == '{') {
                            fb.append('{');
                            i += 2;
                            continue;
                        }
                        throw badFormat();
                    }
                    if (fc == '}') {
                        if (i + 1 < n && format.charAt(i + 1) == '}') {
                            fb.append('}');
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    fb.append(fc);
                    i++;
                }
                itemFormat = fb.toString();
            }
            if (i >= n || format.charAt(i) != '}') {
                throw badFormat();
            }
            i++;
            if (index >= args.length) {
                throw new FormatException(
                        "Index (zero based) must be greater than or equal to zero and less than the size of the argument list.");
            }
            Object arg = args[index];
            String text;
            if (arg == null) {
                text = "";
            } else if (itemFormat == null) {
                text = Convert.ToString(arg, ci);
            } else {
                text = DotNetFormat.Format(arg, itemFormat, ci);
            }
            int pad = width - text.length();
            if (pad > 0 && !left) {
                sb.append(" ".repeat(pad));
            }
            sb.append(text);
            if (pad > 0 && left) {
                sb.append(" ".repeat(pad));
            }
        }
        return sb.toString();
    }

    private static FormatException badFormat() {
        return new FormatException("Input string was not in a correct format.");
    }

    // ------------------------------------------------------------------ Split

    /** .NET {@code s.Split(params char[] separator)}: no/empty separators → white space; empties kept. */
    public static String[] Split(String s, char... separators) {
        return Split(s, StringSplitOptions.None, separators);
    }

    /** .NET {@code s.Split(char[] separator, options)}. Never regex. */
    public static String[] Split(String s, int options, char... separators) {
        if (s == null) {
            throw new NullPointerException();
        }
        checkOptions(options);
        boolean ws = separators == null || separators.length == 0;
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean sep;
            if (ws) {
                sep = isWhiteSpace(c);
            } else {
                sep = false;
                for (char x : separators) {
                    if (x == c) {
                        sep = true;
                        break;
                    }
                }
            }
            if (sep) {
                add(out, s.substring(start, i), options);
                start = i + 1;
            }
        }
        add(out, s.substring(start), options);
        return out.toArray(new String[0]);
    }

    /**
     * .NET {@code s.Split(string[] separator, options)} [S1-16]: ordinal; at each index the first non-empty
     * separator in argument order that matches ends a field; all separators null/empty → white space.
     *
     * <p>The varargs form takes at least one separator so that {@code Split(s, ',')} (the §2A
     * {@code s.Split(c)} form; {@code char} widens to {@code int}) resolves to {@link #Split(String, char...)}
     * instead of being ambiguous with an empty {@code String...}. A {@code String[]} variable uses
     * {@link #Split(String, int, String[])}.
     */
    public static String[] Split(String s, int options, String separator, String... moreSeparators) {
        String[] all = new String[1 + (moreSeparators == null ? 0 : moreSeparators.length)];
        all[0] = separator;
        if (moreSeparators != null) {
            System.arraycopy(moreSeparators, 0, all, 1, moreSeparators.length);
        }
        return Split(s, options, all);
    }

    /** .NET {@code s.Split(string[] separator, options)} with the separators already in an array [S1-16]. */
    public static String[] Split(String s, int options, String[] separators) {
        if (s == null) {
            throw new NullPointerException();
        }
        checkOptions(options);
        List<String> seps = new ArrayList<>();
        if (separators != null) {
            for (String x : separators) {
                if (x != null && !x.isEmpty()) {
                    seps.add(x);
                }
            }
        }
        if (seps.isEmpty()) {
            return Split(s, options, new char[0]);
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < s.length()) {
            String hit = null;
            for (String x : seps) {
                if (s.startsWith(x, i)) {
                    hit = x;
                    break;
                }
            }
            if (hit != null) {
                add(out, s.substring(start, i), options);
                i += hit.length();
                start = i;
            } else {
                i++;
            }
        }
        add(out, s.substring(start), options);
        return out.toArray(new String[0]);
    }

    private static void checkOptions(int options) {
        if (options != StringSplitOptions.None && options != StringSplitOptions.RemoveEmptyEntries) {
            throw new ArgumentException("Illegal enum value: " + options + ".", "options");
        }
    }

    private static void add(List<String> out, String part, int options) {
        if (part.isEmpty() && options == StringSplitOptions.RemoveEmptyEntries) {
            return;
        }
        out.add(part);
    }

    // ------------------------------------------------------------------ Trim

    /** .NET {@code s.Trim()}: removes {@code Char.IsWhiteSpace} characters (incl. U+00A0). */
    public static String Trim(String s) {
        return TrimEnd(TrimStart(s));
    }

    /** .NET {@code s.Trim(params char[])}: no chars → white space. */
    public static String Trim(String s, char... chars) {
        return TrimEnd(TrimStart(s, chars), chars);
    }

    public static String TrimStart(String s) {
        return TrimStart(s, (char[]) null);
    }

    public static String TrimStart(String s, char... chars) {
        if (s == null) {
            throw new NullPointerException();
        }
        int i = 0;
        while (i < s.length() && trimmable(s.charAt(i), chars)) {
            i++;
        }
        return s.substring(i);
    }

    public static String TrimEnd(String s) {
        return TrimEnd(s, (char[]) null);
    }

    public static String TrimEnd(String s, char... chars) {
        if (s == null) {
            throw new NullPointerException();
        }
        int e = s.length();
        while (e > 0 && trimmable(s.charAt(e - 1), chars)) {
            e--;
        }
        return s.substring(0, e);
    }

    private static boolean trimmable(char c, char[] chars) {
        if (chars == null || chars.length == 0) {
            return isWhiteSpace(c);
        }
        for (char x : chars) {
            if (x == c) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ IndexOf and comparisons

    /** .NET {@code s.IndexOf(v, startIndex)} [C27]: start outside 0..length → ArgumentOutOfRangeException. */
    public static int IndexOf(String s, String v, int start) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (v == null) {
            throw new ArgumentNullException("value");
        }
        checkStart(s, start);
        return s.indexOf(v, start);
    }

    /** .NET {@code s.IndexOf(char, startIndex)} [C27]. */
    public static int IndexOf(String s, char v, int start) {
        if (s == null) {
            throw new NullPointerException();
        }
        checkStart(s, start);
        return s.indexOf(v, start);
    }

    private static void checkStart(String s, int start) {
        if (start < 0 || start > s.length()) {
            throw new ArgumentOutOfRangeException("startIndex",
                    "Index was out of range. Must be non-negative and less than the size of the collection.");
        }
    }

    /**
     * .NET {@code s.IndexOf(v, comparison)} [S1-21]: s null → NPE; v null → ArgumentNullException; v empty → 0;
     * ordinal / ordinal-ignore-case (per-char {@code Character.toUpperCase}); culture variants behave as their
     * ordinal counterparts (accepted, RULEBOOK §2A).
     */
    public static int IndexOf(String s, String v, StringComparison comparison) {
        return IndexOf(s, v, 0, comparison);
    }

    /** .NET {@code s.IndexOf(v, startIndex, comparison)}. */
    public static int IndexOf(String s, String v, int start, StringComparison comparison) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (v == null) {
            throw new ArgumentNullException("value");
        }
        checkStart(s, start);
        if (v.isEmpty()) {
            return start;
        }
        if (!ignoreCase(comparison)) {
            return s.indexOf(v, start);
        }
        int last = s.length() - v.length();
        for (int i = start; i <= last; i++) {
            if (regionEqualsIgnoreCase(s, i, v)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean ignoreCase(StringComparison c) {
        if (c == null) {
            throw new ArgumentException("The string comparison type passed in is currently not supported.",
                    "comparisonType");
        }
        return c == StringComparison.OrdinalIgnoreCase || c == StringComparison.CurrentCultureIgnoreCase
                || c == StringComparison.InvariantCultureIgnoreCase;
    }

    private static boolean regionEqualsIgnoreCase(String s, int off, String v) {
        for (int k = 0; k < v.length(); k++) {
            if (Character.toUpperCase(s.charAt(off + k)) != Character.toUpperCase(v.charAt(k))) {
                return false;
            }
        }
        return true;
    }

    /** .NET {@code a.Equals(b, comparison)} / {@code string.Equals(a, b, comparison)}. */
    public static boolean Equals(String a, String b, StringComparison comparison) {
        boolean ic = ignoreCase(comparison);
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() != b.length()) {
            return false;
        }
        return ic ? regionEqualsIgnoreCase(a, 0, b) : a.equals(b);
    }

    /** .NET {@code s.StartsWith(v, comparison)}. */
    public static boolean StartsWith(String s, String v, StringComparison comparison) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (v == null) {
            throw new ArgumentNullException("value");
        }
        boolean ic = ignoreCase(comparison);
        return v.length() <= s.length() && (ic ? regionEqualsIgnoreCase(s, 0, v) : s.startsWith(v));
    }

    /** .NET {@code s.EndsWith(v, comparison)}. */
    public static boolean EndsWith(String s, String v, StringComparison comparison) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (v == null) {
            throw new ArgumentNullException("value");
        }
        boolean ic = ignoreCase(comparison);
        int off = s.length() - v.length();
        return off >= 0 && (ic ? regionEqualsIgnoreCase(s, off, v) : s.endsWith(v));
    }

    /** .NET {@code string.Compare(a, b)}: culture-sensitive (en-US), nulls first. */
    public static int Compare(String a, String b) {
        return Integer.signum(CULTURE_COMPARER.compare(a, b));
    }

    // ------------------------------------------------------------------ Join, Pad, case mapping

    /** .NET {@code string.Join(sep, values)} [C3]: null/DBNull elements → {@code ""}; null sep → {@code ""}. */
    public static String join(String sep, Object[] xs) {
        if (xs == null) {
            throw new ArgumentNullException("value");
        }
        String s = sep == null ? "" : sep;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) {
                sb.append(s);
            }
            sb.append(cs(xs[i]));
        }
        return sb.toString();
    }

    /** .NET {@code string.Join(sep, IEnumerable)} [C3]. */
    public static String join(String sep, Iterable<?> xs) {
        if (xs == null) {
            throw new ArgumentNullException("values");
        }
        String s = sep == null ? "" : sep;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : xs) {
            if (!first) {
                sb.append(s);
            }
            first = false;
            sb.append(cs(o));
        }
        return sb.toString();
    }

    /** .NET {@code s.PadLeft(totalWidth[, paddingChar])}. */
    public static String PadLeft(String s, int totalWidth) {
        return PadLeft(s, totalWidth, ' ');
    }

    public static String PadLeft(String s, int totalWidth, char paddingChar) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (totalWidth < 0) {
            throw new ArgumentOutOfRangeException("totalWidth", "Non-negative number required.");
        }
        return totalWidth <= s.length() ? s : String.valueOf(paddingChar).repeat(totalWidth - s.length()) + s;
    }

    /** .NET {@code s.PadRight(totalWidth[, paddingChar])}. */
    public static String PadRight(String s, int totalWidth) {
        return PadRight(s, totalWidth, ' ');
    }

    public static String PadRight(String s, int totalWidth, char paddingChar) {
        if (s == null) {
            throw new NullPointerException();
        }
        if (totalWidth < 0) {
            throw new ArgumentOutOfRangeException("totalWidth", "Non-negative number required.");
        }
        return totalWidth <= s.length() ? s : s + String.valueOf(paddingChar).repeat(totalWidth - s.length());
    }

    /** .NET {@code ToUpper()}/{@code ToUpperInvariant()} [S1-8]: per-char, length-preserving; null → NPE. */
    public static String ToUpper(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            a[i] = Character.toUpperCase(a[i]);
        }
        return new String(a);
    }

    /** .NET {@code ToLower()}/{@code ToLowerInvariant()} [S1-8]: per-char, length-preserving; null → NPE. */
    public static String ToLower(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            a[i] = Character.toLowerCase(a[i]);
        }
        return new String(a);
    }
}
