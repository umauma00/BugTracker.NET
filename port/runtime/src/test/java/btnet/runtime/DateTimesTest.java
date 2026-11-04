package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateTimesTest {

    @Test
    void parsePinnedPatterns() {
        CultureInfo c = CultureInfo.EN_US;
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), DateTimes.Parse("11/3/2026", c));
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 25), DateTimes.Parse("11/3/2026 2:25 PM", c));
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 25, 7), DateTimes.Parse("11/3/2026 12:25:07 am", c));
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 25), DateTimes.Parse("11/3/2026 14:25", c));
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), DateTimes.Parse(" 2026-11-03 ", c));
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 25, 1, 123_456_700),
                DateTimes.Parse("2026-11-03T14:25:01.1234567", c));
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 25, 1), DateTimes.Parse("20261103 14:25:01", c));
        FormatException e = assertThrows(FormatException.class, () -> DateTimes.Parse("13/1/2026", c));
        assertEquals("String was not recognized as a valid DateTime.", e.getMessage());
        assertThrows(FormatException.class, () -> DateTimes.Parse("yesterday", c));
        assertThrows(ArgumentNullException.class, () -> DateTimes.Parse(null, c));
    }

    @Test
    void ticksMatchDotNet() {
        assertEquals(0L, DateTimes.ticks(DateTimes.MinValue));
        // new DateTime(2000, 1, 1).Ticks == 630822816000000000
        assertEquals(630822816000000000L, DateTimes.ticks(LocalDateTime.of(2000, 1, 1, 0, 0)));
        assertEquals(LocalDateTime.of(2000, 1, 1, 0, 0, 0, 100), DateTimes.fromTicks(630822816000000001L));
    }

    @Test
    void parseExact() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0),
                DateTimes.ParseExact("2026-11-03", "yyyy-MM-dd", CultureInfo.EN_US));
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 5),
                DateTimes.ParseExact(" 11/3/2026 2:05 PM ", "g", CultureInfo.EN_US, DateTimes.DateTimeStyles_AllowWhiteSpaces));
        assertThrows(FormatException.class, () -> DateTimes.ParseExact("2026/11/03", "yyyy-MM-dd", CultureInfo.EN_US));
    }
}
