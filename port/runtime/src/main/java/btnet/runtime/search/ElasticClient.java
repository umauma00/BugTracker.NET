package btnet.runtime.search;

import btnet.runtime.ArgumentNullException;
import btnet.runtime.DotNetException;
import btnet.runtime.Exceptions;
import btnet.runtime.net.HttpClients;
import btnet.runtime.net.Uri;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stands for NEST 1.0.2 {@code Nest.ElasticClient} over
 * {@code new ConnectionSettings(node, defaultIndex)} — a thin HTTP wrapper
 * (no ES client library, §1) [C29, J-C10, N1, S1-13].
 *
 * <p><b>Failure semantics [S1-13], decided from the Elasticsearch.Net 1.0.2 source
 * ({@code Connection/Transport.cs}):</b> {@code new ConnectionSettings(Uri)} uses a
 * {@code SingleNodeConnectionPool} (max retries 0, {@code _throwMaxRetry = false}),
 * and {@code ThrowOnElasticsearchServerExceptions} defaults to false. So
 * (a) a connection-level failure (no HTTP status) rethrows the original
 * {@code WebException} ({@code ThrowOrGetErrorFromStreamResponse}: "status ≤ 0 or
 * none and OriginalException != null → throw") — here a {@code DotNetException}
 * with the .NET message ({@code "Unable to connect to the remote server"},
 * {@code "The operation has timed out"}, else the I/O message) and the cause
 * attached; (b) a non-2xx HTTP response does <b>not</b> throw: {@link #Search}
 * returns an invalid response with no hits, {@link #Index} returns {@code false}.
 * TODO(port): verify NEST 1.0.2 wire format.
 *
 * <p>Requests: {@code Content-Type: application/json}, UTF-8 body, 60 s timeout
 * (Elasticsearch.Net default), client built by {@link HttpClients#newBuilder()}.
 */
public class ElasticClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String baseUri;
    private final String defaultIndex;
    private final HttpClient http;

    /** {@code new ElasticClient(new ConnectionSettings(node, defaultIndex: x))}. */
    public ElasticClient(Uri node, String defaultIndex) {
        this(node == null ? null : node.AbsoluteUri(), defaultIndex);
    }

    public ElasticClient(URI node, String defaultIndex) {
        this(node == null ? null : node.toString(), defaultIndex);
    }

    public ElasticClient(String node, String defaultIndex) {
        if (node == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: uri");
        }
        String b = node;
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.baseUri = b;
        this.defaultIndex = defaultIndex;
        this.http = HttpClients.newBuilder().build();
    }

    /** The default index given at construction. */
    public String DefaultIndex() {
        return defaultIndex;
    }

    /**
     * {@code POST {uri}/{index}/bug/_search} with {@code jsonBody}; parses
     * {@code hits.hits[]._id/_score/_source/highlight} (see the class comment for
     * failures).
     */
    public SearchResponse Search(String index, String jsonBody) {
        String idx = index == null || index.isEmpty() ? defaultIndex : index;
        HttpResponse<String> r = exchange("POST", baseUri + "/" + idx + "/bug/_search", jsonBody);
        SearchResponse result = new SearchResponse();
        result.StatusCode = r.statusCode();
        if (r.statusCode() < 200 || r.statusCode() > 299) {
            result.IsValid = false;
            return result;
        }
        Map<String, Object> root;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = MAPPER.readValue(r.body(), LinkedHashMap.class);
            root = m;
        } catch (JacksonException e) {
            throw new DotNetException(e.getMessage(), e);
        }
        Object hitsObj = root == null ? null : root.get("hits");
        if (!(hitsObj instanceof Map<?, ?> hits)) {
            return result;
        }
        if (hits.get("total") instanceof Number n) {
            result.Total = n.longValue();
        }
        if (!(hits.get("hits") instanceof List<?> list)) {
            return result;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> h)) {
                continue;
            }
            Hit hit = new Hit();
            hit.Id = h.get("_id") == null ? null : String.valueOf(h.get("_id"));
            hit.Score = h.get("_score") instanceof Number s ? s.doubleValue() : 0.0;
            if (h.get("_source") instanceof Map<?, ?> src) {
                Map<String, Object> source = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : src.entrySet()) {
                    source.put(String.valueOf(e.getKey()), e.getValue());
                }
                hit.Source = source;
            }
            if (h.get("highlight") instanceof Map<?, ?> hl) {
                for (Map.Entry<?, ?> e : hl.entrySet()) {
                    Highlight x = new Highlight();
                    x.DocumentId = hit.Id;
                    x.Field = String.valueOf(e.getKey());
                    if (e.getValue() instanceof List<?> frags) {
                        for (Object f : frags) {
                            x.Highlights.add(f == null ? null : String.valueOf(f));
                        }
                    }
                    hit.Highlights.put(x.Field, x);
                }
            }
            result.Hits.add(hit);
        }
        return result;
    }

    /**
     * {@code _client.Index(json, i => i.Type(type).Id(id))} →
     * {@code PUT {uri}/{defaultIndex}/{type}/{id}}; returns whether the response was
     * 2xx (NEST {@code IsValid}). Connection failure → {@code DotNetException}
     * (caught by {@code IndexBug}). TODO(port): whether NEST 1.0.2 throws on a
     * non-2xx index response — decided "no" from Transport.cs (class comment).
     */
    public boolean Index(String jsonBody, String type, long id) {
        HttpResponse<String> r = exchange("PUT", baseUri + "/" + defaultIndex + "/" + type + "/" + id, jsonBody);
        return r.statusCode() >= 200 && r.statusCode() <= 299;
    }

    private HttpResponse<String> exchange(String method, String url, String body) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new DotNetException("Invalid URI: The format of the URI could not be determined.", e);
        }
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException | UnknownHostException e) {
            throw new DotNetException("Unable to connect to the remote server", e);
        } catch (HttpTimeoutException e) {
            throw new DotNetException("The operation has timed out", e);
        } catch (IOException e) {
            throw new DotNetException(e.getMessage() == null ? "Unable to read data from the transport connection." : e.getMessage(), e);
        } catch (InterruptedException e) {
            throw Exceptions.wrap(e);
        }
    }
}
