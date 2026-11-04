package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlControl}: a {@code runat="server"} HTML element. Its HTML
 * attributes live in {@link #Attributes()} (as .NET, where {@code Disabled}, {@code HRef}, {@code Value}
 * … are views of attributes), so they render, and persist in view state, uniformly.
 */
public abstract class HtmlControl<T extends HtmlControl<T>> extends Control<T> {
    private String tagName;

    protected HtmlControl(String id) {
        super(id);
    }

    protected HtmlControl(String id, String defaultTag) {
        super(id);
        this.tagName = defaultTag;
    }

    /** {@code TagName}. */
    public String TagName() { return tagName == null ? "span" : tagName; }

    /** Sets the tag used when the control renders itself (the template's tag wins under {@code bt:control}). */
    public T TagName(String tag) { this.tagName = tag; return self(); }

    /** {@code Disabled}: the {@code disabled} attribute is present. */
    public boolean Disabled() { return Attributes().get("disabled") != null; }

    public T Disabled(boolean value) {
        Attributes().set("disabled", value ? "disabled" : null);
        return self();
    }

    @Override
    protected void AddAttributesToRender(java.util.Map<String, String> a) {
        super.AddAttributesToRender(a);
        if (!a.containsKey("disabled")) a.put("disabled", null);
    }

    /** Attribute or {@code ""} (.NET HtmlControl string-property getters never return null). */
    protected final String attr(String name) {
        String v = Attributes().get(name);
        return v == null ? "" : v;
    }

    /** Attribute setter; {@code null} or {@code ""} removes (.NET {@code MapStringAttributeToString}). */
    protected final T attr(String name, String value) {
        Attributes().set(name, value == null || value.isEmpty() ? null : value);
        return self();
    }
}
