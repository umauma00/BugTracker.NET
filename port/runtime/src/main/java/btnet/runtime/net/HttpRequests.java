package btnet.runtime.net;

import btnet.runtime.ArgumentException;
import btnet.runtime.Exceptions;
import btnet.runtime.InvalidOperationException;
import btnet.runtime.TaskCanceledException;
import btnet.runtime.UriFormatException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stands for the request side of .NET 4.5 {@code HttpClient.PostAsync/GetAsync}
 * [S1-3, S1-5]: {@link #newBuilder(String)} performs .NET's <b>synchronous</b>
 * argument checks and presets the 100 s {@code HttpClient.Timeout};
 * {@link #send(HttpClient, HttpRequest)} performs the exchange with .NET's
 * exception mapping.
 */
public final class HttpRequests {
    /** .NET {@code HttpClient.Timeout} default. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(100);

    private static final String INVALID_REQUEST_URI =
            "An invalid request URI was provided. The request URI must either be an absolute URI or BaseAddress must be set.";

    private HttpRequests() {
    }

    /**
     * {@code null} or empty → {@code InvalidOperationException}; not parseable as
     * a .NET {@code Uri(s, UriKind.RelativeOrAbsolute)} →
     * {@code UriFormatException("Invalid URI: The format of the URI could not be determined.")};
     * relative → the same {@code InvalidOperationException}; absolute but not
     * http/https → {@code ArgumentException("Only 'http' and 'https' schemes are allowed.", "requestUri")};
     * else a builder for the URI (characters .NET escapes percent-encoded as UTF-8)
     * with {@code timeout(100 s)}.
     */
    public static HttpRequest.Builder newBuilder(String uri) {
        if (uri == null || uri.isEmpty()) {
            throw new InvalidOperationException(INVALID_REQUEST_URI);
        }
        if (!Uri.looksAbsolute(uri)) {
            // .NET accepts almost any string as a relative URI
            throw new InvalidOperationException(INVALID_REQUEST_URI);
        }
        Uri u;
        try {
            u = new Uri(uri);
        } catch (UriFormatException e) {
            throw new UriFormatException("Invalid URI: The format of the URI could not be determined.");
        }
        if (!u.Scheme().equals("http") && !u.Scheme().equals("https")) {
            throw new ArgumentException("Only 'http' and 'https' schemes are allowed.", "requestUri");
        }
        URI java;
        try {
            java = u.toJavaUri();
        } catch (IllegalArgumentException e) {
            throw new UriFormatException("Invalid URI: The format of the URI could not be determined.");
        }
        return HttpRequest.newBuilder(java).timeout(DEFAULT_TIMEOUT);
    }

    /**
     * Sends {@code r} with {@code BodyHandlers.ofString()}. The deadline (the
     * request's timeout, default 100 s) covers the whole exchange including the
     * body (.NET {@code HttpCompletionOption.ResponseContentRead}). Timeout →
     * {@code TaskCanceledException("A task was canceled.")}; any other
     * {@code IOException} → {@code HttpRequestException("An error occurred while
     * sending the request.", cause)}; interrupt → {@code Exceptions.wrap} (S1-22).
     */
    public static HttpResponse<String> send(HttpClient c, HttpRequest r) {
        Duration timeout = r.timeout().orElse(DEFAULT_TIMEOUT);
        CompletableFuture<HttpResponse<String>> f = c.sendAsync(r, HttpResponse.BodyHandlers.ofString());
        try {
            return f.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new TaskCanceledException("A task was canceled.");
        } catch (InterruptedException e) {
            f.cancel(true);
            throw Exceptions.wrap(e);
        } catch (CancellationException e) {
            throw new TaskCanceledException("A task was canceled.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof HttpTimeoutException) {
                throw new TaskCanceledException("A task was canceled.");
            }
            if (cause instanceof IOException) {
                throw new HttpRequestException("An error occurred while sending the request.", cause);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new HttpRequestException("An error occurred while sending the request.", cause);
        }
    }
}
