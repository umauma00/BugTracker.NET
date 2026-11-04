package btnet.runtime.web;

import btnet.runtime.security.IIdentity;
import btnet.runtime.security.IPrincipal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ASP.NET Web API {@code System.Web.Http.ApiController} (RULEBOOK §2A "JSON / Web API").
 * Translated controllers are {@code @RestController}s extending it, with
 * {@code @RequestMapping("/api/<Name>")} and one {@code @GetMapping}/{@code @PostMapping}
 * per action returning {@link IHttpActionResult}; parameters are bound with
 * {@link FromUri} / {@link ApiBody} from {@code Request}.
 *
 * <p>The fields {@code User}, {@code Request}, {@code ModelState} are request proxies
 * (the controller is a singleton): each call resolves the current request's object.
 *
 * <p>Results: {@link #Ok()} 200 empty; {@link #Ok(Object)} 200 JSON; {@link #NotFound()}
 * 404 empty; {@link #StatusCode(HttpStatusCode)} empty; {@link #BadRequest(ModelStateDictionary)}
 * 400 {@code {"Message":"The request is invalid.","ModelState":{…}}}. An
 * {@link HttpResponseException} → its status, empty; any other exception → 500
 * {@code {"Message":"An error has occurred."}} (Web API, remote client).
 */
public abstract class ApiController {
    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    /** {@code User}: the current request's principal. */
    public final IPrincipal User = new IPrincipal() {
        @Override
        public IIdentity Identity() {
            return principal().Identity();
        }

        @Override
        public boolean IsInRole(String role) {
            return principal().IsInRole(role);
        }
    };

    /** {@code Request}: the current request. */
    public final HttpRequest Request = HttpRequest.current();

    /** {@code ModelState}: the current request's model state. */
    public final ModelStateDictionary ModelState = ModelStateDictionary.current();

    private static IPrincipal principal() {
        HttpContext c = HttpContext.Current();
        if (c == null) throw new HttpException("User is not available in this context.");
        return c.User();
    }

    protected IHttpActionResult Ok() {
        return IHttpActionResult.empty(200);
    }

    protected IHttpActionResult Ok(Object content) {
        return IHttpActionResult.json(200, content);
    }

    protected IHttpActionResult NotFound() {
        return IHttpActionResult.empty(404);
    }

    protected IHttpActionResult StatusCode(HttpStatusCode status) {
        return IHttpActionResult.empty(status.value);
    }

    protected IHttpActionResult BadRequest() {
        return IHttpActionResult.empty(400);
    }

    protected IHttpActionResult BadRequest(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Message", message);
        return IHttpActionResult.json(400, m);
    }

    protected IHttpActionResult BadRequest(ModelStateDictionary modelState) {
        return IHttpActionResult.json(400, modelState.toErrorBody());
    }

    protected IHttpActionResult InternalServerError() {
        return IHttpActionResult.empty(500);
    }

    protected IHttpActionResult Unauthorized() {
        return IHttpActionResult.empty(401);
    }

    @ExceptionHandler(HttpResponseException.class)
    public IHttpActionResult handleHttpResponseException(HttpResponseException e) {
        return IHttpActionResult.empty(e.GetHttpCode());
    }

    @ExceptionHandler(RuntimeException.class)
    public IHttpActionResult handleUnhandled(RuntimeException e) {
        log.error("Unhandled exception in Web API action", e);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Message", "An error has occurred.");
        return IHttpActionResult.json(500, m);
    }
}
