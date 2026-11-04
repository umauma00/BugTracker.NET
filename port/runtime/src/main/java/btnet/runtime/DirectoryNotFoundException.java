package btnet.runtime;

/** Stands for .NET {@code System.IO.DirectoryNotFoundException}. Unchecked (RULEBOOK §2A Exceptions). */
public class DirectoryNotFoundException extends IOException {
    private static final long serialVersionUID = 1L;

    public DirectoryNotFoundException() {
        super("Attempted to access a path that is not on the disk.");
    }

    public DirectoryNotFoundException(String message) {
        super(message);
    }

    public DirectoryNotFoundException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
