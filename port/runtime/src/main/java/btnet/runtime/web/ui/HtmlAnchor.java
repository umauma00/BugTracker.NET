package btnet.runtime.web.ui;

/** {@code System.Web.UI.HtmlControls.HtmlAnchor}: {@code <a runat="server">}. */
public class HtmlAnchor extends HtmlContainerControl<HtmlAnchor> {
    public HtmlAnchor(String id) {
        super(id, "a");
    }

    public String HRef() { return attr("href"); }

    public HtmlAnchor HRef(String value) { return attr("href", value); }

    public String Target() { return attr("target"); }

    public HtmlAnchor Target(String value) { return attr("target", value); }

    public String Title() { return attr("title"); }

    public HtmlAnchor Title(String value) { return attr("title", value); }

    public String Name() { return attr("name"); }

    public HtmlAnchor Name(String value) { return attr("name", value); }

    @Override
    protected void AddAttributesToRender(java.util.Map<String, String> a) {
        super.AddAttributesToRender(a);
        String href = Attributes().get("href");
        if (href != null) a.put("href", Html.resolveUrl(href));
    }
}
