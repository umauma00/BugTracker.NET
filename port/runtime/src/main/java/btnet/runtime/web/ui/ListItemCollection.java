package btnet.runtime.web.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code System.Web.UI.WebControls.ListItemCollection}: {@code x.Items[i]} → {@code x.Items().get(i)},
 * {@code Items.Count} → {@code Count()}, {@code foreach (ListItem li in x.Items)} →
 * {@code for (ListItem li : x.Items())}. {@code FindByValue}/{@code FindByText} are ordinal and
 * return {@code null} when absent; index errors throw {@code ArgumentOutOfRangeException} with the
 * .NET {@code ArrayList} messages.
 */
public final class ListItemCollection implements Iterable<ListItem> {
    private final ArrayList<ListItem> items = new ArrayList<>();

    public ListItemCollection() {}

    public ListItem get(int index) {
        if (index < 0 || index >= items.size()) {
            throw new btnet.runtime.ArgumentOutOfRangeException("index",
                    "Index was out of range. Must be non-negative and less than the size of the collection.");
        }
        return items.get(index);
    }

    public int Count() { return items.size(); }

    public void Add(ListItem item) { items.add(item); }

    public void Add(String item) { items.add(new ListItem(item)); }

    public void AddRange(ListItem[] range) {
        if (range == null) throw new btnet.runtime.ArgumentNullException("items");
        Collections.addAll(items, range);
    }

    public void Insert(int index, ListItem item) {
        if (index < 0 || index > items.size()) {
            throw new btnet.runtime.ArgumentOutOfRangeException("index",
                    "Insertion index was out of range. Must be non-negative and less than or equal to size.");
        }
        items.add(index, item);
    }

    public void Insert(int index, String item) { Insert(index, new ListItem(item)); }

    /** {@code Remove(ListItem)}: removes the first item {@code Equals} to it (Value and Text). */
    public void Remove(ListItem item) {
        int i = IndexOf(item);
        if (i >= 0) items.remove(i);
    }

    /** {@code Remove(string)} = {@code Remove(new ListItem(s))}. */
    public void Remove(String item) { Remove(new ListItem(item)); }

    public void RemoveAt(int index) {
        get(index);
        items.remove(index);
    }

    public void Clear() { items.clear(); }

    public boolean Contains(ListItem item) { return IndexOf(item) >= 0; }

    public int IndexOf(ListItem item) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).equals(item)) return i;
        return -1;
    }

    public ListItem FindByValue(String value) {
        int i = IndexOfValue(value);
        return i < 0 ? null : items.get(i);
    }

    public ListItem FindByText(String text) {
        for (ListItem li : items) if (li.Text().equals(text)) return li;
        return null;
    }

    public Stream<ListItem> stream() { return items.stream(); }

    @Override
    public Iterator<ListItem> iterator() { return items.iterator(); }

    // ---- runtime internals ----

    int IndexOfValue(String value) {
        if (value == null) return -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).Value().equals(value)) return i;
        return -1;
    }

    void ClearSelection() {
        for (ListItem li : items) li.Selected(false);
    }

    List<ListItem> asList() { return Collections.unmodifiableList(items); }

    /**
     * .NET {@code ListControl.PerformDataBinding} item creation: with a text or value field, each
     * named field via {@code DataBinder.GetPropertyValue} (text formatted with {@code format}); without
     * fields, {@code Text} = {@code Value} = the item's string (formatted when a format is given).
     */
    void AddBound(Iterable<?> data, String textField, String valueField, String format) {
        boolean fields = !textField.isEmpty() || !valueField.isEmpty();
        boolean hasFormat = format != null && !format.isEmpty();
        for (Object o : data) {
            ListItem li = new ListItem();
            if (fields) {
                if (!textField.isEmpty()) li.Text(DataBinder.evalString(o, textField, format));
                if (!valueField.isEmpty()) li.Value(DataBinder.evalString(o, valueField, null));
            } else {
                String s = hasFormat ? btnet.runtime.Strings.Format(format, o) : btnet.runtime.Convert.ToString(o);
                li.Text(s);
                li.Value(btnet.runtime.Convert.ToString(o));
            }
            items.add(li);
        }
    }

    List<Object> save() {
        List<Object> out = new ArrayList<>();
        for (ListItem li : items) out.add(li.save());
        return out;
    }

    void load(Object saved) {
        items.clear();
        if (saved instanceof List<?> l) {
            for (Object o : l) if (o instanceof Map<?, ?> m) items.add(ListItem.load(m));
        }
    }

    /** {@code <option>} markup of every item (.NET ListControl.RenderContents, 3.5 rendering). */
    String renderOptions() {
        StringBuilder sb = new StringBuilder();
        for (ListItem li : items) {
            sb.append("\r\n\t<option");
            if (li.Selected()) sb.append(" selected=\"selected\"");
            sb.append(" value=\"").append(Html.attr(li.Value())).append('"');
            if (!li.Enabled()) sb.append(" disabled=\"disabled\"");
            if (li.hasAttributes()) {
                for (String[] e : li.Attributes().entries()) {
                    sb.append(' ').append(e[0]).append("=\"").append(Html.attr(e[1])).append('"');
                }
                if (li.Attributes().CssStyle().Count() > 0) {
                    sb.append(" style=\"").append(Html.attr(li.Attributes().CssStyle().Value())).append('"');
                }
            }
            sb.append('>').append(Html.encode(li.Text())).append("</option>");
        }
        if (!items.isEmpty()) sb.append("\r\n");
        return sb.toString();
    }
}
