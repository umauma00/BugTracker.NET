package btnet.runtime;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Stands for .NET {@code System.Console} output (RULEBOOK §3 S1-20): {@code WriteLine} writes
 * {@code Convert.ToString(o) + "\r\n"} to a UTF-8 stdout installed once; calls are synchronized.
 */
public final class Console {
    private Console() {
    }

    private static final PrintStream OUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
    private static final Object LOCK = new Object();

    public static void WriteLine() {
        write(Environment.NewLine);
    }

    public static void WriteLine(String s) {
        write((s == null ? "" : s) + Environment.NewLine);
    }

    /** {@code Convert.ToString(o)} (a Throwable → its .NET {@code ToString()}) + CRLF. */
    public static void WriteLine(Object o) {
        write(Convert.ToString(o) + Environment.NewLine);
    }

    public static void Write(String s) {
        write(s == null ? "" : s);
    }

    public static void Write(Object o) {
        write(Convert.ToString(o));
    }

    private static void write(String text) {
        synchronized (LOCK) {
            OUT.print(text);
            OUT.flush();
        }
    }
}
