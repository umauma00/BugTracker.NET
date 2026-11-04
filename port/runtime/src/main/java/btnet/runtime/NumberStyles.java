package btnet.runtime;

/** Stands for the .NET flags enum {@code System.Globalization.NumberStyles} (RULEBOOK §3 J-C5), .NET values. */
public final class NumberStyles {
    private NumberStyles() {
    }

    public static final int None = 0;
    public static final int AllowLeadingWhite = 1;
    public static final int AllowTrailingWhite = 2;
    public static final int AllowLeadingSign = 4;
    public static final int AllowTrailingSign = 8;
    public static final int AllowParentheses = 16;
    public static final int AllowDecimalPoint = 32;
    public static final int AllowThousands = 64;
    public static final int AllowExponent = 128;
    public static final int AllowCurrencySymbol = 256;
    public static final int AllowHexSpecifier = 512;
    public static final int Integer = 7;
    public static final int HexNumber = 515;
    public static final int Number = 111;
    public static final int Float = 167;
    public static final int Currency = 383;
    public static final int Any = 511;
}
