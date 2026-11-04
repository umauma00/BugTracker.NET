package btnet.runtime.mail.pop;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pop3ClientTest {

    /** A scripted POP3 server: answers each command, records what it received. */
    private static Thread server(ServerSocket ss, List<String> received, boolean acceptLogin) {
        Thread t = new Thread(() -> {
            try (Socket s = ss.accept()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                OutputStream o = s.getOutputStream();
                o.write("+OK ready\r\n".getBytes());
                String line;
                while ((line = r.readLine()) != null) {
                    received.add(line);
                    String reply;
                    if (line.startsWith("USER")) {
                        reply = "+OK\r\n";
                    } else if (line.startsWith("PASS")) {
                        reply = acceptLogin ? "+OK\r\n" : "-ERR bad login\r\n";
                    } else if (line.equals("UIDL")) {
                        reply = "+OK\r\n1 abc-17\r\n2 zz9\r\n.\r\n";
                    } else if (line.equals("RETR 2")) {
                        reply = "+OK\r\nSubject: hi\r\n\r\n..dot line\r\nlast\r\n.\r\n";
                    } else if (line.startsWith("RETR")) {
                        reply = "-ERR no such message\r\n";
                    } else if (line.startsWith("DELE")) {
                        reply = "+OK\r\n";
                    } else if (line.equals("QUIT")) {
                        o.write("+OK bye\r\n".getBytes());
                        return;
                    } else {
                        reply = "-ERR\r\n";
                    }
                    o.write(reply.getBytes(StandardCharsets.ISO_8859_1));
                }
            } catch (Exception ignored) {
                // test server ends with the connection
            }
        });
        t.start();
        return t;
    }

    // S1-48: UIDL values verbatim; RETR by number; dot-unstuffing; payload without the final "." line.
    @Test
    void uidsRetrDeleQuit() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            List<String> rec = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread t = server(ss, rec, true);
            try (Pop3Client c = new Pop3Client()) {
                c.Connect("127.0.0.1", ss.getLocalPort(), false);
                c.Authenticate("u", "p");
                assertEquals(List.of("abc-17", "zz9"), c.GetMessageUids());
                Message m = c.GetMessage(2);
                assertEquals("Subject: hi\r\n\r\n.dot line\r\nlast\r\n", new String(m.RawMessage(), StandardCharsets.ISO_8859_1));
                assertEquals("hi", m.Headers().Subject());
                assertThrows(PopServerException.class, () -> c.GetMessage(17));
                assertThrows(InvalidUseException.class, () -> c.GetMessage(0));
                c.DeleteMessage(2);
                c.Disconnect();
            }
            t.join(5000);
            assertEquals(List.of("USER u", "PASS p", "UIDL", "RETR 2", "RETR 17", "DELE 2", "QUIT"), rec);
        }
    }

    // Rejected credentials -> InvalidLoginException; unreachable server -> PopServerNotFoundException.
    @Test
    void failures() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            Thread t = server(ss, new ArrayList<>(), false);
            Pop3Client c = new Pop3Client();
            c.Connect("127.0.0.1", ss.getLocalPort(), false);
            assertThrows(InvalidLoginException.class, () -> c.Authenticate("u", "bad"));
            c.close();
            t.join(5000);
        }
        assertThrows(PopServerNotFoundException.class, () -> new Pop3Client().Connect("127.0.0.1", 1, false));
    }
}
