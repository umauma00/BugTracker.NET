package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** [S2-4] Processes.ReadToEnd = .NET StreamReader with BOM detection; Processes.start failures. */
class ProcessesTest {

    private static String read(Encoding e, int... bytes) {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            b[i] = (byte) bytes[i];
        }
        return Processes.ReadToEnd(new ByteArrayInputStream(b), e);
    }

    @Test
    void emptyStreamIsEmptyString() {
        assertEquals("", read(Encoding.UTF8));
        assertEquals("", read(Encoding.OEM));
    }

    @Test
    void utf8BomIsStripped() {
        assertEquals("ok", read(Encoding.UTF8, 0xEF, 0xBB, 0xBF, 'o', 'k'));
        assertEquals("ok", read(Encoding.OEM, 0xEF, 0xBB, 0xBF, 'o', 'k'));   // BOM switches OEM → UTF-8
        assertEquals("é", read(Encoding.OEM, 0xEF, 0xBB, 0xBF, 0xC3, 0xA9));
    }

    @Test
    void utf16And32BomsSwitchTheDecoding() {
        assertEquals("hi", read(Encoding.UTF8, 0xFF, 0xFE, 'h', 0, 'i', 0));
        assertEquals("hi", read(Encoding.UTF8, 0xFE, 0xFF, 0, 'h', 0, 'i'));
        assertEquals("h", read(Encoding.UTF8, 0xFF, 0xFE, 0, 0, 'h', 0, 0, 0));
        assertEquals("h", read(Encoding.UTF8, 0, 0, 0xFE, 0xFF, 0, 0, 0, 'h'));
    }

    @Test
    void noBomDecodesWithTheGivenEncoding() {
        assertEquals("café", Processes.ReadToEnd(
                new ByteArrayInputStream("café".getBytes(StandardCharsets.UTF_8)), Encoding.UTF8));
        // code page 437: 0x82 = é, 0xB3 = │, 0xFF = NBSP; every byte maps to one char
        assertEquals("é│ ", read(Encoding.OEM, 0x82, 0xB3, 0xFF));
        assertEquals("�", read(Encoding.UTF8, 0xC3));               // malformed → U+FFFD
        assertEquals("a\r\nb", read(Encoding.OEM, 'a', '\r', '\n', 'b'));
    }

    @Test
    void oemEncodingRoundTrips() {
        assertEquals(437, Encoding.OEM.CodePage());
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        String s = Encoding.OEM.GetString(all);
        assertEquals(256, s.length());
        assertEquals(java.util.Arrays.toString(all), java.util.Arrays.toString(Encoding.OEM.GetBytes(s)));
    }

    @Test
    void startFailuresAreWin32Exceptions() {
        Win32Exception missing = assertThrows(Win32Exception.class,
                () -> Processes.start(new ProcessBuilder("/nonexistent/btnet-no-such-program")));
        assertEquals(2, missing.NativeErrorCode());
        assertEquals("The system cannot find the file specified", missing.getMessage());

        ProcessBuilder pb = new ProcessBuilder("git", "--version");
        pb.directory(new File("/nonexistent/btnet-no-such-dir"));
        Win32Exception dir = assertThrows(Win32Exception.class, () -> Processes.start(pb));
        assertEquals(267, dir.NativeErrorCode());
        assertEquals("The directory name is invalid", dir.getMessage());
        assertEquals("System.ComponentModel.Win32Exception", Exceptions.dotNetTypeName(dir));
    }

    @Test
    void nativeErrorCodesAreWin32() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"));
        Win32Exception bare = assertThrows(Win32Exception.class,
                () -> Processes.start(new ProcessBuilder("btnet-no-such-program-on-path")));
        assertEquals(2, bare.NativeErrorCode());

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("btnet-noexec", ".sh");
        try {
            tmp.toFile().setExecutable(false, false);
            Win32Exception denied = assertThrows(Win32Exception.class,
                    () -> Processes.start(new ProcessBuilder(tmp.toString())));
            assertEquals(5, denied.NativeErrorCode());
            assertEquals("Access is denied", denied.getMessage());
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
        Win32Exception isDir = assertThrows(Win32Exception.class,
                () -> Processes.start(new ProcessBuilder(System.getProperty("java.io.tmpdir") + "/")));
        assertEquals(5, isDir.NativeErrorCode());
    }

    @Test
    void jdkStartMessagesAreParsed() {
        assertEquals(2, Processes.errorCode("Cannot run program \"x\": error=2, No such file or directory"));
        assertEquals(2, Processes.errorCode("Cannot run program \"x\": Exec failed, error: 2 (No such file or directory)"));
        assertEquals(267, Processes.errorCode("Cannot run program \"x\" (in directory \"d\"): CreateProcess error=267, The directory name is invalid"));
        assertEquals(0, Processes.errorCode("something else"));
        assertEquals(5, Processes.errnoToWin32(13));
        assertEquals(193, Processes.errnoToWin32(8));
        assertEquals("The specified executable is not a valid application for this OS platform.",
                Processes.win32(193, null).getMessage());
    }
}
