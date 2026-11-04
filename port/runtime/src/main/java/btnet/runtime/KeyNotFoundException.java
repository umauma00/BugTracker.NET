package btnet.runtime;

/** Stands for .NET {@code System.Collections.Generic.KeyNotFoundException}. Unchecked (RULEBOOK §2A Exceptions). */
public class KeyNotFoundException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public KeyNotFoundException() {
        super("The given key was not present in the dictionary.");
    }

    public KeyNotFoundException(String message) {
        super(message);
    }

    public KeyNotFoundException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
