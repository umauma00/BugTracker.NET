package btnet.runtime.web;

import btnet.runtime.NameValueCollection;

/**
 * .NET {@code System.Web.UI.IPostBackDataHandler}: how {@link Page} applies posted values
 * (RULEBOOK §2B B4 [J-U5, J-U11]).
 *
 * <p>On postback, after view state is loaded, {@link Page} calls
 * {@link #LoadPostData(String, NameValueCollection)} on every registered control that
 * implements this interface, passing the control's {@code ClientID()} and the postback
 * collection ({@code Request.Form} for POST, {@code Request.QueryString} for GET). The
 * control decides from its own names (ID, GroupName, {@code x$<i>}, grid item names)
 * whether anything applies — B4: posted values apply only to controls whose key is present,
 * and to checkboxes/radios recorded in view state as rendered visible and enabled (absent
 * name = unchecked). It returns {@code true} if its value changed; those controls get
 * {@link #RaisePostDataChangedEvent()} after {@code Page_Load}.
 */
public interface IPostBackDataHandler {
    boolean LoadPostData(String postDataKey, NameValueCollection postCollection);

    default void RaisePostDataChangedEvent() {
    }
}
