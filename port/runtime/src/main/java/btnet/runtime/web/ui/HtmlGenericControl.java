package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlGenericControl}: {@code span/div/p/td/tr/table/li/title/…}
 * with {@code runat="server"} (B1). The constructor argument is the control {@code ID} (B1), not the
 * tag; the template supplies the tag.
 */
public class HtmlGenericControl extends HtmlContainerControl<HtmlGenericControl> {
    public HtmlGenericControl(String id) {
        super(id);
    }
}
