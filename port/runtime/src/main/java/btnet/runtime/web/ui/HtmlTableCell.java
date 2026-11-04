package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlTableCell} [S2-19]: a {@code <td>}/{@code <th>} with {@code runat="server"}.
 * {@code Visible}, {@code Attributes()}, {@code Style()}, {@code InnerText}/{@code InnerHtml} (an
 * {@link HtmlContainerControl}). Under {@code bt:control} it renders like {@link HtmlGenericControl}: removed
 * when invisible; the template's children are the content unless code set {@code InnerHtml}/{@code InnerText}.
 */
public class HtmlTableCell extends HtmlContainerControl<HtmlTableCell> {
    public HtmlTableCell(String id) {
        super(id, "td");
    }
}
