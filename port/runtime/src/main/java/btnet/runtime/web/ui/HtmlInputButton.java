package btnet.runtime.web.ui;

import btnet.runtime.EventArgs;
import btnet.runtime.NameValueCollection;

import java.util.function.BiConsumer;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputButton} ({@code type="button"}). {@code onserverclick="X"}
 * → {@code OnServerClick(this::X)} in the constructor (B2 step 6 [J-U9]): raised when the button's
 * name is in the postback collection, or when {@code __EVENTTARGET} names it.
 */
public class HtmlInputButton extends HtmlInputControl<HtmlInputButton> {
    private BiConsumer<Object, EventArgs> serverClick;

    public HtmlInputButton(String id) {
        super(id, "button");
    }

    public HtmlInputButton OnServerClick(BiConsumer<Object, EventArgs> handler) {
        this.serverClick = handler;
        return this;
    }

    @Override
    public boolean ProcessPostData(NameValueCollection post, boolean registered) {
        return post.get(UniqueID()) != null && !Disabled();
    }

    @Override
    public boolean IsPostBackEventHandler() { return true; }

    @Override
    public void RaisePostBackEvent(String eventArgument) {
        if (serverClick != null) serverClick.accept(this, EventArgs.Empty);
    }
}
