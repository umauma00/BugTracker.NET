package btnet.runtime;

/** Stands for .NET {@code System.Security.SecurityException}. Unchecked (RULEBOOK §2A Exceptions). */
public class SecurityException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public SecurityException() {
        super("Security error.");
    }

    public SecurityException(String message) {
        super(message);
    }

    public SecurityException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
