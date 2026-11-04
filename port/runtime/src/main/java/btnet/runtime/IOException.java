package btnet.runtime;

/** Stands for .NET {@code System.IO.IOException}. Unchecked (RULEBOOK §2A Exceptions). */
public class IOException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public IOException() {
        super("I/O error occurred.");
    }

    public IOException(String message) {
        super(message);
    }

    public IOException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
