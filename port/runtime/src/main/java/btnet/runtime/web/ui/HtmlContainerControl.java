package btnet.runtime.web.ui;

import btnet.runtime.web.HttpUtility;

/**
 * {@code System.Web.UI.HtmlControls.HtmlContainerControl}: an HTML element with content.
 * {@code InnerHtml} is the one content state; {@code InnerText} reads it HTML-decoded and stores its
 * value HTML-encoded (.NET). While neither was set, the template's own body is rendered (the
 * original's literal children).
 */
public abstract class HtmlContainerControl<T extends HtmlContainerControl<T>> extends HtmlControl<T> {

    protected HtmlContainerControl(String id) {
        super(id);
    }

    protected HtmlContainerControl(String id, String defaultTag) {
        super(id, defaultTag);
    }

    /** {@code InnerHtml} getter: the set content, {@code ""} when never set. */
    public String InnerHtml() {
        String v = vsString("innerhtml", null);
        return v == null ? "" : v;
    }

    /** {@code InnerHtml} setter (raw markup); {@code null} removes the content state. */
    public T InnerHtml(String value) {
        if (value == null) vsRemove("innerhtml"); else vsSet("innerhtml", value);
        return self();
    }

    /** {@code InnerText} getter = {@code HttpUtility.HtmlDecode(InnerHtml)}. */
    public String InnerText() {
        return HttpUtility.HtmlDecode(InnerHtml());
    }

    /** {@code InnerText} setter = {@code InnerHtml = HttpUtility.HtmlEncode(value)}. */
    public T InnerText(String value) {
        return InnerHtml(value == null ? null : Html.encode(value));
    }

    /** Whether code or the initializer set the content. */
    public boolean HasInnerContent() { return vsHas("innerhtml"); }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        return vsHas("innerhtml") ? InnerHtml() : null;
    }
}
