package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.RadioButton} [J-U1, J-U10]: {@code name} = the unique group name
 * ({@code GroupName}, prefixed by the naming container inside a DataGrid item:
 * {@code MyDataGrid$ctl02$permissions}; {@code UniqueID} when no group), {@code value} =
 * {@code Attributes["value"]} if declared, else {@code ID} (.NET {@code ValueAttribute}). Registered
 * for postback while rendered and enabled: checked = the posted group value equals its value.
 */
public class RadioButton extends WebControl<RadioButton> {
    public RadioButton(String id) {
        super(id);
    }

    public String Text() { return vsString("Text", ""); }

    public RadioButton Text(String v) { vsSet("Text", v); return this; }

    public boolean Checked() { return vsBool("Checked", false); }

    public RadioButton Checked(boolean v) { vsSet("Checked", v); return this; }

    public String GroupName() { return vsString("GroupName", ""); }

    public RadioButton GroupName(String v) { vsSet("GroupName", v); return this; }

    public boolean AutoPostBack() { return vsBool("AutoPostBack", false); }

    public RadioButton AutoPostBack(boolean v) { vsSet("AutoPostBack", v); return this; }

    public String TextAlign() { return vsString("TextAlign", "Right"); }

    public RadioButton TextAlign(String v) { vsSet("TextAlign", v); return this; }

    /** .NET {@code RadioButton.ValueAttribute}. */
    public String ValueAttribute() {
        String v = Attributes().get("value");
        if (v != null) return v;
        return ID() != null ? ID() : UniqueID();
    }

    /** .NET {@code RadioButton.UniqueGroupName}. */
    public String UniqueGroupName() {
        String g = GroupName();
        String uid = UniqueID();
        if (g.isEmpty()) return uid;
        int last = uid == null ? -1 : uid.lastIndexOf('$');
        return last >= 0 ? uid.substring(0, last + 1) + g : g;
    }

    @Override
    public boolean RequiresPostBackRegistration() { return IsEnabled(); }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = post.get(UniqueGroupName());
        if (v == null && !registered) return false;
        Checked(v != null && v.equals(ValueAttribute()));
        return false;
    }

    @Override
    protected String TagName() { return "input"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        inputAttributes(a);
    }

    private void inputAttributes(Map<String, String> a) {
        a.put("id", ClientID());
        a.put("type", "radio");
        a.put("name", UniqueGroupName());
        a.put("value", ValueAttribute());
        a.put("checked", Checked() ? "checked" : null);
        a.put("disabled", IsEnabled() ? null : "disabled");
        if (AutoPostBack()) {
            a.put("onclick", Html.mergeScript(Attributes().get("onclick"), Html.postBack(UniqueID(), "")));
        }
    }

    @Override
    public void RenderControl(StringBuilder sb) {
        if (!Visible()) return;
        Map<String, String> outer = new LinkedHashMap<>();
        for (String[] e : Attributes().entries()) {
            if (!"value".equalsIgnoreCase(e[0])) outer.put(e[0].toLowerCase(java.util.Locale.ROOT), e[1]);
        }
        if (!CssClass().isEmpty()) outer.put("class", CssClass());
        if (!ToolTip().isEmpty()) outer.put("title", ToolTip());
        String st = RenderStyle();
        if (st != null) outer.put("style", st);
        if (!IsEnabled()) outer.put("disabled", "disabled");
        boolean wrap = !outer.isEmpty();
        if (wrap) Html.openTag(sb, "span", outer, false);
        Map<String, String> in = new LinkedHashMap<>();
        inputAttributes(in);
        String label = Text().isEmpty() ? "" : "<label for=\"" + Html.attr(ClientID()) + "\">" + Text() + "</label>";
        if ("Left".equalsIgnoreCase(TextAlign())) sb.append(label);
        Html.openTag(sb, "input", in, true);
        if (!"Left".equalsIgnoreCase(TextAlign())) sb.append(label);
        if (wrap) sb.append("</span>");
    }
}
