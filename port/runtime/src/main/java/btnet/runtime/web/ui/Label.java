package btnet.runtime.web.ui;

/** {@code System.Web.UI.WebControls.Label} [J-U10]: {@code <span>}; {@code Text} → inner HTML (raw, as .NET). */
public class Label extends WebControl<Label> {
    public Label(String id) {
        super(id);
    }

    public String Text() { return vsString("Text", ""); }

    public Label Text(String v) { vsSet("Text", v); return this; }

    @Override
    protected String TagName() { return "span"; }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        return vsHas("Text") ? Text() : null;
    }
}
