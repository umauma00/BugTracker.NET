package btnet.runtime.mail;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentNullException;
import btnet.runtime.FormatException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailAddressTest {

    // .NET MailAddress accepts addr, Display <addr>, "Display" <addr>.
    @Test
    void parsesCommonForms() {
        assertEquals("ctrager@yahoo.com", new MailAddress("ctrager@yahoo.com").Address());
        MailAddress a = new MailAddress("Corey Trager <ctrager@yahoo.com>");
        assertEquals("ctrager@yahoo.com", a.Address());
        assertEquals("Corey Trager", a.DisplayName());
        assertEquals("\"Corey Trager\" <ctrager@yahoo.com>", a.toString());
        assertEquals("Corey", new MailAddress("\"Corey\" <c@y.com>").DisplayName());
        assertEquals("c", new MailAddress("c@y.com").User());
        assertEquals("y.com", new MailAddress("c@y.com").Host());
        // email.cs:330 passes an empty display name
        assertEquals("c@y.com", new MailAddress("c@y.com", "", null).toString());
    }

    // .NET: null -> ArgumentNullException, "" -> ArgumentException, malformed -> FormatException
    // (send_email.aspx.cs:425,441 rely on it to flag bad To/CC input).
    @Test
    void rejectsMalformed() {
        assertThrows(ArgumentNullException.class, () -> new MailAddress(null));
        assertThrows(ArgumentException.class, () -> new MailAddress(""));
        for (String bad : List.of("not an address", "a@", "@b.com", "a@@b.com", "a b@c.com", "a@b..com", "x <a@b.com> y")) {
            FormatException e = assertThrows(FormatException.class, () -> new MailAddress(bad), bad);
            assertEquals("The specified string is not in the form required for an e-mail address.", e.getMessage());
        }
    }

    // .NET MailAddressCollection.Add(string) splits on commas.
    @Test
    void collectionAddSplitsOnCommas() {
        MailMessage m = new MailMessage();
        m.To().Add("a@b.com, \"X, Y\" <c@d.com>");
        assertEquals(2, m.To().Count());
        assertEquals("X, Y", m.To().get(1).DisplayName());
    }

    // deliveryMethod=specifiedPickupDirectory writes <dir>/<guid>.eml with X-Sender/X-Receiver lines.
    @Test
    void pickupDirectoryDelivery() throws Exception {
        Path dir = Files.createTempDirectory("pickup");
        SmtpClient c = new SmtpClient();
        c.DeliveryMethod(SmtpClient.SmtpDeliveryMethod.SpecifiedPickupDirectory);
        c.PickupDirectoryLocation(dir.toString());
        MailMessage m = new MailMessage();
        m.From(new MailAddress("from@example.com"));
        m.To().Add(new MailAddress("to@example.com", "To Person"));
        m.Subject("Hello");
        m.Body("Body text");
        m.Headers().Add("Disposition-Notification-To", "from@example.com");
        m.Priority(btnet.runtime.mail.MailPriority.High);
        c.Send(m);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> l = files.toList();
            assertEquals(1, l.size());
            assertTrue(l.get(0).getFileName().toString().endsWith(".eml"));
            String eml = Files.readString(l.get(0), StandardCharsets.ISO_8859_1);
            assertTrue(eml.startsWith("X-Sender: from@example.com\r\nX-Receiver: to@example.com\r\n"), eml);
            assertTrue(eml.contains("Subject: Hello"));
            assertTrue(eml.contains("X-Priority: 1"));
            assertTrue(eml.contains("Disposition-Notification-To: from@example.com"));
        }
    }

    // .NET: a missing pickup directory -> SmtpException("Failure sending mail.") whose base exception is DirectoryNotFound.
    @Test
    void missingPickupDirectory() {
        SmtpClient c = new SmtpClient();
        c.DeliveryMethod(SmtpClient.SmtpDeliveryMethod.SpecifiedPickupDirectory);
        c.PickupDirectoryLocation("/nonexistent/btnet/pickup");
        MailMessage m = new MailMessage("a@b.com", "c@d.com", "s", "b");
        SmtpClient.SmtpException e = assertThrows(SmtpClient.SmtpException.class, () -> c.Send(m));
        assertEquals("Failure sending mail.", e.getMessage());
        assertTrue(e.getCause() instanceof btnet.runtime.DirectoryNotFoundException);
    }

    // C5: TrustAllCertificates is static and sticky.
    @Test
    void trustAllIsSticky() {
        boolean before = SmtpClient.TrustAllCertificates();
        try {
            SmtpClient.TrustAllCertificates(true);
            assertTrue(SmtpClient.TrustAllCertificates());
        } finally {
            SmtpClient.TrustAllCertificates(before);
        }
    }
}
