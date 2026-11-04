package btnet.runtime.web.ui;

/** {@code System.Web.UI.HtmlControls.HtmlImage}: {@code <img runat="server">}. */
public class HtmlImage extends HtmlControl<HtmlImage> {
    public HtmlImage(String id) {
        super(id, "img");
    }

    public String Src() { return attr("src"); }

    public HtmlImage Src(String v) { return attr("src", v); }

    public String Alt() { return attr("alt"); }

    public HtmlImage Alt(String v) { return attr("alt", v); }

    public String Align() { return attr("align"); }

    public HtmlImage Align(String v) { return attr("align", v); }

    public int Border() { return intAttr("border"); }

    public HtmlImage Border(int v) { Attributes().set("border", v == -1 ? null : Integer.toString(v)); return this; }

    public int Width() { return intAttr("width"); }

    public HtmlImage Width(int v) { Attributes().set("width", v == -1 ? null : Integer.toString(v)); return this; }

    public int Height() { return intAttr("height"); }

    public HtmlImage Height(int v) { Attributes().set("height", v == -1 ? null : Integer.toString(v)); return this; }

    private int intAttr(String n) {
        String v = Attributes().get(n);
        return v == null ? -1 : btnet.runtime.Int32.Parse(v);
    }

    @Override
    protected void AddAttributesToRender(java.util.Map<String, String> a) {
        super.AddAttributesToRender(a);
        String src = Attributes().get("src");
        if (src != null) a.put("src", Html.resolveUrl(src));
    }
}
