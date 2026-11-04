package btnet.runtime;

/** Stands for the .NET flags enum {@code System.Text.RegularExpressions.RegexOptions} (RULEBOOK §3 J-C5). */
public final class RegexOptions {
    private RegexOptions() {
    }

    public static final int None = 0;
    public static final int IgnoreCase = 1;
    public static final int Multiline = 2;
    public static final int ExplicitCapture = 4;
    public static final int Compiled = 8;
    public static final int Singleline = 16;
    public static final int IgnorePatternWhitespace = 32;
    public static final int RightToLeft = 64;
    public static final int ECMAScript = 256;
    public static final int CultureInvariant = 512;
}
