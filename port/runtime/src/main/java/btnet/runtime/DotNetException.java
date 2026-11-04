package btnet.runtime;

/**
 * Stands for .NET {@code System.Exception} when it is thrown or constructed (RULEBOOK §2A C11).
 * Unchecked. Base class of the runtime's .NET exception types that have no JDK parent.
 */
public class DotNetException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** .NET {@code new Exception()}: message "Exception of type 'System.Exception' was thrown.". */
    public DotNetException() {
        super("Exception of type 'System.Exception' was thrown.");
    }

    public DotNetException(String message) {
        super(message);
    }

    public DotNetException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
