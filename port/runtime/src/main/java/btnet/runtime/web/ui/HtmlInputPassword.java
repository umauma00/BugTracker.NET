package btnet.runtime.web.ui;

import btnet.runtime.NameValueCollection;

import java.util.List;
import java.util.Map;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputPassword}: like {@code HtmlInputText}, but the value is
 * never rendered nor kept in view state (.NET removes it before rendering).
 */
public class HtmlInputPassword extends HtmlInputControl<HtmlInputPassword> {
    public HtmlInputPassword(String id) {
        super(id, "password");
    }

    public int MaxLength() { String v = Attributes().get("maxlength"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlInputPassword MaxLength(int v) { Attributes().set("maxlength", v == -1 ? null : Integer.toString(v)); return this; }

    public int Size() { String v = Attributes().get("size"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlInputPassword Size(int v) { Attributes().set("size", v == -1 ? null : Integer.toString(v)); return this; }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        String v = firstValue(post, UniqueID());
        if (v != null) Value(v);
        return false;
    }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("value", null);
    }

    @Override
    public Map<String, Object> SaveViewState() {
        Map<String, Object> m = super.SaveViewState();
        Object attrs = m.get("a");
        if (attrs instanceof List<?> l) {
            m.put("a", l.stream().filter(p -> !(p instanceof List<?> pl && "value".equalsIgnoreCase(String.valueOf(pl.get(0))))).toList());
        }
        return m;
    }
}
