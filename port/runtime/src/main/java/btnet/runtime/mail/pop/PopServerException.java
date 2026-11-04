package btnet.runtime.mail.pop;

/**
 * Stands for OpenPop 2.0.x {@code PopServerException}: a -ERR (or missing) server response (OpenPop.Pop3.Exceptions.PopServerException).
 */
public class PopServerException extends PopClientException {
    private static final long serialVersionUID = 1L;

    public PopServerException(String message) {
        super(message);
    }

    public PopServerException(String message, Throwable inner) {
        super(message, inner);
    }
}
