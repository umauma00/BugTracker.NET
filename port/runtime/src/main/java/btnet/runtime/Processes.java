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
     * stdin, so the child reads EOF at once), then {@code pb.start()}. A start failure → {@link Win32Exception}
     * whose {@code NativeErrorCode()} is the Win32 code .NET reports, with .NET's message text:
     * 267 {@code "The directory name is invalid"} (working directory not a directory), 2 {@code "The system cannot
     * find the file specified"} (missing executable), 3 {@code "The system cannot find the path specified"},
     * 5 {@code "Access is denied"}, 193 {@code "The specified executable is not a valid application for this OS
     * platform."}; any other code keeps the JDK message (runtime-only {@code TODO(port): map other Win32 error
     * codes}). The executable is checked before starting (independent of the JDK's message format); otherwise
     * the {@code error=N} / {@code error: N} code of the JDK message is used — a Win32 code on Windows, an errno
     * elsewhere (mapped: ENOENT → 2, ENOTDIR → 3, EACCES/EPERM → 5, ENOEXEC → 193).
     */
    public static Process start(ProcessBuilder pb) {
        if (pb == null) {
            throw new ArgumentNullException("startInfo");
        }
        pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File(isWindows() ? "NUL" : "/dev/null")));
        java.io.File dir = pb.directory();
        if (dir != null && !dir.isDirectory()) {
            throw win32(267, null);
        }
        List<String> cmd = pb.command();
        if (!cmd.isEmpty()) {
            int pre = checkExecutable(cmd.get(0), dir);
            if (pre != 0) {
                throw win32(pre, null);
            }
        }
        try {
            return pb.start();
        } catch (java.io.IOException e) {
            int code = errorCode(e.getMessage());
            if (code != 0 && !isWindows()) {
                code = errnoToWin32(code);
            }
            throw win32(code, e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    /** The {@link Win32Exception} for a Win32 code, with .NET's text where known. */
    static Win32Exception win32(int code, java.io.IOException cause) {
        String text = switch (code) {
            case 2 -> "The system cannot find the file specified";
            case 3 -> "The system cannot find the path specified";
            case 5 -> "Access is denied";
            case 193 -> "The specified executable is not a valid application for this OS platform.";
            case 267 -> "The directory name is invalid";
            default -> cause != null && cause.getMessage() != null ? cause.getMessage() : "Unknown error (0x" + Integer.toHexString(code) + ")";
        };
        return cause == null ? new Win32Exception(code, text) : new Win32Exception(code, text, cause);
    }

    /** POSIX errno → the Win32 code CreateProcess reports for the same condition. */
    static int errnoToWin32(int errno) {
        switch (errno) {
            case 2: return 2;      // ENOENT
            case 20: return 3;     // ENOTDIR
            case 1:                // EPERM
            case 13: return 5;     // EACCES
            case 8: return 193;    // ENOEXEC
            default: return errno;
        }
    }

    /**
     * 0 if {@code exe} can be started as far as can be told beforehand, else the Win32 code: a path that does not
     * exist → 2, a directory or (off Windows) a non-executable file → 5;
     * a bare name not found on {@code PATH} → 2 (off Windows only; Windows also searches the application and
     * current directories, so it is left to {@code start()}).
     */
    static int checkExecutable(String exe, java.io.File dir) {
        if (exe == null || exe.isEmpty()) {
            return 2;
        }
        boolean windows = isWindows();
        boolean hasSep = exe.indexOf('/') >= 0 || (windows && exe.indexOf('\\') >= 0);
        if (hasSep) {
            java.io.File f = new java.io.File(exe);
            if (!f.isAbsolute() && dir != null) {
                f = new java.io.File(dir, exe);
            }
            if (!f.exists()) {
                if (windows) {
                    return 0;      // PATHEXT resolution ("x\\git" → git.exe): leave to CreateProcess
                }
                return 2;          // also for a missing directory component (CreateProcess: ERROR_FILE_NOT_FOUND)
            }
            if (f.isDirectory() || (!windows && !f.canExecute())) {
                return 5;
            }
            return 0;
        }
        if (windows) {
            return 0;
        }
        String path = System.getenv("PATH");
        if (path == null) {
            return 0;
        }
        for (String d : path.split(java.io.File.pathSeparator, -1)) {
            java.io.File f = new java.io.File(d.isEmpty() ? "." : d, exe);
            if (f.isFile() && f.canExecute()) {
                return 0;
            }
        }
        return 2;
    }

    /** The {@code error=N} or {@code error: N} code in a JDK process-start message, 0 if none. */
    static int errorCode(String msg) {
        if (msg == null) {
            return 0;
        }
        for (String key : new String[] {"error=", "error: "}) {
            int i = msg.indexOf(key);
            if (i < 0) {
                continue;
            }
            int j = i + key.length();
            int v = 0;
            boolean any = false;
            while (j < msg.length() && Character.isDigit(msg.charAt(j)) && v < 100000) {
                v = v * 10 + (msg.charAt(j) - '0');
                any = true;
                j++;
            }
            if (any) {
                return v;
            }
        }
        return 0;
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
