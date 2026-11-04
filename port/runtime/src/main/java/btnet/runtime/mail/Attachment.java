package btnet.runtime.mail;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.DirectoryNotFoundException;
import btnet.runtime.Exceptions;
import btnet.runtime.FileNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stands for .NET {@code System.Net.Mail.Attachment}. {@code new Attachment(fileName)}
 * reads the file at construction (.NET opens it there), so a missing file fails at
 * the constructor: {@code FileNotFoundException("Could not find file '<path>'.")} or,
 * for a missing directory, {@code DirectoryNotFoundException("Could not find a part of
 * the path '<path>'.")}. Paths normalise {@code \} → {@code /}. Content type
 * {@code application/octet-stream} unless given; {@code Name} = the file name.
 */
public class Attachment implements AutoCloseable {
    private final byte[] content;
    private final String name;
    private final String mediaType;
    private boolean disposed;

    /** .NET {@code Attachment(string fileName)}. */
    public Attachment(String fileName) {
        this(fileName, (String) null);
    }

    /** .NET {@code Attachment(string fileName, string mediaType)}. */
    public Attachment(String fileName, String mediaType) {
        if (fileName == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: fileName");
        }
        String p = fileName.replace('\\', '/');
        Path path = Paths.get(p);
        if (!Files.isRegularFile(path)) {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                throw new DirectoryNotFoundException("Could not find a part of the path '" + p + "'.");
            }
            throw new FileNotFoundException("Could not find file '" + p + "'.");
        }
        try {
            this.content = Files.readAllBytes(path);
        } catch (IOException e) {
            throw Exceptions.wrap(e);
        }
        Path fn = path.getFileName();
        this.name = fn == null ? p : fn.toString();
        this.mediaType = mediaType == null || mediaType.isEmpty() ? "application/octet-stream" : mediaType;
    }

    /** An in-memory attachment (runtime helper). */
    public Attachment(byte[] content, String name, String mediaType) {
        this.content = content == null ? new byte[0] : content.clone();
        this.name = name;
        this.mediaType = mediaType == null || mediaType.isEmpty() ? "application/octet-stream" : mediaType;
    }

    /** .NET {@code Name}. */
    public String Name() {
        return name;
    }

    /** .NET {@code ContentType.MediaType}. */
    public String MediaType() {
        return mediaType;
    }

    /** The attachment bytes. */
    public byte[] Content() {
        return content.clone();
    }

    /** .NET {@code Dispose()} (idempotent). */
    public void Dispose() {
        disposed = true;
    }

    @Override
    public void close() {
        Dispose();
    }

    boolean isDisposed() {
        return disposed;
    }
}
