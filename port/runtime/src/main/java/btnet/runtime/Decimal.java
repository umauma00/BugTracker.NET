package btnet.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Stands for .NET {@code System.Decimal} parsing ({@code decimal.Parse}, {@code Decimal.TryParse}) over
 * {@link BigDecimal} (RULEBOOK §2A Conversions). Also hosts the package's .NET {@code Number.ParseNumber}
 * emulation used by {@link Int32}, {@link Convert}.
 */
public final class Decimal {
    private Decimal() {
    }

    /** .NET {@code decimal.MaxValue}. */
    public static final BigDecimal MaxValue = new BigDecimal("79228162514264337593543950335");
    /** .NET {@code decimal.MinValue}. */
    public static final BigDecimal MinValue = MaxValue.negate();

    private static final BigInteger MAX_MANTISSA = MaxValue.toBigInteger();

    /** .NET {@code decimal.Parse(s)}: {@code NumberStyles.Number}, en-US. */
    public static BigDecimal Parse(String s) {
        return Parse(s, NumberStyles.Number, CultureInfo.EN_US);
    }

    /** .NET {@code decimal.Parse(s, provider)}: {@code NumberStyles.Number}. */
    public static BigDecimal Parse(String s, CultureInfo culture) {
        return Parse(s, NumberStyles.Number, culture);
    }

    /** .NET {@code decimal.Parse(s, styles)}. */
    public static BigDecimal Parse(String s, int styles) {
        return Parse(s, styles, CultureInfo.EN_US);
    }

    /**
     * .NET {@code decimal.Parse(s, styles, provider)}: {@code null} → ArgumentNullException("s"); bad text →
     * FormatException("Input string was not in a correct format."); out of range → OverflowException.
     */
    public static BigDecimal Parse(String s, int styles, CultureInfo culture) {
        if (s == null) {
            throw new ArgumentNullException("s");
        }
        BigDecimal v = parseNumber(s, styles, culture == null ? CultureInfo.EN_US : culture);
        if (v == null) {
            throw new FormatException("Input string was not in a correct format.");
        }
        return toDecimalRange(v);
    }

    /** .NET {@code decimal.TryParse(s, out result)} (Number style, en-US). */
    public static boolean TryParse(String s, Ref<BigDecimal> result) {
        return TryParse(s, NumberStyles.Number, CultureInfo.EN_US, result);
    }

    /**
     * .NET {@code Decimal.TryParse(s, styles, provider, out result)} (util.cs:162). On failure
     * {@code result.value} is {@code 0} and the method returns false; never throws for bad input.
     */
    public static boolean TryParse(String s, int styles, CultureInfo culture, Ref<BigDecimal> result) {
        BigDecimal v = s == null ? null : parseNumber(s, styles, culture == null ? CultureInfo.EN_US : culture);
        if (v != null) {
            try {
                v = toDecimalRange(v);
            } catch (OverflowException e) {
                v = null;
            }
        }
        if (result != null) {
            result.value = v == null ? BigDecimal.ZERO : v;
        }
        return v != null;
    }

    /** Clamps a parsed value to .NET decimal: scale ≤ 28, 96-bit mantissa, else OverflowException. */
    static BigDecimal toDecimalRange(BigDecimal v) {
        if (v.scale() < 0) {
            v = v.setScale(0);
        }
        if (v.abs().compareTo(MaxValue) > 0) {
            BigDecimal r = v.setScale(0, RoundingMode.HALF_EVEN);
            if (r.abs().compareTo(MaxValue) > 0) {
                throw new OverflowException("Value was either too large or too small for a Decimal.");
            }
            return r;
        }
        if (v.scale() > 28) {
            v = v.setScale(28, RoundingMode.HALF_EVEN);
        }
        while (v.scale() > 0 && v.unscaledValue().abs().compareTo(MAX_MANTISSA) > 0) {
            v = v.setScale(v.scale() - 1, RoundingMode.HALF_EVEN);
        }
        return v;
    }

    static boolean isNumberWhite(char c) {
        return c == 0x20 || (c >= 0x09 && c <= 0x0D);
    }

