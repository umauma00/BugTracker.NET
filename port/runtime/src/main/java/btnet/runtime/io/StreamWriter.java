package btnet.runtime.io;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.Convert;
import btnet.runtime.Encoding;
import btnet.runtime.Environment;
import btnet.runtime.Exceptions;
import java.io.OutputStream;

/**
 * Stands for .NET {@code System.IO.StreamWriter} [N7]. Path constructors open in append or truncate mode (creating
 * the file); missing directory → DirectoryNotFoundException("Could not find a part of the path '&lt;path&gt;'.").
 * Default encoding UTF-8 <b>without</b> BOM; an explicit encoding with a preamble (e.g. {@code Encoding.UTF8})
 * writes it when the file/stream is empty, as .NET. {@code WriteLine} appends CRLF. Close flushes; idempotent.
 */
public class StreamWriter implements AutoCloseable {
    private final OutputStream out;
    private final Encoding encoding;
    private final StringBuilder pending = new StringBuilder();
    private boolean autoFlush;
    private boolean closed;

    public StreamWriter(String path) {
        this(path, false, null);
    }

    public StreamWriter(String path, boolean append) {
        this(path, append, null);
    }

    public StreamWriter(String path, boolean append, Encoding encoding) {
        java.io.File f = File.file(path);
        File.requireParent(f);
        boolean empty = !append || !f.exists() || f.length() == 0;
        try {
            this.out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(f, append));
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
        this.encoding = encoding;
        if (encoding != null && empty) {
            writeBytes(encoding.GetPreamble());
        }
    }

    public StreamWriter(Stream stream) {
        this(stream, null);
    }

    public StreamWriter(Stream stream, Encoding encoding) {
        if (stream == null) {
            throw new ArgumentNullException("stream");
        }
        Stream s = stream;
        this.out = new OutputStream() {
            @Override
            public void write(int b) {
                s.WriteByte(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                s.Write(b, off, len);
            }

            @Override
            public void flush() {
                s.Flush();
            }

            @Override
            public void close() {
                s.Dispose();
            }
        };
        this.encoding = encoding;
        boolean atStart = !s.CanSeek() || s.Position() == 0;
        if (encoding != null && atStart && s.CanSeek()) {
            writeBytes(encoding.GetPreamble());
        }
    }

    private void writeBytes(byte[] b) {
        try {
            out.write(b);
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    private Encoding enc() {
        return encoding == null ? Encoding.UTF8 : encoding;
    }

    private void checkOpen() {
        if (closed) {
            throw new btnet.runtime.InvalidOperationException("Cannot write to a closed TextWriter.");
        }
    }

    /** .NET {@code AutoFlush}. */
    public void AutoFlush(boolean value) {
        autoFlush = value;
        if (value) {
            Flush();
        }
    }

    public void Write(String s) {
        checkOpen();
        if (s != null) {
            pending.append(s);
        }
        if (autoFlush) {
            Flush();
        }
    }

    public void Write(char c) {
        Write(String.valueOf(c));
    }

    public void Write(Object o) {
        Write(Convert.ToString(o));
    }

    public void WriteLine() {
        Write(Environment.NewLine);
    }

    public void WriteLine(String s) {
        Write((s == null ? "" : s) + Environment.NewLine);
    }

    public void WriteLine(Object o) {
        Write(Convert.ToString(o) + Environment.NewLine);
    }

    public void Flush() {
        checkOpen();
        if (pending.length() > 0) {
            // GetBytes of whole chunks only: a dangling high surrogate stays pending
            int n = pending.length();
            if (Character.isHighSurrogate(pending.charAt(n - 1))) {
                n--;
            }
            writeBytes(enc().GetBytes(pending.substring(0, n)));
            pending.delete(0, n);
        }
        try {
            out.flush();
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    public void Close() {
        if (!closed) {
            if (pending.length() > 0) {
                writeBytes(enc().GetBytes(pending.toString()));
                pending.setLength(0);
            }
            closed = true;
            try {
                out.flush();
                out.close();
            } catch (java.io.IOException e) {
                throw Exceptions.wrap(e);
            }
        }
    }

    public void Dispose() {
        Close();
    }

    @Override
    public void close() {
        Close();
    }
}
