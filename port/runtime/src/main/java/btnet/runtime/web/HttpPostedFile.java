package btnet.runtime.web;

import btnet.runtime.io.MemoryStream;
import btnet.runtime.io.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * .NET {@code System.Web.HttpPostedFile} ({@code Request.Files[...]},
 * {@code HtmlInputFile.PostedFile}; RULEBOOK §2A {@code Request.Files} row) over one
 * multipart file part (held in memory; request limit 50 MB, {@code Web.config:46}).
 */
public final class HttpPostedFile {
    private final String fileName;
    private final String contentType;
    private final byte[] content;

    public HttpPostedFile(String fileName, String contentType, byte[] content) {
        this.fileName = fileName == null ? "" : fileName;
        this.contentType = contentType == null ? "" : contentType;
        this.content = content == null ? new byte[0] : content;
    }

    /** The client file name as posted ({@code ""} when no file was chosen). */
    public String FileName() {
        return fileName;
    }

    public int ContentLength() {
        return content.length;
    }

    public String ContentType() {
        return contentType;
    }

    /** A fresh read-only view of the content, positioned at 0. */
    public Stream InputStream() {
        return new MemoryStream(content.clone());
    }

    /** The raw bytes (runtime convenience). */
    public byte[] bytes() {
        return content.clone();
    }

    /**
     * .NET {@code SaveAs(filename)}: the path must be rooted, else {@code HttpException}
     * ({@code "The SaveAs method is configured to require a rooted path, and the path '<p>' is not rooted."});
     * {@code \} is normalized to {@code /}.
     */
    public void SaveAs(String filename) {
        String p = filename == null ? "" : filename.replace('\\', '/');
        Path path = Paths.get(p);
        if (!path.isAbsolute()) {
            throw new HttpException("The SaveAs method is configured to require a rooted path, and the path '"
                + filename + "' is not rooted.");
        }
        try {
            Files.write(path, content);
        } catch (IOException e) {
            throw new HttpException(e.getMessage(), e);
        }
    }
}
