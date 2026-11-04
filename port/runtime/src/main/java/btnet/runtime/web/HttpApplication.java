package btnet.runtime.web;

import btnet.runtime.EventArgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * .NET {@code System.Web.HttpApplication}, base of the translated {@code btnet.pages.Global}
 * (RULEBOOK §2B B6).
 *
 * <p>{@link #startUp()} (called by {@code BtnetApplication} at {@code ApplicationReadyEvent};
 * {@link RequestContextFilter} also calls it before the first request as a fallback) creates
 * the one {@code Global} instance (class {@code btnet.pages.Global}, overridable with system
 * property {@code btnet.global.class}) and runs {@code Application_OnStart(null, EventArgs.Empty)}
 * once, with {@link HttpContext#Current()} bound to the startup pseudo-context for the duration
 * of the call. {@link Page} calls {@link #Application_Error} on an unhandled page exception,
 * with {@code Server.GetLastError()} returning it.
 */
public class HttpApplication {
    private static final Logger log = LoggerFactory.getLogger(HttpApplication.class);

    /** {@code Application}: the {@link HttpApplicationState} singleton. */
    public final HttpApplicationState Application = ApplicationState.get();

    /** {@code Server}: resolves the current context on each call. */
    public final HttpServerUtility Server = new HttpServerUtility(null);

    /** {@code Context}: the current context. */
    public HttpContext Context() {
        return HttpContext.Current();
    }

    public HttpRequest Request() {
        HttpContext c = HttpContext.Current();
        if (c == null) throw new HttpException("Request is not available in this context.");
        return c.Request();
    }

    public HttpResponse Response() {
        HttpContext c = HttpContext.Current();
        if (c == null) throw new HttpException("Response is not available in this context.");
        return c.Response();
    }

    public HttpSessionState Session() {
        HttpContext c = HttpContext.Current();
        if (c == null) throw new HttpException("Session state is not available in this context.");
        return c.Session();
    }

    /** Overridden by {@code Global}. */
    public void Application_OnStart(Object sender, EventArgs e) {
    }

    /** Overridden by {@code Global}. */
    public void Application_Error(Object sender, EventArgs e) {
    }

    private static volatile HttpApplication instance;
    private static volatile boolean started;

    /** The running application instance ({@code Global}, or a plain {@code HttpApplication} if none exists). */
    public static HttpApplication instance() {
        HttpApplication a = instance;
        if (a == null) {
            synchronized (HttpApplication.class) {
                a = instance;
                if (a == null) {
                    a = create();
                    instance = a;
                }
            }
        }
        return a;
    }

    private static HttpApplication create() {
        String cls = System.getProperty("btnet.global.class", "btnet.pages.Global");
        try {
            Class<?> c = Class.forName(cls);
            return (HttpApplication) c.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            log.warn("no {} on the classpath; using a plain HttpApplication", cls);
            return new HttpApplication();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("cannot create " + cls, e);
        }
    }

    /** Runs {@code Application_OnStart} once (idempotent, thread-safe). */
    public static void startUp() {
        if (started) return;
        synchronized (HttpApplication.class) {
            if (started) return;
            started = true;
            HttpApplication app = instance();
            HttpContext prev = HttpContext.bind(HttpContext.startup());
            try {
                app.Application_OnStart(null, EventArgs.Empty);
            } catch (RuntimeException | Error e) {
                log.error("Application_OnStart failed", e);
                throw e;
            } finally {
                HttpContext.restore(prev);
            }
        }
    }

    /** Fallback used before the first request. */
    public static void ensureStarted() {
        if (!started) startUp();
    }

    /** Reports an unhandled exception to {@code Application_Error} (errors inside the handler are logged and dropped). */
    static void reportError(HttpContext ctx, Throwable t, Object sender) {
        ctx.AddError(t);
        try {
            instance().Application_Error(sender, EventArgs.Empty);
        } catch (RuntimeException | Error e) {
            if (e instanceof ResponseEndException) return;
            log.error("Application_Error failed", e);
        }
    }
}
