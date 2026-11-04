package btnet.runtime.mail.pop;

/**
 * Stands for OpenPop 2.0.x {@code InvalidLoginException}: the server rejected USER/PASS (OpenPop.Pop3.Exceptions.InvalidLoginException).
 */
public class InvalidLoginException extends PopClientException {
    private static final long serialVersionUID = 1L;

    public InvalidLoginException(String message) {
        super(message);
    }

    public InvalidLoginException(String message, Throwable inner) {
        super(message, inner);
    }
}
