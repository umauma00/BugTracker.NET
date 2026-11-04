package btnet.runtime.web;

import btnet.runtime.security.ClaimsIdentity;
import btnet.runtime.security.ClaimsPrincipal;
import btnet.runtime.security.IPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * .NET {@code System.Web.HttpContext} (RULEBOOK §2A {@code HttpContext.Current}, §3 [C34, J-U13]).
 *
 * <p>{@link #Current()} is a request-bound {@link ThreadLocal} (not inherited), set by
 * {@link RequestContextFilter}; {@code null} on background threads, as in .NET. During
 * {@code Application_OnStart} it is bound to the startup pseudo-context, whose
 * {@link #Request()} and {@link #Response()} throw {@link HttpException} (caught at
 * {@code util.cs:54-61}) while {@link #Server()}, {@link #Application()} and
 * {@link #Cache()} work.
 *
 * <p>{@link #User()}: the principal installed by {@code btnet.runtime.security}
 * ({@link #PrincipalResolver} if set; else a Spring Security {@link Authentication} whose
 * principal or details is an {@link IPrincipal} / {@link ClaimsIdentity}); anonymous →
 * {@code new ClaimsPrincipal(new ClaimsIdentity())} ({@code IsAuthenticated()} false).
 * Resolved once per request on first use, as ASP.NET fixes {@code User} for the request.
 */
public final class HttpContext {
    private static final ThreadLocal<HttpContext> CURRENT = new ThreadLocal<>();

    /** Optional hook installed by {@code SecurityConfig}; returns {@code null} to fall back. */
    public static volatile Function<HttpContext, IPrincipal> PrincipalResolver;

    private final boolean startup;
    private final HttpServletRequest servletRequest;
    private final HttpServletResponse servletResponse;
    private final HttpRequest request;
    private final HttpResponse response;
    private final HttpServerUtility server;
    private HttpSessionState session;
    private IPrincipal user;
    private Throwable error;
    private final Map<Object, Object> items = new HashMap<>();

    private HttpContext(boolean startup, HttpServletRequest req, HttpServletResponse res, byte[] body, boolean tooLarge) {
        this.startup = startup;
        this.servletRequest = req;
        this.servletResponse = res;
        this.request = startup ? null : new HttpRequest(req, body, tooLarge);
        this.response = startup ? null : new HttpResponse(request);
        this.server = new HttpServerUtility(this);
    }

    /** A context for one servlet request ({@code body} = captured entity body or {@code null}). */
    public static HttpContext forRequest(HttpServletRequest req, HttpServletResponse res, byte[] body, boolean bodyTooLarge) {
        return new HttpContext(false, req, res, body, bodyTooLarge);
    }

    /** The startup pseudo-context of {@code Application_OnStart} (B6). */
    public static HttpContext startup() {
        return new HttpContext(true, null, null, null, false);
    }

    /** .NET {@code HttpContext.Current}. */
    public static HttpContext Current() {
        return CURRENT.get();
    }

    /** Binds {@code ctx} to the current thread and returns the previous binding (restore it with {@link #restore}). */
    public static HttpContext bind(HttpContext ctx) {
        HttpContext prev = CURRENT.get();
        CURRENT.set(ctx);
        return prev;
    }

    public static void restore(HttpContext previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public boolean isStartup() {
        return startup;
    }

    boolean hasRequest() {
        return !startup;
    }

    public HttpServletRequest servletRequest() {
        return servletRequest;
    }

    public HttpServletResponse servletResponse() {
        return servletResponse;
    }

    public HttpRequest Request() {
        if (startup) throw new HttpException("Request is not available in this context.");
        return request;
    }

    public HttpResponse Response() {
        if (startup) throw new HttpException("Response is not available in this context.");
        return response;
    }

    /** .NET {@code HttpContext.Session}; {@code null} in the startup context. */
    public HttpSessionState Session() {
        if (startup) return null;
        if (session == null) session = new HttpSessionState(servletRequest);
        return session;
    }

    public HttpApplicationState Application() {
        return ApplicationState.get();
    }

    public HttpServerUtility Server() {
        return server;
    }

    public Cache Cache() {
        return HttpRuntime.Cache();
    }

    public Map<Object, Object> Items() {
        return items;
    }

    public IPrincipal User() {
        if (user == null) user = resolveUser();
        return user;
    }

    public void User(IPrincipal principal) {
        this.user = principal;
    }

    private IPrincipal resolveUser() {
        Function<HttpContext, IPrincipal> f = PrincipalResolver;
        if (f != null) {
            IPrincipal p = f.apply(this);
            if (p != null) return p;
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null) {
            Object p = a.getPrincipal();
            if (p instanceof IPrincipal ip) return ip;
            if (p instanceof ClaimsIdentity ci) return new ClaimsPrincipal(ci);
            Object d = a.getDetails();
            if (d instanceof IPrincipal ip2) return ip2;
            if (d instanceof ClaimsIdentity ci2) return new ClaimsPrincipal(ci2);
        }
        return new ClaimsPrincipal(new ClaimsIdentity());
    }

    /** The unhandled exception being reported ({@code Server.GetLastError()}). */
    public Throwable Error() {
        return error;
    }

    public void AddError(Throwable t) {
        this.error = t;
    }

    public void ClearError() {
        this.error = null;
    }

    /** The executing page or handler ({@code Context.Handler}); set by {@link Page}. */
    public Object Handler() {
        return items.get(HANDLER_KEY);
    }

    void Handler(Object h) {
        items.put(HANDLER_KEY, h);
    }

    private static final Object HANDLER_KEY = new Object();
}
