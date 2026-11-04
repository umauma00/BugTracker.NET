package btnet.runtime.web;

/**
 * Opaque stand-in for {@code System.Web.Routing.RequestContext}
 * ({@code Request.RequestContext}, RULEBOOK §3 [N2]); only passed to
 * {@link UrlHelper}.
 */
public final class RequestContext {
    private final HttpRequest request;

    RequestContext(HttpRequest request) {
        this.request = request;
    }

    /** The request this context belongs to. */
    public HttpRequest Request() {
        return request;
    }
}
