package btnet.runtime.security;

import btnet.runtime.ArgumentNullException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Arrays;

/**
 * Stands for {@code Microsoft.Owin.Security.IAuthenticationManager}
 * ({@code owinContext.Authentication}) over the {@code HttpSession} security
 * context (B7).
 *
 * <p>As in Katana, signing in or out affects the <b>next</b> request: the
 * principal of the current request ({@code Page.User}, {@link ClaimsPrincipal#Current()})
 * is not changed. {@code SignIn} never changes the session id (the original keeps
 * {@code Session} across login); {@code SignOut} clears only the security
 * context, never the session's other state.
 * TODO(port): persistent auth cookie (OWIN 14-day sliding cookie becomes session-bound).
 */
public class AuthenticationManager {
    /** The session attribute Spring Security's {@code HttpSessionSecurityContextRepository} reads. */
    public static final String SESSION_KEY = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public AuthenticationManager(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    /** {@code Authentication.SignIn(params ClaimsIdentity[])}: stores the identities as the session's principal. */
    public void SignIn(ClaimsIdentity... identities) {
        if (identities == null) {
            throw new ArgumentNullException("identities");
        }
        ClaimsPrincipal principal = new ClaimsPrincipal(Arrays.asList(identities));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new SecurityConfig.ClaimsAuthenticationToken(principal));
        request.getSession(true).setAttribute(SESSION_KEY, ctx);
    }

    /** {@code Authentication.SignOut(params string[])}: removes the stored principal (any type list). */
    public void SignOut(String... authenticationTypes) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.removeAttribute(SESSION_KEY);
        }
    }

    /** {@code Authentication.User}: the current request's principal. */
    public ClaimsPrincipal User() {
        return ClaimsPrincipal.Current();
    }

    HttpServletResponse response() {
        return response;
    }
}
