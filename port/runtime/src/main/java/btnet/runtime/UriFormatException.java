package btnet.runtime;

/** Stands for .NET {@code System.UriFormatException}. Unchecked (RULEBOOK §2A Exceptions). */
public class UriFormatException extends FormatException {
    private static final long serialVersionUID = 1L;

    public UriFormatException() {
        super("Invalid URI: The format of the URI could not be determined.");
    }

    public UriFormatException(String message) {
        super(message);
    }

    public UriFormatException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
