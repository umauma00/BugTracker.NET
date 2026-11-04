package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** [S1-6] .NET Framework 4.5 Double/Float G formatting (G15/G7, HALF_UP on the exact binary value), and "R". */
class DoubleFormatTest {

    @Test
    void generalFixedAndScientificThresholds() {
        assertEquals("100000000000000", Convert.ToString(1e14));          // e = 14 < 15 → fixed
        assertEquals("1E+15", Convert.ToString(1e15));                    // e = 15 → scientific
        assertEquals("1.23456789012346E+17", Convert.ToString(123456789012345678.0));
        assertEquals("0.00015", Convert.ToString(0.00015));               // e = -4 → fixed
        assertEquals("1.5E-05", Convert.ToString(0.000015));              // e = -5 → scientific
        assertEquals("-1.5", Convert.ToString(-1.5));
        assertEquals("0.666666666666667", Convert.ToString(2.0 / 3));
        assertEquals("4.94065645841247E-324", Convert.ToString(Double.MIN_VALUE));
        assertEquals("Infinity", Convert.ToString(Double.POSITIVE_INFINITY));
    }

    @Test
    void floatUsesSevenDigits() {
        assertEquals("3.141593", Convert.ToString((float) Math.PI));
        assertEquals("1E+07", Convert.ToString(1e7f));
        assertEquals("0", Convert.ToString(-0.0f));
    }

    @Test
    void explicitPrecisionAndRoundTrip() {
        assertEquals("0.13", DotNetFormat.Format(0.125, "G2", CultureInfo.EN_US));   // exact tie → away from zero
        assertEquals("0.10000000000000001", DotNetFormat.Format(0.1, "G17", CultureInfo.EN_US));
        assertEquals("0.1", DotNetFormat.Format(0.1, "R", CultureInfo.EN_US));
        assertEquals("0.30000000000000004", DotNetFormat.Format(0.1 + 0.2, "R", CultureInfo.EN_US));
        assertEquals("1.5e+20", DotNetFormat.Format(1.5e20, "g", CultureInfo.EN_US));
    }

    @Test
    void concatenationAndCompositeFormattingUseG15() {
        assertEquals("0.3", Strings.cs(0.1 + 0.2));
        assertEquals("x=0.3", Strings.Format("x={0}", 0.1 + 0.2));
        assertEquals("1E-05", Strings.Format("{0}", 0.00001));
    }
}
