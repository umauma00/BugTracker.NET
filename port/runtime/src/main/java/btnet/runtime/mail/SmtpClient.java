package btnet.runtime.mail;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.ArgumentOutOfRangeException;
import btnet.runtime.DirectoryNotFoundException;
import btnet.runtime.DotNetException;
import btnet.runtime.InvalidOperationException;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Stands for .NET {@code System.Net.Mail.SmtpClient} over Jakarta Mail.
 *
 * <p>{@code new SmtpClient()} takes its defaults from {@code system.net/mailSettings},
 * which the port keeps as {@code btnet.mail.*} (RULEBOOK §2A Settings), read from the
 * Spring {@code Environment} when one exists (relaxed binding: env
 * {@code BTNET_MAIL_HOST}…), else from system properties and
 * {@code application.properties} on the class path:
 * {@code btnet.mail.deliveryMethod} ({@code network} | {@code specifiedPickupDirectory}
 * (default, {@code Web.config:52}) | {@code pickupDirectoryFromIis}),
 * {@code btnet.mail.pickupDirectory} (default {@code C:/temp/pickup}),
 * {@code btnet.mail.host}, {@code btnet.mail.port} (default 25),
 * {@code btnet.mail.user}, {@code btnet.mail.password}, {@code btnet.mail.ssl}
 * ({@code EnableSsl} default), {@code btnet.mail.from}.
 *
 * <p>{@code EnableSsl} is explicit TLS (STARTTLS required), as .NET's SmtpClient
 * (it never speaks implicit TLS on 465). {@code Timeout} default 100 000 ms.
 * Specified pickup directory: writes {@code <dir>/<guid>.eml} preceded by
 * {@code X-Sender}/{@code X-Receiver} lines (the .NET pickup format), the file
 * created new; a missing directory → {@code SmtpException("Failure sending mail.")}
 * with inner {@code DirectoryNotFoundException}. Every failure of {@link #Send}
 * other than argument/state checks → {@code SmtpException("Failure sending mail.", inner)}
 * (callers read {@code GetBaseException().Message}). TODO(port): inner-exception
 * texts are the JDK's (e.g. "Connection refused"), not the .NET socket texts.
 *
 * <p>{@link #TrustAllCertificates(boolean)} [C5] stands for assigning
 * {@code ServicePointManager.ServerCertificateValidationCallback = … true}: static,
 * process-wide and sticky; read by every later {@link #Send} and by
 * {@code btnet.runtime.net.HttpClients}.
 */
public class SmtpClient implements AutoCloseable {

    /** .NET {@code SmtpDeliveryMethod}. */
    public enum SmtpDeliveryMethod {
        Network,
        SpecifiedPickupDirectory,
        PickupDirectoryFromIis
    }

    /** Stands for .NET {@code System.Net.Mail.SmtpException}. */
    public static class SmtpException extends DotNetException {
        private static final long serialVersionUID = 1L;

        public SmtpException(String message) {
            super(message);
        }

        public SmtpException(String message, Throwable inner) {
            super(message, inner);
        }
    }

    /** The {@code system.net/mailSettings/smtp} section as configured ({@code btnet.mail.*}). */
    public static final class MailSettings {
        public final SmtpDeliveryMethod deliveryMethod;
        public final String pickupDirectory;
        public final String host;
        public final int port;
        public final String user;
        public final String password;
        public final boolean ssl;
        public final String from;

        MailSettings(SmtpDeliveryMethod deliveryMethod, String pickupDirectory, String host, int port,
                     String user, String password, boolean ssl, String from) {
            this.deliveryMethod = deliveryMethod;
            this.pickupDirectory = pickupDirectory;
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
            this.ssl = ssl;
            this.from = from;
        }
    }

    /** Hands the Spring {@code Environment} to the mail settings (component-scanned). */
    @Component
    public static class EnvironmentBinder implements EnvironmentAware {
        @Override
        public void setEnvironment(Environment environment) {
            SmtpClient.environment = environment;
            SmtpClient.settings = null;
        }
    }

    private static volatile boolean trustAll;
    private static volatile Environment environment;
    private static volatile MailSettings settings;
    private static volatile Properties classpathProperties;

    private String host;
    private int port;
    private boolean enableSsl;
    private SmtpDeliveryMethod deliveryMethod;
    private String pickupDirectoryLocation;
    private String userName;
    private String password;
    private int timeout = 100000;
    private boolean disposed;

    /** .NET {@code SmtpClient()}: defaults from the mail settings. */
    public SmtpClient() {
        MailSettings s = Settings();
        this.host = s.host;
        this.port = s.port;
        this.enableSsl = s.ssl;
        this.deliveryMethod = s.deliveryMethod;
        this.pickupDirectoryLocation = s.pickupDirectory;
        this.userName = s.user;
        this.password = s.password;
    }

    /** .NET {@code SmtpClient(string host)}. */
    public SmtpClient(String host) {
        this();
        this.host = host;
        this.deliveryMethod = SmtpDeliveryMethod.Network;
    }

    /** .NET {@code SmtpClient(string host, int port)}. */
    public SmtpClient(String host, int port) {
        this(host);
        Port(port);
    }

    // ------------------------------------------------------------- statics

    /** [C5] process-wide trust-all flag (sticky; .NET {@code ServerCertificateValidationCallback}). */
    public static void TrustAllCertificates(boolean value) {
        trustAll = value;
    }

    /** Whether {@link #TrustAllCertificates(boolean)} was set. */
    public static boolean TrustAllCertificates() {
        return trustAll;
    }

    /** The configured mail settings ({@code system.net/mailSettings/smtp}). */
    public static MailSettings Settings() {
        MailSettings s = settings;
        if (s == null) {
            String dm = prop("btnet.mail.deliveryMethod", "specifiedPickupDirectory");
            SmtpDeliveryMethod method = switch (dm.toLowerCase(Locale.ROOT)) {
                case "network" -> SmtpDeliveryMethod.Network;
                case "pickupdirectoryfromiis" -> SmtpDeliveryMethod.PickupDirectoryFromIis;
                default -> SmtpDeliveryMethod.SpecifiedPickupDirectory;
            };
            int port;
            try {
                port = Integer.parseInt(prop("btnet.mail.port", "25").trim());
            } catch (NumberFormatException e) {
                port = 25;
            }
            String ssl = prop("btnet.mail.ssl", "false").trim();
            s = new MailSettings(method,
                    prop("btnet.mail.pickupDirectory", "C:\\temp\\pickup").replace('\\', '/'),
                    emptyToNull(prop("btnet.mail.host", "")),
                    port,
                    emptyToNull(prop("btnet.mail.user", "")),
                    emptyToNull(prop("btnet.mail.password", "")),
                    ssl.equalsIgnoreCase("true") || ssl.equals("1"),
                    emptyToNull(prop("btnet.mail.from", "")));
            settings = s;
        }
        return s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String prop(String key, String dflt) {
        Environment env = environment;
        if (env != null) {
            String v = env.getProperty(key);
            return v == null ? dflt : v;
        }
        String v = System.getProperty(key);
        if (v != null) {
            return v;
        }
        String envName = key.toUpperCase(Locale.ROOT).replace('.', '_');
        v = System.getenv(envName);
        if (v != null) {
            return v;
        }
        return classpathProperties().getProperty(key, dflt);
    }

    private static Properties classpathProperties() {
        Properties p = classpathProperties;
        if (p == null) {
            p = new Properties();
            try (InputStream in = SmtpClient.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (in != null) {
                    p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // no properties: defaults apply
            }
            classpathProperties = p;
        }
        return p;
    }

    // ---------------------------------------------------------- properties

    public String Host() { return host; }

    public void Host(String value) {
        if (value == null) {
            throw new ArgumentNullException("value");
        }
        if (value.isEmpty()) {
            throw new btnet.runtime.ArgumentException("The parameter 'value' cannot be an empty string.", "value");
        }
        this.host = value.trim();
    }

    public int Port() { return port; }

    public void Port(int value) {
        if (value <= 0) {
            throw new ArgumentOutOfRangeException("value", "Specified argument was out of the range of valid values.");
        }
        this.port = value;
    }

    public boolean EnableSsl() { return enableSsl; }

    public void EnableSsl(boolean value) { this.enableSsl = value; }

    public SmtpDeliveryMethod DeliveryMethod() { return deliveryMethod; }

    public void DeliveryMethod(SmtpDeliveryMethod value) { this.deliveryMethod = value; }

    public String PickupDirectoryLocation() { return pickupDirectoryLocation; }

    public void PickupDirectoryLocation(String value) { this.pickupDirectoryLocation = value; }

    public int Timeout() { return timeout; }

    public void Timeout(int value) {
        if (value < 0) {
            throw new ArgumentOutOfRangeException("value", "Specified argument was out of the range of valid values.");
        }
        this.timeout = value;
    }

    /** Stands for {@code Credentials = new NetworkCredential(user, password)}. */
    public void Credentials(String user, String pass) {
        this.userName = user;
        this.password = pass;
    }

    // ---------------------------------------------------------------- Send

    /** .NET {@code Send(from, recipients, subject, body)}. */
    public void Send(String from, String recipients, String subject, String body) {
        try (MailMessage m = new MailMessage(from, recipients, subject, body)) {
            Send(m);
        }
    }

    /** .NET {@code Send(MailMessage)} (see the class comment). */
    public void Send(MailMessage message) {
        if (disposed) {
            throw new btnet.runtime.DotNetException("Cannot access a disposed object.\r\nObject name: 'System.Net.Mail.SmtpClient'.");
        }
        if (message == null) {
            throw new ArgumentNullException("message");
        }
        if (message.From() == null) {
            MailSettings s = Settings();
            if (s.from == null) {
                throw new InvalidOperationException("A from address must be specified.");
            }
            message.From(new MailAddress(s.from));
        }
        if (message.To().isEmpty() && message.CC().isEmpty() && message.Bcc().isEmpty()) {
            throw new InvalidOperationException("A recipient must be specified.");
        }
        SmtpDeliveryMethod dm = deliveryMethod == null ? SmtpDeliveryMethod.Network : deliveryMethod;
        if (dm == SmtpDeliveryMethod.Network && (host == null || host.isEmpty())) {
            throw new InvalidOperationException("The SMTP host was not specified.");
        }
        try {
            switch (dm) {
                case SpecifiedPickupDirectory -> writePickup(message, pickupDirectoryLocation);
                case PickupDirectoryFromIis -> throw new SmtpException("Cannot get IIS pickup directory.");
                default -> sendNetwork(message);
            }
        } catch (SmtpException e) {
            throw e;
        } catch (MessagingException | IOException | RuntimeException e) {
            throw new SmtpException("Failure sending mail.", e);
        }
    }

    private Session session() {
        Properties p = new Properties();
        p.put("mail.smtp.host", host == null ? "" : host);
        p.put("mail.smtp.port", Integer.toString(port));
        String t = Integer.toString(timeout);
        p.put("mail.smtp.connectiontimeout", t);
        p.put("mail.smtp.timeout", t);
        p.put("mail.smtp.writetimeout", t);
        if (enableSsl) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        if (userName != null && !userName.isEmpty()) {
            p.put("mail.smtp.auth", "true");
        }
        if (trustAll) {
            p.put("mail.smtp.ssl.trust", "*");
            p.put("mail.smtp.ssl.checkserveridentity", "false");
            p.put("mail.smtp.ssl.socketFactory", trustAllContext().getSocketFactory());
        } else {
            p.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        return Session.getInstance(p);
    }

    private void sendNetwork(MailMessage message) throws MessagingException {
        Session s = session();
        MimeMessage mm = message.toMimeMessage(s);
        Address[] all = mm.getAllRecipients();
        try (Transport tr = s.getTransport("smtp")) {
            if (userName != null && !userName.isEmpty()) {
                tr.connect(host, port, userName, password);
            } else {
                tr.connect(host, port, null, null);
            }
            tr.sendMessage(mm, all);
        }
    }

    private void writePickup(MailMessage message, String dir) throws MessagingException, IOException {
        if (dir == null || dir.isEmpty()) {
            throw new SmtpException("The SMTP pickup directory was not specified.");
        }
        String d = dir.replace('\\', '/');
        Path folder = Paths.get(d);
        String file = d + (d.endsWith("/") ? "" : "/") + UUID.randomUUID() + ".eml";
        if (!Files.isDirectory(folder)) {
            throw new SmtpException("Failure sending mail.",
                    new DirectoryNotFoundException("Could not find a part of the path '" + file + "'."));
        }
        MimeMessage mm = message.toMimeMessage(session());
        try (OutputStream out = Files.newOutputStream(Paths.get(file), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            StringBuilder env = new StringBuilder();
            env.append("X-Sender: ").append(message.From().Address()).append("\r\n");
            for (Address a : mm.getAllRecipients()) {
                env.append("X-Receiver: ").append(((jakarta.mail.internet.InternetAddress) a).getAddress()).append("\r\n");
            }
            out.write(env.toString().getBytes(StandardCharsets.US_ASCII));
            mm.writeTo(out);
        } catch (FileAlreadyExistsException | NoSuchFileException e) {
            throw new SmtpException("Failure sending mail.", e);
        }
    }

    private static SSLContext trustAllContext() {
        try {
            SSLContext c = SSLContext.getInstance("TLS");
            c.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            return c;
        } catch (GeneralSecurityException e) {
            throw btnet.runtime.Exceptions.wrap(e);
        }
    }

    /** .NET {@code Dispose()} (idempotent). */
    public void Dispose() {
        disposed = true;
    }

    @Override
    public void close() {
        Dispose();
    }
}
