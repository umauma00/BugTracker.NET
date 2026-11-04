package btnet.runtime.search;

import btnet.runtime.DotNetException;
import btnet.runtime.net.Uri;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticClientTest {

    private static final String RESPONSE = "{\"took\":1,\"hits\":{\"total\":2,\"max_score\":1.5,\"hits\":["
            + "{\"_index\":\"btnet\",\"_type\":\"bug\",\"_id\":\"7\",\"_score\":1.5,"
            + "\"_source\":{\"bg_id\":7,\"desc\":\"crash\",\"bg_reported_date\":\"2015-01-13T22:05:16.973\",\"status\":\"new\"},"
            + "\"highlight\":{\"desc\":[\"<span class='highlighted'>crash</span>\"],\"tags\":[\"a\",\"b\"]}},"
            + "{\"_id\":\"8\",\"_score\":null,\"_source\":{\"bg_id\":8}}]}}";

    // C29/N1: hits parsed from hits.hits[]; Highlights ordered by JSON order, empty (never null) without highlight.
    @Test
    void searchParsesHits() throws Exception {
        List<String> seen = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            seen.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " "
                    + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = (ex.getRequestURI().getPath().endsWith("_search") ? RESPONSE : "{\"created\":true}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            ElasticClient c = new ElasticClient(new Uri("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "btnet");
            SearchResponse r = c.Search("btnet", "{\"size\":500}");
            assertEquals("POST /btnet/bug/_search {\"size\":500}", seen.get(0));
            assertEquals(2, r.Hits.size());
            Hit h = r.Hits.get(0);
            assertEquals("7", h.Id);
            assertEquals(1.5, h.Score);
            assertEquals(7, h.Source.get("bg_id"));
            assertEquals("crash", h.Source.get("desc"));
            assertEquals(List.of("desc", "tags"), new ArrayList<>(h.Highlights.keySet()));
            Highlight first = h.Highlights.entrySet().iterator().next().getValue();
            assertEquals("desc", first.Field);
            assertEquals("7", first.DocumentId);
            assertEquals("<span class='highlighted'>crash</span>", first.Highlights.get(0));
            assertTrue(r.Hits.get(1).Highlights.isEmpty());
            assertEquals(0.0, r.Hits.get(1).Score);

            assertTrue(c.Index("{\"bg_id\":7}", "bug", 7L));
            assertEquals("PUT /btnet/bug/7 {\"bg_id\":7}", seen.get(1));
        } finally {
            server.stop(0);
        }
    }

    // S1-13 (Elasticsearch.Net 1.0.2 Transport.cs): a non-2xx answer does not throw — invalid response, no hits.
    @Test
    void non2xxIsAnInvalidResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] b = "{\"error\":\"IndexMissingException[[btnet] missing]\",\"status\":404}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            ElasticClient c = new ElasticClient("http://127.0.0.1:" + server.getAddress().getPort(), "btnet");
            SearchResponse r = c.Search("btnet", "{}");
            assertFalse(r.IsValid);
            assertTrue(r.Hits.isEmpty());
            assertFalse(c.Index("{}", "bug", 1L));
        } finally {
            server.stop(0);
        }
    }

    // S1-13: a connection failure rethrows (single-node pool) -> DotNetException.
    @Test
    void connectionFailureThrows() {
        ElasticClient c = new ElasticClient("http://127.0.0.1:1", "btnet");
        DotNetException e = assertThrows(DotNetException.class, () -> c.Search("btnet", "{}"));
        assertEquals("Unable to connect to the remote server", e.getMessage());
        assertThrows(DotNetException.class, () -> c.Index("{}", "bug", 1L));
    }
}
