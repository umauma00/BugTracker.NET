package btnet.runtime.web;

/**
 * The ThreadAbortException raised by .NET {@code HttpResponse.Redirect(url)} /
 * {@code Redirect(url, true)} (RULEBOOK §2A Exceptions). The runtime
 * {@link Page} sends the buffered 302 response when it catches it (B2 step 8).
 */
public class RedirectException extends ResponseEndException {
    private static final long serialVersionUID = 1L;

    private final String location;

    public RedirectException(String location) {
        this.location = location;
    }

    /** The {@code Location} the redirect was sent to. */
    public String Location() {
        return location;
    }
}
