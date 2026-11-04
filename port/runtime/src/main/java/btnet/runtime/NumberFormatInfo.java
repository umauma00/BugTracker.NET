package btnet.runtime;

/**
 * Stands for .NET {@code System.Globalization.NumberFormatInfo} (RULEBOOK §3 S1-15): .NET property names
 * as accessor methods. en-US and invariant both use {@code "."} decimal and {@code ","} group separators.
 */
public final class NumberFormatInfo {
    private final String currencySymbol;

    NumberFormatInfo(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    /** .NET {@code NumberFormatInfo.CurrentInfo} (en-US). */
    public static NumberFormatInfo CurrentInfo() {
        return CultureInfo.EN_US.NumberFormat();
    }

    /** .NET {@code NumberFormatInfo.InvariantInfo}. */
    public static NumberFormatInfo InvariantInfo() {
        return CultureInfo.InvariantCulture.NumberFormat();
    }

    public String NumberDecimalSeparator() { return "."; }
    public String NumberGroupSeparator() { return ","; }
    public int NumberDecimalDigits() { return 2; }
    public String CurrencyDecimalSeparator() { return "."; }
    public String CurrencyGroupSeparator() { return ","; }
    public int CurrencyDecimalDigits() { return 2; }
    public String CurrencySymbol() { return currencySymbol; }
    public String PercentDecimalSeparator() { return "."; }
    public String PercentGroupSeparator() { return ","; }
    public int PercentDecimalDigits() { return 2; }
    public String PercentSymbol() { return "%"; }
    public String PerMilleSymbol() { return "‰"; }
    public String NegativeSign() { return "-"; }
    public String PositiveSign() { return "+"; }
    public String NaNSymbol() { return "NaN"; }
    public String PositiveInfinitySymbol() { return "Infinity"; }
    public String NegativeInfinitySymbol() { return "-Infinity"; }
}
