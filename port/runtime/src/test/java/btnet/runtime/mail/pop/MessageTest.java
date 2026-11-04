package btnet.runtime.mail.pop;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTest {

    private static Message msg(String raw) {
        return new Message(raw.getBytes(StandardCharsets.UTF_8));
    }

    // S1-48: Headers.From and Headers.Subject are null when the header is absent; To/Cc empty, never null.
    @Test
    void absentHeaders() {
        Message m = msg("X-Other: 1\r\n\r\nbody\r\n");
        assertNull(m.Headers().From());
        assertNull(m.Headers().Subject());
        assertEquals(0, m.Headers().To().size());
        assertEquals(0, m.Headers().Cc().size());
    }

    // RFC 2047 subject decoding; From address/display name; Cc list.
    @Test
    void parsedHeaders() {
        Message m = msg("From: \"Al Kaline\" <al@example.com>\r\n"
                + "To: a@example.com, b@example.com\r\n"
                + "Cc: c@example.com\r\n"
                + "Subject: =?UTF-8?B?w6l0w6k=?= (DO NOT EDIT THIS:12)\r\n\r\nhello\r\n");
        assertEquals("al@example.com", m.Headers().From().Address());
        assertEquals("Al Kaline", m.Headers().From().DisplayName());
        assertEquals("été (DO NOT EDIT THIS:12)", m.Headers().Subject());
        assertEquals(List.of("a@example.com", "b@example.com"), m.Headers().To().stream().map(RfcMailAddress::Address).toList());
        assertEquals("c@example.com", m.Headers().Cc().get(0).Address());
    }

    // OpenPop: no Content-Type -> text/plain us-ascii; GetBodyAsText via Encoding.ASCII ('?' above 0x7F).
    @Test
    void defaultContentTypeIsAscii() {
        Message m = new Message("Subject: x\r\n\r\nabé\r\n".getBytes(StandardCharsets.ISO_8859_1));
        MessagePart p = m.FindFirstPlainTextVersion();
        assertEquals("text/plain", p.ContentType().MediaType());
        assertEquals("ab?\r\n", p.GetBodyAsText());
    }

    // Multipart: first text/plain found depth-first, HTML fallback, attachments with decoded bodies and file names.
    @Test
    void multipart() {
        String raw = "From: a@b.com\r\nSubject: s\r\nMIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"XX\"\r\n\r\n"
                + "--XX\r\nContent-Type: multipart/alternative; boundary=\"YY\"\r\n\r\n"
                + "--YY\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nh=C3=A9llo\r\n"
                + "--YY\r\nContent-Type: text/html; charset=utf-8\r\n\r\n<b>hi</b>\r\n"
                + "--YY--\r\n"
                + "--XX\r\nContent-Type: application/octet-stream; name=\"data.bin\"\r\n"
                + "Content-Disposition: attachment; filename=\"report.bin\"\r\nContent-Transfer-Encoding: base64\r\n\r\nAAEC\r\n"
                + "--XX\r\nContent-Type: image/png\r\nContent-Transfer-Encoding: base64\r\n\r\nAwQ=\r\n"
                + "--XX--\r\n";
        Message m = msg(raw);
        assertEquals("héllo", m.FindFirstPlainTextVersion().GetBodyAsText());
        assertEquals("<b>hi</b>", m.FindFirstHtmlVersion().GetBodyAsText());
        List<MessagePart> att = m.FindAllAttachments();
        assertEquals(2, att.size());
        assertEquals("report.bin", att.get(0).FileName());
        assertArrayEquals(new byte[]{0, 1, 2}, att.get(0).Body());
        assertEquals("(no name)", att.get(1).FileName());
        assertEquals("image/png", att.get(1).ContentType().MediaType());
        assertTrue(m.MessagePart().IsMultiPart());
        assertNull(m.MessagePart().Body());
    }
}
