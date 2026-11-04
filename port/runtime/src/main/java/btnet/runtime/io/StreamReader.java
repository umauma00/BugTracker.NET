package btnet.runtime.io;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.Encoding;
import btnet.runtime.Exceptions;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Stands for .NET {@code System.IO.StreamReader}: default UTF-8 with byte-order-mark detection (UTF-8,
 * UTF-16LE/BE; the BOM is not returned), lossy decoding (U+FFFD). {@code ReadLine} ends lines at {@code \n},
 * {@code \r} or {@code \r\n}. Closing is idempotent.
 */
public class StreamReader implements AutoCloseable {
    private final InputStream raw;
    private PushbackReader reader;
    private final Charset declared;
    private boolean closed;

    /** Opens a file ({@code File.OpenText}). */
    public StreamReader(String path) {
        this(path, Encoding.UTF8);
    }

    public StreamReader(String path, Encoding encoding) {
        this(openFile(path), encoding);
    }

    public StreamReader(Stream stream) {
        this(stream, Encoding.UTF8);
    }

    public StreamReader(Stream stream, Encoding encoding) {
        this(requireStream(stream).ToInputStream(), encoding);
    }

    /** Over a {@code java.io.InputStream} (e.g. a request body). */
    public StreamReader(InputStream in, Encoding encoding) {
        if (in == null) {
            throw new ArgumentNullException("stream");
        }
        this.raw = in;
        this.declared = encoding == null ? StandardCharsets.UTF_8 : encoding.charset();
    }

    private static Stream requireStream(Stream s) {
        if (s == null) {
            throw new ArgumentNullException("stream");
        }
        return s;
    }

    private static InputStream openFile(String path) {
        java.io.File f = File.file(path);
        File.requireFile(f);
        try {
            return new java.io.FileInputStream(f);
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    private PushbackReader reader() {
        if (closed) {
            throw new btnet.runtime.InvalidOperationException("Cannot read from a closed TextReader.");
        }
        if (reader == null) {
            try {
                BufferedInputStream in = new BufferedInputStream(raw);
                in.mark(4);
                int b0 = in.read();
                int b1 = in.read();
                int b2 = in.read();
                Charset cs = declared;
                int skip = 0;
                if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                    cs = StandardCharsets.UTF_8;
                    skip = 3;
                } else if (b0 == 0xFF && b1 == 0xFE) {
                    cs = StandardCharsets.UTF_16LE;
                    skip = 2;
                } else if (b0 == 0xFE && b1 == 0xFF) {
                    cs = StandardCharsets.UTF_16BE;
                    skip = 2;
                }
                in.reset();
                for (int i = 0; i < skip; i++) {
                    in.read();
                }
                Reader r = new InputStreamReader(in, cs.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE));
                reader = new PushbackReader(r, 1);
            } catch (java.io.IOException e) {
                throw Exceptions.wrap(e);
            }
        }
        return reader;
    }

    /** .NET {@code ReadToEnd()}. */
    public String ReadToEnd() {
        PushbackReader r = reader();
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        try {
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
        return sb.toString();
    }

    /** .NET {@code ReadLine()}: null at end of stream. */
    public String ReadLine() {
        PushbackReader r = reader();
        StringBuilder sb = new StringBuilder();
        try {
            int c = r.read();
            if (c < 0) {
                return null;
            }
            while (c >= 0) {
                if (c == '\n') {
                    break;
                }
                if (c == '\r') {
                    int d = r.read();
                    if (d >= 0 && d != '\n') {
                        r.unread(d);
                    }
                    break;
                }
                sb.append((char) c);
                c = r.read();
            }
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
        return sb.toString();
    }

    /** .NET {@code Read()}: next char or -1. */
    public int Read() {
        try {
            return reader().read();
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code Peek()}: next char without consuming it, or -1. */
    public int Peek() {
        PushbackReader r = reader();
        try {
            int c = r.read();
            if (c >= 0) {
                r.unread(c);
            }
            return c;
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** .NET {@code EndOfStream}. */
    public boolean EndOfStream() {
        return Peek() < 0;
    }

    public void Close() {
        if (!closed) {
            closed = true;
            try {
                if (reader != null) {
                    reader.close();
                } else {
                    raw.close();
                }
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
