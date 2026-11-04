package btnet.runtime.mail.pop;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.Exceptions;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Stands for OpenPop 2.0.x {@code OpenPop.Mime.Message} [S1-48]: {@code new Message(bytes)}
 * parses a raw RFC 822 message (MIME parsing by Jakarta Mail, lenient);
 * {@link #RawMessage()} returns the bytes it was built from (for a message fetched
 * by {@link Pop3Client#GetMessage(int)}: the RETR payload, dot-stuffing removed,
 * without the final {@code .} line). Finders walk the part tree depth-first,
 * as OpenPop's visitors.
 */
public class Message {
    private static final Session PARSE_SESSION;

    static {
        Properties p = new Properties();
        p.put("mail.mime.address.strict", "false");
        p.put("mail.mime.base64.ignoreerrors", "true");
        p.put("mail.mime.decodetext.strict", "false");
        p.put("mail.mime.decodefilename", "true");
        p.put("mail.mime.decodeparameters", "true");
        p.put("mail.mime.parameters.strict", "false");
        PARSE_SESSION = Session.getInstance(p);
    }

    private final byte[] rawMessage;
    private final MessageHeader headers;
    private final MessagePart messagePart;

    /** OpenPop {@code Message(byte[] rawMessageContent)}. */
    public Message(byte[] rawMessageContent) {
        if (rawMessageContent == null) {
            throw new ArgumentNullException("rawMessageContent");
        }
        this.rawMessage = rawMessageContent.clone();
        try {
            MimeMessage mm = new MimeMessage(PARSE_SESSION, new ByteArrayInputStream(this.rawMessage));
            this.headers = new MessageHeader(mm);
            this.messagePart = new MessagePart(mm);
        } catch (MessagingException e) {
            throw Exceptions.wrap(e);
        }
    }

    public byte[] RawMessage() { return rawMessage.clone(); }

    public MessageHeader Headers() { return headers; }

    /** OpenPop {@code MessagePart}: the root part. */
    public MessagePart MessagePart() { return messagePart; }

    /** First part (depth-first) with media type {@code text/plain}, else null. */
    public MessagePart FindFirstPlainTextVersion() {
        return FindFirstMessagePartWithMediaType("text/plain");
    }

    /** First part (depth-first) with media type {@code text/html}, else null. */
    public MessagePart FindFirstHtmlVersion() {
        return FindFirstMessagePartWithMediaType("text/html");
    }

    public MessagePart FindFirstMessagePartWithMediaType(String mediaType) {
        return messagePart.findFirst(mediaType);
    }

    public List<MessagePart> FindAllMessagePartsWithMediaType(String mediaType) {
        List<MessagePart> r = new ArrayList<>();
        messagePart.findAll(mediaType, r);
        return r;
    }

    /** Every leaf part whose {@code IsAttachment} is true, in order. */
    public List<MessagePart> FindAllAttachments() {
        List<MessagePart> r = new ArrayList<>();
        messagePart.findAttachments(r);
        return r;
    }

    /** Every leaf text part, in order. */
    public List<MessagePart> FindAllTextVersions() {
        List<MessagePart> r = new ArrayList<>();
        messagePart.findText(r);
        return r;
    }
}
