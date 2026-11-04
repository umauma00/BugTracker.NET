package btnet.runtime.io;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentNullException;
import btnet.runtime.DirectoryNotFoundException;
import btnet.runtime.Encoding;
import btnet.runtime.Exceptions;
import btnet.runtime.FileNotFoundException;
import btnet.runtime.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Stands for .NET {@code System.IO.File} (RULEBOOK §2A, §3 N7). {@code \} is normalized to {@code /}. Missing
 * file → FileNotFoundException("Could not find file '&lt;full path&gt;'."); missing directory →
 * DirectoryNotFoundException("Could not find a part of the path '&lt;full path&gt;'.").
 */
public final class File {
    private File() {
    }

    static java.io.File file(String path) {
        if (path == null) {
            throw new ArgumentNullException("path");
        }
        String p = Path.normalize(path);
        if (p.trim().isEmpty()) {
            throw new ArgumentException("Empty path name is not legal.", "path");
        }
        return new java.io.File(p).getAbsoluteFile();
    }

    static void requireParent(java.io.File f) {
        java.io.File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            throw new DirectoryNotFoundException("Could not find a part of the path '" + f.getPath() + "'.");
        }
    }

    static void requireFile(java.io.File f) {
        if (!f.isFile()) {
            requireParent(f);
            throw new FileNotFoundException("Could not find file '" + f.getPath() + "'.");
        }
    }

    /** .NET {@code File.Exists}: false for null, empty, a directory or any error. */
    public static boolean Exists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        try {
            return new java.io.File(Path.normalize(path)).isFile();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** .NET {@code File.Delete}: a missing file is not an error; a missing directory is. */
    public static void Delete(String path) {
        java.io.File f = file(path);
        requireParent(f);
        if (f.isDirectory()) {
            throw new IOException("Access to the path '" + f.getPath() + "' is denied.");
        }
        try {
            Files.deleteIfExists(f.toPath());
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code File.Move}: destination exists → IOException("Cannot create a file when that file already exists."). */
    public static void Move(String sourceFileName, String destFileName) {
        java.io.File src = file(sourceFileName);
        java.io.File dst = file(destFileName);
        requireFile(src);
        requireParent(dst);
        if (dst.exists()) {
            throw new IOException("Cannot create a file when that file already exists.");
        }
        try {
            Files.move(src.toPath(), dst.toPath());
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code File.Copy(src, dst[, overwrite])}. */
    public static void Copy(String sourceFileName, String destFileName) {
        Copy(sourceFileName, destFileName, false);
    }

    public static void Copy(String sourceFileName, String destFileName, boolean overwrite) {
        java.io.File src = file(sourceFileName);
        java.io.File dst = file(destFileName);
        requireFile(src);
        requireParent(dst);
        if (dst.exists() && !overwrite) {
            throw new IOException("The file '" + dst.getPath() + "' already exists.");
        }
        try {
            if (overwrite) {
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(src.toPath(), dst.toPath());
            }
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code File.OpenText}: a UTF-8 {@link StreamReader} (BOM detection). */
    public static StreamReader OpenText(String path) {
        return new StreamReader(path);
    }

    /** .NET {@code File.CreateText}: truncates or creates; UTF-8 without BOM. */
    public static StreamWriter CreateText(String path) {
        return new StreamWriter(path, false);
    }

    /** .NET {@code File.AppendText} [N7]: append, create if absent, UTF-8 without BOM. */
    public static StreamWriter AppendText(String path) {
        return new StreamWriter(path, true);
    }

    /** .NET {@code File.ReadAllBytes}. */
    public static byte[] ReadAllBytes(String path) {
        java.io.File f = file(path);
        requireFile(f);
        try {
            return Files.readAllBytes(f.toPath());
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code File.WriteAllBytes}: creates or overwrites. */
    public static void WriteAllBytes(String path, byte[] bytes) {
        if (bytes == null) {
            throw new ArgumentNullException("bytes");
        }
        java.io.File f = file(path);
        requireParent(f);
        try {
            Files.write(f.toPath(), bytes);
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code File.ReadAllText(path)}: UTF-8 with BOM detection. */
    public static String ReadAllText(String path) {
        try (StreamReader r = new StreamReader(path)) {
            return r.ReadToEnd();
        }
    }

    public static String ReadAllText(String path, Encoding encoding) {
        try (StreamReader r = new StreamReader(path, encoding)) {
            return r.ReadToEnd();
        }
    }

    /** .NET {@code File.WriteAllText(path, contents)}: UTF-8 without BOM. */
    public static void WriteAllText(String path, String contents) {
        try (StreamWriter w = new StreamWriter(path, false)) {
            w.Write(contents);
        }
    }

    public static void WriteAllText(String path, String contents, Encoding encoding) {
        try (StreamWriter w = new StreamWriter(path, false, encoding)) {
            w.Write(contents);
        }
    }

    /** .NET {@code File.AppendAllText(path, contents)}. */
    public static void AppendAllText(String path, String contents) {
        try (StreamWriter w = new StreamWriter(path, true)) {
            w.Write(contents);
        }
    }
}
