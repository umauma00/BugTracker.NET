package btnet.runtime.web;

/**
 * .NET {@code System.Web.IHtmlString}: markup that {@code <%: %>} /
 * {@link HttpUtility#HtmlEncode(Object)} must not encode (e.g. the result of
 * {@link Scripts#Render(String...)}).
 */
public interface IHtmlString {
    String ToHtmlString();
}
