package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;
import btnet.runtime.web.HttpException;

import java.util.List;
import java.util.Map;

/**
 * {@code System.Web.UI.HtmlControls.HtmlSelect}: {@code <select runat="server">}. Items persist in
 * view state (B4 (a)); posted values select by value (an unknown value fails event validation → 500).
 */
public class HtmlSelect extends HtmlContainerControl<HtmlSelect> {
    private final ListItemCollection items = new ListItemCollection();
    private Object dataSource;

    public HtmlSelect(String id) {
        super(id, "select");
    }

    public ListItemCollection Items() { return items; }

    public boolean Multiple() { return Attributes().get("multiple") != null; }

    public HtmlSelect Multiple(boolean v) { Attributes().set("multiple", v ? "multiple" : null); return this; }

    public int Size() { String v = Attributes().get("size"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlSelect Size(int v) { Attributes().set("size", v == -1 ? null : Integer.toString(v)); return this; }

    public String Name() { return UniqueID(); }

    /** {@code SelectedIndex} getter: first selected; a single-row, single-select list selects item 0 (.NET). */
    public int SelectedIndex() {
        for (int i = 0; i < items.Count(); i++) if (items.get(i).Selected()) return i;
        if (Size() <= 1 && !Multiple()) {
            if (items.Count() > 0) items.get(0).Selected(true);
            return 0;
        }
        return -1;
    }

    public HtmlSelect SelectedIndex(int value) {
        if (items.Count() == 0) { vsSet("cachedSelectedIndex", value); return this; }
        if (value < -1 || value >= items.Count()) {
            throw new btnet.runtime.ArgumentOutOfRangeException("value",
                    "Specified argument was out of the range of valid values.");
        }
        items.ClearSelection();
        if (value >= 0) items.get(value).Selected(true);
        return this;
    }

    /** {@code Value} getter: the selected item's value, {@code ""} when none. */
    public String Value() {
        int i = SelectedIndex();
        return i < 0 || i >= items.Count() ? "" : items.get(i).Value();
    }

    /** {@code Value} setter: selects the item with that value; ignored when there is none (.NET). */
    public HtmlSelect Value(String value) {
        int i = items.IndexOfValue(value);
        if (i >= 0) SelectedIndex(i);
        return this;
    }

    public Object DataSource() { return dataSource; }

    public HtmlSelect DataSource(Object v) { this.dataSource = v; return this; }

    public String DataTextField() { return vsString("DataTextField", ""); }

    public HtmlSelect DataTextField(String v) { vsSet("DataTextField", v); return this; }

    public String DataValueField() { return vsString("DataValueField", ""); }

    public HtmlSelect DataValueField(String v) { vsSet("DataValueField", v); return this; }

    @Override
    protected void OnDataBinding() {
        Iterable<?> data = DataBinder.resolve(dataSource);
        if (data == null) return;
        items.Clear();
        items.AddBound(data, DataTextField(), DataValueField(), null);
        Object cached = vsGet("cachedSelectedIndex");
        if (cached instanceof Number n && n.intValue() >= 0) {
            vsRemove("cachedSelectedIndex");
            SelectedIndex(n.intValue());
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

    @Override
    public boolean RequiresPostBackRegistration() {
        return !Disabled() && Size() > 1;
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String[] values = post.GetValues(UniqueID());
        if (values == null) {
            if (registered) items.ClearSelection();
            return false;
        }
        if (!Multiple()) {
            int n = items.IndexOfValue(values[0]);
            if (n < 0) throw invalidPostback();
            items.ClearSelection();
            items.get(n).Selected(true);
        } else {
            items.ClearSelection();
            for (String v : values) {
                int n = items.IndexOfValue(v);
                if (n < 0) throw invalidPostback();
                items.get(n).Selected(true);
            }
        }
        return false;
    }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("name", UniqueID());
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        if (!Multiple()) {
            int selected = 0;
            for (ListItem li : items) if (li.Selected()) selected++;
            if (selected > 1) {
                // TODO(port): confirm the .NET 4.5 resource text of HtmlSelect_Cant_Multi_Select
                throw new HttpException("Cannot have multiple items selected in a DropDownList.");
            }
        }
        return items.renderOptions();
    }

    List<ListItem> itemList() { return items.asList(); }
}
