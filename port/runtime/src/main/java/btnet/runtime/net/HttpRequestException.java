package btnet.runtime.net;

import btnet.runtime.DotNetException;

/**
 * Stands for .NET {@code System.Net.Http.HttpRequestException} [J-C2]
 * (name used by {@code Exceptions.dotNetToString}).
 */
public class HttpRequestException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public HttpRequestException() {
        super("An error occurred while sending the request.");
    }

    public HttpRequestException(String message) {
        super(message);
    }

    public HttpRequestException(String message, Throwable inner) {
        super(message, inner);
    }
}
