package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

/**
 * {@code System.Web.UI.HtmlControls.HtmlTextArea} [J-U6]: {@code Value}, {@code InnerText} and
 * {@code InnerHtml} are views of one content state. The state is the element's (encoded) inner HTML;
 * {@code Value} = {@code InnerText} (decoded view, setter encodes), so a posted value read back through
 * {@code Value}/{@code InnerText} is the posted text unchanged ({@code signature.InnerText},
 * {@code edit_user.aspx.cs:511}) and renders safely inside {@code <textarea>}.
 * TODO(port): pin the exact .NET 4.5 HtmlTextArea encode/decode (Value⇄InnerHtml vs InnerText) when the original runs (D-001).
 */
public class HtmlTextArea extends HtmlContainerControl<HtmlTextArea> {
    public HtmlTextArea(String id) {
        super(id, "textarea");
    }

    /** {@code Value} getter (decoded content). */
    public String Value() { return InnerText(); }

    /** {@code Value} setter (stores encoded). */
    public HtmlTextArea Value(String value) { return InnerText(value == null ? "" : value); }

    public int Rows() { String v = Attributes().get("rows"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlTextArea Rows(int v) { Attributes().set("rows", v == -1 ? null : Integer.toString(v)); return this; }

    public int Cols() { String v = Attributes().get("cols"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlTextArea Cols(int v) { Attributes().set("cols", v == -1 ? null : Integer.toString(v)); return this; }

    /** {@code Name} = {@code UniqueID}. */
    public String Name() { return UniqueID(); }

    @Override
    protected void AddAttributesToRender(java.util.Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("name", UniqueID());
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = firstValue(post, UniqueID());
        if (v != null) Value(v);
        return false;
    }
}
