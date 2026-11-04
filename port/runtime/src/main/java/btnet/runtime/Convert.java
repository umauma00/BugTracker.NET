package btnet.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Stands for .NET {@code System.Convert} (RULEBOOK §2A "Conversions and parsing", §3 J-C10/J-C11/J-C19, S1-6).
 * Values carry .NET types as mapped by the DataTable facade (Integer, Short, Long, Boolean, BigDecimal,
 * Double, Float, LocalDateTime, String, byte[], UUID, {@link DBNull}).
 */
public final class Convert {
    private Convert() {
    }

    private static final String DBNULL_CAST = "Object cannot be cast from DBNull to other types.";

    // ------------------------------------------------------------------ ToString

    /**
     * .NET {@code Convert.ToString(object)}: null/DBNull → {@code ""}; Boolean → {@code True/False}; LocalDateTime
     * → en-US {@code G}; BigDecimal → plain with scale; Double → G15, Float → G7 (S1-6); Throwable →
     * {@link Exceptions#dotNetToString}; TimeSpan (Duration) → {@code c}; byte[] → {@code System.Byte[]}.
     */
    public static String ToString(Object o) {
        return ToString(o, CultureInfo.EN_US);
    }

    /** .NET {@code Convert.ToString(object, provider)}. */
    public static String ToString(Object o, CultureInfo culture) {
        if (culture == null) {
            culture = CultureInfo.EN_US;
        }
        if (o == null || o == DBNull.Value) {
            return "";
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (o instanceof Character c) {
            return String.valueOf(c.charValue());
        }
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte
                || o instanceof BigInteger) {
            return o.toString();
        }
        if (o instanceof Double || o instanceof Float || o instanceof BigDecimal || o instanceof LocalDateTime
                || o instanceof Duration) {
            return DotNetFormat.Format(o, null, culture);
        }
        if (o instanceof Throwable t) {
            return Exceptions.dotNetToString(t);
        }
        if (o instanceof byte[]) {
            return "System.Byte[]";
        }
        if (o instanceof Enum<?> e) {
            return e.name();
        }
        return o.toString();
    }

    // ------------------------------------------------------------------ integers

    private static String typeName(Object o) {
        if (o instanceof LocalDateTime) {
            return "DateTime";
        }
        if (o instanceof Duration) {
            return "TimeSpan";
        }
        return o.getClass().getSimpleName();
    }

