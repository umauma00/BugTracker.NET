package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

import java.util.Map;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputCheckBox}: {@code Checked} = the {@code checked}
 * attribute. Registered for postback while rendered and not disabled (B4 (d)): then checked = a
 * non-empty value posted under its name; an absent name means unchecked.
 */
public class HtmlInputCheckBox extends HtmlInputControl<HtmlInputCheckBox> {
    public HtmlInputCheckBox(String id) {
        super(id, "checkbox");
    }

    public boolean Checked() { return Attributes().get("checked") != null; }

    public HtmlInputCheckBox Checked(boolean v) { Attributes().set("checked", v ? "checked" : null); return this; }

    @Override
    public boolean RequiresPostBackRegistration() { return !Disabled(); }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = post.get(UniqueID());
        if (v == null && !registered) return false;
        Checked(v != null && !v.isEmpty());
        return false;
    }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        if (!a.containsKey("checked")) a.put("checked", null);
    }
}
