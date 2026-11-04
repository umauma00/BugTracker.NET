package btnet.runtime.mail.pop;

/**
 * Stands for OpenPop 2.0.x {@code PopClientException}: base of every OpenPop exception (OpenPop.Pop3.Exceptions.PopClientException).
 */
public class PopClientException extends btnet.runtime.DotNetException {
    private static final long serialVersionUID = 1L;

    public PopClientException(String message) {
        super(message);
    }

    public PopClientException(String message, Throwable inner) {
        super(message, inner);
    }
}
