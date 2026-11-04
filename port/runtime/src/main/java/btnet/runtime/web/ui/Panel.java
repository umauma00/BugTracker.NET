package btnet.runtime.web.ui;

/** {@code System.Web.UI.WebControls.Panel}: {@code <div>}; the template keeps its content. */
public class Panel extends WebControl<Panel> {
    public Panel(String id) {
        super(id);
    }

    public String GroupingText() { return vsString("GroupingText", ""); }

    public Panel GroupingText(String v) { vsSet("GroupingText", v); return this; }

    public String DefaultButton() { return vsString("DefaultButton", ""); }

    public Panel DefaultButton(String v) { vsSet("DefaultButton", v); return this; }

    @Override
    protected String TagName() { return "div"; }
}
