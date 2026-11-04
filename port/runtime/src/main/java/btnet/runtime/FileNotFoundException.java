package btnet.runtime;

/** Stands for .NET {@code System.IO.FileNotFoundException}. Unchecked (RULEBOOK §2A Exceptions). */
public class FileNotFoundException extends IOException {
    private static final long serialVersionUID = 1L;

    public FileNotFoundException() {
        super("Unable to find the specified file.");
    }

    public FileNotFoundException(String message) {
        super(message);
    }

    public FileNotFoundException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
