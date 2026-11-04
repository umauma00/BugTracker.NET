package btnet.runtime.net;

import java.net.http.HttpResponse;

/**
 * .NET {@code HttpResponseMessage} helpers [J-C2]:
 * {@code resp.EnsureSuccessStatusCode()} → {@code HttpResponses.EnsureSuccessStatusCode(resp)}.
 */
public final class HttpResponses {
    private HttpResponses() {
    }

    /**
     * Status outside 200–299 → {@code HttpRequestException("Response status code
     * does not indicate success: <code> (<reason phrase>).")}; returns the response.
     * {@code java.net.http} does not expose the server's reason phrase, so the
     * standard phrase of the code is used (TODO(port): .NET prints the server's
     * {@code ReasonPhrase}; identical for every standard server).
     */
    public static <T> HttpResponse<T> EnsureSuccessStatusCode(HttpResponse<T> r) {
        int code = r.statusCode();
        if (code < 200 || code > 299) {
            throw new HttpRequestException("Response status code does not indicate success: "
                    + code + " (" + ReasonPhrase(code) + ").");
        }
        return r;
    }

    /** .NET {@code HttpStatusDescription.Get(code)} (empty when unknown). */
    public static String ReasonPhrase(int code) {
        return switch (code) {
            case 100 -> "Continue";
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 203 -> "Non-Authoritative Information";
            case 204 -> "No Content";
            case 205 -> "Reset Content";
            case 206 -> "Partial Content";
            case 300 -> "Multiple Choices";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 305 -> "Use Proxy";
            case 307 -> "Temporary Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 402 -> "Payment Required";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 406 -> "Not Acceptable";
            case 407 -> "Proxy Authentication Required";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 411 -> "Length Required";
            case 412 -> "Precondition Failed";
            case 413 -> "Request Entity Too Large";
            case 414 -> "Request-Uri Too Long";
            case 415 -> "Unsupported Media Type";
            case 416 -> "Requested Range Not Satisfiable";
            case 417 -> "Expectation Failed";
            case 422 -> "Unprocessable Entity";
            case 423 -> "Locked";
            case 424 -> "Failed Dependency";
            case 426 -> "Upgrade Required";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            case 505 -> "Http Version Not Supported";
            case 507 -> "Insufficient Storage";
            default -> "";
        };
    }
}
