package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlTable} [S2-19]: a {@code <table runat="server">}. {@code Visible},
 * {@code Attributes()}, {@code Style()}; no {@code InnerText}/{@code InnerHtml} (.NET throws
 * {@code NotSupportedException}). Under {@code bt:control}: removed when invisible, the template's children are
 * the content.
 */
public class HtmlTable extends HtmlControl<HtmlTable> {
    public HtmlTable(String id) {
        super(id, "table");
    }
}
