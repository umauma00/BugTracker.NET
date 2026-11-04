package btnet.runtime.web.ui;

import java.util.Map;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputControl}: {@code <input runat="server">}. {@code Value}
 * is the {@code value} attribute ({@code ""} when absent); the rendered {@code name} is the
 * {@code UniqueID} — a declared {@code name} is dropped (B5 [J-U12]), except for radios.
 */
public abstract class HtmlInputControl<T extends HtmlInputControl<T>> extends HtmlControl<T> {
    private final String type;

    protected HtmlInputControl(String id, String type) {
        super(id, "input");
        this.type = type;
    }

    /** {@code Type}. */
    public String Type() { return type; }

    public String Value() { return attr("value"); }

    /** {@code Value} setter ({@code null} removes the attribute). */
    public T Value(String value) {
        Attributes().set("value", value);
        return self();
    }

    /** {@code Name} getter = {@code UniqueID} (radios: the group name). */
    public String Name() { return UniqueID(); }

    /** {@code Name} setter: ignored by .NET for non-radio inputs (kept as attribute only for radios). */
    public T Name(String value) { return self(); }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("type", type);
        a.put("name", Name());
    }
}
