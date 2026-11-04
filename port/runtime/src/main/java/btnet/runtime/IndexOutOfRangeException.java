package btnet.runtime;

/** Stands for .NET {@code System.IndexOutOfRangeException}. Unchecked (RULEBOOK §2A Exceptions). */
public class IndexOutOfRangeException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public IndexOutOfRangeException() {
        super("Index was outside the bounds of the array.");
    }

    public IndexOutOfRangeException(String message) {
        super(message);
    }

    public IndexOutOfRangeException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
