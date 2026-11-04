package btnet.runtime.web.ui;

import java.util.Map;

/**
 * {@code System.Web.UI.WebControls.WebControl}: base of the {@code asp:} controls. Rendering follows
 * {@code controlRenderingCompatibilityVersion="3.5"} ({@code Web.config:38}): a disabled control renders
 * {@code disabled="disabled"} (B5: {@code Enabled=false} → {@code disabled="disabled"}).
 */
public abstract class WebControl<T extends WebControl<T>> extends Control<T> {

    protected WebControl(String id) {
        super(id);
    }

    /** {@code Enabled} (own flag). */
    public boolean Enabled() { return vsBool("Enabled", true); }

    public T Enabled(boolean v) { vsSet("Enabled", v); return self(); }

    /** {@code IsEnabled}: own flag and every enclosing WebControl's. */
    public boolean IsEnabled() {
        if (!Enabled()) return false;
        for (Control<?> p = Parent(); p != null; p = p.Parent()) {
            if (p instanceof WebControl<?> w && !w.Enabled()) return false;
        }
        return true;
    }

    public String CssClass() { return vsString("CssClass", ""); }

    public T CssClass(String v) { vsSet("CssClass", v); return self(); }

    public String ToolTip() { return vsString("ToolTip", ""); }

    public T ToolTip(String v) { vsSet("ToolTip", v); return self(); }

    public String Width() { return vsString("Width", ""); }

    public T Width(String v) { vsSet("Width", v); return self(); }

    public String Height() { return vsString("Height", ""); }

    public T Height(String v) { vsSet("Height", v); return self(); }

    public String BorderColor() { return vsString("BorderColor", ""); }

    /** {@code BorderColor} ({@code "black"} renders as {@code Black}, as {@code ColorTranslator.ToHtml}). */
    public T BorderColor(String v) { vsSet("BorderColor", v); return self(); }

    public String BorderWidth() { return vsString("BorderWidth", ""); }

    public T BorderWidth(String v) { vsSet("BorderWidth", v); return self(); }

    public String AccessKey() { return vsString("AccessKey", ""); }

    public T AccessKey(String v) { vsSet("AccessKey", v); return self(); }

    public int TabIndex() { return vsInt("TabIndex", 0); }

    public T TabIndex(int v) { vsSet("TabIndex", v); return self(); }

    /** Attributes of the outer element: id, class, disabled, title, accesskey, tabindex, attributes, style. */
    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        if (!CssClass().isEmpty()) a.put("class", CssClass());
        a.put("disabled", IsEnabled() ? null : "disabled");
        if (!ToolTip().isEmpty()) a.put("title", ToolTip());
        if (!AccessKey().isEmpty()) a.put("accesskey", AccessKey());
        if (TabIndex() != 0) a.put("tabindex", Integer.toString(TabIndex()));
    }

    /** Control style ({@code border-color}, {@code border-width}, {@code width}, {@code height}) then {@code Style}. */
    @Override
    protected String RenderStyle() {
        StringBuilder sb = new StringBuilder();
        if (!BorderColor().isEmpty()) sb.append("border-color:").append(color(BorderColor())).append(';');
        if (!BorderWidth().isEmpty()) sb.append("border-width:").append(BorderWidth()).append(';');
        if (!Width().isEmpty()) sb.append("width:").append(unit(Width())).append(';');
        if (!Height().isEmpty()) sb.append("height:").append(unit(Height())).append(';');
        String own = super.RenderStyle();
        if (own != null) sb.append(own);
        return sb.length() == 0 ? null : sb.toString();
    }

    static String color(String c) {
        if (c.isEmpty() || c.startsWith("#")) return c;
        return Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    static String unit(String u) {
        return u.chars().allMatch(Character::isDigit) ? u + "px" : u;
    }
}
