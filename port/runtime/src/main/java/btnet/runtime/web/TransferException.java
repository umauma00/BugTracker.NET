package btnet.runtime.web;

/**
 * The ThreadAbortException raised by .NET {@code HttpServerUtility.Transfer(path)}
 * after the target page has executed (RULEBOOK §2A Exceptions, §2B B3). The
 * target's output is already in the shared Response buffer; the runtime
 * {@link Page} sends that buffer when it catches this exception.
 */
public class TransferException extends ResponseEndException {
    private static final long serialVersionUID = 1L;

    private final String path;

    public TransferException(String path) {
        this.path = path;
    }

    /** The application path that was transferred to (e.g. {@code /users.aspx}). */
    public String Path() {
        return path;
    }
}
