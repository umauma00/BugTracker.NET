package btnet.runtime;

/**
 * Stands for .NET {@code System.InvalidCastException}. Extends {@link ClassCastException} so that a
 * Java unboxing/reference cast failure and a runtime-raised InvalidCastException are caught alike
 * (RULEBOOK §2A Exceptions).
 */
public class InvalidCastException extends ClassCastException {
    private static final long serialVersionUID = 1L;

    public InvalidCastException() {
        super("Specified cast is not valid.");
    }

    public InvalidCastException(String message) {
        super(message);
    }

    public InvalidCastException(String message, Throwable innerException) {
        super(message);
        initCause(innerException);
    }
}
