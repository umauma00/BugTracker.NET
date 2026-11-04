package btnet.runtime.web;

/**
 * .NET {@code System.Web.UI.IPostBackEventHandler}: how {@link Page} raises postback events
 * (RULEBOOK §2B B2 step 6).
 *
 * <p>After {@code Page_Load} on a postback, {@link Page} picks one source, as ASP.NET:
 * (1) the first registered handler whose {@code ClientID()} is a key of the postback
 * collection (a submit button: {@code asp:Button}, {@code HtmlInputSubmit} with
 * {@code OnServerClick}); else (2) the handler named by {@code __EVENTTARGET}: the control
 * whose {@code ClientID()} equals it, or — for a naming container such as a DataGrid
 * ({@code MyDataGrid$ctl02$ctl00}) — the control with the longest {@code ClientID()} such that
 * the target starts with {@code ClientID() + "$"}. It then calls
 * {@link #RaisePostBackEvent(String, String)} with the full target and
 * {@code __EVENTARGUMENT} (case (1): the button's ClientID and {@code null}).
 */
public interface IPostBackEventHandler {
    void RaisePostBackEvent(String eventArgument);

    /** Naming-container form; the default ignores the target path. */
    default void RaisePostBackEvent(String eventTarget, String eventArgument) {
        RaisePostBackEvent(eventArgument);
    }
}
