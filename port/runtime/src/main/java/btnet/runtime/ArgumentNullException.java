package btnet.runtime;

/**
 * Stands for .NET {@code System.ArgumentNullException}. Note the .NET argument order:
 * {@code (paramName)} and {@code (paramName, message)}; {@code new ArgumentNullException("key")} has the
 * message {@code "Value cannot be null.\r\nParameter name: key"}.
 */
public class ArgumentNullException extends ArgumentException {
    private static final long serialVersionUID = 1L;

    public ArgumentNullException() {
        super("Value cannot be null.", (String) null);
    }

    public ArgumentNullException(String paramName) {
        super("Value cannot be null.", paramName);
    }

    public ArgumentNullException(String paramName, String message) {
        super(message, paramName);
    }

    public ArgumentNullException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
