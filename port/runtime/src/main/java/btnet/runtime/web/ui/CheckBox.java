package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.CheckBox}. Under {@code bt:control} on the template's
 * {@code <input type="checkbox">} (B5; the label is template markup over {@code Text()}) it renders
 * {@code id}, {@code name}={@code UniqueID}, {@code checked}, {@code disabled}; standalone (inside a
 * grid) it renders the .NET 3.5 markup {@code [<span …>]<input …/><label for="id">Text</label>[</span>]}.
 * Registered for postback while rendered and enabled (B4 (d)): checked = a non-empty value posted
 * under its name, absent = unchecked.
 */
public class CheckBox extends WebControl<CheckBox> {
    public CheckBox(String id) {
        super(id);
    }

    public String Text() { return vsString("Text", ""); }

    public CheckBox Text(String v) { vsSet("Text", v); return this; }

    public boolean Checked() { return vsBool("Checked", false); }

    public CheckBox Checked(boolean v) { vsSet("Checked", v); return this; }

    public boolean AutoPostBack() { return vsBool("AutoPostBack", false); }

    public CheckBox AutoPostBack(boolean v) { vsSet("AutoPostBack", v); return this; }

    /** {@code TextAlign}: {@code "Right"} (default) or {@code "Left"}. */
    public String TextAlign() { return vsString("TextAlign", "Right"); }

    public CheckBox TextAlign(String v) { vsSet("TextAlign", v); return this; }

    @Override
    public boolean RequiresPostBackRegistration() { return IsEnabled(); }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = post.get(UniqueID());
        if (v == null && !registered) return false;
        Checked(v != null && !v.isEmpty());
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
        a.put("type", "checkbox");
        a.put("name", UniqueID());
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
        for (String[] e : Attributes().entries()) outer.put(e[0].toLowerCase(java.util.Locale.ROOT), e[1]);
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
