package btnet.runtime.web;

/**
 * ASP.NET MVC 5.2.3 {@code System.Web.Mvc.UrlHelper}; only {@code IsLocalUrl} is used
 * ({@code util.cs:1103-1105}, RULEBOOK §3 [N2]).
 */
public class UrlHelper {
    private final RequestContext requestContext;

    public UrlHelper(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public RequestContext RequestContext() {
        return requestContext;
    }

    /**
     * System.Web.WebPages 3.2 {@code RequestExtensions.IsUrlLocalToHost}: string-only,
     * no trimming, decoding or Uri parsing. A relative {@code "bugs.aspx"} is NOT local.
     */
    public boolean IsLocalUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.charAt(0) == '/') {
            if (url.length() == 1) return true;
            return url.charAt(1) != '/' && url.charAt(1) != '\\';
        }
        if (url.charAt(0) == '~') return url.length() > 1 && url.charAt(1) == '/';
        return false;
    }
}
