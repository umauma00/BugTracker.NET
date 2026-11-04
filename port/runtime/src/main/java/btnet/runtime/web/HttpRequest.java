package btnet.runtime.web;

import btnet.runtime.Encoding;
import btnet.runtime.NameValueCollection;
import btnet.runtime.io.MemoryStream;
import btnet.runtime.io.Stream;
import btnet.runtime.net.Uri;
import btnet.runtime.security.IOwinContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * .NET {@code System.Web.HttpRequest} (RULEBOOK §2A "Web intrinsics", §2B B2/B3) over a
 * servlet request.
 *
 * <ul>
 * <li>{@link #get(String)} = {@code Request["k"]}: QueryString → Form → Cookies →
 * ServerVariables, first hit, multi-values comma-joined (NameValueCollection), else
 * {@code null}.</li>
 * <li>{@link #QueryString()} / {@link #Form()} are parsed by the runtime exactly as
 * {@code HttpValueCollection.FillFromString} (UTF-8, {@code +} → space, a segment without
 * {@code =} goes under the {@code null} key); multipart fields come from the parts.</li>
 * <li><b>Request validation</b> ({@code requestValidationMode="2.0"}, B3 [J-U14]): when
 * enabled by the page ({@link Page} enables it unless {@code @ValidateRequest(false)}),
 * the first access of {@code Form}, {@code QueryString} or {@code Cookies} validates every
 * value of that collection (keys starting with {@code __} exempt) and throws
 * {@link HttpRequestValidationException}; later accesses do not re-validate.</li>
 * </ul>
 *
 * <p>{@link #current()} returns a proxy that resolves {@code HttpContext.Current().Request()}
 * on every call (used by the singleton {@link ApiController}).
 */
public class HttpRequest {
    /** Request limit, {@code maxRequestLength=51200} KB ({@code Web.config:46}). */
    public static final int MAX_REQUEST_BYTES = 51200 * 1024;

    /**
     * Factory for {@link #GetOwinContext()} (the OWIN extension method), installed by
     * {@code btnet.runtime.security.SecurityConfig} at boot.
     */
    public static volatile Function<HttpRequest, IOwinContext> OwinContextFactory;

    private static final HttpRequest PROXY = new HttpRequest();

    private final boolean proxy;
    private final HttpServletRequest req;
    private final byte[] body;          // captured non-multipart body (null if none / not captured)
    private final boolean bodyTooLarge;

    private boolean validateInput;
    private NameValueCollection queryString, form, serverVariables, headers;
    private HttpCookieCollection cookies;
    private HttpFileCollection files;
    private boolean qsValidated, formValidated, cookiesValidated;

    private HttpRequest() {
        this.proxy = true;
        this.req = null;
        this.body = null;
        this.bodyTooLarge = false;
    }

    /** A request over {@code req}; {@code body} is the body captured by {@link RequestContextFilter}, or {@code null}. */
    public HttpRequest(HttpServletRequest req, byte[] body, boolean bodyTooLarge) {
        this.proxy = false;
        this.req = req;
        this.body = body;
        this.bodyTooLarge = bodyTooLarge;
    }

    /** The current-request proxy (see class comment). */
    public static HttpRequest current() {
        return PROXY;
    }

    private HttpRequest t() {
        if (!proxy) return this;
        HttpContext c = HttpContext.Current();
        if (c == null) throw new HttpException("Request is not available in this context.");
        return c.Request();
    }

    /** The underlying servlet request (runtime use). */
    public HttpServletRequest servletRequest() {
        return t().req;
    }

    // ------------------------------------------------------------------ validation

    /** .NET {@code Request.ValidateInput()}: turns validation on for collections not yet accessed. */
    public void ValidateInput() {
        t().validateInput = true;
    }

    /** Runtime: set by {@link Page} from {@code @ValidateRequest}. */
    public void setValidateInput(boolean on) {
        t().validateInput = on;
    }

    private static boolean isAtoZ(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** .NET {@code CrossSiteScriptingValidation.IsDangerousString}. */
    static boolean isDangerousString(String s) {
        if (s == null) return false;
        int start = 0;
        while (true) {
            int i = indexOfAny(s, start);
            if (i < 0 || i == s.length() - 1) return false;
            char c = s.charAt(i);
            char next = s.charAt(i + 1);
            if (c == '<') {
                if (isAtoZ(next) || next == '!' || next == '/' || next == '?') return true;
            } else if (c == '&') {
                if (next == '#') return true;
            }
            start = i + 1;
        }
    }

    private static int indexOfAny(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '&') return i;
        }
        return -1;
    }

    private static String validationMessage(String collection, String key, String value) {
        // TODO(port): pin .NET 4.5's display truncation of the offending value (D-001)
        String v = value.length() > 15 ? value.substring(0, 15) + "..." : value;
        return "A potentially dangerous " + collection + " value was detected from the client ("
            + (key == null ? "" : key) + "=\"" + v + "\").";
    }

    private void validate(NameValueCollection c, String collection) {
        String[] keys = c.AllKeys();
        for (String key : keys) {
            if (key != null && key.startsWith("__")) continue;
            String[] values = c.GetValues(key);
            if (values == null) continue;
            for (String v : values) {
                if (isDangerousString(v)) {
                    throw new HttpRequestValidationException(validationMessage(collection, key, v));
                }
            }
        }
    }

    // ------------------------------------------------------------------ collections

    /** {@code Request["k"]}. */
    public String get(String key) {
        HttpRequest r = t();
        String v = r.QueryString().get(key);
        if (v != null) return v;
        v = r.Form().get(key);
        if (v != null) return v;
        HttpCookie c = r.Cookies().get(key);
        if (c != null) return c.Value();
        return r.ServerVariables().get(key);
    }

    public NameValueCollection QueryString() {
        HttpRequest r = t();
        if (r.queryString == null) {
            NameValueCollection c = new NameValueCollection();
            String q = r.req.getQueryString();
            if (q != null) HttpUtility.fillFromString(c, q, true);
            r.queryString = c;
        }
        if (r.validateInput && !r.qsValidated) {
            r.qsValidated = true;
            r.validate(r.queryString, "Request.QueryString");
        }
        return r.queryString;
    }

    public NameValueCollection Form() {
        HttpRequest r = t();
        if (r.form == null) r.parseForm();
        if (r.validateInput && !r.formValidated) {
            r.formValidated = true;
            r.validate(r.form, "Request.Form");
        }
        return r.form;
    }

    public HttpCookieCollection Cookies() {
        HttpRequest r = t();
        HttpCookieCollection c = r.cookiesNoValidate();
        if (r.validateInput && !r.cookiesValidated) {
            r.cookiesValidated = true;
            for (HttpCookie k : c) {
                if (isDangerousString(k.Value())) {
                    throw new HttpRequestValidationException(validationMessage("Request.Cookies", k.Name(), k.Value()));
                }
            }
        }
        return c;
    }

    HttpCookieCollection cookiesNoValidate() {
        HttpRequest r = t();
        if (r.cookies == null) {
            HttpCookieCollection c = new HttpCookieCollection(false, r);
            Enumeration<String> hs = r.req.getHeaders("Cookie");
            if (hs != null) {
                for (String h : Collections.list(hs)) {
                    for (String pair : h.split(";")) {
                        String p = pair.trim();
                        if (!p.isEmpty()) c.addRaw(HttpCookie.fromRequestPair(p));
                    }
                }
            }
            r.cookies = c;
        }
        return r.cookies;
    }

    public HttpFileCollection Files() {
        HttpRequest r = t();
        if (r.form == null) r.parseForm();
        return r.files;
    }

    /** .NET {@code Request.Params}: QueryString, Form, Cookies, ServerVariables, in that order. */
    public NameValueCollection Params() {
        HttpRequest r = t();
        NameValueCollection p = new NameValueCollection();
        addAll(p, r.QueryString());
        addAll(p, r.Form());
        for (HttpCookie c : r.Cookies()) p.Add(c.Name(), c.Value());
        addAll(p, r.ServerVariables());
        return p;
    }

    private static void addAll(NameValueCollection to, NameValueCollection from) {
        for (String k : from.AllKeys()) {
            String[] vs = from.GetValues(k);
            if (vs == null) continue;
            for (String v : vs) to.Add(k, v);
        }
    }

    public NameValueCollection Headers() {
        HttpRequest r = t();
        if (r.headers == null) {
            NameValueCollection h = new NameValueCollection();
            for (String n : Collections.list(r.req.getHeaderNames())) {
                for (String v : Collections.list(r.req.getHeaders(n))) h.Add(n, v);
            }
            r.headers = h;
        }
        return r.headers;
    }

    /** IIS server variables the source reads ({@code SERVER_NAME}, {@code URL}, {@code LOGON_USER}, …) plus {@code HTTP_*}. */
    public NameValueCollection ServerVariables() {
        HttpRequest r = t();
        if (r.serverVariables == null) {
            HttpServletRequest q = r.req;
            NameValueCollection s = new NameValueCollection();
            StringBuilder allHttp = new StringBuilder();
            StringBuilder allRaw = new StringBuilder();
            for (String n : Collections.list(q.getHeaderNames())) {
                String v = String.join(",", Collections.list(q.getHeaders(n)));
                String key = "HTTP_" + upper(n).replace('-', '_');
                allHttp.append(key).append(':').append(v).append("\r\n");
                allRaw.append(n).append(": ").append(v).append("\r\n");
            }
            s.Add("ALL_HTTP", allHttp.toString());
            s.Add("ALL_RAW", allRaw.toString());
            s.Add("APPL_MD_PATH", "/LM/W3SVC/1/ROOT");
            s.Add("APPL_PHYSICAL_PATH", HttpServerUtility.WebRoot() + "/");
            s.Add("AUTH_TYPE", "");
            s.Add("AUTH_USER", "");
            s.Add("AUTH_PASSWORD", "");
            s.Add("LOGON_USER", "");
            s.Add("REMOTE_USER", "");
            s.Add("CONTENT_LENGTH", String.valueOf(Math.max(q.getContentLength(), 0)));
            s.Add("CONTENT_TYPE", q.getContentType() == null ? "" : q.getContentType());
            s.Add("GATEWAY_INTERFACE", "CGI/1.1");
            s.Add("HTTPS", q.isSecure() ? "on" : "off");
            s.Add("INSTANCE_ID", "1");
            s.Add("LOCAL_ADDR", q.getLocalAddr());
            s.Add("PATH_INFO", Path());
            s.Add("PATH_TRANSLATED", HttpServerUtility.mapPath(Path(), "/"));
            s.Add("QUERY_STRING", q.getQueryString() == null ? "" : q.getQueryString());
            s.Add("REMOTE_ADDR", q.getRemoteAddr());
            s.Add("REMOTE_HOST", q.getRemoteHost());
            s.Add("REMOTE_PORT", String.valueOf(q.getRemotePort()));
            s.Add("REQUEST_METHOD", q.getMethod());
            s.Add("SCRIPT_NAME", Path());
            s.Add("SERVER_NAME", q.getServerName());
            s.Add("SERVER_PORT", String.valueOf(q.getServerPort()));
            s.Add("SERVER_PORT_SECURE", q.isSecure() ? "1" : "0");
            s.Add("SERVER_PROTOCOL", q.getProtocol());
            s.Add("SERVER_SOFTWARE", "Microsoft-IIS/8.0");
            s.Add("URL", Path());
            for (String n : Collections.list(q.getHeaderNames())) {
                s.Add("HTTP_" + upper(n).replace('-', '_'), String.join(",", Collections.list(q.getHeaders(n))));
            }
            r.serverVariables = s;
        }
        return r.serverVariables;
    }

    private static String upper(String s) {
        char[] cs = s.toCharArray();
        for (int i = 0; i < cs.length; i++) cs[i] = Character.toUpperCase(cs[i]);
        return new String(cs);
    }

    private void parseForm() {
        NameValueCollection f = new NameValueCollection();
        HttpFileCollection fs = new HttpFileCollection();
        String ct = req.getContentType() == null ? "" : req.getContentType().toLowerCase(Locale.ROOT);
        if (bodyTooLarge) {
            form = f;
            files = fs;
            throw new HttpException("Maximum request length exceeded.");
        }
        if (ct.startsWith("application/x-www-form-urlencoded")) {
            if (body != null) {
                HttpUtility.fillFromString(f, new String(body, StandardCharsets.UTF_8), true);
            } else {
                // TODO(port): body not captured (RequestContextFilter not installed): servlet parameters minus the query string
                Set<String> qsNames = new HashSet<>();
                for (String k : QueryStringRaw().AllKeys()) {
                    if (k != null) qsNames.add(k);
                }
                for (String n : Collections.list(req.getParameterNames())) {
                    if (qsNames.contains(n)) continue;
                    for (String v : req.getParameterValues(n)) f.Add(n, v);
                }
            }
        } else if (ct.startsWith("multipart/form-data")) {
            try {
                for (Part p : req.getParts()) {
                    byte[] bytes;
                    try (InputStream in = p.getInputStream()) {
                        bytes = in.readAllBytes();
                    }
                    if (p.getSubmittedFileName() == null) {
                        f.Add(p.getName(), new String(bytes, StandardCharsets.UTF_8));
                    } else {
                        fs.add(p.getName(), new HttpPostedFile(p.getSubmittedFileName(), p.getContentType(), bytes));
                    }
                }
            } catch (IllegalStateException tooLarge) {
                form = f;
                files = fs;
                throw new HttpException("Maximum request length exceeded.");
            } catch (IOException | ServletException e) {
                form = f;
                files = fs;
                throw new HttpException(e.getMessage(), e);
            }
        }
        form = f;
        files = fs;
    }

    private NameValueCollection QueryStringRaw() {
        if (queryString == null) {
            NameValueCollection c = new NameValueCollection();
            String q = req.getQueryString();
            if (q != null) HttpUtility.fillFromString(c, q, true);
            queryString = c;
        }
        return queryString;
    }

    // ------------------------------------------------------------------ URL / meta

    /** .NET {@code RawUrl}: the path and query exactly as sent. */
    public String RawUrl() {
        HttpServletRequest q = t().req;
        String qs = q.getQueryString();
        return q.getRequestURI() + (qs == null ? "" : "?" + qs);
    }

    /** .NET {@code Path}: the decoded path, no query. */
    public String Path() {
        String raw = t().req.getRequestURI();
        return HttpUtility.UrlDecode(raw.replace("+", "%2B"));
    }

    public String FilePath() {
        return Path();
    }

    public String CurrentExecutionFilePath() {
        HttpRequest r = t();
        Object transfer = r.req.getAttribute(HttpServerUtility.TRANSFER_ATTRIBUTE);
        return transfer instanceof String s ? s : r.Path();
    }

    public String AppRelativeCurrentExecutionFilePath() {
        return "~" + CurrentExecutionFilePath();
    }

    public String ApplicationPath() {
        return "/";
    }

    public String PhysicalApplicationPath() {
        return HttpServerUtility.WebRoot() + "/";
    }

    public String PhysicalPath() {
        return MapPath(Path());
    }

    /** .NET {@code Request.Url}: runtime {@link Uri} of scheme, host, port, path and query. */
    public Uri Url() {
        HttpServletRequest q = t().req;
        String scheme = q.getScheme();
        int port = q.getServerPort();
        boolean dflt = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        return new Uri(scheme + "://" + q.getServerName() + (dflt || port <= 0 ? "" : ":" + port) + RawUrl());
    }

    /** .NET {@code UrlReferrer}; {@code null} when absent or unparsable. */
    public Uri UrlReferrer() {
        String r = t().req.getHeader("Referer");
        if (r == null || r.isEmpty()) return null;
        try {
            return new Uri(r);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String HttpMethod() {
        return t().req.getMethod();
    }

    public String RequestType() {
        return t().req.getMethod();
    }

    public String UserHostAddress() {
        return t().req.getRemoteAddr();
    }

    public String UserHostName() {
        return t().req.getRemoteHost();
    }

    public String UserAgent() {
        return t().req.getHeader("User-Agent");
    }

    public boolean IsSecureConnection() {
        return t().req.isSecure();
    }

    public boolean IsLocal() {
        String a = t().req.getRemoteAddr();
        return "127.0.0.1".equals(a) || "0:0:0:0:0:0:0:1".equals(a) || "::1".equals(a)
            || (a != null && a.equals(t().req.getLocalAddr()));
    }

    public String ContentType() {
        String ct = t().req.getContentType();
        return ct == null ? "" : ct;
    }

    public int ContentLength() {
        return Math.max(t().req.getContentLength(), 0);
    }

    public int TotalBytes() {
        byte[] b = bodyBytes();
        return b == null ? 0 : b.length;
    }

    /** .NET {@code ContentEncoding} (request encoding UTF-8, no {@code <globalization>}). */
    public Encoding ContentEncoding() {
        return Encoding.UTF8;
    }

    /** .NET {@code InputStream}: the raw entity body, positioned at 0. */
    public Stream InputStream() {
        byte[] b = bodyBytes();
        return new MemoryStream(b == null ? new byte[0] : b);
    }

    /** The raw body bytes (captured by the filter, else read once from the servlet stream). */
    public byte[] bodyBytes() {
        HttpRequest r = t();
        if (r.body != null) return r.body.clone();
        String ct = r.req.getContentType() == null ? "" : r.req.getContentType().toLowerCase(Locale.ROOT);
        if (ct.startsWith("multipart/")) return new byte[0];
        Object cached = r.req.getAttribute(BODY_ATTRIBUTE);
        if (cached instanceof byte[] b) return b.clone();
        try (InputStream in = r.req.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            in.transferTo(o);
            byte[] b = o.toByteArray();
            r.req.setAttribute(BODY_ATTRIBUTE, b);
            return b.clone();
        } catch (IOException | IllegalStateException e) {
            return new byte[0];
        }
    }

    private static final String BODY_ATTRIBUTE = "btnet.runtime.web.HttpRequest.body";

    /** .NET {@code Request.MapPath(p)} = {@code Server.MapPath(p)}. */
    public String MapPath(String virtualPath) {
        return HttpServerUtility.mapPath(virtualPath, directoryOf(Path()));
    }

    static String directoryOf(String path) {
        if (path == null) return "/";
        int i = path.lastIndexOf('/');
        return i < 0 ? "/" : path.substring(0, i + 1);
    }

    /** .NET {@code Request.RequestContext} (opaque, for {@link UrlHelper}). */
    public RequestContext RequestContext() {
        return new RequestContext(t());
    }

    /** OWIN {@code HttpContextBaseExtensions.GetOwinContext(Request)}. */
    public IOwinContext GetOwinContext() {
        Function<HttpRequest, IOwinContext> f = OwinContextFactory;
        if (f == null) throw new btnet.runtime.InvalidOperationException("No owin.Environment item was found in the context.");
        return f.apply(t());
    }
}
