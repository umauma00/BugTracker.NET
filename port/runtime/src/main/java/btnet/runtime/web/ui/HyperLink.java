package btnet.runtime.web.ui;

import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.HyperLink}: {@code <a href="NavigateUrl" target="…">Text</a>};
 * no {@code href} while disabled or when {@code NavigateUrl} is empty; {@code ~/x} resolves to {@code /x}.
 */
public class HyperLink extends WebControl<HyperLink> {
    public HyperLink(String id) {
        super(id);
    }

    public HyperLink() {
        super(null);
    }

    public String NavigateUrl() { return vsString("NavigateUrl", ""); }

    public HyperLink NavigateUrl(String v) { vsSet("NavigateUrl", v); return this; }

    public String Target() { return vsString("Target", ""); }

    public HyperLink Target(String v) { vsSet("Target", v); return this; }

    public String Text() { return vsString("Text", ""); }

    public HyperLink Text(String v) { vsSet("Text", v); return this; }

    public String ImageUrl() { return vsString("ImageUrl", ""); }

    public HyperLink ImageUrl(String v) { vsSet("ImageUrl", v); return this; }

    @Override
    protected String TagName() { return "a"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        String url = NavigateUrl();
        a.put("href", IsEnabled() && !url.isEmpty() ? Html.resolveUrl(url) : null);
        if (!Target().isEmpty()) a.put("target", Target());
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        if (!ImageUrl().isEmpty()) {
            return "<img src=\"" + Html.attr(Html.resolveUrl(ImageUrl())) + "\" alt=\"" + Html.attr(Text()) + "\" style=\"border-width:0px;\" />";
        }
        return vsHas("Text") ? Text() : null;
    }
}
