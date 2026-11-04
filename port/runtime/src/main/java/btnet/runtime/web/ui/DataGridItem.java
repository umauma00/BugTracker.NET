package btnet.runtime.web.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.DataGridItem} [J-U1]: one grid row, a naming container with ID
 * {@code ctl<NN>} (header {@code ctl01}, first item {@code ctl02}), so its controls are named
 * {@code <GridID>$ctl<NN>$<ID>} / id {@code <GridID>_ctl<NN>_<ID>}. {@code Cells()} holds one
 * {@link TableCell} per column ({@code dgi.Cells[1].Text} → {@code dgi.Cells().get(1).Text()});
 * {@code FindControl(id)} finds a TemplateColumn control of this row. {@link #Add(Control)} adds a
 * control to the cell being initialized (the TemplateColumn item factory's {@code item.Add(none)}).
 */
public class DataGridItem extends WebControl<DataGridItem> {
    private final int itemIndex;
    private final ListItemType itemType;
    private Object dataItem;
    private TableCell currentCell;

    public DataGridItem(int itemIndex, int dataSetIndex, ListItemType itemType) {
        super(null);
        this.itemIndex = itemIndex;
        this.itemType = itemType;
    }

    public int ItemIndex() { return itemIndex; }

    public int DataSetIndex() { return itemIndex; }

    public ListItemType ItemType() { return itemType; }

    /** {@code DataItem}: the bound row ({@code DataRowView}) during DataBind, {@code null} after a postback. */
    public Object DataItem() { return dataItem; }

    public DataGridItem DataItem(Object v) { this.dataItem = v; return this; }

    /** {@code Cells}. */
    public List<TableCell> Cells() {
        List<TableCell> l = new ArrayList<>();
        for (Control<?> c : Controls()) if (c instanceof TableCell tc) l.add(tc);
        return Collections.unmodifiableList(l);
    }

    /** Adds {@code c} to the cell being initialized (else to the row). */
    public void Add(Control<?> c) {
        if (currentCell != null) currentCell.Controls().Add(c); else Controls().Add(c);
    }

    void currentCell(TableCell cell) { this.currentCell = cell; }

    @Override
    public boolean IsNamingContainer() { return true; }

    /** Items are created as a DataGrid's children with explicit IDs; they never take an automatic one. */
    @Override
    boolean wantsAutomaticId() { return false; }

    @Override
    protected String TagName() { return "tr"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.remove("id");
    }

    /** {@code DataGridItem.OnBubbleEvent}: wraps a command into {@link DataGridCommandEventArgs} and bubbles it. */
    @Override
    protected boolean OnBubbleEvent(Object source, btnet.runtime.EventArgs args) {
        if (args instanceof CommandEventArgs ce && !(args instanceof DataGridCommandEventArgs)) {
            RaiseBubbleEvent(this, new DataGridCommandEventArgs(this, source, ce));
            return true;
        }
        return false;
    }

    /** Renders {@code <tr>} with the cells of the visible columns only. */
    void render(StringBuilder sb, List<DataGridColumn<?>> columns, String cssClass) {
        if (!Visible()) return;
        Map<String, String> a = new java.util.LinkedHashMap<>();
        AddAttributesToRender(a);
        if (cssClass != null && !cssClass.isEmpty() && !a.containsKey("class")) a.put("class", cssClass);
        Html.openTag(sb, "tr", a, false);
        List<TableCell> cells = Cells();
        for (int i = 0; i < cells.size(); i++) {
            if (i < columns.size() && !columns.get(i).Visible()) continue;
            sb.append("<td");
            TableCell cell = cells.get(i);
            Map<String, String> ca = new java.util.LinkedHashMap<>();
            cell.AddAttributesToRender(ca);
            for (var e : ca.entrySet()) {
                if (e.getValue() != null) sb.append(' ').append(e.getKey()).append("=\"").append(Html.attr(e.getValue())).append('"');
            }
            sb.append('>');
            String inner = cell.RenderInnerHtml(null);
            if (inner != null) sb.append(inner); else cell.RenderChildren(sb);
            sb.append("</td>");
        }
        sb.append("</tr>");
    }
}
