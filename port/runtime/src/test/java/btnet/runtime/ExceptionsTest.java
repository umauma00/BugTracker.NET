package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExceptionsTest {

    @Test
    void messagesUseDotNetText() {
        assertEquals("Object reference not set to an instance of an object.",
                Exceptions.Message(new NullPointerException("Cannot invoke ...")));
        assertEquals("Specified cast is not valid.", Exceptions.Message(new ClassCastException("x")));
        assertEquals("Attempted to divide by zero.", Exceptions.Message(new ArithmeticException("/ by zero")));
        assertEquals("Exception of type 'System.Exception' was thrown.", Exceptions.Message(new DotNetException()));
        assertEquals("Exception of type 'java.lang.RuntimeException' was thrown.",
                Exceptions.Message(new RuntimeException()));
        assertEquals("m\r\nParameter name: p", new ArgumentException("m", "p").getMessage());
        assertEquals("Value cannot be null.\r\nParameter name: key", new ArgumentNullException("key").getMessage());
    }

    @Test
    void dotNetToStringFormat() {
        String s = Exceptions.dotNetToString(new DotNetException("x", new FormatException("y")));
        assertTrue(s.startsWith("System.Exception: x ---> System.FormatException: y"), s);
        assertTrue(s.contains("\r\n   --- End of inner exception stack trace ---"), s);
        assertTrue(Exceptions.dotNetToString(new NullPointerException())
                .startsWith("System.NullReferenceException: Object reference not set"));
    }

    @Test
    void baseOfAndWrap() {
        FormatException inner = new FormatException("i");
        assertSame(inner, Exceptions.baseOf(new DotNetException("o", new DotNetException("m", inner))));
        RuntimeException r = new IllegalStateException();
        assertSame(r, Exceptions.wrap(r));
        assertTrue(Exceptions.wrap(new java.io.FileNotFoundException("f")) instanceof FileNotFoundException);
        assertTrue(Exceptions.wrap(new java.io.IOException("f")) instanceof IOException);
        RuntimeException w = Exceptions.wrap(new InterruptedException());
        assertTrue(Thread.interrupted()); // flag was re-set (S1-22); clears it again
        assertTrue(w instanceof DotNetException);
    }

    @Test
    void collections2() {
        Map<String, Integer> d = new LinkedHashMap<>();
        Collections2.Add(d, "a", 1);
        ArgumentException dup = assertThrows(ArgumentException.class, () -> Collections2.Add(d, "a", 2));
        assertEquals("An item with the same key has already been added.", dup.getMessage());
        KeyNotFoundException k = assertThrows(KeyNotFoundException.class, () -> Collections2.indexer(d, "b"));
        assertEquals("The given key was not present in the dictionary.", k.getMessage());
        ArgumentNullException n = assertThrows(ArgumentNullException.class, () -> Collections2.ContainsKey(d, null));
        assertEquals("Value cannot be null.\r\nParameter name: key", n.getMessage());
    }

    @Test
    void timersInterval() {
        ArgumentException e = assertThrows(ArgumentException.class, () -> Timers.Interval(0));
        assertEquals("'0' is not a valid value for 'Interval'. 'Interval' must be greater than 0.", e.getMessage());
        assertEquals(60000L, Timers.Interval(60 * 1000 * 1));
    }
}
