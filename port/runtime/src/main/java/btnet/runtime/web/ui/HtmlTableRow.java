package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlTableRow} [S2-19]: a {@code <tr runat="server">}. {@code Visible},
 * {@code Attributes()}, {@code Style()}; no {@code InnerText}/{@code InnerHtml} (.NET throws
 * {@code NotSupportedException}; no site uses them). Under {@code bt:control}: removed when invisible, the
 * template's children are the content.
 */
public class HtmlTableRow extends HtmlControl<HtmlTableRow> {
    public HtmlTableRow(String id) {
        super(id, "tr");
    }
}
