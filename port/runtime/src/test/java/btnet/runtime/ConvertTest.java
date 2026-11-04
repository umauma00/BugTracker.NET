package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** .NET System.Convert / Int32 / Decimal semantics (docs.microsoft.com Convert.ToInt32(Object) etc.). */
class ConvertTest {

    @Test
    void toInt32() {
        assertEquals(0, Convert.ToInt32(null));
        assertEquals(42, Convert.ToInt32("  42 "));
        assertEquals(-7, Convert.ToInt32("-7"));
        assertEquals(2, Convert.ToInt32(2.5));   // banker's rounding
        assertEquals(4, Convert.ToInt32(3.5));
        assertEquals(2, Convert.ToInt32(new BigDecimal("2.5")));
        assertEquals(1, Convert.ToInt32(true));
        assertEquals(5, Convert.ToInt32((short) 5));
        InvalidCastException e = assertThrows(InvalidCastException.class, () -> Convert.ToInt32(DBNull.Value));
        assertEquals("Object cannot be cast from DBNull to other types.", e.getMessage());
        assertThrows(FormatException.class, () -> Convert.ToInt32("4.0"));
        assertThrows(FormatException.class, () -> Convert.ToInt32(""));
        OverflowException o = assertThrows(OverflowException.class, () -> Convert.ToInt32("2147483648"));
        assertEquals("Value was either too large or too small for an Int32.", o.getMessage());
        assertThrows(OverflowException.class, () -> Convert.ToInt32(3_000_000_000L));
        assertThrows(InvalidCastException.class, () -> Convert.ToInt32(LocalDateTime.now()));
    }

    @Test
    void int32Parse() {
        assertEquals(7, Int32.Parse("+7"));
        assertEquals(-7, Int32.Parse(" -7 "));
        assertThrows(FormatException.class, () -> Int32.Parse("1,000"));
        ArgumentNullException e = assertThrows(ArgumentNullException.class, () -> Int32.Parse(null));
        assertEquals("Value cannot be null.\r\nParameter name: s", e.getMessage());
        Ref<Integer> r = new Ref<>();
        assertFalse(Int32.TryParse("x", r));
        assertEquals(0, r.value);
    }

    @Test
    void toBoolean() {
        assertFalse(Convert.ToBoolean(null));
        assertTrue(Convert.ToBoolean(" true "));
        assertFalse(Convert.ToBoolean("FALSE"));
        assertTrue(Convert.ToBoolean(2));
        FormatException e = assertThrows(FormatException.class, () -> Convert.ToBoolean("1"));
        assertEquals("String was not recognized as a valid Boolean.", e.getMessage());
    }

    @Test
    void toDecimalFromDouble() {
        // J-C11: .NET new Decimal(double) keeps 15 significant digits
        assertEquals(new BigDecimal("0.1"), Convert.ToDecimal(0.1));
        assertEquals(new BigDecimal("0.333333333333333"), Convert.ToDecimal(1.0 / 3));
        assertEquals("1.5", Convert.ToString(Convert.ToDecimal(1.5)));
        assertThrows(OverflowException.class, () -> Convert.ToDecimal(Double.NaN));
    }

    @Test
    void decimalTryParse() {
        Ref<BigDecimal> x = new Ref<>();
        assertTrue(Decimal.TryParse("1.50", NumberStyles.Float, CultureInfo.EN_US, x)); // util.cs:162
        assertEquals("1.50", x.value.toPlainString());
        assertFalse(Decimal.TryParse("1,000", NumberStyles.Float, CultureInfo.EN_US, x)); // Float has no thousands
        assertEquals(BigDecimal.ZERO, x.value);
        assertEquals(new BigDecimal("1000"), Decimal.Parse("1,000", CultureInfo.EN_US));
        assertEquals(0, new BigDecimal("150").compareTo(Decimal.Parse("1.5e2", NumberStyles.Float, CultureInfo.EN_US)));
        assertThrows(FormatException.class, () -> Decimal.Parse("abc", CultureInfo.EN_US));
    }

    @Test
    void toStringOfValues() {
        assertEquals("", Convert.ToString(null));
        assertEquals("", Convert.ToString(DBNull.Value));
        assertEquals("True", Convert.ToString(true));
        assertEquals("11/3/2026 2:25:01 PM", Convert.ToString(LocalDateTime.of(2026, 11, 3, 14, 25, 1)));
        assertEquals("System.Byte[]", Convert.ToString(new byte[1]));
        assertEquals("System.Exception: boom", Convert.ToString(new DotNetException("boom")).split("\r\n")[0]);
    }

    @Test
    void toCharAndChangeType() {
        assertEquals('\0', Convert.ToChar(0)); // email.cs:202
        assertEquals(5, Convert.ChangeType("5", Integer.class));
        assertEquals((short) 200, Convert.ChangeType(200, ClrTypes.Byte.class));
        assertThrows(OverflowException.class, () -> Convert.ChangeType(300, ClrTypes.Byte.class));
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 25, 1),
                Convert.ChangeType("2026-11-03T14:25:01", LocalDateTime.class));
    }
}
