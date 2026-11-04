package btnet.runtime.web;

import btnet.runtime.DotNetException;

/**
 * .NET {@code System.Web.HttpException} (RULEBOOK §2A Exceptions). Unchecked.
 * {@link #GetHttpCode()} returns the HTTP status the exception stands for
 * (500 unless given).
 */
public class HttpException extends DotNetException {
    private static final long serialVersionUID = 1L;

    private final int httpCode;

    public HttpException(String message) {
        this(500, message);
    }

    public HttpException(String message, Throwable innerException) {
        super(message, innerException);
        this.httpCode = 500;
    }

    public HttpException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    /** .NET {@code HttpException.GetHttpCode()}. */
    public int GetHttpCode() {
        return httpCode;
    }
}
