package btnet.runtime.web.ui;

import btnet.runtime.EventArgs;
import btnet.runtime.NameValueCollection;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * {@code System.Web.UI.WebControls.Button}: {@code <input type="submit" name="b" value="Text">}.
 * {@code OnClick="X"} → {@code b.OnClick(this::X)} (B2 step 6); raised when its name is posted (enabled
 * only), or when {@code __EVENTTARGET} names it; then the {@code Command} event bubbles (DataGrid).
 */
public class Button extends WebControl<Button> {
    private BiConsumer<Object, EventArgs> click;
    private BiConsumer<Object, CommandEventArgs> command;

    public Button(String id) {
        super(id);
    }

    public Button() {
        super(null);
    }

    public String Text() { return vsString("Text", ""); }

    public Button Text(String v) { vsSet("Text", v); return this; }

    public String CommandName() { return vsString("CommandName", ""); }

    public Button CommandName(String v) { vsSet("CommandName", v); return this; }

    public String CommandArgument() { return vsString("CommandArgument", ""); }

    public Button CommandArgument(String v) { vsSet("CommandArgument", v); return this; }

    public String OnClientClick() { return vsString("OnClientClick", ""); }

    public Button OnClientClick(String v) { vsSet("OnClientClick", v); return this; }

    public boolean UseSubmitBehavior() { return vsBool("UseSubmitBehavior", true); }

    public Button UseSubmitBehavior(boolean v) { vsSet("UseSubmitBehavior", v); return this; }

    public boolean CausesValidation() { return vsBool("CausesValidation", true); }

    public Button CausesValidation(boolean v) { vsSet("CausesValidation", v); return this; }

    public Button OnClick(BiConsumer<Object, EventArgs> handler) { this.click = handler; return this; }

    public Button OnCommand(BiConsumer<Object, CommandEventArgs> handler) { this.command = handler; return this; }

    @Override
    protected String TagName() { return "input"; }

    @Override
    protected void AddAttributesToRender(Map<String, String> a) {
        super.AddAttributesToRender(a);
        a.put("type", UseSubmitBehavior() ? "submit" : "button");
        a.put("name", UniqueID());
        if (vsHas("Text")) a.put("value", Text());
        String onclick = OnClientClick();
        String declared = Attributes().get("onclick");
        if (declared != null) onclick = Html.mergeScript(declared, onclick);
        if (!UseSubmitBehavior() && IsEnabled()) onclick = Html.mergeScript(onclick, Html.postBack(UniqueID(), ""));
        if (!onclick.isEmpty()) a.put("onclick", onclick);
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        return post.get(UniqueID()) != null && IsEnabled();
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
