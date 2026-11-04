package btnet.runtime.web;

/**
 * .NET {@code System.Web.HttpRequestValidationException}: thrown on the first access
 * of {@code Request.Form}, {@code QueryString} or {@code Cookies} when a value looks
 * dangerous and the page validates requests (RULEBOOK §2B B3 ValidateRequest row,
 * [J-U14], {@code requestValidationMode="2.0"}). Results in HTTP 500.
 */
public class HttpRequestValidationException extends HttpException {
    private static final long serialVersionUID = 1L;

    public HttpRequestValidationException(String message) {
        super(500, message);
    }
}