    private static long toIntegral(Object o, long min, long max, String target, String overflowName,
            CultureInfo culture) {
        if (o == null) {
            return 0;
        }
        if (o == DBNull.Value) {
            throw new InvalidCastException(DBNULL_CAST);
        }
        String overflow = "Value was either too large or too small for " + overflowName + ".";
        if (o instanceof String s) {
            BigDecimal v = Decimal.parseNumber(s, NumberStyles.Integer, culture == null ? CultureInfo.EN_US : culture);
            if (v == null) {
                throw new FormatException("Input string was not in a correct format.");
            }
            if (v.compareTo(BigDecimal.valueOf(max)) > 0 || v.compareTo(BigDecimal.valueOf(min)) < 0) {
                throw new OverflowException(overflow);
            }
            return v.longValueExact();
        }
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) {
            long v = ((Number) o).longValue();
            if (v < min || v > max) {
                throw new OverflowException(overflow);
            }
            return v;
        }
        if (o instanceof BigInteger b) {
            if (b.compareTo(BigInteger.valueOf(max)) > 0 || b.compareTo(BigInteger.valueOf(min)) < 0) {
                throw new OverflowException(overflow);
            }
            return b.longValue();
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (o instanceof Character c) {
            long v = c;
            if (v > max) {
                throw new OverflowException(overflow);
            }
            return v;
        }
        if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new OverflowException(overflow);
            }
            double r = Math.rint(d); // round half to even
            if (r < (double) min || r > (double) max) {
                throw new OverflowException(overflow);
            }
            BigDecimal bd = new BigDecimal(r);
            if (bd.compareTo(BigDecimal.valueOf(max)) > 0 || bd.compareTo(BigDecimal.valueOf(min)) < 0) {
                throw new OverflowException(overflow);
            }
            return bd.longValue();
        }
        if (o instanceof BigDecimal bd) {
            BigDecimal r = bd.setScale(0, RoundingMode.HALF_EVEN);
            if (r.compareTo(BigDecimal.valueOf(max)) > 0 || r.compareTo(BigDecimal.valueOf(min)) < 0) {
                throw new OverflowException(overflow);
            }
            return r.longValue();
        }
        if (o instanceof LocalDateTime) {
            throw new InvalidCastException("Invalid cast from 'DateTime' to '" + target + "'.");
        }
        throw new InvalidCastException(
                "Unable to cast object of type '" + typeName(o) + "' to type 'System.IConvertible'.");
    }

    /** .NET {@code Convert.ToInt32(object)}: null → 0; DBNull → InvalidCastException; double/decimal → banker's rounding. */
    public static int ToInt32(Object o) {
        return (int) toIntegral(o, Integer.MIN_VALUE, Integer.MAX_VALUE, "Int32", "an Int32", CultureInfo.EN_US);
    }

    public static int ToInt32(Object o, CultureInfo culture) {
        return (int) toIntegral(o, Integer.MIN_VALUE, Integer.MAX_VALUE, "Int32", "an Int32", culture);
    }

    /** .NET {@code Convert.ToInt64(object)} [J-C10]. */
    public static long ToInt64(Object o) {
        return toIntegral(o, Long.MIN_VALUE, Long.MAX_VALUE, "Int64", "an Int64", CultureInfo.EN_US);
    }

    public static long ToInt64(Object o, CultureInfo culture) {
        return toIntegral(o, Long.MIN_VALUE, Long.MAX_VALUE, "Int64", "an Int64", culture);
    }

    /** .NET {@code Convert.ToInt16(object)}. */
    public static short ToInt16(Object o) {
        return (short) toIntegral(o, Short.MIN_VALUE, Short.MAX_VALUE, "Int16", "an Int16", CultureInfo.EN_US);
    }

    /**
     * .NET {@code Convert.ToByte(object)}: range 0–255; returned as {@code short} because .NET byte values are held
     * as {@link Short} (RULEBOOK §3 S1-7).
     */
    public static short ToByte(Object o) {
        return (short) toIntegral(o, 0, 255, "Byte", "an unsigned byte", CultureInfo.EN_US);
    }

    // ------------------------------------------------------------------ Boolean

    /**
     * .NET {@code Convert.ToBoolean(object)}: null → false; String trimmed (white space and NUL), case-insensitive
     * {@code True/False} else FormatException("String was not recognized as a valid Boolean."); numbers ≠ 0.
     */
    public static boolean ToBoolean(Object o) {
        if (o == null) {
            return false;
        }
        if (o == DBNull.Value) {
            throw new InvalidCastException(DBNULL_CAST);
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            String t = Strings.Trim(s, ' ', '\t', '\n', '\u000B', '\f', '\r', '\u0085', ' ', '\u0000');
            if (t.equalsIgnoreCase("True")) {
                return true;
            }
            if (t.equalsIgnoreCase("False")) {
                return false;
            }
            throw new FormatException("String was not recognized as a valid Boolean.");
        }
        if (o instanceof BigDecimal bd) {
            return bd.signum() != 0;
        }
        if (o instanceof BigInteger bi) {
            return bi.signum() != 0;
        }
        if (o instanceof Double || o instanceof Float) {
            return ((Number) o).doubleValue() != 0.0;
        }
        if (o instanceof Number n) {
            return n.longValue() != 0;
        }
        if (o instanceof Character) {
            throw new InvalidCastException("Invalid cast from 'Char' to 'Boolean'.");
        }
        if (o instanceof LocalDateTime) {
            throw new InvalidCastException("Invalid cast from 'DateTime' to 'Boolean'.");
        }
        throw new InvalidCastException(
                "Unable to cast object of type '" + typeName(o) + "' to type 'System.IConvertible'.");
    }

    // ------------------------------------------------------------------ Decimal / Double / Single

    /** .NET {@code Convert.ToDecimal(object)} [J-C11]: Double → 15 significant digits, HALF_EVEN, zeros stripped. */
    public static BigDecimal ToDecimal(Object o) {
        return ToDecimal(o, CultureInfo.EN_US);
    }

    public static BigDecimal ToDecimal(Object o, CultureInfo culture) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o == DBNull.Value) {
            throw new InvalidCastException(DBNULL_CAST);
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof String s) {
            return Decimal.Parse(s, NumberStyles.Number, culture);
        }
        if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            int digits = o instanceof Float ? 7 : 15;
            if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) > 7.9228162514264337593543950335e28) {
                throw new OverflowException("Value was either too large or too small for a Decimal.");
            }
            String text = o instanceof Float ? Float.toString((Float) o) : Double.toString(d);
            BigDecimal bd = new BigDecimal(text).round(new MathContext(digits, RoundingMode.HALF_EVEN));
            bd = bd.signum() == 0 ? BigDecimal.ZERO : bd.stripTrailingZeros();
            if (bd.scale() < 0) {
                bd = bd.setScale(0);
            }
            return Decimal.toDecimalRange(bd);
        }
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) {
            return BigDecimal.valueOf(((Number) o).longValue());
        }
        if (o instanceof BigInteger bi) {
            return Decimal.toDecimalRange(new BigDecimal(bi));
        }
        if (o instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (o instanceof LocalDateTime) {
            throw new InvalidCastException("Invalid cast from 'DateTime' to 'Decimal'.");
        }
        if (o instanceof Character) {
            throw new InvalidCastException("Invalid cast from 'Char' to 'Decimal'.");
        }
        throw new InvalidCastException(
                "Unable to cast object of type '" + typeName(o) + "' to type 'System.IConvertible'.");
    }

    /** .NET {@code Convert.ToDouble(object)}: String → {@code Float | AllowThousands}, en-US. */
    public static double ToDouble(Object o) {
        return ToDouble(o, CultureInfo.EN_US);
    }

    public static double ToDouble(Object o, CultureInfo culture) {
        if (o == null) {
            return 0.0;
        }
        if (o == DBNull.Value) {
            throw new InvalidCastException(DBNULL_CAST);
        }
        if (o instanceof String s) {
            String t = Strings.Trim(s);
            NumberFormatInfo nfi = (culture == null ? CultureInfo.EN_US : culture).NumberFormat();
            if (t.equals(nfi.NaNSymbol())) {
                return Double.NaN;
            }
            if (t.equals(nfi.PositiveInfinitySymbol())) {
                return Double.POSITIVE_INFINITY;
            }
            if (t.equals(nfi.NegativeInfinitySymbol())) {
                return Double.NEGATIVE_INFINITY;
            }
            BigDecimal v = Decimal.parseNumber(s, NumberStyles.Float | NumberStyles.AllowThousands,
                    culture == null ? CultureInfo.EN_US : culture);
            if (v == null) {
                throw new FormatException("Input string was not in a correct format.");
            }
            double d = v.doubleValue();
            if (Double.isInfinite(d)) {
                throw new OverflowException("Value was either too large or too small for a Double.");
            }
            return d;
        }
        if (o instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof LocalDateTime) {
            throw new InvalidCastException("Invalid cast from 'DateTime' to 'Double'.");
        }
        if (o instanceof Character) {
            throw new InvalidCastException("Invalid cast from 'Char' to 'Double'.");
        }
        throw new InvalidCastException(
                "Unable to cast object of type '" + typeName(o) + "' to type 'System.IConvertible'.");
    }

    /** .NET {@code Convert.ToSingle(object)} ({@code view_report.aspx}). */
    public static float ToSingle(Object o) {
        if (o instanceof String s) {
            double d = ToDouble(s);
            float f = (float) d;
            if (Float.isInfinite(f) && !Double.isInfinite(d)) {
                throw new OverflowException("Value was either too large or too small for a Single.");
            }
            return f;
        }
        return (float) ToDouble(o);
    }

    // ------------------------------------------------------------------ DateTime / Char

    /** .NET {@code Convert.ToDateTime(object)}: null → MinValue; String → {@link DateTimes#Parse(String, CultureInfo)}. */
    public static LocalDateTime ToDateTime(Object o) {
        return ToDateTime(o, CultureInfo.EN_US);
    }

    public static LocalDateTime ToDateTime(Object o, CultureInfo culture) {
        if (o == null) {
            return DateTimes.MinValue;
        }
        if (o == DBNull.Value) {
            throw new InvalidCastException(DBNULL_CAST);
        }
        if (o instanceof LocalDateTime dt) {
            return dt;
        }
        if (o instanceof String s) {
            return DateTimes.Parse(s, culture == null ? CultureInfo.EN_US : culture);
        }
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte
                || o instanceof Double || o instanceof Float || o instanceof BigDecimal || o instanceof Boolean
                || o instanceof Character) {
            throw new InvalidCastException("Invalid cast from '" + clrName(o) + "' to 'DateTime'.");
        }
        throw new InvalidCastException(
                "Unable to cast object of type '" + typeName(o) + "' to type 'System.IConvertible'.");
    }

    private static String clrName(Object o) {
        if (o instanceof Integer) return "Int32";
        if (o instanceof Long) return "Int64";
        if (o instanceof Short) return "Int16";
        if (o instanceof Byte) return "SByte";
        if (o instanceof Double) return "Double";
        if (o instanceof Float) return "Single";
        if (o instanceof BigDecimal) return "Decimal";
        if (o instanceof Boolean) return "Boolean";
        if (o instanceof Character) return "Char";
        return typeName(o);
    }

    /** .NET {@code Convert.ToChar(object)} ({@code email.cs:202} {@code Convert.ToChar(0)}). */
    public static char ToChar(Object o) {
        if (o == null) {
            return '\0';
        }
        if (o == DBNull.Value) {
            throw new InvalidCastException(DBNULL_CAST);
        }
        if (o instanceof Character c) {
            return c;
        }
        if (o instanceof String s) {
            if (s.length() != 1) {
                throw new FormatException("String must be exactly one character long.");
            }
            return s.charAt(0);
        }
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) {
            long v = ((Number) o).longValue();
            if (v < 0 || v > 0xFFFF) {
                throw new OverflowException("Value was either too large or too small for a character.");
            }
            return (char) v;
        }
        throw new InvalidCastException("Invalid cast from '" + clrName(o) + "' to 'Char'.");
    }

    // ------------------------------------------------------------------ misc

    /** .NET {@code Convert.IsDBNull(o)}. */
    public static boolean IsDBNull(Object o) {
        return o == DBNull.Value;
    }

    /** .NET {@code Convert.ToBase64String(bytes)}. */
    public static String ToBase64String(byte[] bytes) {
        if (bytes == null) {
            throw new ArgumentNullException("inArray");
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** .NET {@code Convert.FromBase64String(s)}. */
    public static byte[] FromBase64String(String s) {
        if (s == null) {
            throw new ArgumentNullException("s");
        }
        try {
            return Base64.getMimeDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new FormatException(
                    "The input is not a valid Base-64 string as it contains a non-base 64 character, more than two padding characters, or an illegal character among the padding characters. ");
        }
    }

    /** .NET {@code Convert.ChangeType(value, type)} with en-US. */
    public static Object ChangeType(Object value, Class<?> type) {
        return ChangeType(value, type, CultureInfo.EN_US);
    }

    /**
     * .NET {@code Convert.ChangeType(value, type, provider)}, used by {@code DataRow.set} for typed columns [J-C11].
     * {@code type} is a token of the §2A typeof table. A value already of the type is returned as is;
     * {@code null} → {@code null}. {@code ClrTypes.Byte} yields a {@link Short} in 0–255.
     */
    public static Object ChangeType(Object value, Class<?> type, CultureInfo culture) {
        if (type == null) {
            throw new ArgumentNullException("conversionType");
        }
        if (value == null) {
            return null;
        }
        if (type == Object.class || (type.isInstance(value) && type != Short.class)) {
            return value;
        }
        if (type == String.class) {
            return ToString(value, culture);
        }
        if (type == Integer.class) {
            return ToInt32(value, culture);
        }
        if (type == Long.class) {
            return ToInt64(value, culture);
        }
        if (type == Short.class) {
            if (value instanceof Short) {
                return value;
            }
            return ToInt16(value);
        }
        if (type == ClrTypes.Byte.class) {
            return ToByte(value);
        }
        if (type == Boolean.class) {
            return ToBoolean(value);
        }
        if (type == Double.class) {
            return ToDouble(value, culture);
        }
        if (type == Float.class) {
            return ToSingle(value);
        }
        if (type == BigDecimal.class) {
            return ToDecimal(value, culture);
        }
        if (type == LocalDateTime.class) {
            return ToDateTime(value, culture);
        }
        if (type == Character.class) {
            return ToChar(value);
        }
        if (type == UUID.class && value instanceof String s) {
            try {
                return UUID.fromString(Strings.Trim(s, '{', '}', ' '));
            } catch (IllegalArgumentException e) {
                throw new FormatException(
                        "Guid should contain 32 digits with 4 dashes (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
            }
        }
        throw new InvalidCastException("Invalid cast from '" + clrName(value) + "' to '"
                + (type == UUID.class ? "Guid" : type.getSimpleName()) + "'.");
    }
}
