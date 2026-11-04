package btnet.runtime.mail.pop;

/**
 * Stands for OpenPop 2.0.x {@code PopServerNotAvailableException}: the server greeting was not +OK (OpenPop.Pop3.Exceptions.PopServerNotAvailableException).
 */
public class PopServerNotAvailableException extends PopClientException {
    private static final long serialVersionUID = 1L;

    public PopServerNotAvailableException(String message) {
        super(message);
    }

    public PopServerNotAvailableException(String message, Throwable inner) {
        super(message, inner);
    }
}
