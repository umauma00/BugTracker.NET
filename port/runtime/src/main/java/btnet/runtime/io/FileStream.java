package btnet.runtime.io;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.ArgumentOutOfRangeException;
import btnet.runtime.Exceptions;
import btnet.runtime.FileNotFoundException;
import btnet.runtime.IOException;
import java.io.RandomAccessFile;

/**
 * Stands for .NET {@code System.IO.FileStream} [J-C6] over {@link RandomAccessFile}: {@code CreateNew} on an existing
 * file → IOException("The file '&lt;path&gt;' already exists.") (never truncates); {@code Open}/{@code Truncate} on a
 * missing file → FileNotFoundException("Could not find file '&lt;path&gt;'."); missing directory →
 * DirectoryNotFoundException; {@code Create} truncates; {@code Append} writes at the end. FileShare is ignored;
 * {@code \} is normalized to {@code /}.
 */
public class FileStream extends Stream {
    private final RandomAccessFile raf;
    private final FileAccess access;
    private final String name;

    public FileStream(String path, FileMode mode) {
        this(path, mode, mode == FileMode.Append ? FileAccess.Write : FileAccess.ReadWrite, FileShare.Read);
    }

    public FileStream(String path, FileMode mode, FileAccess access) {
        this(path, mode, access, FileShare.Read);
    }

    public FileStream(String path, FileMode mode, FileAccess access, FileShare share) {
        if (path == null) {
            throw new ArgumentNullException("path");
        }
        if (mode == null || access == null) {
            throw new ArgumentOutOfRangeException("mode", "Enum value was out of legal range.");
        }
        String p = Path.normalize(path);
        if (p.isEmpty()) {
            throw new btnet.runtime.ArgumentException("Empty path name is not legal.", "path");
        }
        java.io.File f = new java.io.File(p);
        String full = f.getAbsolutePath();
        this.name = full;
        this.access = access;
        java.io.File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) {
            throw new btnet.runtime.DirectoryNotFoundException("Could not find a part of the path '" + full + "'.");
        }
        if (f.isDirectory()) {
            throw new IOException("Access to the path '" + full + "' is denied.");
        }
        boolean exists = f.exists();
        switch (mode) {
            case CreateNew:
                if (exists) {
                    throw new IOException("The file '" + full + "' already exists.");
                }
                break;
            case Open:
            case Truncate:
                if (!exists) {
                    throw new FileNotFoundException("Could not find file '" + full + "'.");
                }
                break;
            default:
                break;
        }
        try {
            String rafMode = access == FileAccess.Read ? "r" : "rw";
            if (access == FileAccess.Read && !exists) {
                // Create/OpenOrCreate with read-only access: .NET rejects the combination for Create; create it
                new RandomAccessFile(f, "rw").close();
            }
            this.raf = new RandomAccessFile(f, rafMode);
            if (mode == FileMode.Create || mode == FileMode.Truncate) {
                raf.setLength(0);
            }
            if (mode == FileMode.Append) {
                raf.seek(raf.length());
            }
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code Name} (full path). */
    public String Name() {
        return name;
    }

    @Override
    public boolean CanRead() {
        return access != FileAccess.Write;
    }

    @Override
    public boolean CanWrite() {
        return access != FileAccess.Read;
    }

    @Override
    public int Read(byte[] buffer, int offset, int count) {
        checkOpen();
        checkArgs(buffer, offset, count);
        if (access == FileAccess.Write) {
            throw new UnsupportedOperationException("Stream does not support reading.");
        }
        if (count == 0) {
            return 0;
        }
        try {
            int n = raf.read(buffer, offset, count);
            return n < 0 ? 0 : n;
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    @Override
    public void Write(byte[] buffer, int offset, int count) {
        checkOpen();
        checkArgs(buffer, offset, count);
        if (access == FileAccess.Read) {
            throw new UnsupportedOperationException("Stream does not support writing.");
        }
        try {
            raf.write(buffer, offset, count);
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    @Override
    public long Position() {
        checkOpen();
        try {
            return raf.getFilePointer();
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    @Override
    public void Position(long value) {
        checkOpen();
        if (value < 0) {
            throw new ArgumentOutOfRangeException("value", "Non-negative number required.");
        }
        try {
            raf.seek(value);
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    @Override
    public long Length() {
        checkOpen();
        try {
            return raf.length();
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    @Override
    protected void disposeCore() {
        try {
            raf.close();
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }
}
