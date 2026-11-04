package btnet.runtime;

import java.math.BigDecimal;

/** Stands for .NET {@code System.Int32} parsing ({@code int.Parse}, {@code Int32.Parse}, {@code TryParse}). */
public final class Int32 {
    private Int32() {
    }

    public static final int MaxValue = Integer.MAX_VALUE;
    public static final int MinValue = Integer.MIN_VALUE;

    /**
     * .NET {@code Int32.Parse(s)}: {@code NumberStyles.Integer} (leading/trailing white space 0x09–0x0D/0x20,
     * one leading sign), en-US. {@code null} → ArgumentNullException("s"); otherwise invalid →
     * FormatException("Input string was not in a correct format."); out of range →
     * OverflowException("Value was either too large or too small for an Int32.").
     */
    public static int Parse(String s) {
        return Parse(s, NumberStyles.Integer, CultureInfo.EN_US);
    }

    public static int Parse(String s, CultureInfo culture) {
        return Parse(s, NumberStyles.Integer, culture);
    }

    public static int Parse(String s, int styles) {
        return Parse(s, styles, CultureInfo.EN_US);
    }

    public static int Parse(String s, int styles, CultureInfo culture) {
        if (s == null) {
            throw new ArgumentNullException("s");
        }
        BigDecimal v = Decimal.parseNumber(s, styles, culture == null ? CultureInfo.EN_US : culture);
        if (v == null) {
            throw new FormatException("Input string was not in a correct format.");
        }
        BigDecimal stripped = v.signum() == 0 ? BigDecimal.ZERO : v.stripTrailingZeros();
        if (stripped.scale() > 0) {
            // a fractional part with non-zero digits: .NET reports overflow for integer parsing
            throw new OverflowException("Value was either too large or too small for an Int32.");
        }
        if (v.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0
                || v.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
            throw new OverflowException("Value was either too large or too small for an Int32.");
        }
        return v.intValue();
    }

    /** .NET {@code int.TryParse(s, out result)}: result 0 on failure. */
    public static boolean TryParse(String s, Ref<Integer> result) {
        return TryParse(s, NumberStyles.Integer, CultureInfo.EN_US, result);
    }

    public static boolean TryParse(String s, int styles, CultureInfo culture, Ref<Integer> result) {
        try {
            int v = Parse(s, styles, culture);
            if (result != null) {
                result.value = v;
            }
            return true;
        } catch (ArgumentNullException | FormatException | OverflowException e) {
            if (result != null) {
                result.value = 0;
            }
            return false;
        }
    }
}
