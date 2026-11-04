package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.DataGridColumn}: {@code HeaderText}, {@code Visible} (an invisible
 * column keeps its cells — {@code dgi.Cells[1].Text} works — but they are not rendered).
 */
public abstract class DataGridColumn<C extends DataGridColumn<C>> {
    private String headerText = "";
    private boolean visible = true;
    private String itemCssClass = "";
    private String headerCssClass = "";

    protected DataGridColumn(String headerText) {
        this.headerText = headerText == null ? "" : headerText;
    }

    @SuppressWarnings("unchecked")
    protected final C self() { return (C) this; }

    public String HeaderText() { return headerText; }

    public C HeaderText(String v) { this.headerText = v == null ? "" : v; return self(); }

    public boolean Visible() { return visible; }

    public C Visible(boolean v) { this.visible = v; return self(); }

    /** {@code <ItemStyle CssClass=…>} of the column. */
    public String ItemStyleCssClass() { return itemCssClass; }

    public C ItemStyleCssClass(String v) { this.itemCssClass = v == null ? "" : v; return self(); }

    /** {@code <HeaderStyle CssClass=…>} of the column. */
    public String HeaderStyleCssClass() { return headerCssClass; }

    public C HeaderStyleCssClass(String v) { this.headerCssClass = v == null ? "" : v; return self(); }

    /** {@code InitializeCell}: header text, or the column's controls for an item. */
    protected void InitializeCell(TableCell cell, int columnIndex, ListItemType itemType, DataGridItem item) {
        if (itemType == ListItemType.Header) {
            cell.Text(headerText.isEmpty() ? "&nbsp;" : headerText);
            if (!headerCssClass.isEmpty()) cell.CssClass(headerCssClass);
        } else if (!itemCssClass.isEmpty()) {
            cell.CssClass(itemCssClass);
        }
    }

    /** Data binding of one item cell (DataGrid.DataBind only). */
    protected void BindCell(TableCell cell, DataGridItem item, Object dataItem) {}
}
