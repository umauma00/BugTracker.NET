package judge.core;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * One browser-like session: cookie jar, manual redirect following (so the
 * redirect chain itself can be asserted), relative paths resolved against
 * {@link JudgeConfig#baseUrl()}.
 *
 * <p>Paths are the original's public URLs (e.g. {@code "bugs.aspx"}). The port
 * must answer the same paths — see judge/README.md, "Judge contract".
 */
public final class Web {

    /** A finished exchange: the final response plus every redirect hop before it. */
    public record Page(int status, URI uri, Map<String, List<String>> headers, byte[] body, List<URI> redirects) {
        public String text() { return new String(body, StandardCharsets.UTF_8); }
        public Document html() { return Jsoup.parse(text(), uri.toString()); }
        public Optional<String> header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst();
        }
        /** Path of the final URI relative to the base, lower-cased, without query. */
        public String path() { return Norm.relPath(uri); }
    }

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public URI resolve(String path) { return JudgeConfig.baseUrl().resolve(path); }

    public Page get(String path) { return send(HttpRequest.newBuilder(resolve(path)).GET(), true); }

    /** GET an absolute URI (e.g. a GET form's action plus query string), following redirects. */
    public Page get(URI uri) { return send(HttpRequest.newBuilder(uri).GET(), true); }

    /** GET without following redirects — for asserting the redirect itself. */
    public Page getNoFollow(String path) { return send(HttpRequest.newBuilder(resolve(path)).GET(), false); }

    public Page postForm(URI action, List<Map.Entry<String, String>> fields) {
        StringJoiner sj = new StringJoiner("&");
        for (var f : fields) {
            sj.add(URLEncoder.encode(f.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(f.getValue() == null ? "" : f.getValue(), StandardCharsets.UTF_8));
        }
        return send(HttpRequest.newBuilder(action)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(sj.toString())), true);
    }

    public Page postJson(String path, String json) {
        return send(HttpRequest.newBuilder(resolve(path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), true);
    }

    public Page getJson(String path) {
        return send(HttpRequest.newBuilder(resolve(path)).header("Accept", "application/json").GET(), true);
    }

    /** multipart/form-data POST (file uploads). {@code files}: field -> (filename, bytes); part type application/octet-stream. */
    public Page postMultipart(URI action, List<Map.Entry<String, String>> fields,
                              Map<String, Map.Entry<String, byte[]>> files) {
        Map<String, String> types = new java.util.HashMap<>();
        for (String k : files.keySet()) types.put(k, "application/octet-stream");
        return postMultipart(action, fields, files, types);
    }

    /** As above, with an explicit part Content-Type per file field (default application/octet-stream). */
    public Page postMultipart(URI action, List<Map.Entry<String, String>> fields,
                              Map<String, Map.Entry<String, byte[]>> files, Map<String, String> contentTypes) {
        String boundary = "----judge" + UUID.randomUUID();
        List<byte[]> parts = new ArrayList<>();
        for (var f : fields) {
            parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey()
                    + "\"\r\n\r\n" + (f.getValue() == null ? "" : f.getValue()) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (var e : files.entrySet()) {
            parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey()
                    + "\"; filename=\"" + e.getValue().getKey()
                    + "\"\r\nContent-Type: " + contentTypes.getOrDefault(e.getKey(), "application/octet-stream")
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(e.getValue().getValue());
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(action)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(parts)), true);
    }

    private Page send(HttpRequest.Builder b, boolean follow) {
        List<URI> hops = new ArrayList<>();
        HttpRequest req = b.timeout(Duration.ofSeconds(60)).build();
        try {
            for (int i = 0; i < 10; i++) {
                HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int s = r.statusCode();
                Optional<String> loc = r.headers().firstValue("Location");
                if (follow && (s == 301 || s == 302 || s == 303 || s == 307 || s == 308) && loc.isPresent()) {
                    URI next = req.uri().resolve(loc.get());
                    hops.add(next);
                    // 307/308 would re-POST; the original only issues 302 (Response.Redirect).
                    req = HttpRequest.newBuilder(next).GET().timeout(Duration.ofSeconds(60)).build();
                    continue;
                }
                return new Page(s, req.uri(), r.headers().map(), r.body(), List.copyOf(hops));
            }
            throw new AssertionError("Too many redirects: " + hops);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP failure on " + req.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
