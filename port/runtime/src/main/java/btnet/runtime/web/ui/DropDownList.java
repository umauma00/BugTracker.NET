package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;
import btnet.runtime.web.HttpException;

import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.DropDownList}: {@code <select name="x">}. {@code SelectedIndex}
 * with items but no selection selects item 0 (.NET); rendering two selected items throws
 * {@code HttpException("Cannot have multiple items selected in a DropDownList.")} (→ 500). A posted
 * value selects by value; one not among the items fails event validation (B4).
 */
public class DropDownList extends ListControl<DropDownList> {
    public DropDownList(String id) {
        super(id);
    }

    @Override
    public int SelectedIndex() {
        int i = super.SelectedIndex();
        if (i < 0 && Items().Count() > 0) {
            Items().get(0).Selected(true);
            i = 0;
        }
        return i;
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = firstValue(post, UniqueID());
        if (v == null) return false;
        int n = indexOfEnabledValue(v);
        if (n < 0) throw invalidPostback();
        Items().ClearSelection();
        Items().get(n).Selected(true);
        return false;
    }

    @Override
    protected String TagName() { return "select"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        selectAttributes(a);
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        if (selectedCount() > 1) throw new HttpException("Cannot have multiple items selected in a DropDownList.");
        return Items().renderOptions();
    }
}
