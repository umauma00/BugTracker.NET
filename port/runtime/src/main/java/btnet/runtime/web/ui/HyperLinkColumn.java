package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.HyperLinkColumn}:
 * {@code <asp:HyperLinkColumn HeaderText="Download" Text="Download" DataNavigateUrlField="url" Target="_blank"/>}
 * → {@code new HyperLinkColumn("Download", "Download", "url", "_blank")}. Each item cell holds a
 * {@link HyperLink} whose {@code NavigateUrl} is bound from the field (formatted by
 * {@code DataNavigateUrlFormatString}).
 */
public class HyperLinkColumn extends DataGridColumn<HyperLinkColumn> {
    private String text;
    private String dataNavigateUrlField;
    private String dataNavigateUrlFormatString = "";
    private String dataTextField = "";
    private String dataTextFormatString = "";
    private String navigateUrl = "";
    private String target;

    public HyperLinkColumn(String headerText, String text, String dataNavigateUrlField, String target) {
        super(headerText);
        this.text = text == null ? "" : text;
        this.dataNavigateUrlField = dataNavigateUrlField == null ? "" : dataNavigateUrlField;
        this.target = target == null ? "" : target;
    }

    public HyperLinkColumn() {
        this("", "", "", "");
    }

    public String Text() { return text; }

    public HyperLinkColumn Text(String v) { this.text = v == null ? "" : v; return this; }

    public String DataNavigateUrlField() { return dataNavigateUrlField; }

    public HyperLinkColumn DataNavigateUrlField(String v) { this.dataNavigateUrlField = v == null ? "" : v; return this; }

    public String DataNavigateUrlFormatString() { return dataNavigateUrlFormatString; }

    public HyperLinkColumn DataNavigateUrlFormatString(String v) { this.dataNavigateUrlFormatString = v == null ? "" : v; return this; }

    public String DataTextField() { return dataTextField; }

    public HyperLinkColumn DataTextField(String v) { this.dataTextField = v == null ? "" : v; return this; }

    public String DataTextFormatString() { return dataTextFormatString; }

    public HyperLinkColumn DataTextFormatString(String v) { this.dataTextFormatString = v == null ? "" : v; return this; }

    public String NavigateUrl() { return navigateUrl; }

    public HyperLinkColumn NavigateUrl(String v) { this.navigateUrl = v == null ? "" : v; return this; }

    public String Target() { return target; }

    public HyperLinkColumn Target(String v) { this.target = v == null ? "" : v; return this; }

    @Override
    protected void InitializeCell(TableCell cell, int columnIndex, ListItemType itemType, DataGridItem item) {
        super.InitializeCell(cell, columnIndex, itemType, item);
        if (itemType != ListItemType.Header && itemType != ListItemType.Footer) {
            HyperLink hl = new HyperLink();
            hl.Text(text);
            hl.NavigateUrl(navigateUrl);
            if (!target.isEmpty()) hl.Target(target);
            cell.Controls().Add(hl);
        }
    }

    @Override
    protected void BindCell(TableCell cell, DataGridItem item, Object dataItem) {
        HyperLink hl = (HyperLink) cell.Controls().get(0);
        if (!dataNavigateUrlField.isEmpty()) {
            hl.NavigateUrl(DataBinder.format(DataBinder.eval(dataItem, dataNavigateUrlField), dataNavigateUrlFormatString));
        }
        if (!dataTextField.isEmpty()) {
            hl.Text(DataBinder.format(DataBinder.eval(dataItem, dataTextField), dataTextFormatString));
        }
    }
}
