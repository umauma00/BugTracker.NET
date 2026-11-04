package btnet.runtime.mail.pop;

/**
 * Stands for OpenPop 2.0.x {@code PopServerNotFoundException}: the TCP connection to the server could not be made (OpenPop.Pop3.Exceptions.PopServerNotFoundException).
 */
public class PopServerNotFoundException extends PopClientException {
    private static final long serialVersionUID = 1L;

    public PopServerNotFoundException(String message) {
        super(message);
    }

    public PopServerNotFoundException(String message, Throwable inner) {
        super(message, inner);
    }
}
