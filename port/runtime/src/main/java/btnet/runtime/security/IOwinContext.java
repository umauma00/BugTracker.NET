package btnet.runtime.security;

import btnet.runtime.InvalidOperationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Stands for {@code Microsoft.Owin.IOwinContext} (B7):
 * {@code Request.GetOwinContext()} returns one;
 * {@code owinContext.Authentication.SignIn(identity)} →
 * {@code owinContext.Authentication().SignIn(identity)}.
 */
public interface IOwinContext {
    /** {@code IOwinContext.Authentication}. */
    AuthenticationManager Authentication();

    /** The OWIN context of a servlet exchange (used by runtime {@code HttpRequest.GetOwinContext()}). */
    static IOwinContext of(HttpServletRequest request, HttpServletResponse response) {
        AuthenticationManager m = new AuthenticationManager(request, response);
        return () -> m;
    }

    /**
     * The OWIN context of the current request thread (Spring
     * {@code RequestContextHolder}); none → {@code InvalidOperationException}
     * with the Katana text for a missing {@code owin.Environment}.
     */
    static IOwinContext Current() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra instanceof ServletRequestAttributes sra) {
            return of(sra.getRequest(), sra.getResponse());
        }
        throw new InvalidOperationException("No owin.Environment item was found in the context.");
    }
}
