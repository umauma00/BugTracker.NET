package btnet.runtime;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stands for .NET {@code System.DateTime} static members over {@link LocalDateTime} (RULEBOOK §2A Dates,
 * §3 C20/S1-11): {@code DateTime.Parse} (pinned pattern list), {@code DateTime.ParseExact},
 * {@code Ticks}, {@code MinValue}/{@code MaxValue}.
 */
public final class DateTimes {
    private DateTimes() {
    }

    /** .NET {@code DateTime.MinValue}. */
    public static final LocalDateTime MinValue = LocalDateTime.of(1, 1, 1, 0, 0, 0);
    /** .NET {@code DateTime.MaxValue}. */
    public static final LocalDateTime MaxValue = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_900);

    /** .NET {@code DateTimeStyles} bits accepted by {@link #ParseExact(String, String, CultureInfo, int)}. */
    public static final int DateTimeStyles_None = 0;
    public static final int DateTimeStyles_AllowLeadingWhite = 1;
    public static final int DateTimeStyles_AllowTrailingWhite = 2;
    public static final int DateTimeStyles_AllowInnerWhite = 4;
    public static final int DateTimeStyles_AllowWhiteSpaces = 7;

    private static final String NOT_RECOGNIZED = "String was not recognized as a valid DateTime.";
    private static final long TICKS_PER_DAY = 864_000_000_000L;
    private static final long EPOCH_DAY_0001 = LocalDate.of(1, 1, 1).toEpochDay();

    /** .NET {@code dt.Ticks}: 100 ns intervals since 0001-01-01T00:00:00 (Kind ignored). */
    public static long ticks(LocalDateTime dt) {
        long days = dt.toLocalDate().toEpochDay() - EPOCH_DAY_0001;
        return days * TICKS_PER_DAY + dt.toLocalTime().toNanoOfDay() / 100;
    }

    /** .NET {@code new DateTime(ticks)}. */
    public static LocalDateTime fromTicks(long ticks) {
        if (ticks < 0 || ticks > ticks(MaxValue)) {
            throw new ArgumentOutOfRangeException("ticks",
                    "Ticks must be between DateTime.MinValue.Ticks and DateTime.MaxValue.Ticks.");
        }
        long days = ticks / TICKS_PER_DAY;
        long rem = ticks % TICKS_PER_DAY;
        return LocalDate.ofEpochDay(EPOCH_DAY_0001 + days).atStartOfDay().plusNanos(rem * 100);
    }

    // ---------------------------------------------------------------------------- Parse

    private static final String TT = " *([AaPp][Mm])";
    private static final Pattern US_DATE = Pattern.compile("([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})");
    private static final Pattern US_12 = Pattern.compile(
            "([0-9]{1,2})/([0-9]{1,2})/([0-9]{4}) +([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?" + TT);
    private static final Pattern US_24 = Pattern.compile(
            "([0-9]{1,2})/([0-9]{1,2})/([0-9]{4}) +([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?");
    private static final Pattern ISO_DATE = Pattern.compile("([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})");
    private static final Pattern ISO_24 = Pattern.compile(
            "([0-9]{4})-([0-9]{1,2})-([0-9]{1,2}) +([0-9]{1,2}):([0-9]{2})(?::([0-9]{2})(?:\\.([0-9]{1,7}))?)?");
    private static final Pattern ISO_12 = Pattern.compile(
            "([0-9]{4})-([0-9]{1,2})-([0-9]{1,2}) +([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?" + TT);
    private static final Pattern ISO_T = Pattern.compile(
            "([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})T([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,7}))?");
    private static final Pattern SQL = Pattern.compile(
            "([0-9]{4})([0-9]{2})([0-9]{2}) ([0-9]{2}):([0-9]{2}):([0-9]{2})");

    /** .NET {@code DateTime.Parse(s)} with the current (en-US) culture. */
    public static LocalDateTime Parse(String s) {
        return Parse(s, CultureInfo.EN_US);
    }

    /**
     * .NET {@code DateTime.Parse(s, culture)}, approximated by the pinned pattern list of RULEBOOK §3 S1-11, tried
     * in order: en-US {@code M/d/yyyy[ h:mm[:ss] tt | H:mm[:ss]]}; ISO {@code yyyy-M-d[ H:mm[:ss[.f…]] | h:mm[:ss] tt]},
     * {@code yyyy-M-dTH:mm:ss[.f…]}; {@code yyyyMMdd HH:mm:ss}. {@code null} → ArgumentNullException; anything
     * else or an out-of-range field → FormatException("String was not recognized as a valid DateTime.").
     * The same list is used for every culture.
     * TODO(port): confirm that .NET 4.5 accepts "yyyyMMdd HH:mm:ss" (SQLServerDateFormat output).
     */
    public static LocalDateTime Parse(String s, CultureInfo culture) {
        if (s == null) {
            throw new ArgumentNullException("s");
        }
        String t = Strings.Trim(s);
        Matcher m;
        if ((m = US_DATE.matcher(t)).matches()) {
            return build(m.group(3), m.group(1), m.group(2), null, null, null, null, null);
        }
        if ((m = US_12.matcher(t)).matches()) {
            return build(m.group(3), m.group(1), m.group(2), m.group(4), m.group(5), m.group(6), null, m.group(7));
        }
        if ((m = US_24.matcher(t)).matches()) {
            return build(m.group(3), m.group(1), m.group(2), m.group(4), m.group(5), m.group(6), null, null);
        }
        if ((m = ISO_DATE.matcher(t)).matches()) {
            return build(m.group(1), m.group(2), m.group(3), null, null, null, null, null);
        }
        if ((m = ISO_24.matcher(t)).matches()) {
            return build(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6), m.group(7), null);
        }
        if ((m = ISO_12.matcher(t)).matches()) {
            return build(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6), null, m.group(7));
        }
        if ((m = ISO_T.matcher(t)).matches()) {
            return build(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6), m.group(7), null);
        }
        if ((m = SQL.matcher(t)).matches()) {
            return build(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6), null, null);
        }
        throw new FormatException(NOT_RECOGNIZED);
    }

    private static LocalDateTime build(String y, String mo, String d, String h, String mi, String sec,
            String frac, String ampm) {
        try {
            int hour = h == null ? 0 : Integer.parseInt(h);
            if (ampm != null) {
                if (hour > 12) {
                    throw new FormatException(NOT_RECOGNIZED);
                }
                boolean pm = Character.toUpperCase(ampm.charAt(0)) == 'P';
                if (hour == 12) {
                    hour = 0;
                }
                if (pm) {
                    hour += 12;
                }
            }
            int nanos = 0;
            if (frac != null) {
                String f7 = (frac + "0000000").substring(0, 7);
                nanos = Integer.parseInt(f7) * 100;
            }
            return LocalDateTime.of(Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d), hour,
                    mi == null ? 0 : Integer.parseInt(mi), sec == null ? 0 : Integer.parseInt(sec), nanos);
        } catch (DateTimeException | NumberFormatException e) {
            throw new FormatException(NOT_RECOGNIZED);
        }
    }

    /** .NET {@code DateTime.TryParse(s, out result)}: {@link #MinValue} on failure. */
    public static boolean TryParse(String s, Ref<LocalDateTime> result) {
        try {
            LocalDateTime v = Parse(s, CultureInfo.EN_US);
            if (result != null) {
                result.value = v;
            }
            return true;
        } catch (FormatException | ArgumentNullException e) {
            if (result != null) {
                result.value = MinValue;
            }
            return false;
        }
    }

    // ---------------------------------------------------------------------------- ParseExact

    /** .NET {@code DateTime.ParseExact(s, format, provider)}. */
    public static LocalDateTime ParseExact(String s, String format, CultureInfo culture) {
        return ParseExact(s, format, culture, DateTimeStyles_None);
    }

    /**
     * .NET {@code DateTime.ParseExact(s, format, provider, styles)} ({@code edit_task.aspx.cs:434}). A
     * one-character format is expanded as a standard format of the culture; custom specifiers
     * {@code d dd ddd dddd M MM MMM MMMM y yy yyyy h hh H HH m mm s ss f… F… t tt : / 'lit' \c} are matched in
     * order. {@code styles}: the {@code DateTimeStyles_*} bits (AllowWhiteSpaces = 7). Failure →
     * FormatException("String was not recognized as a valid DateTime.").
     */
    public static LocalDateTime ParseExact(String s, String format, CultureInfo culture, int styles) {
        if (s == null) {
            throw new ArgumentNullException("s");
        }
        if (format == null) {
            throw new ArgumentNullException("format");
        }
        if (format.isEmpty()) {
            throw new FormatException("Format specifier was invalid.");
        }
        CultureInfo ci = culture == null ? CultureInfo.EN_US : culture;
        String f = format;
        if (f.length() == 1) {
            String e = DotNetFormat.expandStandardDate(f.charAt(0), ci);
            if (e == null) {
                throw new FormatException("Format specifier was invalid.");
            }
            f = e;
        }
        boolean allowInner = (styles & DateTimeStyles_AllowInnerWhite) != 0;
        String in = s;
        if ((styles & DateTimeStyles_AllowLeadingWhite) != 0) {
            in = Strings.TrimStart(in);
        }
        if ((styles & DateTimeStyles_AllowTrailingWhite) != 0) {
            in = Strings.TrimEnd(in);
        }
        int[] pos = {0};
        int year = 1, month = 1, day = 1, hour = 0, minute = 0, second = 0, nanos = 0;
        boolean hour12 = false;
        Boolean pm = null;
        int i = 0;
        while (i < f.length()) {
            char c = f.charAt(i);
            int n = 1;
            while (i + n < f.length() && f.charAt(i + n) == c && "dMyhHmsfFt".indexOf(c) >= 0) {
                n++;
            }
            if (allowInner && c != ' ') {
                skipWhite(in, pos);
            }
            switch (c) {
                case 'd':
                    if (n <= 2) {
                        day = readInt(in, pos, n, 2);
                    } else {
                        readName(in, pos, n == 3 ? CultureInfo.ABBR_DAY_NAMES : CultureInfo.DAY_NAMES);
                    }
                    break;
                case 'M':
                    if (n <= 2) {
                        month = readInt(in, pos, n, 2);
                    } else {
                        month = readName(in, pos, n == 3 ? CultureInfo.ABBR_MONTH_NAMES : CultureInfo.MONTH_NAMES) + 1;
                    }
                    break;
                case 'y':
                    if (n <= 2) {
                        int yy = readInt(in, pos, n, 2);
                        year = yy + (yy <= 29 ? 2000 : 1900); // TwoDigitYearMax = 2029
                    } else {
                        year = readInt(in, pos, n, Math.max(n, 4));
                    }
                    break;
                case 'h':
                    hour = readInt(in, pos, Math.min(n, 2), 2);
                    hour12 = true;
                    break;
                case 'H':
                    hour = readInt(in, pos, Math.min(n, 2), 2);
                    break;
                case 'm':
                    minute = readInt(in, pos, Math.min(n, 2), 2);
                    break;
                case 's':
                    second = readInt(in, pos, Math.min(n, 2), 2);
                    break;
                case 'f':
                case 'F': {
                    int start = pos[0];
                    int max = n;
                    int got = 0;
                    while (got < max && pos[0] < in.length() && Character.isDigit(in.charAt(pos[0]))
                            && in.charAt(pos[0]) <= '9') {
                        pos[0]++;
                        got++;
                    }
                    if (c == 'f' && got != n) {
                        throw new FormatException(NOT_RECOGNIZED);
                    }
                    if (got > 0) {
                        nanos = Integer.parseInt((in.substring(start, pos[0]) + "0000000").substring(0, 7)) * 100;
                    }
                    break;
                }
                case 't': {
                    String am = ci.amDesignator;
                    String pmd = ci.pmDesignator;
                    if (n == 1) {
                        if (pos[0] < in.length() && Character.toUpperCase(in.charAt(pos[0])) == pmd.charAt(0)) {
                            pm = true;
                        } else if (pos[0] < in.length() && Character.toUpperCase(in.charAt(pos[0])) == am.charAt(0)) {
                            pm = false;
                        } else {
                            throw new FormatException(NOT_RECOGNIZED);
                        }
                        pos[0]++;
                    } else if (in.regionMatches(true, pos[0], pmd, 0, pmd.length())) {
                        pm = true;
                        pos[0] += pmd.length();
                    } else if (in.regionMatches(true, pos[0], am, 0, am.length())) {
                        pm = false;
                        pos[0] += am.length();
                    } else {
                        throw new FormatException(NOT_RECOGNIZED);
                    }
                    break;
                }
                case ':':
                    expect(in, pos, ci.timeSeparator);
                    break;
                case '/':
                    expect(in, pos, ci.dateSeparator);
                    break;
                case '\'':
                case '"': {
                    int j = i + 1;
                    StringBuilder lit = new StringBuilder();
                    while (j < f.length() && f.charAt(j) != c) {
                        if (f.charAt(j) == '\\' && j + 1 < f.length()) {
                            j++;
                        }
                        lit.append(f.charAt(j));
                        j++;
                    }
                    if (j >= f.length()) {
                        throw new FormatException("Format specifier was invalid.");
                    }
                    expect(in, pos, lit.toString());
                    n = j - i + 1;
                    break;
                }
                case '\\':
                    if (i + 1 >= f.length()) {
                        throw new FormatException("Format specifier was invalid.");
                    }
                    expect(in, pos, String.valueOf(f.charAt(i + 1)));
                    n = 2;
                    break;
                case '%':
                    n = 1; // the next character is a single custom specifier: handled on the next iteration
                    break;
                case ' ':
                    if (allowInner) {
                        skipWhite(in, pos);
                    } else {
                        expect(in, pos, " ");
                    }
                    break;
                default:
                    expect(in, pos, String.valueOf(c));
                    break;
            }
            i += n;
        }
        if (allowInner) {
            skipWhite(in, pos);
        }
        if (pos[0] != in.length()) {
            throw new FormatException(NOT_RECOGNIZED);
        }
        if (pm != null) {
            if (hour > 12) {
                throw new FormatException(NOT_RECOGNIZED);
            }
            if (hour == 12) {
                hour = 0;
            }
            if (pm) {
                hour += 12;
            }
        } else if (hour12 && hour == 12) {
            hour = 12;
        }
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second, nanos);
        } catch (DateTimeException e) {
            throw new FormatException(NOT_RECOGNIZED);
        }
    }

    private static void skipWhite(String in, int[] pos) {
        while (pos[0] < in.length() && Strings.isWhiteSpace(in.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    private static void expect(String in, int[] pos, String lit) {
        if (!in.startsWith(lit, pos[0])) {
            throw new FormatException(NOT_RECOGNIZED);
        }
        pos[0] += lit.length();
    }

    private static int readInt(String in, int[] pos, int min, int max) {
        int start = pos[0];
        while (pos[0] < in.length() && pos[0] - start < max && in.charAt(pos[0]) >= '0' && in.charAt(pos[0]) <= '9') {
            pos[0]++;
        }
        if (pos[0] - start < min) {
            throw new FormatException(NOT_RECOGNIZED);
        }
        return Integer.parseInt(in.substring(start, pos[0]));
    }

    private static int readName(String in, int[] pos, String[] names) {
        int best = -1;
        int bestLen = 0;
        for (int k = 0; k < names.length; k++) {
            String nm = names[k];
            if (nm.length() > bestLen && in.regionMatches(true, pos[0], nm, 0, nm.length())) {
                best = k;
                bestLen = nm.length();
            }
        }
        if (best < 0) {
            throw new FormatException(NOT_RECOGNIZED);
        }
        pos[0] += bestLen;
        return best;
    }
}
