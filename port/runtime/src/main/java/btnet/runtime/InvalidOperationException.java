package btnet.runtime;

/** Stands for .NET {@code System.InvalidOperationException}. Unchecked (RULEBOOK §2A Exceptions). */
public class InvalidOperationException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public InvalidOperationException() {
        super("Operation is not valid due to the current state of the object.");
    }

    public InvalidOperationException(String message) {
        super(message);
    }

    public InvalidOperationException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
