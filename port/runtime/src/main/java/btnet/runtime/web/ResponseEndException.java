package btnet.runtime.web;

/**
 * Stands for .NET {@code System.Threading.ThreadAbortException} as raised by
 * {@code HttpResponse.End()} (RULEBOOK §2A Exceptions, §3 [C7]).
 *
 * <p>Extends {@link Error} so that a translated {@code catch (RuntimeException e)}
 * (C# {@code catch (Exception e)}) never swallows page control flow. Subclasses
 * {@link RedirectException} ({@code Response.Redirect}) and {@link TransferException}
 * ({@code Server.Transfer}). The message is the .NET abort text
 * {@code "Thread was being aborted."}, which C7 catch bodies print.
 */
public class ResponseEndException extends Error {
    private static final long serialVersionUID = 1L;

    /** .NET {@code ThreadAbortException.Message}. */
    public static final String MESSAGE = "Thread was being aborted.";

    public ResponseEndException() {
        super(MESSAGE, null, false, false);
    }
}
