package btnet.runtime;

/**
 * Stands for .NET {@code System.ArgumentException} (RULEBOOK §3 J-C19). The two-string constructor is
 * {@code (message, paramName)}; {@link #getMessage()} is {@code message + "\r\nParameter name: " + paramName}
 * as in .NET 4.5 (paramName null or empty → message alone).
 */
public class ArgumentException extends DotNetException {
    private static final long serialVersionUID = 1L;

    private final String paramName;

    public ArgumentException() {
        this("Value does not fall within the expected range.", (String) null);
    }

    public ArgumentException(String message) {
        this(message, (String) null);
    }

    public ArgumentException(String message, Throwable innerException) {
        super(message, innerException);
        this.paramName = null;
    }

    public ArgumentException(String message, String paramName) {
        super(message);
        this.paramName = paramName;
    }

    public ArgumentException(String message, String paramName, Throwable innerException) {
        super(message, innerException);
        this.paramName = paramName;
    }

    /** .NET {@code ParamName}. */
    public String ParamName() {
        return paramName;
    }

    @Override
    public String getMessage() {
        String m = super.getMessage();
        if (paramName == null || paramName.isEmpty()) {
            return m;
        }
        return (m == null ? "" : m) + "\r\nParameter name: " + paramName;
    }
}
