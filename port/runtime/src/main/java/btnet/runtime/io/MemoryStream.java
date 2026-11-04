package btnet.runtime.io;

import btnet.runtime.ArgumentOutOfRangeException;
import java.util.Arrays;

/**
 * Stands for .NET {@code System.IO.MemoryStream} [J-C6]: read/write over a buffer that grows by copying.
 * {@code new MemoryStream(bytes)} starts at position 0 with length {@code bytes.length}.
 */
public class MemoryStream extends Stream {
    private byte[] buf;
    private int length;
    private int position;

    public MemoryStream() {
        this.buf = new byte[256];
        this.length = 0;
    }

    public MemoryStream(int capacity) {
        if (capacity < 0) {
            throw new ArgumentOutOfRangeException("capacity", "Capacity must be positive.");
        }
        this.buf = new byte[capacity];
    }

    public MemoryStream(byte[] buffer) {
        if (buffer == null) {
            throw new btnet.runtime.ArgumentNullException("buffer");
        }
        this.buf = buffer;
        this.length = buffer.length;
    }

    @Override
    public int Read(byte[] buffer, int offset, int count) {
        checkOpen();
        checkArgs(buffer, offset, count);
        int n = Math.min(count, length - position);
        if (n <= 0) {
            return 0;
        }
        System.arraycopy(buf, position, buffer, offset, n);
        position += n;
        return n;
    }

    @Override
    public void Write(byte[] buffer, int offset, int count) {
        checkOpen();
        checkArgs(buffer, offset, count);
        int end = position + count;
        if (end > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(end, Math.max(256, buf.length * 2)));
        }
        if (position > length) {
            Arrays.fill(buf, length, position, (byte) 0);
        }
        System.arraycopy(buffer, offset, buf, position, count);
        position = end;
        if (end > length) {
            length = end;
        }
    }

    @Override
    public long Position() {
        checkOpen();
        return position;
    }

    @Override
    public void Position(long value) {
        checkOpen();
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ArgumentOutOfRangeException("value", "Non-negative number required.");
        }
        position = (int) value;
    }

    @Override
    public long Length() {
        checkOpen();
        return length;
    }

    /** .NET {@code SetLength}. */
    public void SetLength(long value) {
        checkOpen();
        int v = (int) value;
        if (v > buf.length) {
            buf = Arrays.copyOf(buf, v);
        } else if (v > length) {
            Arrays.fill(buf, length, v, (byte) 0);
        }
        length = v;
        if (position > v) {
            position = v;
        }
    }

    /** .NET {@code ToArray()}: a copy of the content (also after Dispose, as .NET). */
    public byte[] ToArray() {
        return Arrays.copyOf(buf, length);
    }
}
