package btnet.runtime.web.ui;

import btnet.runtime.EventArgs;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * {@code System.Web.UI.WebControls.LinkButton}: {@code <a href="javascript:__doPostBack('uid','')">Text</a>}
 * (no {@code href} while disabled). Raised through {@code __EVENTTARGET}; {@code Click}, then
 * {@code Command}, which bubbles to a DataGrid's {@code ItemCommand}.
 */
public class LinkButton extends WebControl<LinkButton> {
    private BiConsumer<Object, EventArgs> click;
    private BiConsumer<Object, CommandEventArgs> command;

    public LinkButton(String id) {
        super(id);
    }

    public LinkButton() {
        super(null);
    }

    public String Text() { return vsString("Text", ""); }

    public LinkButton Text(String v) { vsSet("Text", v); return this; }

    public String CommandName() { return vsString("CommandName", ""); }

    public LinkButton CommandName(String v) { vsSet("CommandName", v); return this; }

    public String CommandArgument() { return vsString("CommandArgument", ""); }

    public LinkButton CommandArgument(String v) { vsSet("CommandArgument", v); return this; }

    public String OnClientClick() { return vsString("OnClientClick", ""); }

    public LinkButton OnClientClick(String v) { vsSet("OnClientClick", v); return this; }

    public boolean CausesValidation() { return vsBool("CausesValidation", true); }

    public LinkButton CausesValidation(boolean v) { vsSet("CausesValidation", v); return this; }

    public LinkButton OnClick(BiConsumer<Object, EventArgs> handler) { this.click = handler; return this; }

    public LinkButton OnCommand(BiConsumer<Object, CommandEventArgs> handler) { this.command = handler; return this; }

    @Override
    protected String TagName() { return "a"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        String onclick = OnClientClick();
        String declared = Attributes().get("onclick");
        if (declared != null) onclick = Html.mergeScript(declared, onclick);
        if (!onclick.isEmpty()) a.put("onclick", onclick);
        a.put("href", IsEnabled() ? "javascript:" + Html.postBack(UniqueID(), "") : null);
    }

    @Override
    protected String RenderInnerHtml(String templateTag) {
        return vsHas("Text") ? Text() : null;
    }

    @Override
    public boolean IsPostBackEventHandler() { return true; }

    @Override
    public void RaisePostBackEvent(String eventArgument) {
        if (click != null) click.accept(this, EventArgs.Empty);
        CommandEventArgs ce = new CommandEventArgs(CommandName(), CommandArgument());
        if (command != null) command.accept(this, ce);
        RaiseBubbleEvent(this, ce);
    }
}
