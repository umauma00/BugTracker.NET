package btnet.runtime.mail.pop;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.Exceptions;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentDisposition;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimePartDataSource;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Stands for OpenPop 2.0.x {@code OpenPop.Mime.MessagePart} (members used by
 * {@code Mime.cs}: {@code ContentType.MediaType}, {@code Body}, {@code FileName},
 * {@code GetBodyAsText()}; plus {@code IsMultiPart}, {@code IsText},
 * {@code IsAttachment}, {@code MessageParts}, {@code ContentDisposition}).
 *
 * <ul>
 *   <li>No {@code Content-Type} header → {@code text/plain; charset=us-ascii}
 *       (OpenPop's default).</li>
 *   <li>{@code Body}: the transfer-decoded bytes; {@code null} for a multipart part.</li>
 *   <li>{@code GetBodyAsText()}: {@code Body} decoded with the part's charset; no
 *       charset → ASCII (.NET {@code Encoding.ASCII}: bytes above 0x7F become
 *       {@code '?'}); an unknown charset → windows-1252 (TODO(port): OpenPop's
 *       {@code EncodingFinder} fallback table).</li>
 *   <li>{@code FileName}: {@code Content-Disposition} filename, else
 *       {@code Content-Type} name, else {@code "(no name)"}.</li>
 *   <li>{@code IsText}: media type {@code text/*} or {@code message/rfc822};
 *       {@code IsAttachment}: {@code (!IsText && !IsMultiPart) ||
 *       (ContentDisposition != null && !ContentDisposition.Inline)}.</li>
 * </ul>
 */
public class MessagePart {

    /** Stands for {@code System.Net.Mime.ContentType} as OpenPop exposes it. */
    public static final class ContentTypeInfo {
        private final String mediaType;
        private final String charSet;
        private final String name;
        private final String boundary;

        ContentTypeInfo(String mediaType, String charSet, String name, String boundary) {
            this.mediaType = mediaType;
            this.charSet = charSet;
            this.name = name;
            this.boundary = boundary;
        }

        public String MediaType() { return mediaType; }

        public String CharSet() { return charSet; }

        public String Name() { return name; }

        public String Boundary() { return boundary; }

        @Override
        public String toString() {
            return mediaType + (charSet == null ? "" : "; charset=" + charSet) + (name == null ? "" : "; name=" + name);
        }
    }

    /** Stands for {@code System.Net.Mime.ContentDisposition} as OpenPop exposes it. */
    public static final class ContentDispositionInfo {
        private final String dispositionType;
        private final String fileName;

        ContentDispositionInfo(String dispositionType, String fileName) {
            this.dispositionType = dispositionType;
            this.fileName = fileName;
        }

        public String DispositionType() { return dispositionType; }

        public String FileName() { return fileName; }

        /** {@code Inline}: disposition type is {@code inline}. */
        public boolean Inline() { return "inline".equalsIgnoreCase(dispositionType); }
    }

    private final ContentTypeInfo contentType;
    private final ContentDispositionInfo contentDisposition;
    private final String contentTransferEncoding;
    private final String contentId;
    private final byte[] body;
    private final List<MessagePart> messageParts;

    MessagePart(MimePart part) {
        try {
            this.contentType = parseContentType(part.getHeader("Content-Type", null));
            this.contentDisposition = parseDisposition(part.getHeader("Content-Disposition", null));
            String cte = part.getHeader("Content-Transfer-Encoding", null);
            this.contentTransferEncoding = cte == null ? "7bit" : cte.trim().toLowerCase(Locale.ROOT);
            String cid = part.getHeader("Content-ID", null);
            this.contentId = cid == null ? null : cid.trim().replaceAll("^<|>$", "");
            if (IsMultiPart()) {
                this.body = null;
                List<MessagePart> children = new ArrayList<>();
                try {
                    MimeMultipart mp = new MimeMultipart(new MimePartDataSource(part));
                    for (int i = 0; i < mp.getCount(); i++) {
                        BodyPart bp = mp.getBodyPart(i);
                        if (bp instanceof MimePart mpart) {
                            children.add(new MessagePart(mpart));
                        }
                    }
                } catch (MessagingException malformed) {
                    // a multipart without parsable parts has no children (OpenPop yields none)
                }
                this.messageParts = Collections.unmodifiableList(children);
            } else {
                this.messageParts = Collections.emptyList();
                byte[] b;
                try (InputStream in = new MimePartDataSource(part).getInputStream()) {
                    b = in.readAllBytes();
                } catch (IOException e) {
                    b = new byte[0];
                }
                this.body = b;
            }
        } catch (MessagingException e) {
            throw Exceptions.wrap(e);
        }
    }

    private static ContentTypeInfo parseContentType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ContentTypeInfo("text/plain", "us-ascii", null, null);
        }
        try {
            ContentType ct = new ContentType(raw);
            String name = ct.getParameter("name");
            return new ContentTypeInfo(ct.getBaseType().toLowerCase(Locale.ROOT), ct.getParameter("charset"),
                    name == null ? null : decode(name), ct.getParameter("boundary"));
        } catch (ParseException e) {
            String base = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            return new ContentTypeInfo(base.contains("/") ? base : "text/plain", null, null, null);
        }
    }

    private static ContentDispositionInfo parseDisposition(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            ContentDisposition cd = new ContentDisposition(raw);
            String fn = cd.getParameter("filename");
            return new ContentDispositionInfo(cd.getDisposition(), fn == null ? null : decode(fn));
        } catch (ParseException e) {
            return new ContentDispositionInfo(raw.split(";", 2)[0].trim(), null);
        }
    }

    private static String decode(String s) {
        try {
            return MimeUtility.decodeText(s);
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    public ContentTypeInfo ContentType() { return contentType; }

    /** {@code null} when the part has no {@code Content-Disposition}. */
    public ContentDispositionInfo ContentDisposition() { return contentDisposition; }

    public String ContentTransferEncoding() { return contentTransferEncoding; }

    public String ContentId() { return contentId; }

    /** The decoded body bytes ({@code null} for multipart parts). */
    public byte[] Body() { return body; }

    public List<MessagePart> MessageParts() { return messageParts; }

    public boolean IsMultiPart() {
        return contentType.MediaType().startsWith("multipart/");
    }

    public boolean IsText() {
        String mt = contentType.MediaType();
        return mt.startsWith("text/") || mt.equals("message/rfc822");
    }

    public boolean IsAttachment() {
        return (!IsText() && !IsMultiPart()) || (contentDisposition != null && !contentDisposition.Inline());
    }

    public String FileName() {
        if (contentDisposition != null && contentDisposition.FileName() != null) {
            return contentDisposition.FileName();
        }
        if (contentType.Name() != null) {
            return contentType.Name();
        }
        return "(no name)";
    }

    /** The name of the charset {@link #GetBodyAsText()} uses ({@code us-ascii} when none). */
    public String BodyEncoding() {
        String cs = contentType.CharSet();
        return cs == null || cs.isEmpty() ? "us-ascii" : cs;
    }

    /** OpenPop {@code GetBodyAsText()} (see the class comment); a multipart part → {@code ArgumentNullException}. */
    public String GetBodyAsText() {
        if (body == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: bytes");
        }
        String cs = contentType.CharSet();
        if (cs == null || cs.isEmpty() || cs.equalsIgnoreCase("us-ascii") || cs.equalsIgnoreCase("ascii")) {
            char[] c = new char[body.length];
            for (int i = 0; i < body.length; i++) {
                int v = body[i] & 0xFF;
                c[i] = v > 0x7F ? '?' : (char) v;
            }
            return new String(c);
        }
        Charset charset;
        try {
            charset = Charset.forName(MimeUtility.javaCharset(cs.trim()));
        } catch (RuntimeException e) {
            charset = Charset.forName("windows-1252");
        }
        return new String(body, charset);
    }

    // ------------------------------------------------------------- finders

    MessagePart findFirst(String mediaType) {
        if (contentType.MediaType().equalsIgnoreCase(mediaType)) {
            return this;
        }
        for (MessagePart p : messageParts) {
            MessagePart r = p.findFirst(mediaType);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    void findAll(String mediaType, List<MessagePart> out) {
        if (contentType.MediaType().equalsIgnoreCase(mediaType)) {
            out.add(this);
        }
        for (MessagePart p : messageParts) {
            p.findAll(mediaType, out);
        }
    }

    void findAttachments(List<MessagePart> out) {
        if (IsMultiPart()) {
            for (MessagePart p : messageParts) {
                p.findAttachments(out);
            }
        } else if (IsAttachment()) {
            out.add(this);
        }
    }

    void findText(List<MessagePart> out) {
        if (IsMultiPart()) {
            for (MessagePart p : messageParts) {
                p.findText(out);
            }
        } else if (IsText()) {
            out.add(this);
        }
    }
}
