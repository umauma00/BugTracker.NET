package btnet.runtime.io;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.ArgumentOutOfRangeException;
import btnet.runtime.Exceptions;
import btnet.runtime.InvalidOperationException;

/**
 * Stands for .NET {@code System.IO.Stream} [J-C6]: {@code Read}, {@code Write}, {@code CopyTo}, {@code Position},
 * {@code Length}, {@code Dispose}/{@code Close}/{@code close} (idempotent). Use after disposal throws
 * InvalidOperationException("Cannot access a closed Stream.") (.NET ObjectDisposedException derives from it).
 * {@link #FromInputStream}/{@link #ToInputStream} bridge to {@code java.io} for the web facade.
 */
public abstract class Stream implements AutoCloseable {
    private boolean disposed;

    protected Stream() {
    }

    public abstract int Read(byte[] buffer, int offset, int count);

    public abstract void Write(byte[] buffer, int offset, int count);

    public abstract long Position();

    public abstract void Position(long value);

    public abstract long Length();

    public boolean CanRead() {
        return true;
    }

    public boolean CanWrite() {
        return true;
    }

    public boolean CanSeek() {
        return true;
    }

    public void Flush() {
    }

    /** .NET {@code ReadByte()}: -1 at end. */
    public int ReadByte() {
        byte[] b = new byte[1];
        int n = Read(b, 0, 1);
        return n <= 0 ? -1 : (b[0] & 0xFF);
    }

    public void WriteByte(int value) {
        Write(new byte[] {(byte) value}, 0, 1);
    }

    /** .NET {@code CopyTo(destination)}: from the current position to the end. */
    public void CopyTo(Stream destination) {
        if (destination == null) {
            throw new ArgumentNullException("destination");
        }
        byte[] buf = new byte[81920];
        int n;
        while ((n = Read(buf, 0, buf.length)) > 0) {
            destination.Write(buf, 0, n);
        }
    }

    /** Releases resources; idempotent. Subclasses override {@link #disposeCore()}. */
    public final void Dispose() {
        if (!disposed) {
            disposed = true;
            disposeCore();
        }
    }

    public final void Close() {
        Dispose();
    }

    @Override
    public final void close() {
        Dispose();
    }

    protected void disposeCore() {
    }

    protected final boolean isDisposed() {
        return disposed;
    }

    protected final void checkOpen() {
        if (disposed) {
            throw new InvalidOperationException("Cannot access a closed Stream.");
        }
    }

    protected static void checkArgs(byte[] buffer, int offset, int count) {
        if (buffer == null) {
            throw new ArgumentNullException("buffer");
        }
        if (offset < 0) {
            throw new ArgumentOutOfRangeException("offset", "Non-negative number required.");
        }
        if (count < 0) {
            throw new ArgumentOutOfRangeException("count", "Non-negative number required.");
        }
        if (buffer.length - offset < count) {
            throw new btnet.runtime.ArgumentException(
                    "Offset and length were out of bounds for the array or count is greater than the number of elements from index to the end of the source collection.");
        }
    }

    /** A {@code java.io.InputStream} view reading from this stream's current position. */
    public java.io.InputStream ToInputStream() {
        Stream self = this;
        return new java.io.InputStream() {
            @Override
            public int read() {
                return self.ReadByte();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (len == 0) {
                    return 0;
                }
                int n = self.Read(b, off, len);
                return n <= 0 ? -1 : n;
            }
        };
    }

    /** Wraps a {@code java.io.InputStream} as a read-only, forward-only Stream ({@code CanSeek} false). */
    public static Stream FromInputStream(java.io.InputStream in) {
        if (in == null) {
            throw new ArgumentNullException("stream");
        }
        return new Stream() {
            private long pos;

            @Override
            public int Read(byte[] buffer, int offset, int count) {
                checkOpen();
                checkArgs(buffer, offset, count);
                if (count == 0) {
                    return 0;
                }
                try {
                    int n = in.read(buffer, offset, count);
                    if (n < 0) {
                        return 0;
                    }
                    pos += n;
                    return n;
                } catch (java.io.IOException e) {
                    throw Exceptions.wrap(e);
                }
            }

            @Override
            public void Write(byte[] buffer, int offset, int count) {
                throw new UnsupportedOperationException("Stream does not support writing.");
            }

            @Override
            public long Position() {
                return pos;
            }

            @Override
            public void Position(long value) {
                throw new UnsupportedOperationException("Stream does not support seeking.");
            }

            @Override
            public long Length() {
                throw new UnsupportedOperationException("Stream does not support seeking.");
            }

            @Override
            public boolean CanWrite() {
                return false;
            }

            @Override
            public boolean CanSeek() {
                return false;
            }

            @Override
            protected void disposeCore() {
                try {
                    in.close();
                } catch (java.io.IOException e) {
                    throw Exceptions.wrap(e);
                }
            }
        };
    }
}
