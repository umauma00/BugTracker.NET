package btnet.runtime;

/** Stands for .NET {@code System.Configuration.ConfigurationErrorsException}. Unchecked (RULEBOOK §2A Exceptions). */
public class ConfigurationErrorsException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public ConfigurationErrorsException() {
        super("An error occurred during the processing of a configuration file.");
    }

    public ConfigurationErrorsException(String message) {
        super(message);
    }

    public ConfigurationErrorsException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
