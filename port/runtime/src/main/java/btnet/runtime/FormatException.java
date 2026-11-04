package btnet.runtime;

/** Stands for .NET {@code System.FormatException}. Unchecked (RULEBOOK §2A Exceptions). */
public class FormatException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public FormatException() {
        super("One of the identified items was in an invalid format.");
    }

    public FormatException(String message) {
        super(message);
    }

    public FormatException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
