package btnet.runtime;

/**
 * Stands for .NET {@code System.ArgumentOutOfRangeException} [C27]. .NET argument order:
 * {@code (paramName)} and {@code (paramName, message)}.
 */
public class ArgumentOutOfRangeException extends ArgumentException {
    private static final long serialVersionUID = 1L;

    public ArgumentOutOfRangeException() {
        super("Specified argument was out of the range of valid values.", (String) null);
    }

    public ArgumentOutOfRangeException(String paramName) {
        super("Specified argument was out of the range of valid values.", paramName);
    }

    public ArgumentOutOfRangeException(String paramName, String message) {
        super(message, paramName);
    }

    public ArgumentOutOfRangeException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
