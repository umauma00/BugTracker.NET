package btnet.runtime.web.ui;

import btnet.runtime.web.HttpContext;
import btnet.runtime.web.HttpPostedFile;

import java.util.Map;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputFile}: {@code PostedFile()} =
 * {@code Context.Request.Files[UniqueID]} (.NET), {@code null} when no part was posted. The value is
 * never rendered.
 */
public class HtmlInputFile extends HtmlInputControl<HtmlInputFile> {
    private HttpPostedFile postedFile;

    public HtmlInputFile(String id) {
        super(id, "file");
    }

    public HttpPostedFile PostedFile() {
        if (postedFile != null) return postedFile;
        HttpContext ctx = HttpContext.Current();
        if (ctx == null) return null;
        return ctx.Request().Files().get(UniqueID());
    }

    /** Lets the page hand the posted file over explicitly (tests, transfers). */
    public HtmlInputFile PostedFile(HttpPostedFile file) {
        this.postedFile = file;
        return this;
    }

    public String Accept() { return attr("accept"); }

    public HtmlInputFile Accept(String v) { return attr("accept", v); }

    public int MaxLength() { String v = Attributes().get("maxlength"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlInputFile MaxLength(int v) { Attributes().set("maxlength", v == -1 ? null : Integer.toString(v)); return this; }

    public int Size() { String v = Attributes().get("size"); return v == null ? -1 : btnet.runtime.Int32.Parse(v); }

    public HtmlInputFile Size(int v) { Attributes().set("size", v == -1 ? null : Integer.toString(v)); return this; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("value", null);
    }
}
