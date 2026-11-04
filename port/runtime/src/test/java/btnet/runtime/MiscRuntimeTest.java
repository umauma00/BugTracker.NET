package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MiscRuntimeTest {

    @Test
    void commandLineToArgvW() {
        // CommandLineToArgvW: 2n backslashes + quote → n backslashes; "" → empty argument
        assertEquals(List.of("a", "b c", "d\\e", ""), Processes.SplitArgs("a \"b c\" d\\\\\"e\" \"\""));
        assertEquals(List.of("a\\\\b"), Processes.SplitArgs("a\\\\b"));
        assertEquals(List.of("say \"hi\""), Processes.SplitArgs("\"say \\\"hi\\\"\""));
        assertEquals(List.of("svn", "log", "--xml"), Processes.command("svn", "log  --xml"));
    }

    @Test
    void encodings() {
        // Encoding.Default = windows-1252; 0x81 decodes to U+0081 like .NET
        assertEquals("\u20AC\u0081\u00E9", Encoding.Default.GetString(new byte[] {(byte) 0x80, (byte) 0x81, (byte) 0xE9}));
        assertArrayEquals(new byte[] {(byte) 0x80, '?'}, Encoding.Default.GetBytes("\u20AC\u4E00"));
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Encoding.UTF8.GetPreamble());
        assertEquals(0, Encoding.Default.GetPreamble().length);
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD}, Encoding.UTF8.GetBytes("\uD800"));
    }

    @Test
    void nameValueCollection() {
        NameValueCollection c = new NameValueCollection();
        c.Add("A", "1");
        c.Add("a", "2");
        assertEquals("1,2", c.get("a"));
        assertEquals(2, c.GetValues("A").length);
        assertArrayEquals(new String[] {"A"}, c.AllKeys());
        c.set("a", null);
        assertNull(c.get("A"));
        assertNull(c.get("missing"));
    }

    @Test
    void stringDictionaryLowerCasesKeys() {
        StringDictionary d = new StringDictionary();
        d.set("MessageInputFile", "x");
        assertEquals("x", d.get("messageinputfile"));
        assertEquals(List.of("messageinputfile"), d.Keys());
        assertNull(d.get("Pop3Server"));
    }

    @Test
    void nullsCoalesceIsLazy() {
        assertEquals("a", Nulls.coalesce("a", () -> { throw new AssertionError(); }));
        assertEquals("b", Nulls.coalesce((String) null, "b"));
    }
}
