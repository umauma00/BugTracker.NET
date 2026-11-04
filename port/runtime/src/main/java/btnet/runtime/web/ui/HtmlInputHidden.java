package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputHidden}. An absent posted key keeps the restored
 * {@code Value} (B4, {@code edit_bug.aspx.cs:1302-1304}).
 */
public class HtmlInputHidden extends HtmlInputControl<HtmlInputHidden> {
    public HtmlInputHidden(String id) {
        super(id, "hidden");
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = firstValue(post, UniqueID());
        if (v != null) Value(v);
        return false;
    }
}
