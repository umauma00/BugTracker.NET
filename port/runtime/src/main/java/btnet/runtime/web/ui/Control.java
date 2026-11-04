package btnet.runtime.web.ui;

import btnet.runtime.ArgumentException;
import btnet.runtime.EventArgs;
import btnet.runtime.NameValueCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code System.Web.UI.Control}: base of every server control (RULEBOOK §2B B1, §3 [S1-27]).
 *
 * <p><b>Typing.</b> Self-bounded ({@code Control<T extends Control<T>>}): every property setter
 * {@code T X(V value)} returns {@code this} as the concrete control, so field initializers chain
 * ({@code control(new CheckBox("x")).Text("Admin")}). Getters are {@code X()} (§2A property rule).
 *
 * <p><b>Naming (B5, judge contract 2/3).</b> Names are flat: {@link #UniqueID()} = the {@code ID},
 * except below a naming container that contributes a prefix — only {@link DataGrid} and
 * {@link DataGridItem} do ({@code MyDataGrid$ctl02$none}). Pages, masters and user controls never
 * contribute (no {@code ctl00$body$}). {@link #ClientID()} = {@code UniqueID} with {@code $} →
 * {@code _}.
 *
 * <p><b>View state (B4).</b> Every property, attribute and style value set on a control (in the field
 * initializer or by code) lives in its state bag; {@link #SaveViewState()} returns it as plain
 * JSON-able data (maps, lists, strings, booleans, integers) and {@link #LoadViewState(Map)} restores
 * it. Restoring the declarative values set again by the initializer is idempotent, so this equals
 * ASP.NET's "changed after TrackViewState" persistence.
 *
 * <p><b>Page ↔ control contract</b> (used by {@code btnet.runtime.web.Page.ProcessRequest}, B2):
 * <ol>
 * <li>after {@code Page_Init}, on postback: {@link #LoadViewStateTree} with the decoded
 *     {@code __VIEWSTATE} map (recreates DataGrid items before posted values load, B4 (c)); then
 *     {@link #LoadPostDataTree} with the postback collection (B2 step 2) — returns the submit
 *     control that must raise its event, or {@code null};</li>
 * <li>after the {@code Page_Load}s: {@link #RaisePostBackEventTree} (B2 step 6: that control,
 *     else the control named by {@code __EVENTTARGET});</li>
 * <li>before rendering: {@link #SaveViewStateTree} → encode/sign (ViewStateCodec) → expose the string
 *     to the template as model variable {@code viewState} ({@code templates/runtime/form.html}).</li>
 * </ol>
 * {@code roots} are the controls the page created with {@code control(...)} (masters/user controls
 * included; their own children are reached through {@link #Controls()}).
 */
public abstract class Control<T extends Control<T>> {

    /** Key of the B4 (d) list (controls registered for postback at save time) in the tree state map. */
    public static final String REGISTERED_KEY = "\u0001registered";

    private String id;
    private Control<?> parent;
    private ControlCollection controls;
    private int automaticIdCounter;
    private final CssStyleCollection style = new CssStyleCollection();
    private final AttributeCollection attributes = new AttributeCollection(style);
    /** The state bag ({@code ViewState}); values are JSON-able. */
    private final LinkedHashMap<String, Object> bag = new LinkedHashMap<>();

    protected Control() {}

    protected Control(String id) {
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    // ------------------------------------------------------------------ identity / tree

    /** {@code Control.ID}. */
    public String ID() { return id; }

    public T ID(String value) { this.id = value; return self(); }

    public Control<?> Parent() { return parent; }

    void setParent(Control<?> p) { this.parent = p; }

    /** {@code Control.Controls}. */
    public ControlCollection Controls() {
        if (controls == null) controls = new ControlCollection(this);
        return controls;
    }

    public boolean HasControls() { return controls != null && controls.Count() > 0; }

    /**
     * Adds {@code c} to {@link #Controls()} and returns it — the same helper a page uses for its own
     * fields (B1), so a {@code UserControl} (B6) creates its child controls with
     * {@code control(new X("id"))} too.
     */
    protected <C extends Control<?>> C control(C c) {
        Controls().Add(c);
        return c;
    }

    /**
     * Whether this control prefixes the {@code UniqueID} of its descendants ({@code INamingContainer}
     * that is rendered with a prefix). Only {@link DataGrid}/{@link DataGridItem} return {@code true};
     * {@code UserControl} must not (flat names, judge contract 2).
     */
    public boolean IsNamingContainer() { return false; }

    /** {@code Control.NamingContainer}: nearest ancestor with {@link #IsNamingContainer()}. */
    public Control<?> NamingContainer() {
        for (Control<?> p = parent; p != null; p = p.parent) {
            if (p.IsNamingContainer()) return p;
        }
        return null;
    }

    boolean wantsAutomaticId() { return true; }

    void assignAutomaticId(String autoId) { this.id = autoId; }

    String nextAutomaticId() { return Html.ctl(automaticIdCounter++); }

    /** {@code Control.UniqueID}: the postback name ({@code name} attribute, B5). */
    public String UniqueID() {
        Control<?> nc = NamingContainer();
        if (id == null) {
            if (nc == null || !wantsAutomaticId()) return null;
            assignAutomaticId(nc.nextAutomaticId());
        }
        return nc == null ? id : nc.UniqueID() + "$" + id;
    }

    /** {@code Control.ClientID} [J-U7]: exactly the rendered {@code id}. */
    public String ClientID() {
        String u = UniqueID();
        return u == null ? null : u.replace('$', '_');
    }

    /**
     * {@code Control.FindControl(id)}: searches this control's naming scope (the control itself when
     * it is a naming container, else its naming container, else its own subtree); IDs compare
     * case-insensitively, as ASP.NET's named-control table; a {@code $}-path descends container by
     * container. {@code null} when not found.
     */
    public Control<?> FindControl(String id) {
        if (id == null || id.isEmpty()) return null;
        Control<?> scope = IsNamingContainer() ? this : (NamingContainer() != null ? NamingContainer() : this);
        int d = id.indexOf('$');
        if (d >= 0) {
            Control<?> first = findIn(scope, id.substring(0, d));
            return first == null ? null : first.FindControl(id.substring(d + 1));
        }
        return findIn(scope, id);
    }

    private static Control<?> findIn(Control<?> scope, String id) {
        if (scope.controls == null) return null;
        for (Control<?> c : scope.controls) {
            if (c.id != null && c.id.equalsIgnoreCase(id)) return c;
            if (!c.IsNamingContainer()) {
                Control<?> r = findIn(c, id);
                if (r != null) return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ common properties

    /** {@code Control.Visible} getter: own flag and every ancestor's (as .NET). */
    public boolean Visible() {
        if (!vsBool("Visible", true)) return false;
        return parent == null || parent.Visible();
    }

    public T Visible(boolean value) { vsSet("Visible", value); return self(); }

    /** {@code Attributes} (HtmlControl / WebControl / UserControl). */
    public AttributeCollection Attributes() { return attributes; }

    /** {@code Style}. */
    public CssStyleCollection Style() { return style; }

    /** Fluent {@code Style().set(name, value)} [S1-32]. */
    public T Style(String name, String value) { style.set(name, value); return self(); }

    /** {@code Control.DataBind()}: {@link #OnDataBinding()}, then every child. */
    public void DataBind() {
        OnDataBinding();
        if (controls != null) {
            for (Control<?> c : new ArrayList<>(controls.asList())) c.DataBind();
        }
    }

    protected void OnDataBinding() {}

    // ------------------------------------------------------------------ state bag

    protected final Object vsGet(String key) { return bag.get(key); }

    protected final boolean vsHas(String key) { return bag.containsKey(key); }

    protected final void vsSet(String key, Object value) { bag.put(key, value); }

    protected final void vsRemove(String key) { bag.remove(key); }

    protected final String vsString(String key, String def) {
        if (!bag.containsKey(key)) return def;
        Object v = bag.get(key);
        return v == null ? null : v.toString();
    }

    protected final boolean vsBool(String key, boolean def) {
        Object v = bag.get(key);
        return v instanceof Boolean b ? b : def;
    }

    protected final int vsInt(String key, int def) {
        Object v = bag.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    /** {@code SaveViewState}: bag, attributes and style as JSON-able data (never {@code null}). */
    public Map<String, Object> SaveViewState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", new LinkedHashMap<>(bag));
        m.put("a", attributes.save());
        m.put("s", style.save());
        return m;
    }

    /** {@code LoadViewState}: restores what {@link #SaveViewState()} produced (after a JSON round trip). */
    @SuppressWarnings("unchecked")
    public void LoadViewState(Map<String, ?> saved) {
        if (saved == null) return;
        Object b = saved.get("b");
        if (b instanceof Map<?, ?> bm) {
            for (Map.Entry<?, ?> e : bm.entrySet()) bag.put(String.valueOf(e.getKey()), e.getValue());
        }
        if (saved.containsKey("s")) style.load(saved.get("s"));
        if (saved.containsKey("a")) attributes.load(saved.get("a"));
    }

    /**
     * Key of this control in the page's view-state map: its {@code UniqueID}, or for an ID-less
     * control (table cells) its parent's key + {@code #<index>}.
     */
    public String ViewStateKey() {
        String u = UniqueID();
        if (u != null) return u;
        if (parent == null) return "#" + System.identityHashCode(this);
        return parent.ViewStateKey() + "#" + parent.Controls().IndexOf(this);
    }

    // ------------------------------------------------------------------ postback

    /**
     * B4 (d) [J-U11]: whether the control registers for postback when rendered visible
     * ({@code Page.RegisterRequiresPostBack}); such a control loads post data even when its key is
     * absent (absent = unchecked / deselected). Checkboxes, radios, {@code CheckBoxList}, multi-select
     * {@code ListBox}, list-style {@code HtmlSelect} — each only while enabled.
     */
    public boolean RequiresPostBackRegistration() { return false; }

    /**
     * {@code IPostBackDataHandler.LoadPostData}: applies the posted value(s) when this control's key is
     * in {@code post} or {@code registered} is true (B4). Returns {@code true} when the control must raise
     * its postback event later ({@code RegisterRequiresRaiseEvent}: a submit button whose name was posted).
     */
    public boolean ProcessPostData(NameValueCollection post, boolean registered) { return false; }

    /** {@code IPostBackEventHandler}? */
    public boolean IsPostBackEventHandler() { return false; }

    /** {@code IPostBackEventHandler.RaisePostBackEvent(eventArgument)}. */
    public void RaisePostBackEvent(String eventArgument) {}

    /** {@code OnBubbleEvent}: {@code true} = handled, stop bubbling. */
    protected boolean OnBubbleEvent(Object source, EventArgs args) { return false; }

    /** {@code RaiseBubbleEvent}: offers the event to each ancestor until one handles it. */
    protected final void RaiseBubbleEvent(Object source, EventArgs args) {
        for (Control<?> p = parent; p != null; p = p.parent) {
            if (p.OnBubbleEvent(source, args)) return;
        }
    }

    /** First value of {@code key}, or {@code null} when absent. */
    protected static String firstValue(NameValueCollection post, String key) {
        if (key == null) return null;
        String[] vs = post.GetValues(key);
        return vs == null || vs.length == 0 ? null : vs[0];
    }

    /** ASP.NET event validation failure ({@code enableEventValidation}, B4): → 500. */
    protected static RuntimeException invalidPostback() {
        return new ArgumentException("Invalid postback or callback argument.  Event validation is enabled using "
                + "<pages enableEventValidation=\"true\"/> in configuration or <%@ Page EnableEventValidation=\"true\" %> "
                + "in a page.  For security purposes, this feature verifies that arguments to postback or callback events "
                + "originate from the server control that originally rendered them.  If the data is valid and expected, "
                + "use the ClientScriptManager.RegisterForEventValidation method in order to register the postback or "
                + "callback data for validation.");
    }

    // ------------------------------------------------------------------ rendering

    /** Tag used by {@link #RenderControl(StringBuilder)} when the control renders itself. */
    protected String TagName() { return "span"; }

    /**
     * The attributes this control owns on its element, lower-case keys; a {@code null} value means
     * "must not be present". Applied by {@code bt:control} over the template's literal attributes, and
     * written by {@link #RenderControl(StringBuilder)}.
     */
    protected void AddAttributesToRender(Map<String, String> a) {
        String cid = ClientID();
        if (cid != null) a.put("id", cid);
        for (String[] e : attributes.entries()) a.put(e[0].toLowerCase(Locale.ROOT), e[1]);
        String st = RenderStyle();
        if (st != null && !st.isEmpty()) a.put("style", st);
    }

    /** The {@code style} attribute value, or {@code null}. */
    protected String RenderStyle() {
        return style.Count() == 0 ? null : style.Value();
    }

    /**
     * Inner HTML this control renders, or {@code null} to keep the template's body (children /
     * literal content). {@code templateTag} = the template element's name, {@code null} when the control
     * renders itself.
     */
    protected String RenderInnerHtml(String templateTag) { return null; }

    /** {@code true} when the control renders nothing although visible (an unbound DataGrid). */
    public boolean IsRenderSuppressed() { return false; }

    /** {@code RenderControl(HtmlTextWriter)}: the control's complete WebForms markup (used inside grids/lists). */
    public void RenderControl(StringBuilder sb) {
        if (!Visible() || IsRenderSuppressed()) return;
        Map<String, String> a = new LinkedHashMap<>();
        AddAttributesToRender(a);
        String tag = TagName();
        if (Html.isVoid(tag)) {
            Html.openTag(sb, tag, a, true);
            return;
        }
        Html.openTag(sb, tag, a, false);
        String inner = RenderInnerHtml(null);
        if (inner != null) sb.append(inner); else RenderChildren(sb);
        sb.append("</").append(tag).append('>');
    }

    protected void RenderChildren(StringBuilder sb) {
        if (controls == null) return;
        for (Control<?> c : controls) c.RenderControl(sb);
    }

    /** Convenience: {@link #RenderControl(StringBuilder)} into a new string. */
    public String RenderControlToString() {
        StringBuilder sb = new StringBuilder();
        RenderControl(sb);
        return sb.toString();
    }

    /** Applied by {@code bt:control}: attributes for the template element. */
    final Map<String, String> attributesForTemplate() {
        Map<String, String> a = new LinkedHashMap<>();
        AddAttributesToRender(a);
        return a;
    }

    final String innerHtmlForTemplate(String templateTag) {
        return RenderInnerHtml(templateTag);
    }

    // ------------------------------------------------------------------ tree helpers (Page contract)

    /** Pre-order list of {@code roots} and all their descendants. */
    public static List<Control<?>> Descendants(Iterable<? extends Control<?>> roots) {
        List<Control<?>> out = new ArrayList<>();
        for (Control<?> r : roots) collect(r, out);
        return out;
    }

    private static void collect(Control<?> c, List<Control<?>> out) {
        out.add(c);
        if (c.controls != null) for (Control<?> ch : c.controls) collect(ch, out);
    }

    /** The control whose {@code UniqueID} equals {@code uniqueId} ({@code __EVENTTARGET}), or {@code null}. */
    public static Control<?> FindByUniqueID(Iterable<? extends Control<?>> roots, String uniqueId) {
        if (uniqueId == null) return null;
        for (Control<?> c : Descendants(roots)) {
            if (c.id != null && uniqueId.equals(c.UniqueID())) return c;
        }
        return null;
    }

    /** B4: the whole tree's state, keyed by {@link #ViewStateKey()}, plus the (d) registration list. */
    public static Map<String, Object> SaveViewStateTree(Iterable<? extends Control<?>> roots) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> registered = new ArrayList<>();
        for (Control<?> c : Descendants(roots)) {
            String k = c.ViewStateKey();
            out.put(k, c.SaveViewState());
            if (c.Visible() && !c.IsRenderSuppressed() && c.RequiresPostBackRegistration()) registered.add(k);
        }
        out.put(REGISTERED_KEY, registered);
        return out;
    }

    /** B4: restores the tree state; children a control recreates while loading (grid items) are loaded too. */
    @SuppressWarnings("unchecked")
    public static void LoadViewStateTree(Iterable<? extends Control<?>> roots, Map<String, ?> saved) {
        if (saved == null) return;
        List<Control<?>> rs = new ArrayList<>();
        for (Control<?> r : roots) rs.add(r);
        for (Control<?> r : rs) loadRec(r, saved);
    }

    @SuppressWarnings("unchecked")
    private static void loadRec(Control<?> c, Map<String, ?> saved) {
        Object s = saved.get(c.ViewStateKey());
        if (s instanceof Map<?, ?> m) c.LoadViewState((Map<String, ?>) m);
        if (c.controls != null) {
            for (Control<?> ch : new ArrayList<>(c.controls.asList())) loadRec(ch, saved);
        }
    }

    /**
     * B4 [J-U5, J-U11]: applies {@code post} (Form for POST, QueryString for GET) to every control whose
     * key is posted or that was registered (d) when the state was saved. Returns the control that must
     * raise its event in {@link #RaisePostBackEventTree} (the last posted submit button), or {@code null}.
     */
    public static Control<?> LoadPostDataTree(Iterable<? extends Control<?>> roots, NameValueCollection post,
                                              Map<String, ?> saved) {
        Set<String> registered = new HashSet<>();
        if (saved != null && saved.get(REGISTERED_KEY) instanceof List<?> l) {
            for (Object o : l) registered.add(String.valueOf(o));
        }
        Control<?> raise = null;
        for (Control<?> c : Descendants(roots)) {
            if (c.ProcessPostData(post, registered.contains(c.ViewStateKey()))) raise = c;
        }
        return raise;
    }

    /** B2 step 6: the registered submit control, else the {@code IPostBackEventHandler} named by {@code __EVENTTARGET}. */
    public static void RaisePostBackEventTree(Iterable<? extends Control<?>> roots, NameValueCollection post,
                                              Control<?> registered) {
        if (registered != null) {
            registered.RaisePostBackEvent(null);
            return;
        }
        String target = post.get("__EVENTTARGET");
        if (target == null || target.isEmpty()) return;
        Control<?> c = FindByUniqueID(roots, target);
        if (c != null && c.IsPostBackEventHandler()) c.RaisePostBackEvent(post.get("__EVENTARGUMENT"));
    }

    /** Unmodifiable empty root list. */
    public static List<Control<?>> NoControls() { return Collections.emptyList(); }

    @Override
    public String toString() {
        return getClass().getName();
    }
}
