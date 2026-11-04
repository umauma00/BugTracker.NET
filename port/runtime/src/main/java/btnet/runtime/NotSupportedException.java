package btnet.runtime;

/** Stands for .NET {@code System.NotSupportedException} [S2-19]. Unchecked. */
public class NotSupportedException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public NotSupportedException() {
        super("Specified method is not supported.");
    }

    public NotSupportedException(String message) {
        super(message);
    }

    public NotSupportedException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
