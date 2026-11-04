package btnet.runtime.web;

import btnet.runtime.EventArgs;
import btnet.runtime.security.IPrincipal;
import btnet.runtime.web.ui.Control;

/**
 * .NET {@code System.Web.UI.UserControl}, base of the translated {@code .ascx} classes in
 * {@code btnet.Controls} and (via {@link MasterPage}) of the masters (RULEBOOK §2B B6 [S1-26]).
 *
 * <p>{@code UserControl(String id)} is the only constructor; the host creates it like any
 * control, {@code control(new MainMenu("MainMenu"))}. Its own controls are created with the
 * inherited {@code Control.control(c)} in its field initializers (they become its
 * {@code Controls()}; a user control is not a naming container, names stay flat). The intrinsics ({@code Request},
 * {@code Response}, {@code Server}, {@code Session}, {@code Application}, {@code Context},
 * {@code Cache}, {@code User}, {@code IsPostBack}, {@code Page}) are fields bound by the
 * hosting {@link Page} before {@code OnPreInit}; {@code Page_Init}/{@code Page_Load} are
 * called by the page lifecycle (B2 step 4/5).
 */
public class UserControl extends Control<UserControl> {
    public HttpRequest Request;
    public HttpResponse Response;
    public HttpServerUtility Server;
    public HttpSessionState Session;
    public HttpApplicationState Application;
    public HttpContext Context;
    public Cache Cache;
    public IPrincipal User;
    public boolean IsPostBack;
    public Page Page;

    public UserControl(String id) {
        super(id);
    }

    /** Binds the intrinsics of {@code page} into this control (the page visits every user control). */
    void bindTo(Page page) {
        this.Page = page;
        this.Request = page.Request;
        this.Response = page.Response;
        this.Server = page.Server;
        this.Session = page.Session;
        this.Application = page.Application;
        this.Context = page.Context;
        this.Cache = page.Cache;
        this.User = page.User;
        this.IsPostBack = page.IsPostBack;
    }

    /** AutoEventWireup {@code Page_Init}; overridden by translated code. */
    protected void Page_Init(Object sender, EventArgs e) {
    }

    /** AutoEventWireup {@code Page_Load}; overridden by translated code. */
    protected void Page_Load(Object sender, EventArgs e) {
    }

    void runInit() {
        Page_Init(this, EventArgs.Empty);
    }

    void runLoad() {
        Page_Load(this, EventArgs.Empty);
    }

    /** {@code ResolveUrl("~/x")} → {@code "/x"}. */
    public String ResolveUrl(String url) {
        return btnet.runtime.web.Page.resolveUrlStatic(url);
    }

    public String MapPath(String path) {
        return Server.MapPath(path);
    }
}
