package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.HtmlControls.HtmlForm}. The template renders the form tag itself (B5);
 * {@link #ClientID()} returns the id that tag carries: the value given to {@link #ClientID(String)}
 * (the page sets it per B5: {@code aspnetForm} on master pages without {@code ClientIDMode="Static"},
 * {@code ctl00} for an ID-less form without master), else the declared {@code ID}, else {@code ctl00}.
 */
public class HtmlForm extends HtmlContainerControl<HtmlForm> {
    public HtmlForm(String id) {
        super(id, "form");
    }

    public HtmlForm ClientID(String renderedId) { vsSet("ClientIDOverride", renderedId); return this; }

    @Override
    public String ClientID() {
        String o = vsString("ClientIDOverride", null);
        if (o != null) return o;
        return ID() != null ? ID() : "ctl00";
    }

    /** {@code HtmlForm.Name} = the rendered name ({@code aspnetForm} on masters, B5). */
    public String Name() { return ClientID(); }

    public String Action() { return attr("action"); }

    public HtmlForm Action(String v) { return attr("action", v); }

    public String Method() { String m = Attributes().get("method"); return m == null ? "post" : m; }

    public HtmlForm Method(String v) { return attr("method", v); }

    public String Enctype() { return attr("enctype"); }

    public HtmlForm Enctype(String v) { return attr("enctype", v); }

    public String Target() { return attr("target"); }

    public HtmlForm Target(String v) { return attr("target", v); }
}
