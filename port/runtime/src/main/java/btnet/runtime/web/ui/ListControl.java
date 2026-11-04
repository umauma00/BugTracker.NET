package btnet.runtime.web.ui;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentOutOfRangeException;

import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.ListControl} [J-U18]: base of {@link DropDownList}, {@link ListBox},
 * {@link CheckBoxList}, {@link RadioButtonList}. Items persist in view state (B4 (a)) with their
 * selection. {@code SelectedValue(v)}: if items exist, selects the item with that value (none →
 * {@code ArgumentOutOfRangeException("'<ID>' has a SelectedValue which is invalid because it does not
 * exist in the list of items.\r\nParameter name: value")}); the value is always cached and re-applied
 * by {@link #DataBind()} after the items are rebuilt (same exception when missing).
 */
public abstract class ListControl<T extends ListControl<T>> extends WebControl<T> {
    private final ListItemCollection items = new ListItemCollection();
    private Object dataSource;
    private String cachedSelectedValue;
    private int cachedSelectedIndex = -1;

    protected ListControl(String id) {
        super(id);
    }

    public ListItemCollection Items() { return items; }

    public boolean AutoPostBack() { return vsBool("AutoPostBack", false); }

    public T AutoPostBack(boolean v) { vsSet("AutoPostBack", v); return self(); }

    public Object DataSource() { return dataSource; }

    public T DataSource(Object v) { this.dataSource = v; return self(); }

    public String DataTextField() { return vsString("DataTextField", ""); }

    public T DataTextField(String v) { vsSet("DataTextField", v == null ? "" : v); return self(); }

    public String DataValueField() { return vsString("DataValueField", ""); }

    public T DataValueField(String v) { vsSet("DataValueField", v == null ? "" : v); return self(); }

    public String DataTextFormatString() { return vsString("DataTextFormatString", ""); }

    public T DataTextFormatString(String v) { vsSet("DataTextFormatString", v == null ? "" : v); return self(); }

    public boolean AppendDataBoundItems() { return vsBool("AppendDataBoundItems", false); }

    public T AppendDataBoundItems(boolean v) { vsSet("AppendDataBoundItems", v); return self(); }

    /** {@code SelectedIndex} getter: index of the first selected item, {@code -1} when none. */
    public int SelectedIndex() {
        for (int i = 0; i < items.Count(); i++) if (items.get(i).Selected()) return i;
        return -1;
    }

    /** {@code SelectedIndex} setter (.NET 4.5 semantics incl. caching for a later DataBind). */
    public T SelectedIndex(int value) {
        if (value < -1) {
            if (items.Count() == 0) value = -1;
            else throw new ArgumentOutOfRangeException("value", rangeMessage("SelectedIndex"));
        }
        if ((items.Count() != 0 && value < items.Count()) || value == -1) {
            items.ClearSelection();
            if (value >= 0) items.get(value).Selected(true);
        }
        cachedSelectedIndex = value;
        return self();
    }

    public ListItem SelectedItem() {
        int i = SelectedIndex();
        return i >= 0 ? items.get(i) : null;
    }

    /** {@code SelectedValue} getter: the selected item's value, {@code ""} when none. */
    public String SelectedValue() {
        ListItem li = SelectedItem();
        return li == null ? "" : li.Value();
    }

    /** {@code SelectedValue} setter [J-U18]. */
    public T SelectedValue(String value) {
        if (items.Count() != 0) {
            if (value == null) {
                items.ClearSelection();
                cachedSelectedValue = null;
                return self();
            }
            ListItem li = items.FindByValue(value);
            if (li == null) throw new ArgumentOutOfRangeException("value", rangeMessage("SelectedValue"));
            items.ClearSelection();
            li.Selected(true);
        }
        cachedSelectedValue = value;
        return self();
    }

    /** {@code Text} = {@code SelectedValue}. */
    public String Text() { return SelectedValue(); }

    public T Text(String value) { return SelectedValue(value); }

    public void ClearSelection() { items.ClearSelection(); }

    private String rangeMessage(String property) {
        return "'" + ID() + "' has a " + property + " which is invalid because it does not exist in the list of items.";
    }

    /** {@code ListControl.PerformDataBinding} (.NET 4.5). */
    @Override
    protected void OnDataBinding() {
        Iterable<?> data = DataBinder.resolve(dataSource);
        if (data != null) {
            if (!AppendDataBoundItems()) items.Clear();
            items.AddBound(data, DataTextField(), DataValueField(), DataTextFormatString());
        }
        if (cachedSelectedValue != null) {
            int idx = items.IndexOfValue(cachedSelectedValue);
            if (idx == -1) throw new ArgumentOutOfRangeException("value", rangeMessage("SelectedValue"));
            if (cachedSelectedIndex != -1 && cachedSelectedIndex != idx) {
                throw new ArgumentException("SelectedIndex and SelectedValue are mutually exclusive.");
            }
            SelectedIndex(idx);
            cachedSelectedValue = null;
            cachedSelectedIndex = -1;
        } else if (cachedSelectedIndex != -1) {
            if (cachedSelectedIndex > items.Count() - 1) {
                throw new ArgumentOutOfRangeException("value", rangeMessage("SelectedIndex"));
            }
            SelectedIndex(cachedSelectedIndex);
            cachedSelectedIndex = -1;
        }
    }

    @Override
    public Map<String, Object> SaveViewState() {
        Map<String, Object> m = super.SaveViewState();
        m.put("items", items.save());
        return m;
    }

    @Override
    public void LoadViewState(Map<String, ?> saved) {
        super.LoadViewState(saved);
        if (saved != null && saved.containsKey("items")) items.load(saved.get("items"));
    }

    /** Select-element attributes: {@code name}, and {@code onchange} = declared script, then the AutoPostBack call (B5 [J-U8]). */
    protected void selectAttributes(Map<String, String> a) {
        a.put("name", UniqueID());
        String declared = Attributes().get("onchange");
        String onchange = AutoPostBack() ? Html.mergeScript(declared, Html.postBack(UniqueID(), "")) : declared;
        a.put("onchange", onchange == null || onchange.isEmpty() ? null : onchange);
    }

    /** Index of the item with {@code value} among enabled items ({@code FindByValueInternal(v, false)}). */
    protected int indexOfEnabledValue(String value) {
        if (value == null) return -1;
        for (int i = 0; i < items.Count(); i++) {
            ListItem li = items.get(i);
            if (li.Enabled() && li.Value().equals(value)) return i;
        }
        return -1;
    }

    protected int selectedCount() {
        int n = 0;
        for (ListItem li : items) if (li.Selected()) n++;
        return n;
    }
}
