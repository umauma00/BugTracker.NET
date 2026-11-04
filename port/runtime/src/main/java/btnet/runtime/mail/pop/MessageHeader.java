package btnet.runtime.mail.pop;

import btnet.runtime.Exceptions;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.util.List;

/**
 * Stands for OpenPop {@code OpenPop.Mime.Header.MessageHeader} [S1-48]:
 * {@link #From()} — {@code null} when the header is absent; {@link #Subject()} —
 * {@code null} when absent (RFC 2047 decoded); {@link #To()}/{@link #Cc()}/{@link #Bcc()}
 * — empty, never null.
 */
public final class MessageHeader {
    private final RfcMailAddress from;
    private final String subject;
    private final List<RfcMailAddress> to;
    private final List<RfcMailAddress> cc;
    private final List<RfcMailAddress> bcc;
    private final List<RfcMailAddress> replyTo;
    private final String messageId;
    private final String date;

    MessageHeader(MimeMessage m) {
        try {
            String f = m.getHeader("From", ",");
            this.from = f == null ? null : RfcMailAddress.parse(f);
            this.subject = m.getHeader("Subject") == null ? null : m.getSubject();
            this.to = RfcMailAddress.parseList(m.getHeader("To", ","));
            this.cc = RfcMailAddress.parseList(m.getHeader("Cc", ","));
            this.bcc = RfcMailAddress.parseList(m.getHeader("Bcc", ","));
            this.replyTo = RfcMailAddress.parseList(m.getHeader("Reply-To", ","));
            String id = m.getHeader("Message-ID", null);
            this.messageId = id == null ? null : id.trim().replaceAll("^<|>$", "");
            String d = m.getHeader("Date", null);
            this.date = d == null ? null : d.trim();
        } catch (MessagingException e) {
            throw Exceptions.wrap(e);
        }
    }

    public RfcMailAddress From() { return from; }

    public String Subject() { return subject; }

    public List<RfcMailAddress> To() { return to; }

    public List<RfcMailAddress> Cc() { return cc; }

    public List<RfcMailAddress> Bcc() { return bcc; }

    public List<RfcMailAddress> ReplyTo() { return replyTo; }

    /** OpenPop {@code MessageId} (angle brackets removed), {@code null} if absent. */
    public String MessageId() { return messageId; }

    /** OpenPop {@code Date}: the raw header text, {@code null} if absent. */
    public String Date() { return date; }
}
