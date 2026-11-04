package btnet.runtime.web;

import btnet.runtime.AppPaths;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * .NET {@code System.Web.HttpServerUtility} ({@code Server}, RULEBOOK §2A/§2B B3).
 *
 * <ul>
 * <li>{@link #MapPath(String)}: virtual → physical path under the webroot
 * ({@code AppPaths.WebRoot()}: {@code btnet.webroot}, env {@code BTNET_WEBROOT}, default
 * {@code port/web/webroot}); {@code ~/x} and {@code /x} are app-rooted, other paths are
 * relative to the current request directory; {@code \} → {@code /}; {@code MapPath(null)}
 * → the webroot (no trailing separator); a {@code ..} above the root →
 * {@code HttpException}.</li>
 * <li>{@link #Transfer(String)}: executes the target page in the same request (servlet
 * include; the target sees the same Form/QueryString, runs with {@code IsPostBack=false}
 * and appends to the same Response buffer), then throws {@link TransferException}.</li>
 * </ul>
 */
public class HttpServerUtility {
    /** Request attribute holding the transfer target path while a transferred page executes. */
    public static final String TRANSFER_ATTRIBUTE = "btnet.runtime.web.transfer";

    private final HttpContext context; // null = resolve HttpContext.Current() on each call

    /** {@code context == null}: resolves the current context per call (for {@link HttpApplication#Server}). */
    public HttpServerUtility(HttpContext context) {
        this.context = context;
    }

    private HttpContext ctx() {
        HttpContext c = context != null ? context : HttpContext.Current();
        if (c == null) throw new HttpException("Server operation is not available in this context.");
        return c;
    }

    // ------------------------------------------------------------------ paths

    /** The physical application root (= {@code AppPaths.WebRoot()}: absolute, {@code /}-separated). */
    public static String WebRoot() {
        String w = AppPaths.WebRoot().replace('\\', '/');
        while (w.length() > 1 && w.endsWith("/")) w = w.substring(0, w.length() - 1);
        return w;
    }

    /** Sets the webroot (Spring property {@code btnet.webroot}); delegates to {@code AppPaths.SetWebRoot}. */
    public static void setWebRoot(String path) {
        if (path != null && !path.isEmpty()) AppPaths.SetWebRoot(path);
    }

    /**
     * Maps {@code virtualPath} relative to {@code currentDirectory} (a virtual directory such as
     * {@code "/"}); the request-free form used by {@code AppPaths.MapPath}.
     */
    public static String mapPath(String virtualPath, String currentDirectory) {
        String root = WebRoot();
        if (virtualPath == null || virtualPath.isEmpty()) {
            virtualPath = currentDirectory == null ? "/" : currentDirectory;
        }
        String v = virtualPath.replace('\\', '/');
        if (v.length() >= 2 && v.charAt(1) == ':' && Character.isLetter(v.charAt(0))) {
            throw new HttpException("'" + virtualPath + "' is a physical path, but a virtual path was expected.");
        }
        if (v.startsWith("//")) {
            throw new HttpException("'" + virtualPath + "' is a physical path, but a virtual path was expected.");
        }
        String abs;
        if (v.equals("~")) {
            abs = "/";
        } else if (v.startsWith("~/")) {
            abs = v.substring(1);
        } else if (v.startsWith("/")) {
            abs = v;
        } else {
            String dir = currentDirectory == null ? "/" : currentDirectory;
            if (!dir.endsWith("/")) dir = dir + "/";
            abs = dir + v;
        }
        List<String> parts = new ArrayList<>();
        for (String seg : abs.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (parts.isEmpty()) {
                    throw new HttpException("Cannot use a leading .. to exit above the top directory.");
                }
                parts.remove(parts.size() - 1);
            } else {
                parts.add(seg);
            }
        }
        StringBuilder sb = new StringBuilder(root);
        for (String seg : parts) sb.append('/').append(seg);
        return sb.toString();
    }

    /** .NET {@code Server.MapPath(path)}. */
    public String MapPath(String path) {
        String dir = "/";
        HttpContext c = context != null ? context : HttpContext.Current();
        if (c != null && c.hasRequest()) dir = HttpRequest.directoryOf(c.Request().Path());
        return mapPath(path, dir);
    }

    // ------------------------------------------------------------------ encoding

    public String HtmlEncode(String s) {
        return HttpUtility.HtmlEncode(s);
    }

    public String HtmlDecode(String s) {
        return HttpUtility.HtmlDecode(s);
    }

    public String UrlEncode(String s) {
        return HttpUtility.UrlEncode(s);
    }

    public String UrlDecode(String s) {
        return HttpUtility.UrlDecode(s);
    }

    public String UrlPathEncode(String s) {
        return HttpUtility.UrlPathEncode(s);
    }

    // ------------------------------------------------------------------ errors / info

    /** .NET {@code Server.GetLastError()}: the unhandled exception being reported to {@code Application_Error}. */
    public Throwable GetLastError() {
        HttpContext c = context != null ? context : HttpContext.Current();
        return c == null ? null : c.Error();
    }

    public void ClearError() {
        HttpContext c = context != null ? context : HttpContext.Current();
        if (c != null) c.ClearError();
    }

    /** .NET {@code Server.MachineName} (= {@code Environment.MachineName}: upper-case NetBIOS-style host name). */
    public String MachineName() {
        String h;
        try {
            h = InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            h = System.getenv().getOrDefault("HOSTNAME", "localhost");
        }
        int dot = h.indexOf('.');
        if (dot > 0) h = h.substring(0, dot);
        char[] cs = h.toCharArray();
        for (int i = 0; i < cs.length; i++) cs[i] = Character.toUpperCase(cs[i]);
        return new String(cs);
    }

    private int scriptTimeout = 30000000;

    /**
     * .NET {@code Server.ScriptTimeout}: with {@code compilation debug="true"}
     * ({@code Web.config:31}) ASP.NET reports 30000000 seconds.
     */
    public int ScriptTimeout() {
        return scriptTimeout;
    }

    public void ScriptTimeout(int seconds) {
        scriptTimeout = seconds;
    }

    // ------------------------------------------------------------------ transfer

    /** .NET {@code Server.Transfer(path)} (preserveForm = true). */
    public void Transfer(String path) {
        Execute(path);
        throw new TransferException(targetPath(path));
    }

    /** .NET {@code Server.Transfer(path, preserveForm)}; the form is always preserved in this port. */
    public void Transfer(String path, boolean preserveForm) {
        Transfer(path);
    }

    static String targetPath(String path) {
        String p = path.replace('\\', '/');
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q); // TODO(port): Transfer with a query string (no source site)
        if (p.startsWith("~/")) return p.substring(1);
        if (p.startsWith("/")) return p;
        return "/" + p;
    }

    /** .NET {@code Server.Execute(path)}: runs the target page into the same Response buffer, without ending. */
    public void Execute(String path) {
        if (path == null) throw new btnet.runtime.ArgumentNullException("path");
        HttpContext c = ctx();
        HttpServletRequest req = c.servletRequest();
        String target = targetPath(path);
        Object previous = req.getAttribute(TRANSFER_ATTRIBUTE);
        req.setAttribute(TRANSFER_ATTRIBUTE, target);
        try {
            RequestDispatcher d = req.getRequestDispatcher(target);
            if (d == null) throw new HttpException(404, "Error executing child request for " + path + ".");
            d.include(req, c.servletResponse());
        } catch (ServletException | IOException e) {
            Throwable root = null;
            for (Throwable t = e; t != null; t = nextCause(t)) {
                if (t instanceof ResponseEndException) return; // the target ended: its output is in the buffer
                if (t instanceof RuntimeException || t instanceof Error) root = t;
            }
            if (root instanceof RuntimeException re) throw re;
            if (root instanceof Error err) throw err;
            throw new HttpException("Error executing child request for " + path + ".", e);
        } finally {
            if (previous == null) req.removeAttribute(TRANSFER_ATTRIBUTE);
            else req.setAttribute(TRANSFER_ATTRIBUTE, previous);
        }
    }

    /** Next link of a cause chain, following {@link ServletException#getRootCause()}; {@code null} at the end. */
    static Throwable nextCause(Throwable t) {
        Throwable next = t instanceof ServletException se && se.getRootCause() != null ? se.getRootCause() : t.getCause();
        return next == t ? null : next;
    }
}
