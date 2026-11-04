package btnet.runtime.web;

/**
 * .NET {@code System.Web.Http.HttpResponseException}: aborts a Web API action with a
 * fixed status code and an empty body. Raised by {@link FromUri#bind} for a missing
 * simple parameter (Web API selects no action → 404) and by {@link ApiBody#bind} for an
 * unsupported media type (415); handled by {@link ApiController}.
 */
public class HttpResponseException extends HttpException {
    private static final long serialVersionUID = 1L;

    public HttpResponseException(int statusCode) {
        super(statusCode, "Processing of the HTTP request resulted in an exception. Please see the HTTP response returned by the 'Response' property of this exception for details.");
    }

    public HttpResponseException(HttpStatusCode statusCode) {
        this(statusCode.value);
    }
}
