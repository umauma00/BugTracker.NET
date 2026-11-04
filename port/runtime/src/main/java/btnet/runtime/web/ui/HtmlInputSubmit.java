package btnet.runtime.web.ui;

import btnet.runtime.EventArgs;
import btnet.runtime.NameValueCollection;

import java.util.function.BiConsumer;

/**
 * {@code System.Web.UI.HtmlControls.HtmlInputSubmit} ({@code type="submit"}). {@code onserverclick="X"}
 * → {@code OnServerClick(this::X)}; raised when its name ({@code UniqueID}) is posted (B2 step 6).
 */
public class HtmlInputSubmit extends HtmlInputControl<HtmlInputSubmit> {
    private BiConsumer<Object, EventArgs> serverClick;

    public HtmlInputSubmit(String id) {
        super(id, "submit");
    }

    public HtmlInputSubmit OnServerClick(BiConsumer<Object, EventArgs> handler) {
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
