package btnet.runtime.net;

import btnet.runtime.ArgumentException;
import btnet.runtime.InvalidOperationException;
import btnet.runtime.TaskCanceledException;
import btnet.runtime.UriFormatException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestsTest {

    // .NET HttpClient.PostAsync synchronous checks (D38: a null LoginUrl fails here, before any request).
    @Test
    void synchronousArgumentChecks() {
        String msg = "An invalid request URI was provided. The request URI must either be an absolute URI or BaseAddress must be set.";
        assertEquals(msg, assertThrows(InvalidOperationException.class, () -> HttpRequests.newBuilder(null)).getMessage());
        assertEquals(msg, assertThrows(InvalidOperationException.class, () -> HttpRequests.newBuilder("")).getMessage());
        assertEquals(msg, assertThrows(InvalidOperationException.class, () -> HttpRequests.newBuilder("insert_bug.aspx")).getMessage());
        assertEquals("Invalid URI: The format of the URI could not be determined.",
                assertThrows(UriFormatException.class, () -> HttpRequests.newBuilder("http://")).getMessage());
        assertThrows(ArgumentException.class, () -> HttpRequests.newBuilder("ftp://host/x"));
    }

    // .NET HttpClient.Timeout default = 100 s; characters .NET escapes are percent-encoded.
    @Test
    void builderPresets() {
        HttpRequest r = HttpRequests.newBuilder("http://localhost/insert bug.aspx").GET().build();
        assertEquals(Duration.ofSeconds(100), r.timeout().orElseThrow());
        assertEquals(URI.create("http://localhost/insert%20bug.aspx"), r.uri());
        assertEquals(HttpClient.Version.HTTP_1_1, HttpClients.newBuilder().build().version());
    }

    // Timeout -> TaskCanceledException("A task was canceled."); connection failure -> HttpRequestException.
    @Test
    void sendMapsFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/ok", ex -> {
            byte[] b = "hi".getBytes();
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpClient c = HttpClients.newBuilder().build();
            HttpResponse<String> ok = HttpRequests.send(c, HttpRequests.newBuilder("http://127.0.0.1:" + port + "/ok").GET().build());
            assertEquals("hi", ok.body());
            HttpRequest slow = HttpRequests.newBuilder("http://127.0.0.1:" + port + "/slow").timeout(Duration.ofMillis(300)).GET().build();
            assertEquals("A task was canceled.", assertThrows(TaskCanceledException.class, () -> HttpRequests.send(c, slow)).getMessage());
        } finally {
            server.stop(0);
        }
        HttpRequest refused = HttpRequests.newBuilder("http://127.0.0.1:1/").GET().build();
        HttpRequestException e = assertThrows(HttpRequestException.class, () -> HttpRequests.send(HttpClients.newBuilder().build(), refused));
        assertEquals("An error occurred while sending the request.", e.getMessage());
        assertTrue(e.getCause() != null);
    }

    // .NET EnsureSuccessStatusCode message text.
    @Test
    void ensureSuccessStatusCode() {
        HttpRequestException e = assertThrows(HttpRequestException.class,
                () -> HttpResponses.EnsureSuccessStatusCode(new FakeResponse(404)));
        assertEquals("Response status code does not indicate success: 404 (Not Found).", e.getMessage());
        HttpResponses.EnsureSuccessStatusCode(new FakeResponse(204));
    }

    // ServicePointManager.CertificatePolicy is process-wide and sticky.
    @Test
    void certificatePolicyIsSticky() {
        ICertificatePolicy p = (sp, cert, req, problem) -> true;
        ServicePointManager.CertificatePolicy(p);
        try {
            assertEquals(p, ServicePointManager.CertificatePolicy());
        } finally {
            ServicePointManager.CertificatePolicy(null);
        }
    }

    private record FakeResponse(int statusCode) implements HttpResponse<String> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public String body() { return ""; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("http://x/"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
