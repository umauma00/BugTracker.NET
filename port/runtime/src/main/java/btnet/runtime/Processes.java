package btnet.runtime;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code System.Diagnostics.ProcessStartInfo} argument handling (RULEBOOK §2A Processes, C20): the Windows
 * {@code CommandLineToArgvW} splitting of {@code Arguments} into a {@code ProcessBuilder} command list;
 * {@code Process.Start} ({@link #start}) and {@code StreamReader.ReadToEnd} over a child's pipe
 * ({@link #ReadToEnd}) [S2-4].
 */
public final class Processes {
    private Processes() {
    }

    /**
     * Splits a Windows argument string with the {@code CommandLineToArgvW} rules: space/tab separate arguments
     * outside quotes; {@code 2n} backslashes + {@code "} → {@code n} backslashes and a quote toggle; {@code 2n+1}
     * backslashes + {@code "} → {@code n} backslashes and a literal quote; backslashes not before a quote are
     * literal; inside quotes every third consecutive quote yields a literal quote (Wine/Windows behaviour);
     * {@code ""} produces an empty argument. {@code null}/blank → empty list.
     */
    public static List<String> SplitArgs(String args) {
        List<String> out = new ArrayList<>();
        if (args == null) {
            return out;
        }
        int n = args.length();
        int i = 0;
        while (i < n) {
            while (i < n && (args.charAt(i) == ' ' || args.charAt(i) == '\t')) {
                i++;
            }
            if (i >= n) {
                break;
            }
            StringBuilder cur = new StringBuilder();
            int bcount = 0;
            int qcount = 0;
            while (i < n) {
                char c = args.charAt(i);
                if ((c == ' ' || c == '\t') && qcount == 0) {
                    break;
                } else if (c == '\\') {
                    cur.append(c);
                    bcount++;
                    i++;
                } else if (c == '"') {
                    if ((bcount & 1) == 0) {
                        cur.setLength(cur.length() - bcount / 2);
                        qcount++;
                    } else {
                        cur.setLength(cur.length() - bcount / 2 - 1);
                        cur.append('"');
                    }
                    i++;
                    bcount = 0;
                    while (i < n && args.charAt(i) == '"') {
                        if (++qcount == 3) {
                            cur.append('"');
                            qcount = 0;
                        }
                        i++;
                    }
                    if (qcount == 2) {
                        qcount = 0;
                    }
                } else {
                    cur.append(c);
                    bcount = 0;
                    i++;
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    /** {@code FileName} + {@code Arguments} → the {@code ProcessBuilder} command list. */
    public static List<String> command(String fileName, String arguments) {
        if (fileName == null || fileName.isEmpty()) {
            throw new InvalidOperationException("Cannot start process because a file name has not been provided.");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(fileName);
        cmd.addAll(SplitArgs(arguments));
        return cmd;
    }

    /**
     * .NET {@code p.Start()} [S2-4]: redirects the child's stdin from the OS null device (the IIS worker has no
     * stdin, so the child reads EOF at once), then {@code pb.start()}. A start failure → {@link Win32Exception}:
     * {@code "The directory name is invalid"} (267) for a working directory that is not a directory,
     * {@code "The system cannot find the file specified"} (2) for a missing executable, else the JDK message
     * (runtime-only {@code TODO(port): map other Win32 error codes}).
     */
    public static Process start(ProcessBuilder pb) {
        if (pb == null) {
            throw new ArgumentNullException("startInfo");
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File(windows ? "NUL" : "/dev/null")));
        java.io.File dir = pb.directory();
        if (dir != null && !dir.isDirectory()) {
            throw new Win32Exception(267, "The directory name is invalid");
        }
        try {
            return pb.start();
        } catch (java.io.IOException e) {
            int code = errorCode(e.getMessage());
            if (code == 267) {
                throw new Win32Exception(267, "The directory name is invalid", e);
            }
            if (code == 2 || code == 3) {
                throw new Win32Exception(2, "The system cannot find the file specified", e);
            }
            throw new Win32Exception(code, e.getMessage(), e);
        }
    }

    /** The {@code error=N} code in a JDK process-start message, 0 if none. */
    private static int errorCode(String msg) {
        if (msg == null) {
            return 0;
        }
        int i = msg.indexOf("error=");
        if (i < 0) {
            return 0;
        }
        int j = i + 6;
        int v = 0;
        boolean any = false;
        while (j < msg.length() && Character.isDigit(msg.charAt(j)) && j - i < 16) {
            v = v * 10 + (msg.charAt(j) - '0');
            any = true;
            j++;
        }
        return any ? v : 0;
    }

    /**
     * .NET {@code new StreamReader(in, e, detectEncodingFromByteOrderMarks: true).ReadToEnd()} [S2-4]: reads to EOF;
     * a leading {@code EF BB BF} → UTF-8, {@code FE FF} → UTF-16BE, {@code FF FE} → UTF-16LE
     * ({@code FF FE 00 00} → UTF-32LE), {@code 00 00 FE FF} → UTF-32BE — the BOM is stripped and the decoding
     * switches; otherwise a leading preamble of {@code e} is stripped and the bytes are decoded with {@code e}
     * (malformed input → U+FFFD). An exhausted stream → {@code ""}. {@code IOException} → {@link Exceptions#wrap}.
     */
    public static String ReadToEnd(InputStream in, Encoding e) {
        if (in == null) {
            throw new ArgumentNullException("stream");
        }
        if (e == null) {
            throw new ArgumentNullException("encoding");
        }
        byte[] b;
        try {
            b = in.readAllBytes();
        } catch (java.io.IOException ex) {
            throw Exceptions.wrap(ex);
        }
        return decodeDetecting(b, e);
    }

    /** The decoding half of {@link #ReadToEnd} (BOM detection over a complete byte array). */
    static String decodeDetecting(byte[] b, Encoding e) {
        int n = b.length;
        if (n == 0) {
            return "";
        }
        byte[] pre = e.GetPreamble();
        if (pre.length > 0 && startsWith(b, pre)) {
            return e.GetString(b, pre.length, n - pre.length);
        }
        if (n >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return Encoding.BigEndianUnicode.GetString(b, 2, n - 2);
        }
        if (n >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            if (n >= 4 && b[2] == 0 && b[3] == 0) {
                return new String(b, 4, n - 4, Charset.forName("UTF-32LE"));
            }
            return Encoding.Unicode.GetString(b, 2, n - 2);
        }
        if (n >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return Encoding.UTF8.GetString(b, 3, n - 3);
        }
        if (n >= 4 && b[0] == 0 && b[1] == 0 && (b[2] & 0xFF) == 0xFE && (b[3] & 0xFF) == 0xFF) {
            return new String(b, 4, n - 4, Charset.forName("UTF-32BE"));
        }
        return e.GetString(b, 0, n);
    }

    private static boolean startsWith(byte[] b, byte[] p) {
        if (b.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (b[i] != p[i]) {
                return false;
            }
        }
        return true;
    }
}
