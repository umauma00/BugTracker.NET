package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

/** {@code System.Web.UI.HtmlControls.HtmlInputText}: {@code <input type="text" runat="server">}. */
public class HtmlInputText extends HtmlInputControl<HtmlInputText> {
    public HtmlInputText(String id) {
        super(id, "text");
    }

    public int MaxLength() { String v = Attributes().get("maxlength"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlInputText MaxLength(int v) { Attributes().set("maxlength", v == -1 ? null : Integer.toString(v)); return this; }

    public int Size() { String v = Attributes().get("size"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlInputText Size(int v) { Attributes().set("size", v == -1 ? null : Integer.toString(v)); return this; }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = firstValue(post, UniqueID());
        if (v != null) Value(v);
        return false;
    }
}
