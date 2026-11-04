package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.TableItemStyle} [S2-21] of a grid's {@code <HeaderStyle>}/{@code <ItemStyle>}/
 * {@code <AlternatingItemStyle>}: only {@code CssClass} is used by the source ({@code class} of the rendered row).
 */
public final class TableItemStyle {
    private String cssClass = "";

    public String CssClass() { return cssClass; }

    /** Fluent {@code CssClass} setter; {@code null} → {@code ""}. */
    public TableItemStyle CssClass(String v) { this.cssClass = v == null ? "" : v; return this; }
}
