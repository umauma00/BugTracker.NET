package btnet.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Stands for .NET {@code IFormattable.ToString(format, provider)} of {@code DateTime}, the numeric types
 * and {@code TimeSpan} (RULEBOOK §2A "Dates, numbers, culture", §3 S1-6/S1-15).
 *
 * <ul>
 * <li>Dates: standard formats {@code d D t T f F g G M m Y y s u o O r R} and the custom specifiers
 * {@code d dd ddd dddd f..fffffff F..FFFFFFF g h hh H HH K m mm M MM MMM MMMM s ss t tt y yy yyy yyyy z zz zzz
 * : / 'lit' "lit" \c %c}.</li>
 * <li>Numbers: standard {@code C D E F G N P R X} (with precision) and custom {@code 0 # . , % ‰ E0 'lit' \c ;}.
 * Double is first reduced to 15 significant digits, Float to 7 (.NET Framework); decimal uses its exact
 * digits; integer types exact. Rounding is half away from zero on those digits (.NET {@code RoundNumber}).</li>
 * </ul>
 * {@code java.time.format.DateTimeFormatter} and {@code java.text.DecimalFormat} are deliberately not used.
 */
public final class DotNetFormat {
    private DotNetFormat() {
    }

    private static final String BAD_FORMAT = "Input string was not in a correct format.";
    private static final String BAD_SPECIFIER = "Format specifier was invalid.";

    /** Formats with en-US. */
    public static String Format(Object value, String format) {
        return Format(value, format, CultureInfo.EN_US);
    }

    /**
     * .NET {@code value.ToString(format, culture)}. {@code null}/DBNull → {@code ""}; a String ignores the
     * format; Boolean → {@code True/False}; any other non-formattable value → {@link Convert#ToString(Object)}.
     */
    public static String Format(Object value, String format, CultureInfo culture) {
        if (culture == null) {
            culture = CultureInfo.EN_US;
        }
        if (value == null || value == DBNull.Value) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof LocalDateTime dt) {
            return formatDate(dt, format, culture);
        }
        if (value instanceof Duration d) {
            return formatTimeSpan(d, format);
        }
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal
                || value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            return formatNumber((Number) value, format, culture);
        }
        return Convert.ToString(value, culture);
    }

    // =====================================================================================
    // Dates
    // =====================================================================================

    /** Expands a one-character standard date format to its custom pattern, or returns null if not standard. */
    static String expandStandardDate(char c, CultureInfo ci) {
        switch (c) {
            case 'd': return ci.shortDatePattern;
            case 'D': return ci.longDatePattern;
            case 't': return ci.shortTimePattern;
            case 'T': return ci.longTimePattern;
            case 'f': return ci.longDatePattern + " " + ci.shortTimePattern;
            case 'F': return ci.longDatePattern + " " + ci.longTimePattern;
            case 'U': return ci.longDatePattern + " " + ci.longTimePattern; // TODO(port): 'U' converts to UTC in .NET
            case 'g': return ci.shortDatePattern + " " + ci.shortTimePattern;
            case 'G': return ci.shortDatePattern + " " + ci.longTimePattern;
            case 'm': case 'M': return ci.monthDayPattern;
            case 'y': case 'Y': return ci.yearMonthPattern;
            case 's': return "yyyy'-'MM'-'dd'T'HH':'mm':'ss";
            case 'u': return "yyyy'-'MM'-'dd HH':'mm':'ss'Z'";
            case 'o': case 'O': return "yyyy'-'MM'-'dd'T'HH':'mm':'ss.fffffffK";
            case 'r': case 'R': return "ddd, dd MMM yyyy HH':'mm':'ss 'GMT'";
            default: return null;
        }
    }

    static String formatDate(LocalDateTime dt, String format, CultureInfo ci) {
        if (format == null || format.isEmpty()) {
            format = "G";
        }
        if (format.length() == 1) {
            char c = format.charAt(0);
            String expanded = expandStandardDate(c, ci);
            if (expanded == null) {
                throw new FormatException(BAD_FORMAT);
            }
            if (c == 'r' || c == 'R' || c == 'o' || c == 'O' || c == 's' || c == 'u') {
                ci = CultureInfo.InvariantCulture;
            }
            format = expanded;
        }
        return formatDateCustom(dt, format, ci);
    }

    private static int repeat(String f, int i, char c) {
        int j = i + 1;
        while (j < f.length() && f.charAt(j) == c) {
            j++;
        }
        return j - i;
    }

    private static void digits(StringBuilder sb, long v, int minLen) {
        String s = Long.toString(v);
        for (int k = s.length(); k < minLen; k++) {
            sb.append('0');
        }
        sb.append(s);
    }

    static String formatDateCustom(LocalDateTime dt, String f, CultureInfo ci) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < f.length()) {
            char ch = f.charAt(i);
            int n;
            switch (ch) {
                case 'g': {
                    n = repeat(f, i, ch);
                    sb.append("A.D.");
                    break;
                }
                case 'h': {
                    n = repeat(f, i, ch);
                    int h = dt.getHour() % 12;
                    digits(sb, h == 0 ? 12 : h, n <= 1 ? 1 : 2);
                    break;
                }
                case 'H': {
                    n = repeat(f, i, ch);
                    digits(sb, dt.getHour(), n <= 1 ? 1 : 2);
                    break;
                }
                case 'm': {
                    n = repeat(f, i, ch);
                    digits(sb, dt.getMinute(), n <= 1 ? 1 : 2);
                    break;
                }
                case 's': {
                    n = repeat(f, i, ch);
                    digits(sb, dt.getSecond(), n <= 1 ? 1 : 2);
                    break;
                }
                case 'f':
                case 'F': {
                    n = repeat(f, i, ch);
                    if (n > 7) {
                        throw new FormatException(BAD_FORMAT);
                    }
                    StringBuilder frac = new StringBuilder();
                    digits(frac, dt.getNano() / 100, 7);
                    String part = frac.substring(0, n);
                    if (ch == 'F') {
                        int e = part.length();
                        while (e > 0 && part.charAt(e - 1) == '0') {
                            e--;
                        }
                        part = part.substring(0, e);
                        if (part.isEmpty() && sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
                            sb.setLength(sb.length() - 1);
                        }
                    }
                    sb.append(part);
                    break;
                }
                case 't': {
                    n = repeat(f, i, ch);
                    String des = dt.getHour() < 12 ? ci.amDesignator : ci.pmDesignator;
                    if (n == 1) {
                        if (!des.isEmpty()) {
                            sb.append(des.charAt(0));
                        }
                    } else {
                        sb.append(des);
                    }
                    break;
                }
                case 'd': {
                    n = repeat(f, i, ch);
                    if (n <= 2) {
                        digits(sb, dt.getDayOfMonth(), n);
                    } else {
                        int dow = dt.getDayOfWeek().getValue() % 7; // Sunday = 0
                        sb.append(n == 3 ? CultureInfo.ABBR_DAY_NAMES[dow] : CultureInfo.DAY_NAMES[dow]);
                    }
                    break;
                }
                case 'M': {
                    n = repeat(f, i, ch);
                    int m = dt.getMonthValue();
                    if (n <= 2) {
                        digits(sb, m, n);
                    } else {
                        sb.append(n == 3 ? CultureInfo.ABBR_MONTH_NAMES[m - 1] : CultureInfo.MONTH_NAMES[m - 1]);
                    }
                    break;
                }
                case 'y': {
                    n = repeat(f, i, ch);
                    int y = dt.getYear();
                    if (n <= 2) {
                        digits(sb, y % 100, n);
                    } else {
                        digits(sb, y, n);
                    }
                    break;
                }
                case 'z': {
                    n = repeat(f, i, ch);
                    ZoneOffset off = ZoneId.systemDefault().getRules().getOffset(dt);
                    int total = off.getTotalSeconds() / 60;
                    sb.append(total < 0 ? '-' : '+');
                    total = Math.abs(total);
                    if (n == 1) {
                        digits(sb, total / 60, 1);
                    } else if (n == 2) {
                        digits(sb, total / 60, 2);
                    } else {
                        digits(sb, total / 60, 2);
                        sb.append(':');
                        digits(sb, total % 60, 2);
                    }
                    break;
                }
                case 'K': {
                    n = 1; // DateTimeKind.Unspecified → nothing
                    break;
                }
                case ':': {
                    n = 1;
                    sb.append(ci.timeSeparator);
                    break;
                }
                case '/': {
                    n = 1;
                    sb.append(ci.dateSeparator);
                    break;
                }
                case '\'':
                case '"': {
                    int j = i + 1;
                    boolean closed = false;
                    while (j < f.length()) {
                        char c2 = f.charAt(j);
                        if (c2 == ch) {
                            closed = true;
                            j++;
                            break;
                        }
                        if (c2 == '\\') {
                            if (j + 1 >= f.length()) {
                                throw new FormatException(BAD_FORMAT);
                            }
                            sb.append(f.charAt(j + 1));
                            j += 2;
                            continue;
                        }
                        sb.append(c2);
                        j++;
                    }
                    if (!closed) {
                        throw new FormatException(BAD_FORMAT);
                    }
                    n = j - i;
                    break;
                }
                case '%': {
                    if (i + 1 < f.length() && f.charAt(i + 1) != '%') {
                        // format exactly the single next character as a custom specifier
                        String one = formatDateCustomSingle(dt, f.charAt(i + 1), ci);
                        sb.append(one);
                        n = 2;
                    } else {
                        throw new FormatException(BAD_FORMAT);
                    }
                    break;
                }
                case '\\': {
                    if (i + 1 >= f.length()) {
                        throw new FormatException(BAD_FORMAT);
                    }
                    sb.append(f.charAt(i + 1));
                    n = 2;
                    break;
                }
                default: {
                    sb.append(ch);
                    n = 1;
                    break;
                }
            }
            i += n;
        }
        return sb.toString();
    }

    private static String formatDateCustomSingle(LocalDateTime dt, char c, CultureInfo ci) {
        // "%d" etc.: a single specifier treated as custom (one repetition).
        return formatDateCustom(dt, c == '\'' || c == '"' || c == '\\' ? "\\" + c : String.valueOf(c), ci);
    }

    /** .NET {@code TimeSpan.ToString()} / {@code "c"}: {@code [-][d.]hh:mm:ss[.fffffff]}. */
    static String formatTimeSpan(Duration d, String format) {
        if (format != null && !format.isEmpty() && !format.equals("c") && !format.equals("t")
                && !format.equals("T")) {
            // TODO(port): TimeSpan "g", "G" and custom formats are not used by the source.
            throw new FormatException(BAD_FORMAT);
        }
        StringBuilder sb = new StringBuilder();
        long ticks = d.getSeconds() * 10_000_000L + d.getNano() / 100;
        if (ticks < 0) {
            sb.append('-');
            ticks = -ticks;
        }
        long days = ticks / 864_000_000_000L;
        long rem = ticks % 864_000_000_000L;
        if (days != 0) {
            sb.append(days).append('.');
        }
        digits(sb, rem / 36_000_000_000L, 2);
        sb.append(':');
        digits(sb, (rem / 600_000_000L) % 60, 2);
        sb.append(':');
        digits(sb, (rem / 10_000_000L) % 60, 2);
        long frac = rem % 10_000_000L;
        if (frac != 0) {
            sb.append('.');
            digits(sb, frac, 7);
        }
        return sb.toString();
    }

    // =====================================================================================
    // Numbers
    // =====================================================================================

    /** Decimal digit representation: value = 0.d1d2d3... × 10^scale, digits without trailing zeros. */
    static final class Num {
        boolean neg;
        String digits; // "" for zero
        int scale;

        Num(boolean neg, String digits, int scale) {
            int e = digits.length();
            while (e > 0 && digits.charAt(e - 1) == '0') {
                e--;
            }
            int s = 0;
            while (s < e && digits.charAt(s) == '0') {
                s++;
                scale--;
            }
            this.digits = digits.substring(s, e);
            if (this.digits.isEmpty()) {
                this.neg = false;
                this.scale = 0;
            } else {
                this.neg = neg;
                this.scale = scale;
            }
        }

        boolean isZero() {
            return digits.isEmpty();
        }

        static Num of(BigDecimal bd) {
            boolean neg = bd.signum() < 0;
            BigDecimal a = bd.abs();
            String u = a.unscaledValue().toString();
            return new Num(neg, u, u.length() - a.scale());
        }

        static Num of(long v) {
            boolean neg = v < 0;
            String u = neg ? new BigInteger(Long.toString(v)).negate().toString() : Long.toString(v);
            return new Num(neg, u, u.length());
        }

        static Num ofDouble(double d, int precision) {
            BigDecimal bd = new BigDecimal(d).round(new MathContext(precision, RoundingMode.HALF_UP));
            return of(bd);
        }

        /** Keep {@code pos} significant digits, rounding half away from zero. */
        Num round(int pos) {
            if (isZero() || pos >= digits.length()) {
                return this;
            }
            if (pos < 0) {
                return new Num(false, "", 0);
            }
            boolean up = digits.charAt(pos) >= '5';
            char[] a = digits.substring(0, pos).toCharArray();
            int sc = scale;
            String kept;
            if (up) {
                int i = a.length - 1;
                while (i >= 0 && a[i] == '9') {
                    a[i] = '0';
                    i--;
                }
                if (i < 0) {
                    kept = "1" + new String(a);
                    sc++;
                } else {
                    a[i]++;
                    kept = new String(a);
                }
            } else {
                kept = new String(a);
            }
            return new Num(neg, kept, sc);
        }

        Num times10(int k) {
            return isZero() ? this : new Num(neg, digits, scale + k);
        }
    }

    private enum Kind { INT, DEC, DBL, FLT }

    private static String zeros(int n) {
        return n <= 0 ? "" : "0".repeat(n);
    }

    static String formatNumber(Number value, String format, CultureInfo ci) {
        NumberFormatInfo nfi = ci.NumberFormat();
        Kind kind;
        if (value instanceof Double) {
            kind = Kind.DBL;
        } else if (value instanceof Float) {
            kind = Kind.FLT;
        } else if (value instanceof BigDecimal) {
            kind = Kind.DEC;
        } else {
            kind = Kind.INT;
        }
        if (format == null || format.isEmpty()) {
            format = "G";
        }

        char fc = format.charAt(0);
        int prec = -1;
        boolean standard = false;
        if ((fc >= 'A' && fc <= 'Z') || (fc >= 'a' && fc <= 'z')) {
            if (format.length() == 1) {
                standard = true;
            } else if (format.length() <= 3) {
                boolean allDigits = true;
                for (int k = 1; k < format.length(); k++) {
                    char d = format.charAt(k);
                    if (d < '0' || d > '9') {
                        allDigits = false;
                    }
                }
                if (allDigits) {
                    standard = true;
                    prec = Integer.parseInt(format.substring(1));
                }
            }
        }

        if (kind == Kind.DBL || kind == Kind.FLT) {
            double d = value.doubleValue();
            if (Double.isNaN(d)) {
                return nfi.NaNSymbol();
            }
            if (Double.isInfinite(d)) {
                return d > 0 ? nfi.PositiveInfinitySymbol() : nfi.NegativeInfinitySymbol();
            }
        }

        if (!standard) {
            return formatCustomNumber(toNum(value, kind, kind == Kind.FLT ? 7 : 15), format, nfi);
        }

        char uc = Character.toUpperCase(fc);
        switch (uc) {
            case 'G': {
                if (kind == Kind.DBL || kind == Kind.FLT) {
                    int p = prec <= 0 ? (kind == Kind.DBL ? 15 : 7) : prec;
                    Num num = Num.ofDouble(value.doubleValue(), p);
                    return general(num, p, fc == 'g' ? 'e' : 'E', nfi);
                }
                if (prec <= 0) {
                    if (kind == Kind.DEC) {
                        return plainDecimal((BigDecimal) value, nfi);
                    }
                    return integerString(value);
                }
                Num num = toNum(value, kind, 0).round(prec);
                return general(num, prec, fc == 'g' ? 'e' : 'E', nfi);
            }
            case 'R': {
                if (kind == Kind.DBL) {
                    double d = value.doubleValue();
                    String g15 = formatNumber(d, "G15", ci);
                    if (Double.parseDouble(formatNumber(d, "G15", CultureInfo.InvariantCulture)) == d) {
                        return g15;
                    }
                    return formatNumber(d, "G17", ci);
                }
                if (kind == Kind.FLT) {
                    float x = value.floatValue();
                    String g7 = formatNumber(x, "G7", ci);
                    if (Float.parseFloat(formatNumber(x, "G7", CultureInfo.InvariantCulture)) == x) {
                        return g7;
                    }
                    return formatNumber(x, "G9", ci);
                }
                throw new FormatException(BAD_SPECIFIER);
            }
            case 'F':
            case 'N': {
                int p = prec < 0 ? nfi.NumberDecimalDigits() : prec;
                Num num = toNum(value, kind, kind == Kind.FLT ? 7 : 15);
                return fixed(num, p, uc == 'N', nfi.NumberDecimalSeparator(), nfi.NumberGroupSeparator(), true);
            }
            case 'P': {
                int p = prec < 0 ? nfi.PercentDecimalDigits() : prec;
                Num num = toNum(value, kind, kind == Kind.FLT ? 7 : 15).times10(2);
                Num r = num.round(num.scale + p);
                String body = fixed(r, p, true, nfi.PercentDecimalSeparator(), nfi.PercentGroupSeparator(), false);
                return (r.neg ? nfi.NegativeSign() : "") + body + " " + nfi.PercentSymbol();
            }
            case 'C': {
                int p = prec < 0 ? nfi.CurrencyDecimalDigits() : prec;
                Num num = toNum(value, kind, kind == Kind.FLT ? 7 : 15);
                Num r = num.round(num.scale + p);
                String body = fixed(r, p, true, nfi.CurrencyDecimalSeparator(), nfi.CurrencyGroupSeparator(), false);
                return r.neg ? "(" + nfi.CurrencySymbol() + body + ")" : nfi.CurrencySymbol() + body;
            }
            case 'E': {
                int p = prec < 0 ? 6 : prec;
                Num num = toNum(value, kind, kind == Kind.FLT ? 7 : 15).round(p + 1);
                StringBuilder sb = new StringBuilder();
                if (num.neg) {
                    sb.append(nfi.NegativeSign());
                }
                String d = num.isZero() ? "0" : num.digits;
                sb.append(d.charAt(0));
                if (p > 0) {
                    sb.append(nfi.NumberDecimalSeparator());
                    String rest = d.substring(1);
                    sb.append(rest).append(zeros(p - rest.length()));
                }
                int e = num.isZero() ? 0 : num.scale - 1;
                sb.append(fc == 'e' ? 'e' : 'E').append(e < 0 ? '-' : '+');
                String es = Integer.toString(Math.abs(e));
                sb.append(zeros(3 - es.length())).append(es);
                return sb.toString();
            }
            case 'D': {
                if (kind != Kind.INT) {
                    throw new FormatException(BAD_SPECIFIER);
                }
                BigInteger bi = toBigInteger(value);
                String s = bi.abs().toString();
                return (bi.signum() < 0 ? nfi.NegativeSign() : "") + zeros(prec - s.length()) + s;
            }
            case 'X': {
                if (kind != Kind.INT) {
                    throw new FormatException(BAD_SPECIFIER);
                }
                String s;
                if (value instanceof Long l) {
                    s = Long.toHexString(l);
                } else if (value instanceof Integer i) {
                    s = Integer.toHexString(i);
                } else if (value instanceof Short sh) {
                    s = Integer.toHexString(sh & 0xFFFF);
                } else if (value instanceof Byte b) {
                    s = Integer.toHexString(b & 0xFF);
                } else {
                    s = ((BigInteger) value).toString(16);
                }
                s = fc == 'X' ? s.toUpperCase(java.util.Locale.ROOT) : s;
                return zeros(prec - s.length()) + s;
            }
            default:
                throw new FormatException(BAD_SPECIFIER);
        }
    }

    private static BigInteger toBigInteger(Number value) {
        if (value instanceof BigInteger b) {
            return b;
        }
        return BigInteger.valueOf(value.longValue());
    }

    private static String integerString(Number value) {
        return toBigInteger(value).toString();
    }

    private static Num toNum(Number value, Kind kind, int precision) {
        switch (kind) {
            case DBL:
            case FLT:
                return Num.ofDouble(value.doubleValue(), precision);
            case DEC:
                return Num.of((BigDecimal) value);
            default:
                if (value instanceof BigInteger b) {
                    return Num.of(new BigDecimal(b));
                }
                return Num.of(value.longValue());
        }
    }

    /** Decimal "G" without precision: fixed-point, scale preserved, culture separator (S1-15). */
    static String plainDecimal(BigDecimal d, NumberFormatInfo nfi) {
        if (d.scale() < 0) {
            d = d.setScale(0);
        }
        String s = d.toPlainString();
        if (!".".equals(nfi.NumberDecimalSeparator())) {
            s = s.replace(".", nfi.NumberDecimalSeparator());
        }
        if (s.startsWith("-")) {
            s = nfi.NegativeSign() + s.substring(1);
        }
        return s;
    }

    /** .NET "G" body for an already-rounded number with the given precision. */
    private static String general(Num num, int precision, char expChar, NumberFormatInfo nfi) {
        if (num.isZero()) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        if (num.neg) {
            sb.append(nfi.NegativeSign());
        }
        String d = num.digits;
        int e = num.scale - 1;
        if (e > -5 && e < precision) {
            if (num.scale <= 0) {
                sb.append('0').append(nfi.NumberDecimalSeparator()).append(zeros(-num.scale)).append(d);
            } else if (num.scale >= d.length()) {
                sb.append(d).append(zeros(num.scale - d.length()));
            } else {
                sb.append(d, 0, num.scale).append(nfi.NumberDecimalSeparator()).append(d.substring(num.scale));
            }
        } else {
            sb.append(d.charAt(0));
            if (d.length() > 1) {
                sb.append(nfi.NumberDecimalSeparator()).append(d.substring(1));
            }
            sb.append(expChar).append(e < 0 ? '-' : '+');
            String es = Integer.toString(Math.abs(e));
            sb.append(zeros(2 - es.length())).append(es);
        }
        return sb.toString();
    }

    private static String group(String intPart, String sep) {
        if (intPart.length() <= 3) {
            return intPart;
        }
        StringBuilder sb = new StringBuilder();
        int first = intPart.length() % 3;
        if (first == 0) {
            first = 3;
        }
        sb.append(intPart, 0, first);
        for (int i = first; i < intPart.length(); i += 3) {
            sb.append(sep).append(intPart, i, i + 3);
        }
        return sb.toString();
    }

    /** "F"/"N" body: {@code decimals} fraction digits, optional grouping, sign optional. */
    private static String fixed(Num num, int decimals, boolean grouping, String decSep, String groupSep,
            boolean withSign) {
        Num r = num.round(num.scale + decimals);
        String d = r.digits;
        String intPart;
        if (r.scale > 0) {
            intPart = d.length() >= r.scale ? d.substring(0, r.scale) : d + zeros(r.scale - d.length());
        } else {
            intPart = "0";
        }
        if (grouping) {
            intPart = group(intPart, groupSep);
        }
        StringBuilder sb = new StringBuilder();
        if (withSign && r.neg) {
            sb.append('-');
        }
        sb.append(intPart);
        if (decimals > 0) {
            sb.append(decSep);
            StringBuilder frac = new StringBuilder();
            if (r.scale < 0) {
                frac.append(zeros(-r.scale));
            }
            int from = Math.max(r.scale, 0);
            if (from < d.length()) {
                frac.append(d.substring(from));
            }
            if (frac.length() > decimals) {
                frac.setLength(decimals);
            }
            sb.append(frac).append(zeros(decimals - frac.length()));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- custom numeric formats

    private static java.util.List<String> sections(String f) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (c == '\\' && i + 1 < f.length()) {
                cur.append(c).append(f.charAt(++i));
            } else if (c == '\'' || c == '"') {
                int j = f.indexOf(c, i + 1);
                if (j < 0) {
                    j = f.length() - 1;
                }
                cur.append(f, i, j + 1);
                i = j;
            } else if (c == ';') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    static String formatCustomNumber(Num num, String format, NumberFormatInfo nfi) {
        java.util.List<String> secs = sections(format);
        String sec;
        boolean signInFormat = false;
        if (secs.size() == 1) {
            sec = secs.get(0);
        } else if (num.isZero()) {
            sec = secs.size() >= 3 && !secs.get(2).isEmpty() ? secs.get(2) : secs.get(0);
        } else if (num.neg) {
            sec = secs.get(1).isEmpty() ? secs.get(0) : secs.get(1);
            signInFormat = !secs.get(1).isEmpty();
        } else {
            sec = secs.get(0);
        }
        return customSection(num, sec, nfi, signInFormat);
    }

    private static String customSection(Num num, String f, NumberFormatInfo nfi, boolean signInFormat) {
        // ---- analysis pass
        int intPlaces = 0;
        int firstZeroInt = -1;  // index (among int placeholders) of the first '0'
        int fracPlaces = 0;
        int lastZeroFrac = 0;   // number of forced fraction digits
        boolean seenPoint = false;
        boolean thousands = false;
        int scaleDiv = 0;       // count of scaling commas
        int percent = 0;
        int permille = 0;
        boolean sci = false;
        int expMin = 0;
        boolean expPlus = false;
        int pendingCommas = 0;
        boolean anyDigitBefore = false;
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                int j = f.indexOf(c, i + 1);
                i = j < 0 ? f.length() : j;
                continue;
            }
            if (sci) {
                continue;
            }
            switch (c) {
                case '0':
                case '#':
                    if (!seenPoint) {
                        if (pendingCommas > 0 && anyDigitBefore) {
                            thousands = true;
                        }
                        pendingCommas = 0;
                        if (c == '0' && firstZeroInt < 0) {
                            firstZeroInt = intPlaces;
                        }
                        intPlaces++;
                        anyDigitBefore = true;
                    } else {
                        fracPlaces++;
                        if (c == '0') {
                            lastZeroFrac = fracPlaces;
                        }
                    }
                    break;
                case '.':
                    if (!seenPoint) {
                        seenPoint = true;
                        scaleDiv += pendingCommas;
                        pendingCommas = 0;
                    }
                    break;
                case ',':
                    if (!seenPoint && anyDigitBefore) {
                        pendingCommas++;
                    }
                    break;
                case '%':
                    percent++;
                    break;
                case '‰':
                    permille++;
                    break;
                case 'E':
                case 'e': {
                    int j = i + 1;
                    if (j < f.length() && (f.charAt(j) == '+' || f.charAt(j) == '-')) {
                        expPlus = f.charAt(j) == '+';
                        j++;
                    }
                    int z = 0;
                    while (j < f.length() && f.charAt(j) == '0') {
                        z++;
                        j++;
                    }
                    if (z > 0) {
                        sci = true;
                        expMin = z;
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (!seenPoint) {
            scaleDiv += pendingCommas;
        }
        int minInt = firstZeroInt < 0 ? 0 : intPlaces - firstZeroInt;

        // ---- value preparation
        Num n = num.times10(2 * percent + 3 * permille - 3 * scaleDiv);
        int exponent = 0;
        if (sci) {
            if (!n.isZero()) {
                int ip = Math.max(intPlaces, 1);
                exponent = n.scale - ip;
                n = new Num(n.neg, n.digits, ip);
                n = n.round(ip + fracPlaces);
                if (n.scale != ip && !n.isZero()) { // rounding carried into a new digit
                    exponent += n.scale - ip;
                    n = new Num(n.neg, n.digits, ip);
                }
            }
        } else {
            n = n.round(n.scale + fracPlaces);
        }
        String d = n.digits;
        String intDigits;
        if (n.isZero() || n.scale <= 0) {
            intDigits = "";
        } else {
            intDigits = d.length() >= n.scale ? d.substring(0, n.scale) : d + zeros(n.scale - d.length());
        }
        if (intDigits.length() < minInt) {
            intDigits = zeros(minInt - intDigits.length()) + intDigits;
        }
        StringBuilder fracB = new StringBuilder();
        if (!n.isZero()) {
            if (n.scale < 0) {
                fracB.append(zeros(-n.scale));
            }
            int from = Math.max(n.scale, 0);
            if (from < d.length()) {
                fracB.append(d.substring(from));
            }
        }
        if (fracB.length() > fracPlaces) {
            fracB.setLength(fracPlaces);
        }
        while (fracB.length() < lastZeroFrac) {
            fracB.append('0');
        }
        // drop trailing zeros beyond the forced ones
        while (fracB.length() > lastZeroFrac && fracB.charAt(fracB.length() - 1) == '0') {
            fracB.setLength(fracB.length() - 1);
        }
        String frac = fracB.toString();
        String grouped = thousands ? group(intDigits, nfi.NumberGroupSeparator()) : intDigits;

        // ---- output pass
        StringBuilder out = new StringBuilder();
        if (n.neg && !signInFormat) {
            out.append(nfi.NegativeSign());
        }
        int intSeen = 0;
        int fracSeen = 0;
        boolean afterPoint = false;
        boolean expDone = false;
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (c == '\\') {
                if (i + 1 < f.length()) {
                    out.append(f.charAt(++i));
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                int j = f.indexOf(c, i + 1);
                if (j < 0) {
                    out.append(f, i + 1, f.length());
                    i = f.length();
                } else {
                    out.append(f, i + 1, j);
                    i = j;
                }
                continue;
            }
            if (expDone && (c == '0' || c == '#' || c == '.' || c == ',')) {
                continue;
            }
            switch (c) {
                case '0':
                case '#':
                    if (!afterPoint) {
                        if (thousands) {
                            if (intSeen == 0) {
                                out.append(grouped);
                            }
                        } else {
                            int idx = intDigits.length() - intPlaces + intSeen; // right-aligned slot
                            if (intSeen == 0 && idx > 0) {
                                out.append(intDigits, 0, idx);
                            }
                            if (idx >= 0 && idx < intDigits.length()) {
                                out.append(intDigits.charAt(idx));
                            }
                        }
                        intSeen++;
                    } else {
                        if (fracSeen < frac.length()) {
                            out.append(frac.charAt(fracSeen));
                        }
                        fracSeen++;
                    }
                    break;
                case '.':
                    if (!afterPoint) {
                        afterPoint = true;
                        if (intPlaces == 0 && !thousands) {
                            out.append(intDigits);
                        }
                        if (!frac.isEmpty()) {
                            out.append(nfi.NumberDecimalSeparator());
                        }
                    }
                    break;
                case ',':
                    break;
                case '%':
                    out.append(nfi.PercentSymbol());
                    break;
                case '‰':
                    out.append(nfi.PerMilleSymbol());
                    break;
                case 'E':
                case 'e': {
                    if (sci && !expDone) {
                        int j = i + 1;
                        if (j < f.length() && (f.charAt(j) == '+' || f.charAt(j) == '-')) {
                            j++;
                        }
                        while (j < f.length() && f.charAt(j) == '0') {
                            j++;
                        }
                        out.append(c);
                        if (exponent < 0) {
                            out.append('-');
                        } else if (expPlus) {
                            out.append('+');
                        }
                        String es = Integer.toString(Math.abs(exponent));
                        out.append(zeros(expMin - es.length())).append(es);
                        i = j - 1;
                        expDone = true;
                    } else {
                        out.append(c);
                    }
                    break;
                }
                default:
                    out.append(c);
                    break;
            }
        }
        return out.toString();
    }
}
