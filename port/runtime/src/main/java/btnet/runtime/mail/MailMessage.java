package btnet.runtime.mail;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.Encoding;
import btnet.runtime.Exceptions;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.activation.DataHandler;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Stands for .NET {@code System.Net.Mail.MailMessage}. Properties follow the
 * runtime convention ({@code msg.Subject = s} → {@code msg.Subject(s)}); the
 * collections keep the .NET {@code Add} member: {@code msg.To.Add(a)} →
 * {@code msg.To().Add(a)}, {@code msg.Headers.Add(n, v)} →
 * {@code msg.Headers().Add(n, v)}, {@code msg.Attachments.Add(x)} →
 * {@code msg.Attachments().Add(x)}.
 *
 * <p>Body encoding: an explicit {@code BodyEncoding} wins ({@code Encoding.UTF8} →
 * utf-8, {@code Encoding.Default} → windows-1252); without one, .NET picks us-ascii
 * for an all-ASCII body and utf-8 otherwise. {@code Priority}: High →
 * {@code X-Priority: 1}, {@code Priority: urgent}, {@code Importance: high};
 * Low → {@code 5}, {@code non-urgent}, {@code low}; Normal → none (.NET).
 */
public class MailMessage implements AutoCloseable {
    private MailAddress from;
    private MailAddress sender;
    private final MailAddressCollection to = new MailAddressCollection();
    private final MailAddressCollection cc = new MailAddressCollection();
    private final MailAddressCollection bcc = new MailAddressCollection();
    private final MailAddressCollection replyToList = new MailAddressCollection();
    private final AttachmentCollection attachments = new AttachmentCollection();
    private final HeaderCollection headers = new HeaderCollection();
    private String subject = "";
    private String body = "";
    private boolean isBodyHtml;
    private btnet.runtime.mail.MailPriority priority = btnet.runtime.mail.MailPriority.Normal;
    private Encoding bodyEncoding;
    private Encoding subjectEncoding;
    private boolean disposed;

    public MailMessage() {
    }

    /** .NET {@code MailMessage(string from, string to)}. */
    public MailMessage(String from, String to) {
        if (from == null) {
            throw new ArgumentNullException("from");
        }
        if (to == null) {
            throw new ArgumentNullException("to");
        }
        this.from = new MailAddress(from);
        this.to.Add(to);
    }

    /** .NET {@code MailMessage(from, to, subject, body)}. */
    public MailMessage(String from, String to, String subject, String body) {
        this(from, to);
        Subject(subject);
        Body(body);
    }

    /** .NET {@code MailMessage(MailAddress from, MailAddress to)}. */
    public MailMessage(MailAddress from, MailAddress to) {
        if (from == null) {
            throw new ArgumentNullException("from");
        }
        if (to == null) {
            throw new ArgumentNullException("to");
        }
        this.from = from;
        this.to.Add(to);
    }

    public MailAddress From() { return from; }

    public void From(MailAddress value) {
        if (value == null) {
            throw new ArgumentNullException("value");
        }
        this.from = value;
    }

    public MailAddress Sender() { return sender; }

    public void Sender(MailAddress value) { this.sender = value; }

    public MailAddressCollection To() { return to; }

    public MailAddressCollection CC() { return cc; }

    public MailAddressCollection Bcc() { return bcc; }

    public MailAddressCollection ReplyToList() { return replyToList; }

    public AttachmentCollection Attachments() { return attachments; }

    public HeaderCollection Headers() { return headers; }

    public String Subject() { return subject; }

