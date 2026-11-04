package btnet.runtime.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import btnet.runtime.DirectoryNotFoundException;
import btnet.runtime.FileNotFoundException;
import btnet.runtime.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** .NET System.IO semantics (docs: Path, File.AppendText, FileMode, XmlTextReader). */
class IoTest {
    @TempDir
    java.nio.file.Path tmp;

    private String p(String name) {
        return tmp.resolve(name).toString().replace('\\', '/');
    }

    @Test
    void pathMembers() {
        assertEquals("y.txt", Path.GetFileName("C:\\x\\y.txt"));
        assertEquals(".TXT", Path.GetExtension("a.TXT"));
        assertEquals("", Path.GetExtension("a."));
        assertEquals("", Path.GetExtension("noext"));
        assertEquals("a.b", Path.GetFileNameWithoutExtension("dir/a.b.c"));
        assertEquals("/a/b", Path.GetDirectoryName("/a/b/c.txt"));
        assertEquals("", Path.GetDirectoryName("c.txt"));
        assertNull(Path.GetDirectoryName(null));
        assertEquals("a/b", Path.Combine("a", "b"));
        assertEquals("a/b", Path.Combine("a\\", "b"));
        assertEquals("/b", Path.Combine("a", "/b"));
        assertTrue(Path.IsPathRooted("C:\\x"));
        assertFalse(Path.IsPathRooted("x"));
        assertEquals(12, Path.GetRandomFileName().length());
    }

    @Test
    void appendTextWritesCrlfWithoutBom() throws Exception {
        String f = p("log.txt");
        try (StreamWriter w = File.AppendText(f)) {
            w.WriteLine("é");
        }
        StreamWriter w2 = File.AppendText(f);
        w2.WriteLine("x");
        w2.Close();
        w2.Close(); // idempotent
        assertArrayEquals("é\r\nx\r\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(tmp.resolve("log.txt")));
        DirectoryNotFoundException e = assertThrows(DirectoryNotFoundException.class, () -> File.AppendText(p("nope/x.txt")));
        assertTrue(e.getMessage().startsWith("Could not find a part of the path '"));
    }

    @Test
    void openTextStripsBomAndReadsLines() throws Exception {
        Files.write(tmp.resolve("s.css"), new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', '\r', '\n', 'b', '\r', 'c'});
        StreamReader r = File.OpenText(p("s.css"));
        assertEquals("a", r.ReadLine());
        assertEquals("b", r.ReadLine());
        assertEquals("c", r.ReadLine());
        assertNull(r.ReadLine());
        r.Close();
        assertThrows(FileNotFoundException.class, () -> File.OpenText(p("missing.html")));
    }

    @Test
    void fileStreamModes() throws Exception {
        String f = p("att.bin");
        try (FileStream fs = new FileStream(f, FileMode.CreateNew, FileAccess.Write)) {
            fs.Write(new byte[] {1, 2, 3}, 0, 3);
        }
        IOException e = assertThrows(IOException.class, () -> new FileStream(f, FileMode.CreateNew, FileAccess.Write));
        assertTrue(e.getMessage().endsWith("' already exists."), e.getMessage());
        assertEquals(3, Files.size(tmp.resolve("att.bin"))); // CreateNew never truncates
        try (FileStream in = new FileStream(f, FileMode.Open, FileAccess.Read, FileShare.Read)) {
            byte[] buf = new byte[10];
            assertEquals(3, in.Read(buf, 0, 10));
            assertEquals(0, in.Read(buf, 0, 10));
            assertEquals(3, in.Length());
        }
        assertThrows(FileNotFoundException.class, () -> new FileStream(p("x"), FileMode.Open, FileAccess.Read));
    }

    @Test
    void memoryStream() {
        MemoryStream ms = new MemoryStream(new byte[] {9, 8});
        assertEquals(2, ms.Length());
        ms.Position(2);
        ms.Write(new byte[] {7}, 0, 1);
        ms.Position(0);
        byte[] b = new byte[5];
        assertEquals(3, ms.Read(b, 0, 5));
        assertArrayEquals(new byte[] {9, 8, 7}, ms.ToArray());
        ms.Dispose();
        ms.Dispose();
    }

    @Test
    void fileAndDirectoryMembers() throws Exception {
        Directory.CreateDirectory(p("d/e"));
        assertTrue(Directory.Exists(p("d/e")));
        Files.writeString(tmp.resolve("d/a.txt"), "1");
        Files.writeString(tmp.resolve("d/b.txtx"), "2");
        Files.writeString(tmp.resolve("d/c.log"), "3");
        String[] txt = Directory.GetFiles(p("d"), "*.txt"); // 3-char extension quirk matches b.txtx too
        assertEquals(2, txt.length);
        assertTrue(txt[0].endsWith("/a.txt"));
        assertTrue(File.Exists(p("d/a.txt")));
        assertFalse(File.Exists(p("d")));
        File.Delete(p("d/missing.txt")); // no exception for a missing file
        File.Move(p("d/a.txt"), p("d/z.txt"));
        assertThrows(IOException.class, () -> File.Move(p("d/b.txtx"), p("d/z.txt")));
        assertThrows(IOException.class, () -> Directory.Delete(p("d")));
        File.WriteAllBytes(p("d/raw.eml"), new byte[] {1});
        assertArrayEquals(new byte[] {1}, File.ReadAllBytes(p("d/raw.eml")));
    }

    @Test
    void xmlTextReaderIsLazyAndReportsEmptyElementsOnce() throws Exception {
        XmlTextReader missing = new XmlTextReader(p("none.config")); // constructor does not open (C35)
        assertThrows(FileNotFoundException.class, missing::Read);

        Files.writeString(tmp.resolve("svc.config"),
                "<?xml version=\"1.0\"?><configuration><add key=\"k\" value=\"v\"/><website>x</website></configuration>");
        XmlTextReader tr = new XmlTextReader(p("svc.config"));
        assertTrue(tr.Read());
        assertEquals(XmlNodeType.XmlDeclaration, tr.NodeType());
        assertTrue(tr.Read());
        assertEquals("configuration", tr.Name());
        assertTrue(tr.Read());
        assertEquals("add", tr.Name());
        assertEquals(XmlNodeType.Element, tr.NodeType());
        assertEquals("k", tr.get("key"));
        assertEquals("v", tr.get("value"));
        assertNull(tr.get("other"));
        assertTrue(tr.Read());
        assertEquals("website", tr.Name()); // no EndElement for <add/>
        assertEquals(XmlNodeType.Element, tr.NodeType());
        assertTrue(tr.Read());
        assertEquals(XmlNodeType.Text, tr.NodeType());
        assertEquals("x", tr.Value());
        assertTrue(tr.Read());
        assertEquals(XmlNodeType.EndElement, tr.NodeType());
        assertEquals("website", tr.Name());
        assertTrue(tr.Read());
        assertEquals("configuration", tr.Name());
        assertFalse(tr.Read());
        tr.Close();
        tr.Close();
    }
}
