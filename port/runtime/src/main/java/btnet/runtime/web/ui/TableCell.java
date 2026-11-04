package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.TableCell} ({@code DataGridItem.Cells[i]}): {@code Text()} is the
 * cell text (raw HTML, as .NET; a BoundColumn's empty value is {@code &nbsp;}). Renders {@code <td>}
 * with its text, or its child controls when it has some.
 */
public class TableCell extends WebControl<TableCell> {
    private boolean header;

    public TableCell() {
        super(null);
    }

    public String Text() { return vsString("Text", ""); }

    public TableCell Text(String v) { vsSet("Text", v); return this; }

    public int ColumnSpan() { return vsInt("ColumnSpan", 0); }

    public TableCell ColumnSpan(int v) { vsSet("ColumnSpan", v); return this; }

    TableCell header(boolean h) { this.header = h; return this; }

    @Override
    boolean wantsAutomaticId() { return false; }

    @Override
    protected String TagName() { return header ? "th" : "td"; }

    @Override
    protected void AddAttributesToRender(java.util.Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.remove("id");
        if (ColumnSpan() > 0) a.put("colspan", Integer.toString(ColumnSpan()));
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        return HasControls() ? null : Text();
    }
}