    /** .NET setter: {@code null} → {@code ""}; CR/LF are not allowed in a subject (.NET throws {@code ArgumentException}). */
    public void Subject(String value) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new btnet.runtime.ArgumentException("The specified string is not in the form required for a subject.");
        }
        this.subject = value == null ? "" : value;
    }

    public String Body() { return body; }

    /** .NET setter: {@code null} → {@code ""}. */
    public void Body(String value) { this.body = value == null ? "" : value; }

    public boolean IsBodyHtml() { return isBodyHtml; }

    public void IsBodyHtml(boolean value) { this.isBodyHtml = value; }

    public btnet.runtime.mail.MailPriority Priority() { return priority; }

    public void Priority(btnet.runtime.mail.MailPriority value) { this.priority = value; }

    public Encoding BodyEncoding() { return bodyEncoding; }

    public void BodyEncoding(Encoding value) { this.bodyEncoding = value; }

    public Encoding SubjectEncoding() { return subjectEncoding; }

    public void SubjectEncoding(Encoding value) { this.subjectEncoding = value; }

    /** .NET {@code Dispose()}: disposes the attachments (idempotent). */
    public void Dispose() {
        if (!disposed) {
            disposed = true;
            for (Attachment a : attachments) {
                a.Dispose();
            }
        }
    }

    @Override
    public void close() {
        Dispose();
    }

    // ---------------------------------------------------------- MIME building

    static String charsetOf(Encoding e, String text) {
        if (e == null) {
            return isAscii(text) ? "us-ascii" : "utf-8";
        }
        if (e == Encoding.Default) {
            return "windows-1252";
        }
        // Encoding.UTF8 and anything else the source might pass
        return "utf-8";
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    static InternetAddress toInternet(MailAddress a) {
        try {
            String cs = a.DisplayNameEncoding() == null ? "utf-8" : charsetOf(a.DisplayNameEncoding(), a.DisplayName());
            InternetAddress ia = new InternetAddress(a.Address(), a.DisplayName().isEmpty() ? null : a.DisplayName(), cs);
            return ia;
        } catch (UnsupportedEncodingException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** Builds the Jakarta Mail message (runtime-internal, used by {@link SmtpClient}). */
    MimeMessage toMimeMessage(Session session) throws MessagingException {
        MimeMessage m = new MimeMessage(session);
        if (from != null) {
            m.setFrom(toInternet(from));
        }
        if (sender != null) {
            m.setSender(toInternet(sender));
        }
        for (MailAddress a : to) {
            m.addRecipient(Message.RecipientType.TO, toInternet(a));
        }
        for (MailAddress a : cc) {
            m.addRecipient(Message.RecipientType.CC, toInternet(a));
        }
        for (MailAddress a : bcc) {
            m.addRecipient(Message.RecipientType.BCC, toInternet(a));
        }
        if (!replyToList.isEmpty()) {
            m.setReplyTo(replyToList.stream().map(MailMessage::toInternet).toArray(InternetAddress[]::new));
        }
        String subjectCharset = subjectEncoding != null ? charsetOf(subjectEncoding, subject) : (isAscii(subject) ? "us-ascii" : "utf-8");
        m.setSubject(subject, subjectCharset);
        m.setSentDate(new Date());
        switch (priority) {
            case High -> {
                m.setHeader("X-Priority", "1");
                m.setHeader("Priority", "urgent");
                m.setHeader("Importance", "high");
            }
            case Low -> {
                m.setHeader("X-Priority", "5");
                m.setHeader("Priority", "non-urgent");
                m.setHeader("Importance", "low");
            }
            default -> {
            }
        }
        for (Map.Entry<String, String> h : headers.entries()) {
            m.addHeader(h.getKey(), MimeUtility.fold(h.getKey().length() + 2, h.getValue()));
        }
        String charset = charsetOf(bodyEncoding, body);
        String subtype = isBodyHtml ? "html" : "plain";
        if (attachments.isEmpty()) {
            m.setText(body, charset, subtype);
        } else {
            MimeMultipart mp = new MimeMultipart("mixed");
            MimeBodyPart text = new MimeBodyPart();
            text.setText(body, charset, subtype);
            mp.addBodyPart(text);
            for (Attachment a : attachments) {
                MimeBodyPart p = new MimeBodyPart();
                p.setDataHandler(new DataHandler(new ByteArrayDataSource(a.Content(), a.MediaType())));
                try {
                    p.setFileName(MimeUtility.encodeText(a.Name(), StandardCharsets.UTF_8.name(), null));
                } catch (UnsupportedEncodingException e) {
                    throw Exceptions.wrap(e);
                }
                p.setDisposition(MimeBodyPart.ATTACHMENT);
                mp.addBodyPart(p);
            }
            m.setContent(mp);
        }
        m.saveChanges();
        return m;
    }

    // ------------------------------------------------------------ collections

    /** .NET {@code MailAddressCollection}. */
    public static class MailAddressCollection extends ArrayList<MailAddress> {
        private static final long serialVersionUID = 1L;

        /** .NET {@code Add(MailAddress)}; null → {@code ArgumentNullException}. */
        public void Add(MailAddress item) {
            if (item == null) {
                throw new ArgumentNullException("item");
            }
            add(item);
        }

        /**
         * .NET {@code Add(string addresses)}: comma-separated addresses (commas inside
         * quotes or angle brackets do not split); null → {@code ArgumentNullException},
         * empty → {@code ArgumentException}.
         */
        public void Add(String addresses) {
            if (addresses == null) {
                throw new ArgumentNullException("addresses");
            }
            if (addresses.isEmpty()) {
                throw new btnet.runtime.ArgumentException("The parameter 'addresses' cannot be an empty string.", "addresses");
            }
            for (String a : splitAddresses(addresses)) {
                add(new MailAddress(a));
            }
        }

        public int Count() {
            return size();
        }

        /** .NET {@code ToString()}: the addresses joined with {@code ", "}. */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (MailAddress a : this) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(a);
            }
            return sb.toString();
        }

        private static List<String> splitAddresses(String s) {
            List<String> r = new ArrayList<>();
            boolean quoted = false;
            int angle = 0;
            int start = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                } else if (!quoted && c == '<') {
                    angle++;
                } else if (!quoted && c == '>') {
                    angle = Math.max(0, angle - 1);
                } else if (!quoted && angle == 0 && c == ',') {
                    r.add(s.substring(start, i));
                    start = i + 1;
                }
            }
            r.add(s.substring(start));
            return r;
        }
    }

    /** .NET {@code AttachmentCollection}. */
    public static class AttachmentCollection extends ArrayList<Attachment> {
        private static final long serialVersionUID = 1L;

        /** .NET {@code Add(Attachment)}; null → {@code ArgumentNullException}. */
        public void Add(Attachment item) {
            if (item == null) {
                throw new ArgumentNullException("item");
            }
            add(item);
        }

        public int Count() {
            return size();
        }
    }

    /** .NET {@code MailMessage.Headers} (a {@code NameValueCollection}: ordered, case-insensitive names). */
    public static class HeaderCollection {
        private final List<Map.Entry<String, String>> entries = new ArrayList<>();

        /** .NET {@code Add(name, value)}: appends (a second value for a name is kept). */
        public void Add(String name, String value) {
            if (name == null) {
                throw new ArgumentNullException("name");
            }
            entries.add(Map.entry(name, value == null ? "" : value));
        }

        /** .NET indexer read: values of {@code name} comma-joined, {@code null} if absent. */
        public String get(String name) {
            StringBuilder sb = null;
            for (Map.Entry<String, String> e : entries) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    if (sb == null) {
                        sb = new StringBuilder(e.getValue());
                    } else {
                        sb.append(',').append(e.getValue());
                    }
                }
            }
            return sb == null ? null : sb.toString();
        }

        /** .NET indexer write / {@code Set}: replaces every value of {@code name}. */
        public void set(String name, String value) {
            Remove(name);
            Add(name, value);
        }

        /** .NET {@code Remove(name)}. */
        public void Remove(String name) {
            entries.removeIf(e -> e.getKey().equalsIgnoreCase(name));
        }

        public int Count() {
            return entries.size();
        }

        List<Map.Entry<String, String>> entries() {
            return entries;
        }
    }
}
