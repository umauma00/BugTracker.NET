package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Expected strings are .NET Framework 4.5 en-US outputs (docs: Standard/Custom Date and Time / Numeric Format Strings). */
class DotNetFormatTest {
    private static final LocalDateTime DT = LocalDateTime.of(2026, 11, 3, 14, 25, 1, 123_456_700);

    @Test
    void customDateFormatsUsedBySource() {
        // edit_bug.aspx.cs:295 — backslash-escaped colons are literal ':'; fff truncates
        assertEquals("20261103 14:25:01:123", DotNetFormat.Format(DT, "yyyyMMdd HH\\:mm\\:ss\\:fff", CultureInfo.EN_US));
        // util.cs:99 — "yyy" is a MINIMUM of 3 digits
        assertEquals("2026-11-03 14:25:01:123", DotNetFormat.Format(DT, "yyy-MM-dd HH:mm:ss:fff", CultureInfo.EN_US));
        assertEquals("20261103_142501", DotNetFormat.Format(DT, "yyyyMMdd_HHmmss", CultureInfo.EN_US));
        // POP3Main.cs:240 — h is 12-hour without padding, 00:05 → 12:05 AM
        assertEquals("2026-11-03 12:05 AM",
                DotNetFormat.Format(LocalDateTime.of(2026, 11, 3, 0, 5), "yyyy-MM-dd h:mm tt", CultureInfo.EN_US));
        // F trims trailing zeros and the preceding '.'
        assertEquals("14:25:01", DotNetFormat.Format(DT.withNano(0), "HH:mm:ss.FFF", CultureInfo.EN_US));
        assertEquals("Tue, 03 Nov 2026", DotNetFormat.Format(DT, "ddd, dd MMM yyyy", CultureInfo.EN_US));
    }

    @Test
    void standardDateFormatsEnUsAndInvariant() {
        assertEquals("11/3/2026", DotNetFormat.Format(DT, "d", CultureInfo.EN_US));
        assertEquals("11/3/2026 2:25 PM", DotNetFormat.Format(DT, "g", CultureInfo.EN_US));
        assertEquals("11/3/2026 2:25:01 PM", DotNetFormat.Format(DT, "G", CultureInfo.EN_US));
        // DateTime.ToLongTimeString() = "T"
        assertEquals("2:25:01 PM", DotNetFormat.Format(DT, "T", CultureInfo.EN_US));
        assertEquals("2026-11-03T14:25:01", DotNetFormat.Format(DT, "s", CultureInfo.EN_US));
        assertEquals("11/03/2026 14:25:01", DotNetFormat.Format(DT, "G", CultureInfo.InvariantCulture));
        assertThrows(FormatException.class, () -> DotNetFormat.Format(DT, "Q", CultureInfo.EN_US));
    }

    @Test
    void doubleGeneralIs15SignificantDigits() {
        // S1-6: .NET Framework Double.ToString() = G15
        assertEquals("0.333333333333333", Convert.ToString(1.0 / 3));
        assertEquals("0.3", Convert.ToString(0.1 + 0.2));
        assertEquals("5", Convert.ToString(5.0));
        assertEquals("10000000", Convert.ToString(1e7));
        assertEquals("1E+15", Convert.ToString(1e15));
        assertEquals("1E-05", Convert.ToString(0.00001));
        assertEquals("0.0001", Convert.ToString(0.0001));
        assertEquals("1.79769313486232E+308", Convert.ToString(Double.MAX_VALUE));
        assertEquals("0", Convert.ToString(-0.0));
        assertEquals("NaN", Convert.ToString(Double.NaN));
        assertEquals("-Infinity", Convert.ToString(Double.NEGATIVE_INFINITY));
        assertEquals("0.1", Convert.ToString(0.1f)); // Float G7
    }

    @Test
    void decimalGeneralKeepsScale() {
        // S1-15: decimal G without precision is fixed-point with the scale kept
        assertEquals("1.50", Convert.ToString(new BigDecimal("1.50")));
        assertEquals("1.50", DotNetFormat.Format(new BigDecimal("1.50"), "G", CultureInfo.InvariantCulture));
        assertEquals("100", Convert.ToString(new BigDecimal("1E+2")));
    }

    @Test
    void customNumericFormats() {
        assertEquals("05", DotNetFormat.Format(5, "0#", CultureInfo.EN_US));    // POP3Main.cs:131
        assertEquals("12", DotNetFormat.Format(12, "0#", CultureInfo.EN_US));
        assertEquals("0007", DotNetFormat.Format(7L, "0000", CultureInfo.EN_US)); // dbutil.cs:165
        assertEquals("12345", DotNetFormat.Format(12345L, "0000", CultureInfo.EN_US)); // never truncated
        assertEquals("09", DotNetFormat.Format(9, "00", CultureInfo.EN_US));       // edit_task.aspx.cs:212
        assertEquals("1,234.57", DotNetFormat.Format(1234.5678, "#,##0.00", CultureInfo.EN_US));
        assertEquals("0.00", DotNetFormat.Format(0, "#,##0.00", CultureInfo.EN_US));
        assertEquals("", DotNetFormat.Format(0, "#", CultureInfo.EN_US));
        assertEquals("-42", DotNetFormat.Format(-42, "00", CultureInfo.EN_US));
        assertEquals("50%", DotNetFormat.Format(0.5, "0%", CultureInfo.EN_US));
    }

    @Test
    void standardNumericFormats() {
        assertEquals("FF", DotNetFormat.Format((byte) -1, "X2", CultureInfo.EN_US)); // UpdatePassword.aspx.cs:72
        assertEquals("0A", DotNetFormat.Format(10, "X2", CultureInfo.EN_US));
        assertEquals("ffffffff", DotNetFormat.Format(-1, "x", CultureInfo.EN_US));
        assertEquals("1,234.57", DotNetFormat.Format(1234.5678, "N2", CultureInfo.EN_US));
        assertEquals("2.68", DotNetFormat.Format(2.675, "F2", CultureInfo.EN_US)); // 15-digit then half away from zero
        assertEquals("0.00", DotNetFormat.Format(-0.001, "F2", CultureInfo.EN_US)); // Framework drops "-" on zero
        assertEquals("00042", DotNetFormat.Format(42, "D5", CultureInfo.EN_US));
        assertEquals("50.00 %", DotNetFormat.Format(0.5, "P", CultureInfo.EN_US));
        assertEquals("($1,234.50)", DotNetFormat.Format(-1234.5, "C", CultureInfo.EN_US));
        assertEquals("1.234500E+003", DotNetFormat.Format(1234.5, "E", CultureInfo.EN_US));
        assertEquals("1.2E+04", DotNetFormat.Format(12345, "G2", CultureInfo.EN_US));
        assertThrows(FormatException.class, () -> DotNetFormat.Format(1.5, "D", CultureInfo.EN_US));
    }
}