    /**
     * .NET {@code Number.ParseNumber} for the given {@link NumberStyles} bits (hex excluded): returns the exact
     * value, or {@code null} when the text does not match the style. Trailing {@code '\0'} characters are
     * ignored as in .NET.
     */
    static BigDecimal parseNumber(String s, int styles, CultureInfo culture) {
        NumberFormatInfo nfi = culture.NumberFormat();
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\0') {
            end--;
        }
        int i = 0;
        boolean neg = false;
        boolean signSeen = false;
        boolean parens = false;
        String cur = nfi.CurrencySymbol();
        String dec = nfi.NumberDecimalSeparator();
        String grp = nfi.NumberGroupSeparator();
        // leading part
        while (i < end) {
            char c = s.charAt(i);
            if ((styles & NumberStyles.AllowLeadingWhite) != 0 && isNumberWhite(c) && !signSeen && !parens) {
                i++;
            } else if ((styles & NumberStyles.AllowLeadingSign) != 0 && !signSeen
                    && s.startsWith(nfi.PositiveSign(), i)) {
                signSeen = true;
                i += nfi.PositiveSign().length();
            } else if ((styles & NumberStyles.AllowLeadingSign) != 0 && !signSeen
                    && s.startsWith(nfi.NegativeSign(), i)) {
                signSeen = true;
                neg = true;
                i += nfi.NegativeSign().length();
            } else if ((styles & NumberStyles.AllowParentheses) != 0 && !parens && !signSeen && c == '(') {
                parens = true;
                neg = true;
                i++;
            } else if ((styles & NumberStyles.AllowCurrencySymbol) != 0 && !cur.isEmpty() && s.startsWith(cur, i)) {
                i += cur.length();
            } else {
                break;
            }
        }
        StringBuilder digits = new StringBuilder();
        int fracDigits = 0;
        boolean any = false;
        while (i < end) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                any = true;
                i++;
            } else if ((styles & NumberStyles.AllowThousands) != 0 && any && s.startsWith(grp, i)) {
                i += grp.length();
            } else {
                break;
            }
        }
        if ((styles & NumberStyles.AllowDecimalPoint) != 0 && i < end && s.startsWith(dec, i)) {
            i += dec.length();
            while (i < end) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                    fracDigits++;
                    any = true;
                    i++;
                } else {
                    break;
                }
            }
        }
        if (!any) {
            return null;
        }
        long exp = 0;
        if ((styles & NumberStyles.AllowExponent) != 0 && i < end && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            int j = i + 1;
            boolean eneg = false;
            if (j < end && s.startsWith(nfi.PositiveSign(), j)) {
                j += nfi.PositiveSign().length();
            } else if (j < end && s.startsWith(nfi.NegativeSign(), j)) {
                j += nfi.NegativeSign().length();
                eneg = true;
            }
            int startDigits = j;
            while (j < end && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
                if (exp < 100_000) {
                    exp = exp * 10 + (s.charAt(j) - '0');
                }
                j++;
            }
            if (j > startDigits) {
                i = j;
                if (eneg) {
                    exp = -exp;
                }
            } else {
                exp = 0;
            }
        }
        // trailing part
        while (i < end) {
            char c = s.charAt(i);
            if ((styles & NumberStyles.AllowTrailingWhite) != 0 && isNumberWhite(c)) {
                i++;
            } else if ((styles & NumberStyles.AllowTrailingSign) != 0 && !signSeen && !parens
                    && s.startsWith(nfi.PositiveSign(), i)) {
                signSeen = true;
                i += nfi.PositiveSign().length();
            } else if ((styles & NumberStyles.AllowTrailingSign) != 0 && !signSeen && !parens
                    && s.startsWith(nfi.NegativeSign(), i)) {
                signSeen = true;
                neg = true;
                i += nfi.NegativeSign().length();
            } else if (parens && c == ')') {
                parens = false;
                i++;
            } else if ((styles & NumberStyles.AllowCurrencySymbol) != 0 && !cur.isEmpty() && s.startsWith(cur, i)) {
                i += cur.length();
            } else {
                break;
            }
        }
        if (i != end || parens) {
            return null;
        }
        BigDecimal v = new BigDecimal(new BigInteger(digits.toString()), fracDigits);
        if (exp != 0) {
            v = v.scaleByPowerOfTen((int) exp);
        }
        return neg ? v.negate() : v;
    }
}
