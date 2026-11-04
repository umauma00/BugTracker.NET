package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;
import btnet.runtime.web.HttpException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.ListBox}: {@code <select size="Rows" name="x" [multiple]>}
 * ({@code Rows} default 4). A multi-select list box registers for postback while enabled (.NET
 * {@code OnPreRender}), so nothing posted deselects everything; a single-select one loads only when
 * its name is posted. Unknown posted values fail event validation (B4).
 */
public class ListBox extends ListControl<ListBox> {
    public ListBox(String id) {
        super(id);
    }

    public ListSelectionMode SelectionMode() {
        Object v = vsGet("SelectionMode");
        return v == null ? ListSelectionMode.Single : ListSelectionMode.valueOf(v.toString());
    }

    public ListBox SelectionMode(ListSelectionMode v) { vsSet("SelectionMode", v.name()); return this; }

    public int Rows() { return vsInt("Rows", 4); }

    public ListBox Rows(int v) { vsSet("Rows", v); return this; }

    /** {@code GetSelectedIndices()}. */
    public int[] GetSelectedIndices() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < Items().Count(); i++) if (Items().get(i).Selected()) l.add(i);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public boolean RequiresPostBackRegistration() {
        return SelectionMode() == ListSelectionMode.Multiple && IsEnabled();
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String[] values = post.GetValues(UniqueID());
        if (values == null) {
            if (registered) Items().ClearSelection();
            return false;
        }
        if (SelectionMode() == ListSelectionMode.Single) {
            int n = indexOfEnabledValue(values[0]);
            if (n < 0) throw invalidPostback();
            Items().ClearSelection();
            Items().get(n).Selected(true);
        } else {
            List<Integer> idx = new ArrayList<>();
            for (String v : values) {
                int n = indexOfEnabledValue(v);
                if (n < 0) throw invalidPostback();
                idx.add(n);
            }
            Items().ClearSelection();
            for (int n : idx) Items().get(n).Selected(true);
        }
        return false;
    }

    @Override
    protected String TagName() { return "select"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("size", Integer.toString(Rows()));
        selectAttributes(a);
        a.put("multiple", SelectionMode() == ListSelectionMode.Multiple ? "multiple" : null);
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        if (SelectionMode() == ListSelectionMode.Single && selectedCount() > 1) {
            throw new HttpException("Cannot have multiple items selected when the SelectionMode is Single.");
        }
        return Items().renderOptions();
    }
}
