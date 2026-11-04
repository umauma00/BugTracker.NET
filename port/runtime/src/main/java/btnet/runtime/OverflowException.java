package btnet.runtime;

/** Stands for .NET {@code System.OverflowException}. Unchecked (RULEBOOK §2A Exceptions). */
public class OverflowException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public OverflowException() {
        super("Arithmetic operation resulted in an overflow.");
    }

    public OverflowException(String message) {
        super(message);
    }

    public OverflowException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
