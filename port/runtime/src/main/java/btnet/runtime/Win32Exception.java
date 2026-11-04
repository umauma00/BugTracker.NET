package btnet.runtime;

/**
 * Stands for .NET {@code System.ComponentModel.Win32Exception} [S2-4]: a process start failure
 * ({@link Processes#start(ProcessBuilder)}). {@link #NativeErrorCode()} is the Win32 error code.
 */
public class Win32Exception extends DotNetException {
    private static final long serialVersionUID = 1L;

    private final int nativeErrorCode;

    public Win32Exception(int nativeErrorCode, String message) {
        super(message);
        this.nativeErrorCode = nativeErrorCode;
    }

    public Win32Exception(int nativeErrorCode, String message, Throwable innerException) {
        super(message, innerException);
        this.nativeErrorCode = nativeErrorCode;
    }

    /** .NET {@code NativeErrorCode}. */
    public int NativeErrorCode() {
        return nativeErrorCode;
    }
}
