package btnet.runtime.io;

import btnet.runtime.DirectoryNotFoundException;
import btnet.runtime.Exceptions;
import btnet.runtime.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stands for .NET {@code System.IO.Directory} (RULEBOOK §2A). {@code GetFiles} returns {@code <path>/<name>}
 * (path as given, normalized to {@code /}), sorted ordinal-ignore-case (NTFS order), with Windows wildcard
 * matching, including the 3-character-extension quirk ({@code *.txt} also matches {@code a.txtx}).
 */
public final class Directory {
    private Directory() {
    }

    public static boolean Exists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return new java.io.File(Path.normalize(path)).isDirectory();
    }

    /** .NET {@code Directory.CreateDirectory}: creates all missing parents; an existing directory is fine. */
    public static void CreateDirectory(String path) {
        java.io.File f = File.file(path);
        if (f.isDirectory()) {
            return;
        }
        if (f.exists()) {
            throw new IOException("Cannot create \"" + f.getPath() + "\" because a file or directory with the same name already exists.");
        }
        try {
            java.nio.file.Files.createDirectories(f.toPath());
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code Directory.Delete(path)}: must be empty. */
    public static void Delete(String path) {
        Delete(path, false);
    }

    /** .NET {@code Directory.Delete(path, recursive)}. */
    public static void Delete(String path, boolean recursive) {
        java.io.File f = File.file(path);
        if (!f.isDirectory()) {
            throw new DirectoryNotFoundException("Could not find a part of the path '" + f.getPath() + "'.");
        }
        String[] children = f.list();
        if (children != null && children.length > 0) {
            if (!recursive) {
                throw new IOException("The directory is not empty.\r\n");
            }
            deleteTree(f);
            return;
        }
        if (!f.delete()) {
            throw new IOException("Access to the path '" + f.getPath() + "' is denied.");
        }
    }

    private static void deleteTree(java.io.File f) {
        java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) {
                if (k.isDirectory()) {
                    deleteTree(k);
                } else if (!k.delete()) {
                    throw new IOException("Access to the path '" + k.getPath() + "' is denied.");
                }
            }
        }
        if (!f.delete()) {
            throw new IOException("Access to the path '" + f.getPath() + "' is denied.");
        }
    }

    public static String[] GetFiles(String path) {
        return list(path, "*", true);
    }

    public static String[] GetFiles(String path, String searchPattern) {
        return list(path, searchPattern, true);
    }

    public static String[] GetDirectories(String path) {
        return list(path, "*", false);
    }

    public static String[] GetDirectories(String path, String searchPattern) {
        return list(path, searchPattern, false);
    }

    private static String[] list(String path, String searchPattern, boolean files) {
        if (searchPattern == null) {
            throw new btnet.runtime.ArgumentNullException("searchPattern");
        }
        java.io.File dir = File.file(path);
        if (!dir.isDirectory()) {
            throw new DirectoryNotFoundException("Could not find a part of the path '" + dir.getPath() + "'.");
        }
        Pattern p = wildcard(searchPattern);
        java.io.File[] kids = dir.listFiles();
        List<String> names = new ArrayList<>();
        if (kids != null) {
            for (java.io.File k : kids) {
                if ((files ? k.isFile() : k.isDirectory()) && p.matcher(k.getName()).matches()) {
                    names.add(k.getName());
                }
            }
        }
        String[] arr = names.toArray(new String[0]);
        Arrays.sort(arr, String.CASE_INSENSITIVE_ORDER);
        String base = Path.normalize(path);
        String prefix = base.endsWith("/") ? base : base + "/";
        for (int i = 0; i < arr.length; i++) {
            arr[i] = prefix + arr[i];
        }
        return arr;
    }

    static Pattern wildcard(String pattern) {
        String pat = pattern;
        if (pat.equals("*.*") || pat.equals("*")) {
            return Pattern.compile(".*", Pattern.DOTALL);
        }
        boolean extQuirk = false;
        int dot = pat.lastIndexOf('.');
        if (pat.startsWith("*") && dot >= 0 && pat.length() - dot - 1 == 3
                && pat.substring(dot + 1).chars().noneMatch(c -> c == '*' || c == '?')) {
            extQuirk = true;
        }
        StringBuilder re = new StringBuilder();
        for (char c : pat.toCharArray()) {
            if (c == '*') {
                re.append(".*");
            } else if (c == '?') {
                re.append(".?");
            } else {
                re.append(Pattern.quote(String.valueOf(c)));
            }
        }
        if (extQuirk) {
            re.append("[^.]*");
        }
        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    }
}
