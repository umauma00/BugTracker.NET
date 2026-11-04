package btnet.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code System.Diagnostics.ProcessStartInfo} argument handling (RULEBOOK §2A Processes, C20): the Windows
 * {@code CommandLineToArgvW} splitting of {@code Arguments} into a {@code ProcessBuilder} command list.
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
}
