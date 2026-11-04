package btnet.runtime.web;

import btnet.runtime.EventArgs;
import btnet.runtime.Exceptions;
import btnet.runtime.NameValueCollection;
import btnet.runtime.security.IPrincipal;
import btnet.runtime.web.ui.Control;
import btnet.runtime.web.ui.HtmlForm;
import btnet.runtime.web.ui.HtmlHead;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * .NET {@code System.Web.UI.Page}: base of every translated page (directly, or through the
 * translated {@code btnet.BasePage}) — RULEBOOK §2B B1/B2.
 *
 * <h2>Shape</h2>
 * Intrinsics are public fields used bare ({@code Request}, {@code Response}, {@code Server},
 * {@code Session}, {@code Application}, {@code Context}, {@code Cache}, {@code User},
 * {@code IsPostBack}, {@code ViewStateUserKey}, {@code Page} = this, {@code Header}). Controls
 * are registered in field initializers with {@link #control(Control)}, the master with
 * {@link #master(MasterPage)}. The page's {@code handle} mapping calls
 * {@link #ProcessRequest(HttpServletRequest, HttpServletResponse, String)}.
 *
 * <h2>Lifecycle ({@code ProcessRequest}, B2)</h2>
 * <ol>
 * <li>Bind intrinsics (also into the master and user controls); request validation on
 * unless {@code @ValidateRequest(false)}.</li>
 * <li>Postback collection = {@code Request.Form} for POST, else {@code Request.QueryString};
 * {@code IsPostBack} = it holds {@code __VIEWSTATE} or {@code __EVENTTARGET} and this is
 * not a {@code Server.Transfer} target.</li>
 * <li>{@link #OnPreInit(EventArgs)} (BasePage authorization).</li>
 * <li>{@code Page_Init} of user controls (inner first, the master among them), then the page;
 * on postback: decode {@code __VIEWSTATE} ({@link ViewStateCodec}, MAC with
 * {@code ViewStateUserKey}), {@code Control.LoadViewStateTree}, then
 * {@code Control.LoadPostDataTree} (the web.ui contract, see {@code Control}).</li>
 * <li>{@code Page_Load} of the page, the master, then user controls in markup order.</li>
 * <li>Postback: {@code Control.RaisePostBackEventTree} (the posted submit button, else the
 * {@code __EVENTTARGET} control).</li>
 * <li>{@code Control.SaveViewStateTree} → {@link ViewStateCodec} ({@link #ViewStateValue()},
 * model variable {@code viewState} for {@code templates/runtime/form.html}); render the
 * template with model {@code page}, {@code master}, {@code viewState} <b>into the Response
 * buffer</b> (after whatever code wrote), and send the buffer.</li>
 * <li>{@link ResponseEndException} (End / Redirect / Transfer, also from inside the template)
 * → the buffer is sent as it stands; any other exception → {@code Application_Error}
 * ({@code Server.GetLastError()}) and HTTP 500.</li>
 * </ol>
 * Controls are keyed in view state by {@code Control.ViewStateKey()} (their UniqueID).
 */
public class Page implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(Page.class);

    public HttpRequest Request;
    public HttpResponse Response;
    public HttpServerUtility Server;
    public HttpSessionState Session;
    public HttpApplicationState Application;
    public HttpContext Context;
    public Cache Cache;
    public IPrincipal User;
    public boolean IsPostBack;
    public String ViewStateUserKey;
    public Page Page = this;
    /** {@code Page.Header} ({@code <head runat="server">}); its {@code Title} is rendered by the layout. */
    public HtmlHead Header = new HtmlHead("Head1");

    private final List<Control<?>> pageControls = new ArrayList<>();
    private MasterPage pageMaster;
    private String executionPath;
    private String viewStateValue = "";
    private boolean transferTarget;

    // ------------------------------------------------------------------ registration

    /** Registers a control declared by the page ({@code control(new HtmlInputText("x"))}); returns it. */
    protected <C extends Control<?>> C control(C c) {
        pageControls.add(c);
        return c;
    }

    /** Attaches the master ({@code MasterPageFile}); returns it. */
    protected <M extends MasterPage> M master(M m) {
        this.pageMaster = m;
        return m;
    }

    /** The attached master, or {@code null}. */
    public MasterPage masterPage() {
        return pageMaster;
    }

    /** The page's registered controls, in registration order. */
    public List<Control<?>> registeredControls() {
        return Collections.unmodifiableList(pageControls);
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /** .NET {@code OnPreInit}; {@code BasePage} overrides it and calls {@code super.OnPreInit(e)}. */
    protected void OnPreInit(EventArgs e) {
    }

    /** AutoEventWireup {@code Page_Init}. */
    protected void Page_Init(Object sender, EventArgs e) {
    }

    /** AutoEventWireup {@code Page_Load}. */
    protected void Page_Load(Object sender, EventArgs e) {
    }

    // ------------------------------------------------------------------ page members

    /** {@code Page.Title} getter (= {@code Header.Title}). */
    public String Title() {
        return Header.Title();
    }

    /** {@code Page.Title = v} (= {@code Header.Title = v}). */
    public void Title(String v) {
        Header.Title(v);
    }

    /** The {@code <title>} text: {@code Header.Title} if code set one, else the markup default. */
    public String TitleOr(String markupTitle) {
        String t = Header.Title();
        return t == null || t.isEmpty() ? markupTitle : t;
    }

    /** {@code th:action} of a server form: the executing page's path plus the request's query string (B5). */
    public String FormAction() {
        String qs = Request.servletRequest().getQueryString();
        return executionPath + (qs == null ? "" : "?" + qs);
    }

    /** The {@code __VIEWSTATE} value to render (saved just before render). */
    public String ViewStateValue() {
        return viewStateValue;
    }

    /** True while this page runs as a {@code Server.Transfer} target. */
    public boolean IsTransferTarget() {
        return transferTarget;
    }

    public String MapPath(String virtualPath) {
        return Server.MapPath(virtualPath);
    }

    public String ResolveUrl(String url) {
        return resolveUrlStatic(url);
    }

    static String resolveUrlStatic(String url) {
        if (url == null) return null;
        if (url.equals("~")) return "/";
        if (url.startsWith("~/")) return url.substring(1);
        return url;
    }

    // ------------------------------------------------------------------ Spring

    private static volatile ApplicationContext applicationContext;
    private static volatile ITemplateEngine templateEngine;
    private static volatile JakartaServletWebApplication webApplication;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
    }

    private static ITemplateEngine engine(ServletContext sc) {
        ITemplateEngine e = templateEngine;
        if (e == null) {
            ApplicationContext ac = applicationContext;
            if (ac == null) ac = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
            e = ac.getBean(ITemplateEngine.class);
            templateEngine = e;
        }
        return e;
    }

    private static JakartaServletWebApplication webApplication(ServletContext sc) {
        JakartaServletWebApplication w = webApplication;
        if (w == null) {
            w = JakartaServletWebApplication.buildApplication(sc);
            webApplication = w;
        }
        return w;
    }

    // ------------------------------------------------------------------ ProcessRequest

    /**
     * Runs the page (B2) and sends the response. {@code templateName} is the Thymeleaf template
     * (e.g. {@code "delete_priority"}), or {@code null} for a page with no markup (only the
     * Response buffer is sent).
     */
    public void ProcessRequest(HttpServletRequest req, HttpServletResponse res, String templateName) {
        HttpContext ctx = HttpContext.Current();
        HttpContext previous = null;
        boolean bound = false;
        if (ctx == null || ctx.isStartup()) {
            ctx = HttpContext.forRequest(req, res, null, false);
            previous = HttpContext.bind(ctx);
            bound = true;
        }
        try {
            HttpServletRequest sreq = ctx.servletRequest();
            Object transferAttr = sreq.getAttribute(HttpServerUtility.TRANSFER_ATTRIBUTE);
            transferTarget = transferAttr instanceof String;
            executionPath = transferTarget ? (String) transferAttr : sreq.getRequestURI();
            try {
                execute(ctx, templateName);
            } catch (Throwable t) {
                if (findEnd(t) != null) {
                    // End / Redirect / Transfer: the buffer is sent as it stands (B2 step 8, C8)
                } else if (transferTarget) {
                    throw t; // reported by the transferring page
                } else {
                    handleError(ctx, unwrap(t));
                }
            }
            if (!transferTarget) {
                ctx.Response().flushTo(ctx.servletResponse(), "HEAD".equalsIgnoreCase(sreq.getMethod()));
            }
        } finally {
            if (bound) HttpContext.restore(previous);
        }
    }

    private void execute(HttpContext ctx, String templateName) {
        // 1. intrinsics
        Context = ctx;
        Request = ctx.Request();
        Response = ctx.Response();
        Server = ctx.Server();
        Session = ctx.Session();
        Application = ctx.Application();
        Cache = ctx.Cache();
        User = ctx.User();
        Page = this;
        if (!transferTarget) ctx.Handler(this);
        Request.setValidateInput(validatesRequest());

        // 2. postback collection
        boolean post = "POST".equalsIgnoreCase(Request.HttpMethod());
        NameValueCollection postData = post ? Request.Form() : Request.QueryString();
        IsPostBack = !transferTarget
            && (postData.GetValues("__VIEWSTATE") != null || postData.GetValues("__EVENTTARGET") != null);
        List<Control<?>> roots = roots();
        List<Control<?>> all = Control.Descendants(roots);
        for (Control<?> c : all) {
            if (c instanceof UserControl uc) uc.bindTo(this);
            if (c instanceof HtmlForm f) {
                String id = ServerFormClientID(f);
                if (id != null) f.ClientID(id);
            }
        }

        // 3. PreInit
        OnPreInit(EventArgs.Empty);

        // 4. Init (inner user controls first, then the master, then the page), view state, posted values
        List<UserControl> ucs = new ArrayList<>();
        for (Control<?> c : all) {
            if (c instanceof UserControl uc) ucs.add(uc);
        }
        for (int i = ucs.size() - 1; i >= 0; i--) ucs.get(i).runInit();
        Page_Init(this, EventArgs.Empty);
        Control<?> raise = null;
        if (IsPostBack) {
            Object state = ViewStateCodec.Deserialize(postData.get("__VIEWSTATE"), ViewStateUserKey);
            @SuppressWarnings("unchecked")
            Map<String, ?> saved = state instanceof Map<?, ?> m ? (Map<String, ?>) m : null;
            Control.LoadViewStateTree(roots, saved);
            raise = Control.LoadPostDataTree(roots, postData, saved);
        }

        // 5. Load: page, master, then user controls in markup order
        Page_Load(this, EventArgs.Empty);
        if (pageMaster != null) pageMaster.runLoad();
        for (UserControl uc : ucs) {
            if (uc != pageMaster) uc.runLoad();
        }

        // 6. postback event
        if (IsPostBack) Control.RaisePostBackEventTree(roots, postData, raise);

        // 7. save view state, render
        viewStateValue = ViewStateCodec.Serialize(Control.SaveViewStateTree(roots), ViewStateUserKey);
        if (templateName != null) render(ctx, templateName);
    }

    /**
     * The id/name the server form renders (B5 [J-U7]), set as {@code HtmlForm.ClientID(String)} before
     * {@code OnPreInit}: {@code aspnetForm} on a master page, else {@code null} (the form's declared
     * {@code ID}, or {@code ctl00}). A page whose content placeholder has {@code ClientIDMode="Static"}
     * ({@code bugs.aspx:11} → {@code frm}) overrides this to return {@code form.ID()}.
     */
    protected String ServerFormClientID(HtmlForm form) {
        return pageMaster != null ? "aspnetForm" : null;
    }

    private boolean validatesRequest() {
        for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
            ValidateRequest v = c.getDeclaredAnnotation(ValidateRequest.class);
            if (v != null) return v.value();
        }
        return true;
    }

    /** The control-tree roots: the master (its children hang below it, first in markup order), then the page's {@code control(...)} fields. */
    private List<Control<?>> roots() {
        List<Control<?>> out = new ArrayList<>();
        if (pageMaster != null) out.add(pageMaster);
        out.addAll(pageControls);
        return out;
    }

    private void render(HttpContext ctx, String templateName) {
        HttpServletRequest req = ctx.servletRequest();
        HttpServletResponse res = ctx.servletResponse();
        ServletContext sc = req.getServletContext();
        Map<String, Object> vars = new HashMap<>();
        vars.put("page", this);
        vars.put("master", pageMaster);
        vars.put("viewState", viewStateValue);
        WebContext wc = new WebContext(webApplication(sc).buildExchange(req, res), Locale.US, vars);
        // TODO(port): Thymeleaf writes template text straight to the given Writer (no own buffering) — verify on 3.1 so render regions interleave (B2 step 8)
        engine(sc).process(templateName, wc, Response.Output());
    }

    // ------------------------------------------------------------------ errors

    static ResponseEndException findEnd(Throwable t) {
        for (Throwable x = t; x != null; x = next(x)) {
            if (x instanceof ResponseEndException r) return r;
        }
        return null;
    }

    private static Throwable next(Throwable t) {
        Throwable n = t instanceof ServletException se && se.getRootCause() != null ? se.getRootCause() : t.getCause();
        return n == t ? null : n;
    }

    /** Strips Thymeleaf / SpEL / reflection wrappers so {@code Application_Error} sees the page's exception. */
    static Throwable unwrap(Throwable t) {
        Throwable x = t;
        while (x != null) {
            String n = x.getClass().getName();
            boolean wrapper = x instanceof InvocationTargetException || x instanceof ServletException
                || n.startsWith("org.thymeleaf.") || n.startsWith("org.springframework.expression.");
            Throwable c = next(x);
            if (!wrapper || c == null) return x;
            x = c;
        }
        return t;
    }

    private void handleError(HttpContext ctx, Throwable error) {
        log.error("Unhandled exception in {}", executionPath, error);
        HttpApplication.reportError(ctx, error, this);
        HttpResponse r = ctx.Response();
        r.Clear();
        r.ContentType("text/html");
        r.RedirectLocation(null);
        int code = error instanceof HttpException he ? he.GetHttpCode() : 500;
        r.StatusCode(code >= 400 ? code : 500);
        String message;
        try {
            message = Exceptions.Message(error);
        } catch (RuntimeException e) {
            message = String.valueOf(error.getMessage());
        }
        String m = HttpUtility.HtmlEncode(message);
        r.Write("<!DOCTYPE html>\r\n<html><head><title>" + m + "</title></head><body>\r\n"
            + "<h1>Server Error in '/' Application.</h1>\r\n<h2><i>" + m + "</i></h2>\r\n</body></html>\r\n");
    }
}
