package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

import java.util.Map;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputRadioButton}: renders the declared {@code name} (the
 * group, B5) and {@code value}. The field initializer must carry them
 * ({@code control(new HtmlInputRadioButton("and")).Name("and_or").Value("and").Checked(true)}) because
 * postback loading needs them. {@code Value} defaults to the {@code ID} (.NET). Registered for postback
 * while rendered and not disabled: checked = the posted group value equals {@code Value}.
 */
public class HtmlInputRadioButton extends HtmlInputControl<HtmlInputRadioButton> {
    public HtmlInputRadioButton(String id) {
        super(id, "radio");
    }

    public boolean Checked() { return Attributes().get("checked") != null; }

    public HtmlInputRadioButton Checked(boolean v) { Attributes().set("checked", v ? "checked" : null); return this; }

    @Override
    public String Name() {
        String n = Attributes().get("name");
        return n != null ? n : UniqueID();
    }

    @Override
    public HtmlInputRadioButton Name(String value) {
        Attributes().set("name", value);
        return this;
    }

    @Override
    public String Value() {
        String v = Attributes().get("value");
        if (v != null && !v.isEmpty()) return v;
        return ID() != null ? ID() : UniqueID();
    }

    @Override
    public boolean RequiresPostBackRegistration() { return !Disabled(); }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = post.get(Name());
        if (v == null && !registered) return false;
        Checked(v != null && v.equals(Value()));
        return false;
    }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("value", Value());
        if (!a.containsKey("checked")) a.put("checked", null);
    }
}
