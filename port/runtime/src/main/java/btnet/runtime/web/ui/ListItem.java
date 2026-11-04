package btnet.runtime.web.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code System.Web.UI.WebControls.ListItem}. {@code Text} falls back to {@code Value} and vice versa
 * ({@code ""} when both unset); {@code equals} compares {@code Value} and {@code Text} (.NET
 * {@code ListItem.Equals}, used by {@code Items.Remove(item)}); {@code toString()} = {@code Text}
 * ({@code <%= mode.SelectedItem %>}).
 */
public final class ListItem {
    private String text;
    private String value;
    private boolean selected;
    private boolean enabled = true;
    private AttributeCollection attributes;

    public ListItem() {
        this(null, null, true);
    }

    public ListItem(String text) {
        this(text, null, true);
    }

    public ListItem(String text, String value) {
        this(text, value, true);
    }

    public ListItem(String text, String value, boolean enabled) {
        this.text = text;
        this.value = value;
        this.enabled = enabled;
    }

    /** {@code ListItem.FromString(s)}. */
    public static ListItem FromString(String s) {
        return new ListItem(s);
    }

    public String Text() {
        if (text != null) return text;
        return value != null ? value : "";
    }

    public ListItem Text(String v) { this.text = v; return this; }

    public String Value() {
        if (value != null) return value;
        return text != null ? text : "";
    }

    public ListItem Value(String v) { this.value = v; return this; }

    public boolean Selected() { return selected; }

    public ListItem Selected(boolean v) { this.selected = v; return this; }

    public boolean Enabled() { return enabled; }

    public ListItem Enabled(boolean v) { this.enabled = v; return this; }

    public AttributeCollection Attributes() {
        if (attributes == null) attributes = new AttributeCollection(new CssStyleCollection());
        return attributes;
    }

    boolean hasAttributes() { return attributes != null && attributes.Count() > 0; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ListItem other)) return false;
        return Value().equals(other.Value()) && Text().equals(other.Text());
    }

    @Override
    public int hashCode() {
        return Objects.hash(Value(), Text());
    }

    @Override
    public String toString() {
        return Text();
    }

    Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", text);
        m.put("v", value);
        if (selected) m.put("s", true);
        if (!enabled) m.put("e", false);
        if (hasAttributes()) m.put("a", attributes.save());
        return m;
    }

    static ListItem load(Map<?, ?> m) {
        ListItem li = new ListItem((String) m.get("t"), (String) m.get("v"));
        li.selected = Boolean.TRUE.equals(m.get("s"));
        li.enabled = !Boolean.FALSE.equals(m.get("e"));
        if (m.containsKey("a")) li.Attributes().load(m.get("a"));
        return li;
    }
}
