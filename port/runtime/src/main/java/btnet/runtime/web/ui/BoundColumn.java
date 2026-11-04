package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.BoundColumn}: {@code <asp:BoundColumn HeaderText="File" DataField="file"/>}
 * → {@code new BoundColumn("File", "file")}. The cell text is {@code Convert.ToString(value)}
 * (or {@code String.Format(DataFormatString, value)}), not encoded, {@code &nbsp;} when empty (.NET).
 */
public class BoundColumn extends DataGridColumn<BoundColumn> {
    private String dataField;
    private String dataFormatString = "";
    private boolean readOnly;

    public BoundColumn(String headerText, String dataField) {
        super(headerText);
        this.dataField = dataField == null ? "" : dataField;
    }

    public BoundColumn() {
        this("", "");
    }

    public String DataField() { return dataField; }

    public BoundColumn DataField(String v) { this.dataField = v == null ? "" : v; return this; }

    public String DataFormatString() { return dataFormatString; }

    public BoundColumn DataFormatString(String v) { this.dataFormatString = v == null ? "" : v; return this; }

    public boolean ReadOnly() { return readOnly; }

    public BoundColumn ReadOnly(boolean v) { this.readOnly = v; return this; }

    @Override
    protected void BindCell(TableCell cell, DataGridItem item, Object dataItem) {
        Object value = dataField.isEmpty() ? dataItem : DataBinder.eval(dataItem, dataField);
        String text = DataBinder.format(value, dataFormatString);
        cell.Text(text.isEmpty() ? "&nbsp;" : text);
    }
}
