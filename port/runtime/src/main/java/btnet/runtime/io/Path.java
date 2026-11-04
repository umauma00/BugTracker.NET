package btnet.runtime.io;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentNullException;
import java.security.SecureRandom;

/**
 * Stands for .NET {@code System.IO.Path} (RULEBOOK §2A File/Directory/Path). Both {@code /} and {@code \} are
 * separators on input (the source builds {@code "App_Data\\uploads"}, browsers post {@code C:\...\file.txt});
 * paths returned by this class use {@code /}.
 */
public final class Path {
    private Path() {
    }

    /** .NET {@code Path.DirectorySeparatorChar} on the port. */
    public static final char DirectorySeparatorChar = '/';
    public static final char AltDirectorySeparatorChar = '\\';

    private static final SecureRandom RANDOM = new SecureRandom();

    static String normalize(String p) {
        return p == null ? null : p.replace('\\', '/');
    }

    private static boolean isSep(char c) {
        return c == '/' || c == '\\';
    }

    private static int lastSep(String p) {
        for (int i = p.length() - 1; i >= 0; i--) {
            char c = p.charAt(i);
            if (isSep(c) || (c == ':' && i == 1)) {
                return i;
            }
        }
        return -1;
    }

    /** .NET {@code Path.Combine(paths)}: a rooted later part replaces what precedes; null → ArgumentNullException. */
    public static String Combine(String... paths) {
        if (paths == null) {
            throw new ArgumentNullException("paths");
        }
        String result = "";
        for (String p : paths) {
            if (p == null) {
                throw new ArgumentNullException("paths");
            }
            if (p.isEmpty()) {
                continue;
            }
            if (IsPathRooted(p) || result.isEmpty()) {
                result = p;
            } else {
                char last = result.charAt(result.length() - 1);
                result = (isSep(last) || last == ':') ? result + p : result + "/" + p;
            }
        }
        return normalize(result);
    }

    /** .NET {@code Path.GetFileName}: text after the last separator; null → null. */
    public static String GetFileName(String path) {
        if (path == null) {
            return null;
        }
        return path.substring(lastSep(path) + 1);
    }

    /** .NET {@code Path.GetExtension}: includes the dot; {@code ""} when none or a trailing dot; null → null. */
    public static String GetExtension(String path) {
        if (path == null) {
            return null;
        }
        String name = GetFileName(path);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot);
    }

    /** .NET {@code Path.GetFileNameWithoutExtension}. */
    public static String GetFileNameWithoutExtension(String path) {
        if (path == null) {
            return null;
        }
        String name = GetFileName(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /**
     * .NET {@code Path.GetDirectoryName}: everything before the last separator (normalized to {@code /}); null or a
     * root → null; no separator → {@code ""}.
     */
    public static String GetDirectoryName(String path) {
        if (path == null) {
            return null;
        }
        if (path.isEmpty()) {
            throw new ArgumentException("The path is not of a legal form.");
        }
        String p = normalize(path);
        if (p.equals("/") || p.matches("[A-Za-z]:/?")) {
            return null;
        }
        int i = lastSep(p);
        if (i < 0) {
            return "";
        }
        if (i == 0) {
            return "/";
        }
        if (i == 2 && p.charAt(1) == ':') {
            return p.substring(0, 3);
        }
        return p.substring(0, i);
    }

    /** .NET {@code Path.IsPathRooted}: starts with a separator or a drive {@code X:}. */
    public static boolean IsPathRooted(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return isSep(path.charAt(0)) || (path.length() >= 2 && path.charAt(1) == ':');
    }

    /** .NET {@code Path.GetTempPath()}: {@code java.io.tmpdir} with a trailing {@code /}. */
    public static String GetTempPath() {
        String t = normalize(System.getProperty("java.io.tmpdir"));
        return t.endsWith("/") ? t : t + "/";
    }

    /** .NET {@code Path.GetRandomFileName()}: 8.3 name of lower-case letters and digits. */
    public static String GetRandomFileName() {
        String chars = "abcdefghijklmnopqrstuvwxyz012345";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 11; i++) {
            if (i == 8) {
                sb.append('.');
            }
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** .NET {@code Path.GetFullPath}. */
    public static String GetFullPath(String path) {
        if (path == null) {
            throw new ArgumentNullException("path");
        }
        return normalize(new java.io.File(normalize(path)).getAbsoluteFile().toPath().normalize().toString());
    }

    /** .NET {@code Path.ChangeExtension}. */
    public static String ChangeExtension(String path, String extension) {
        if (path == null) {
            return null;
        }
        String name = GetFileName(path);
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? path : path.substring(0, path.length() - name.length() + dot);
        if (extension == null) {
            return base;
        }
        return extension.startsWith(".") ? base + extension : base + "." + extension;
    }
}
