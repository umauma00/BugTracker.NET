package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.RadioButtonList} [J-U10]: item {@code i} renders
 * {@code <input id="x_i" type="radio" name="x" value="<item value>" [checked]/><label for="x_i">text</label>},
 * table rows when the template element is a {@code <table>}, else flow with {@code <br />}. The posted
 * value selects by value ({@code SelectedValue}); an unknown value fails event validation (B4).
 */
public class RadioButtonList extends ListControl<RadioButtonList> {
    public RadioButtonList(String id) {
        super(id);
    }

    public RepeatDirection RepeatDirection() {
        Object v = vsGet("RepeatDirection");
        return v == null ? btnet.runtime.web.ui.RepeatDirection.Vertical : btnet.runtime.web.ui.RepeatDirection.valueOf(v.toString());
    }

    public RadioButtonList RepeatDirection(RepeatDirection v) { vsSet("RepeatDirection", v.name()); return this; }

    public RepeatLayout RepeatLayout() {
        Object v = vsGet("RepeatLayout");
        return v == null ? btnet.runtime.web.ui.RepeatLayout.Table : btnet.runtime.web.ui.RepeatLayout.valueOf(v.toString());
    }

    public RadioButtonList RepeatLayout(RepeatLayout v) { vsSet("RepeatLayout", v.name()); return this; }

    public int RepeatColumns() { return vsInt("RepeatColumns", 0); }

    public RadioButtonList RepeatColumns(int v) { vsSet("RepeatColumns", v); return this; }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = firstValue(post, UniqueID());
        if (v == null) return false;
        int n = indexOfEnabledValue(v);
        if (n < 0) throw invalidPostback();
        Items().ClearSelection();
        Items().get(n).Selected(true);
        return false;
    }

    @Override
    protected String TagName() { return RepeatLayout() == btnet.runtime.web.ui.RepeatLayout.Table ? "table" : "span"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        if (RepeatLayout() == btnet.runtime.web.ui.RepeatLayout.Table) {
            a.put("border", "0");
        }
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        String tag = templateTag != null ? templateTag : TagName();
        String uid = UniqueID();
        String cid = ClientID();
        java.util.List<String> cells = new java.util.ArrayList<>();
        for (int i = 0; i < Items().Count(); i++) {
            ListItem li = Items().get(i);
            StringBuilder sb = new StringBuilder();
            boolean disabled = !IsEnabled() || !li.Enabled();
            if (disabled) sb.append("<span disabled=\"disabled\">");
            sb.append("<input id=\"").append(Html.attr(cid + "_" + i)).append("\" type=\"radio\" name=\"")
                    .append(Html.attr(uid)).append("\" value=\"").append(Html.attr(li.Value())).append('"');
            if (li.Selected()) sb.append(" checked=\"checked\"");
            if (disabled) sb.append(" disabled=\"disabled\"");
            if (AutoPostBack()) {
                sb.append(" onclick=\"").append(Html.attr(Html.postBack(uid + "$" + i, ""))).append('"');
            }
            sb.append(" /><label for=\"").append(Html.attr(cid + "_" + i)).append("\">").append(li.Text()).append("</label>");
            if (disabled) sb.append("</span>");
            cells.add(sb.toString());
        }
        return Repeat.layout(cells, "table".equalsIgnoreCase(tag), RepeatDirection() == btnet.runtime.web.ui.RepeatDirection.Horizontal);
    }
}
