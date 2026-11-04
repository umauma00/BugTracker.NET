package btnet.runtime.web;

/**
 * .NET {@code System.Web.HttpRuntime}: {@link #Cache()} is the one process-wide
 * {@link btnet.runtime.web.Cache} (RULEBOOK §2A {@code HttpRuntime.Cache[k]}).
 */
public final class HttpRuntime {
    private HttpRuntime() {
    }

    private static final Cache CACHE = new Cache();

    public static Cache Cache() {
        return CACHE;
    }

    /** .NET {@code HttpRuntime.AppDomainAppVirtualPath} (context root {@code /}, B8). */
    public static String AppDomainAppVirtualPath() {
        return "/";
    }

    /** .NET {@code HttpRuntime.AppDomainAppPath}: the physical webroot with a trailing separator. */
    public static String AppDomainAppPath() {
        return HttpServerUtility.WebRoot() + "/";
    }
}
