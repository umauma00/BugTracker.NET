package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StringsTest {

    @Test
    void compositeFormat() {
        // edit_task.aspx.cs:210 "{0:0000}-{1:00}-{2:00}"
        assertEquals("2026-03-07", Strings.Format("{0:0000}-{1:00}-{2:00}", 2026, 3, 7));
        assertEquals("{1}", Strings.Format("{{{0}}}", 1));
        assertEquals("   ab|1  |", Strings.Format("{0,5}|{1,-3}|", "ab", 1));
        assertEquals("x=", Strings.Format("x={0}", (Object) null)); // null argument → ""
        assertEquals("True 1.5", Strings.Format("{0} {1}", true, 1.5));
        // .NET: "Index (zero based) must be greater than or equal to zero and less than the size of the argument list."
        assertThrows(FormatException.class, () -> Strings.Format("{1}", "a"));
        assertThrows(FormatException.class, () -> Strings.Format("{0", "a"));
        assertThrows(FormatException.class, () -> Strings.Format("}", "a"));
    }

    @Test
    void concatenationSemantics() {
        assertEquals("", Strings.cs(null));
        assertEquals("", Strings.cs(DBNull.Value));
        assertEquals("True", Strings.cs(Boolean.TRUE));
        assertEquals("1.50", Strings.cs(new BigDecimal("1.50")));
        assertThrows(NullPointerException.class, () -> Strings.ToStringStrict(null));
    }

    @Test
    void splitKeepsEmptiesAndUsesFirstSeparatorInArgumentOrder() {
        assertArrayEquals(new String[] {"a", "", "b", ""}, Strings.Split("a,,b,", ','));
        assertArrayEquals(new String[] {"a", "b"}, Strings.Split("a,,b,", StringSplitOptions.RemoveEmptyEntries, ','));
        assertArrayEquals(new String[] {"x", "y"}, Strings.Split("xaby", StringSplitOptions.None, "ab", "a"));
        assertArrayEquals(new String[] {"x", "by"}, Strings.Split("xaby", StringSplitOptions.None, "a", "ab"));
        assertArrayEquals(new String[] {"a", "b"}, Strings.Split("a b", new char[0])); // no separators → white space
    }

    @Test
    void trimUsesDotNetWhiteSpace() {
        assertEquals("x", Strings.Trim("\u00A0 x\t\r\n"));
        assertEquals("x,", Strings.TrimStart(",x,", ','));
        assertEquals(",x", Strings.TrimEnd(",x,", ','));
    }

    @Test
    void caseMappingIsLengthPreserving() {
        assertEquals("STRASSE".length() - 1, Strings.ToUpper("straße").length());
        assertEquals("STRAßE", Strings.ToUpper("straße"));
        assertEquals("abc", Strings.ToLower("ABC"));
        assertThrows(NullPointerException.class, () -> Strings.ToLower(null));
    }

    @Test
    void indexOfOverloads() {
        assertEquals(2, Strings.IndexOf("Hello", "LL", StringComparison.OrdinalIgnoreCase));
        assertEquals(-1, Strings.IndexOf("Hello", "LL", StringComparison.Ordinal));
        assertEquals(0, Strings.IndexOf("Hello", "", StringComparison.Ordinal));
        assertEquals(3, Strings.IndexOf("abc", "", 3));
        // .NET throws where Java clamps (C27, version_control.cs:470)
        assertThrows(ArgumentOutOfRangeException.class, () -> Strings.IndexOf("abc", " ", -1));
        assertThrows(ArgumentOutOfRangeException.class, () -> Strings.IndexOf("abc", ' ', 4));
        assertThrows(ArgumentNullException.class, () -> Strings.IndexOf("abc", null, StringComparison.Ordinal));
    }

    @Test
    void joinPrintsNullAsEmpty() {
        assertEquals(", a, 1", Strings.join(", ", Arrays.asList(null, "a", 1)));
        assertEquals("a-b", Strings.join("-", new String[] {"a", "b"}));
        assertEquals("ab", Strings.join(null, new Object[] {"a", "b"}));
    }

    @Test
    void cultureComparerLowerBeforeUpperNullFirst() {
        assertEquals(true, Strings.CULTURE_COMPARER.compare("a", "A") < 0);
        assertEquals(true, Strings.CULTURE_COMPARER.compare("A", "b") < 0);
        assertEquals(true, Strings.CULTURE_COMPARER.compare(null, "a") < 0);
    }
}
