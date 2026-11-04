package btnet.runtime.web;

/**
 * .NET {@code System.Web.UI.MasterPage}, base of {@code btnet.pages.LoggedIn} /
 * {@code LoggedOut} (RULEBOOK §2B B6). A master is a {@link UserControl} with the naming
 * container ID {@code ctl00}; a page attaches it with
 * {@code public final LoggedIn Master = master(new LoggedIn());}. Its {@code Page_Load} runs
 * right after the page's (B2 step 5); its template is the layout
 * {@code templates/layouts/<Name>.html}, which sees it as {@code ${master}}.
 */
public class MasterPage extends UserControl {
    public MasterPage() {
        super("ctl00");
    }
}
