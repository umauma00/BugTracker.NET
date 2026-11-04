package btnet.runtime.web;

/**
 * Holder of the one {@link HttpApplicationState} (RULEBOOK §3 [C32]): created at
 * class load, before {@code Application_OnStart}; the same object every page's
 * {@code Application} field, {@code HttpContext.Application()} and
 * {@code Util.context.Application} (→ {@code ApplicationState.get()}) refer to.
 */
public final class ApplicationState {
    private ApplicationState() {
    }

    private static final HttpApplicationState INSTANCE = new HttpApplicationState();

    public static HttpApplicationState get() {
        return INSTANCE;
    }
}
