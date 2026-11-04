package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlHead} ({@code Page.Header}, B3): {@code Title()} /
 * {@code Title(v)}; {@code Page.Header.Title += x} → {@code Header.Title(Header.Title() + Strings.cs(x))}.
 * {@code Title()} is {@code ""} until set; layouts render the master's literal {@code btnet bugs} then.
 */
public class HtmlHead extends HtmlContainerControl<HtmlHead> {
    public HtmlHead(String id) {
        super(id, "head");
    }

    public HtmlHead() {
        this(null);
    }

    public String Title() {
        String v = vsString("Title", null);
        return v == null ? "" : v;
    }

    public HtmlHead Title(String value) { vsSet("Title", value); return this; }

    /** Whether code set a title (layouts fall back to the master's literal title otherwise). */
    public boolean HasTitle() { return vsGet("Title") != null; }
}
