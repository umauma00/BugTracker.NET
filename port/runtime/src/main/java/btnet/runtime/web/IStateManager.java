package btnet.runtime.web;

/**
 * .NET {@code System.Web.UI.IStateManager}: the view-state contract between the page
 * lifecycle ({@link Page}, RULEBOOK §2B B2 step 4/7, B4) and the controls of
 * {@code btnet.runtime.web.ui}.
 *
 * <p>Every control registered with {@code control(...)} that implements this interface
 * gets, in this order: {@link #TrackViewState()} after {@code Page_Init};
 * {@link #LoadViewState(Object)} on postback with exactly the object its
 * {@link #SaveViewState()} returned on the previous response (only if that was non-null);
 * {@link #SaveViewState()} before render. The state must be JSON-shaped (see
 * {@link ViewStateCodec}): {@code null}, {@code Boolean}, numbers, {@code String},
 * {@code List}, {@code Map<String,?>}. A DataGrid saves its items here and recreates them
 * in {@code LoadViewState}, i.e. before posted values load (B4 (c)).
 */
public interface IStateManager {
    /** Called once after {@code Page_Init}; changes made from now on are view state. */
    default void TrackViewState() {
    }

    /** The control's state to persist, or {@code null} for none. */
    Object SaveViewState();

    /** Restores what {@link #SaveViewState()} returned on the previous response. */
    void LoadViewState(Object savedState);
}
