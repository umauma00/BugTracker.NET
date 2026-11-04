package btnet.runtime.mail.pop;

/**
 * Stands for OpenPop 2.0.x {@code InvalidUseException}: a Pop3Client member was used in the wrong connection state (OpenPop.Pop3.Exceptions.InvalidUseException).
 */
public class InvalidUseException extends PopClientException {
    private static final long serialVersionUID = 1L;

    public InvalidUseException(String message) {
        super(message);
    }

    public InvalidUseException(String message, Throwable inner) {
        super(message, inner);
    }
}
