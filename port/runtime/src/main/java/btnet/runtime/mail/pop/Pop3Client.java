package btnet.runtime.mail.pop;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.ArgumentOutOfRangeException;
import btnet.runtime.Exceptions;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands for OpenPop 2.0.x {@code OpenPop.Pop3.Pop3Client} [S1-48] — a POP3
 * (RFC 1939) client over a plain or TLS socket.
 *
 * <ul>
 *   <li>{@link #Connect(String, int, boolean)}: TCP connect (60 s timeouts, OpenPop's
 *       defaults); {@code useSsl} → POP3S with default JDK trust and host-name
 *       check ({@code ServicePointManager} never applies); unreachable →
 *       {@link PopServerNotFoundException}; greeting not {@code +OK} →
 *       {@link PopServerNotAvailableException}.</li>
 *   <li>{@link #Authenticate(String, String)}: USER/PASS; rejection →
 *       {@link InvalidLoginException}. TODO(port): OpenPop
 *       {@code AuthenticationMethod.Auto} uses APOP when the greeting carries a
 *       timestamp.</li>
 *   <li>{@link #GetMessageUids()}: {@code UIDL}, the UID strings <b>verbatim</b>,
 *       server order. {@link #GetMessage(int)}: {@code RETR n} by message
 *       <b>number</b>. {@link #DeleteMessage(int)}: {@code DELE n} (committed at
 *       {@code QUIT}). {@link #Disconnect()}: {@code QUIT}, then closes.</li>
 *   <li>{@link #close()} = OpenPop {@code Dispose}: if still connected, runs
 *       {@link #Disconnect()} (so pending DELEs are committed), then closes the
 *       socket. TODO(port): confirm against OpenPop's {@code Dispose} whether it
 *       sends {@code QUIT}.</li>
 *   <li>A {@code -ERR} reply → {@link PopServerException}; using a member in the wrong
 *       state → {@link InvalidUseException}; socket I/O failures →
 *       {@code Exceptions.wrap(IOException)}.</li>
 * </ul>
 */
public class Pop3Client implements AutoCloseable {
    private static final int TIMEOUT_MS = 60000;

    private enum State { DISCONNECTED, AUTHORIZATION, TRANSACTION }

    private State state = State.DISCONNECTED;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private String lastServerResponse;
    private boolean disposed;

    public Pop3Client() {
    }

    /** OpenPop {@code Connected}. */
    public boolean Connected() {
        return state != State.DISCONNECTED;
    }

    /** OpenPop {@code LastServerResponse}. */
    public String LastServerResponse() {
        return lastServerResponse;
    }

    /** OpenPop {@code Connect(hostname, port, useSsl)}. */
    public void Connect(String hostname, int port, boolean useSsl) {
        assertNotDisposed();
        if (hostname == null) {
            throw new ArgumentNullException("hostname");
        }
        if (port > 65535 || port < 0) {
            throw new ArgumentOutOfRangeException("port", "Specified argument was out of the range of valid values.");
        }
        if (state != State.DISCONNECTED) {
            throw new InvalidUseException("You cannot ask to connect to a POP3 server, when we are already connected to one. Disconnect first.");
        }
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(hostname, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
        } catch (IOException e) {
            closeQuietly(s);
            throw new PopServerNotFoundException("Server not found", e);
        }
        try {
            if (useSsl) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(s, hostname, port, true);
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(params);
                ssl.startHandshake();
                s = ssl;
            }
            this.socket = s;
            this.in = new BufferedInputStream(s.getInputStream());
            this.out = s.getOutputStream();
        } catch (IOException e) {
            closeQuietly(s);
            throw Exceptions.wrap(e);
        }
        String greeting = readLineString();
        lastServerResponse = greeting;
        if (greeting == null || !greeting.startsWith("+")) {
            closeQuietly(socket);
            socket = null;
            throw new PopServerNotAvailableException("Server is not available",
                    new PopServerException("The server did not respond with a + response. The response was: \"" + greeting + "\""));
        }
        state = State.AUTHORIZATION;
    }

    /** OpenPop {@code Authenticate(username, password)} (USER/PASS). */
    public void Authenticate(String username, String password) {
        assertNotDisposed();
        if (username == null) {
            throw new ArgumentNullException("username");
        }
        if (password == null) {
            throw new ArgumentNullException("password");
        }
        if (state != State.AUTHORIZATION) {
            throw new InvalidUseException("You have to be connected and not authorized when trying to authorize yourself");
        }
        try {
            sendCommand("USER " + username);
            sendCommand("PASS " + password);
        } catch (PopServerException e) {
            throw new InvalidLoginException("Server did not accept user credentials", e);
        }
        state = State.TRANSACTION;
    }

    /** OpenPop {@code GetMessageCount()} ({@code STAT}). */
    public int GetMessageCount() {
        assertTransaction("You cannot get the message count without authenticating yourself towards the server first");
        String r = sendCommand("STAT");
        String[] parts = r.split(" ");
        try {
            return Integer.parseInt(parts[1]);
        } catch (RuntimeException e) {
            throw new PopServerException("The server did not respond with a valid STAT response: \"" + r + "\"");
        }
    }

    /** OpenPop {@code GetMessageUids()} ({@code UIDL}): the UID values verbatim, server order. */
    public List<String> GetMessageUids() {
        assertTransaction("You cannot get message IDs when you are not authenticated");
        sendCommand("UIDL");
        List<String> uids = new ArrayList<>();
        for (byte[] line : readMultiLine()) {
            String s = new String(line, StandardCharsets.ISO_8859_1);
            String[] parts = s.split(" ");
            if (parts.length < 2) {
                throw new PopServerException("The server did not respond with a valid UIDL line: \"" + s + "\"");
            }
            uids.add(parts[1]);
        }
        return uids;
    }

    /** OpenPop {@code GetMessageUid(n)} ({@code UIDL n}). */
    public String GetMessageUid(int messageNumber) {
        assertTransaction("You cannot get message ID if you are not authenticated");
        validateNumber(messageNumber);
        String r = sendCommand("UIDL " + messageNumber);
        String[] parts = r.split(" ");
        if (parts.length < 3) {
            throw new PopServerException("The server did not respond with a valid UIDL response: \"" + r + "\"");
        }
        return parts[2];
    }

    /** OpenPop {@code GetMessage(messageNumber)} ({@code RETR n}). */
    public Message GetMessage(int messageNumber) {
        return new Message(GetMessageAsBytes(messageNumber));
    }

    /**
     * OpenPop {@code GetMessageAsBytes(n)}: the RETR payload, dot-stuffing removed,
     * each line terminated by CRLF, without the final {@code .} line.
     */
    public byte[] GetMessageAsBytes(int messageNumber) {
        assertTransaction("You cannot get a message if you are not authenticated");
        validateNumber(messageNumber);
        sendCommand("RETR " + messageNumber);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (byte[] line : readMultiLine()) {
            bos.write(line, 0, line.length);
            bos.write('\r');
            bos.write('\n');
        }
        return bos.toByteArray();
    }

    /** OpenPop {@code DeleteMessage(n)} ({@code DELE n}). */
    public void DeleteMessage(int messageNumber) {
        assertTransaction("You cannot delete any messages without authenticating yourself towards the server first");
        validateNumber(messageNumber);
        sendCommand("DELE " + messageNumber);
    }

    /** OpenPop {@code DeleteAllMessages()}. */
    public void DeleteAllMessages() {
        int count = GetMessageCount();
        for (int i = 1; i <= count; i++) {
            DeleteMessage(i);
        }
    }

    /** OpenPop {@code NoOperation()} ({@code NOOP}). */
    public void NoOperation() {
        assertTransaction("You cannot use the NOOP command unless you are authenticated to the server");
        sendCommand("NOOP");
    }

    /** OpenPop {@code Reset()} ({@code RSET}). */
    public void Reset() {
        assertTransaction("You cannot use the RSET command unless you are authenticated to the server");
        sendCommand("RSET");
    }

    /** OpenPop {@code Disconnect()}: {@code QUIT}, then the socket is closed in any case. */
    public void Disconnect() {
        assertNotDisposed();
        if (state == State.DISCONNECTED) {
            throw new InvalidUseException("You cannot disconnect a connection which is already disconnected");
        }
        try {
            sendCommand("QUIT");
        } finally {
            disconnectStreams();
        }
    }

    /** OpenPop {@code Dispose()} (see the class comment). */
    public void Dispose() {
        if (disposed) {
            return;
        }
        try {
            if (state != State.DISCONNECTED) {
                Disconnect();
            }
        } finally {
            disconnectStreams();
            disposed = true;
        }
    }

    @Override
    public void close() {
        Dispose();
    }

    // ------------------------------------------------------------------ wire

    private void assertNotDisposed() {
        if (disposed) {
            throw new btnet.runtime.DotNetException("Cannot access a disposed object.\r\nObject name: 'Pop3Client'.");
        }
    }

    private void assertTransaction(String message) {
        assertNotDisposed();
        if (state != State.TRANSACTION) {
            throw new InvalidUseException(message);
        }
    }

    private static void validateNumber(int messageNumber) {
        if (messageNumber <= 0) {
            throw new InvalidUseException("The messageNumber argument cannot have a value of zero or less. Valid messageNumber is in the range [1, messageCount]");
        }
    }

    /** Sends one command line; returns the {@code +OK} line, or throws {@link PopServerException}. */
    private String sendCommand(String command) {
        try {
            out.write((command + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        } catch (IOException e) {
            throw Exceptions.wrap(e);
        }
        String r = readLineString();
        lastServerResponse = r;
        if (r == null) {
            throw new PopServerException("The stream used to retrieve responses from was closed");
        }
        if (!r.startsWith("+")) {
            throw new PopServerException("The server did not respond with a + response. The response was: \"" + r + "\"");
        }
        return r;
    }

    private String readLineString() {
        byte[] b = readLineBytes();
        return b == null ? null : new String(b, StandardCharsets.ISO_8859_1);
    }

    /** One line without its CR/LF; {@code null} at end of stream. */
    private byte[] readLineBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int c;
            boolean any = false;
            while ((c = in.read()) != -1) {
                any = true;
                if (c == '\n') {
                    break;
                }
                bos.write(c);
            }
            if (!any) {
                return null;
            }
            byte[] b = bos.toByteArray();
            if (b.length > 0 && b[b.length - 1] == '\r') {
                byte[] t = new byte[b.length - 1];
                System.arraycopy(b, 0, t, 0, t.length);
                return t;
            }
            return b;
        } catch (IOException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** The lines of a multi-line response up to (not including) the {@code .} line, dot-unstuffed. */
    private List<byte[]> readMultiLine() {
        List<byte[]> lines = new ArrayList<>();
        while (true) {
            byte[] line = readLineBytes();
            if (line == null) {
                throw new PopServerException("The stream used to retrieve responses from was closed");
            }
            if (line.length == 1 && line[0] == '.') {
                return lines;
            }
            if (line.length > 0 && line[0] == '.') {
                byte[] t = new byte[line.length - 1];
                System.arraycopy(line, 1, t, 0, t.length);
                line = t;
            }
            lines.add(line);
        }
    }

    private void disconnectStreams() {
        state = State.DISCONNECTED;
        closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing a dead socket
            }
        }
    }
}
