package btnet.runtime.web.ui;

import btnet.runtime.ArgumentOutOfRangeException;
import btnet.runtime.NameValueCollection;
import btnet.runtime.web.HttpException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** .NET-semantics checks of {@code btnet.runtime.web.ui} (RULEBOOK §2B B4/B5, §3 [J-U1, J-U7, J-U11, J-U18, S1-27]). */
class ServerControlsTest {

    // HttpUtility.HtmlEncode (.NET 4.5): < > " & ' plus U+00A0..U+00FF as &#NNN;
    @Test
    void htmlEncodeMatchesDotNet45() {
        assertEquals("&lt;a href=&#39;x&#39;&gt;&amp;&#233;&quot;", Html.encode("<a href='x'>&é\""));
        assertEquals("", Html.encode(null));
        // HtmlAttributeEncode: " & < ' only
        assertEquals("a&lt;b>&amp;&quot;&#39;", Html.attr("a<b>&\"'"));
    }

    // ListItem.Text/Value fall back to each other; Equals compares Value and Text; ToString = Text
    @Test
    void listItemFallbacksAndEquality() {
        ListItem a = new ListItem("x");
        assertEquals("x", a.Value());
        ListItem b = new ListItem(null, "7");
        assertEquals("7", b.Text());
        assertEquals("", new ListItem().Text());
        assertEquals(new ListItem("t", "v"), new ListItem("t", "v"));
        assertNotEquals(new ListItem("t", "v"), new ListItem("t", "w"));
        assertEquals("t", new ListItem("t", "v").toString());
    }

    // ListItemCollection: FindByValue/FindByText ordinal, null when absent; Remove by Equals; Insert bounds (ArrayList)
    @Test
    void listItemCollectionMembers() {
        ListItemCollection c = new ListItemCollection();
        c.Add(new ListItem("new", "1"));
        c.Add(new ListItem("closed", "5"));
        c.Insert(0, new ListItem("[none]", "0"));
        assertEquals("0", c.get(0).Value());
        assertEquals("closed", c.FindByValue("5").Text());
        assertNull(c.FindByValue("9"));
        assertNull(c.FindByText("New"));
        c.Remove(new ListItem("new", "1"));
        assertEquals(2, c.Count());
        assertThrows(ArgumentOutOfRangeException.class, () -> c.Insert(3, new ListItem("z")));
        assertThrows(ArgumentOutOfRangeException.class, () -> c.get(2));
    }

    // DropDownList.SelectedIndex: with items and no selection, item 0 becomes selected (.NET)
    @Test
    void dropDownListSelectsFirstItemByDefault() {
        DropDownList d = new DropDownList("status");
        assertEquals(-1, d.SelectedIndex());
        assertNull(d.SelectedItem());
        d.Items().Add(new ListItem("new", "1"));
        d.Items().Add(new ListItem("closed", "5"));
        assertEquals(0, d.SelectedIndex());
        assertEquals("1", d.SelectedValue());
        assertTrue(d.Items().get(0).Selected());
    }

    // [J-U18] SelectedValue before DataBind is cached and applied after the items are rebuilt
    @Test
    void selectedValueIsCachedUntilDataBind() {
        DropDownList q = new DropDownList("query").DataTextField("Description").DataValueField("Id");
        q.SelectedValue("2");
        q.DataSource(List.of(row("Description", "all", "Id", 1), row("Description", "mine", "Id", 2)));
        q.DataBind();
        assertEquals("2", q.SelectedValue());
        assertEquals("mine", q.SelectedItem().Text());
    }

    // [J-U18] a cached value missing from the bound items → ArgumentOutOfRangeException (stale queryId = 500)
    @Test
    void staleSelectedValueFailsAtDataBind() {
        DropDownList q = new DropDownList("query").DataTextField("Description").DataValueField("Id");
        q.SelectedValue("99");
        q.DataSource(List.of(row("Description", "all", "Id", 1)));
        ArgumentOutOfRangeException e = assertThrows(ArgumentOutOfRangeException.class, q::DataBind);
        assertTrue(e.getMessage().startsWith("'query' has a SelectedValue which is invalid because it does not exist in the list of items."),
                e.getMessage());
    }

    // [J-U18] with items present, an unknown SelectedValue throws immediately
    @Test
    void unknownSelectedValueWithItemsThrows() {
        DropDownList d = new DropDownList("p");
        d.Items().Add(new ListItem("a", "1"));
        assertThrows(ArgumentOutOfRangeException.class, () -> d.SelectedValue("2"));
    }

    // DropDownList.RenderContents → HttpException "Cannot have multiple items selected in a DropDownList."
    @Test
    void dropDownListWithTwoSelectedItemsFailsToRender() {
        DropDownList d = new DropDownList("p");
        d.Items().Add(new ListItem("a", "1"));
        d.Items().Add(new ListItem("b", "2"));
        d.Items().FindByValue("1").Selected(true);
        d.Items().FindByValue("2").Selected(true);
        HttpException e = assertThrows(HttpException.class, () -> d.RenderControlToString());
        assertTrue(e.getMessage().contains("Cannot have multiple items selected in a DropDownList."));
    }

    // B5 [J-U8]: onchange = declared script, then __doPostBack('x','')
    @Test
    void autoPostBackMergesDeclaredOnchange() {
        ListBox p = new ListBox("project").SelectionMode(ListSelectionMode.Multiple).Rows(6).AutoPostBack(true);
        p.Attributes().set("onchange", "set_project_changed()");
        Map<String, String> a = p.attributesForTemplate();
        assertEquals("set_project_changed();__doPostBack('project','')", a.get("onchange"));
        assertEquals("project", a.get("name"));
        assertEquals("6", a.get("size"));
        assertEquals("multiple", a.get("multiple"));
        p.AutoPostBack(false);
        assertEquals("set_project_changed()", p.attributesForTemplate().get("onchange"));
    }

    // [S1-27] fluent setters keep the concrete type; [S1-32] Style(name, value)
    @Test
    void fluentSettersReturnConcreteType() {
        HtmlInputSubmit s = new HtmlInputSubmit("submit_button").Value("Update");
        assertEquals("Update", s.Value());
        HtmlGenericControl tr = new HtmlGenericControl("tags_row").Style("display", "none");
        assertEquals("display:none;", tr.Style().Value());
        assertEquals("none", tr.Style().get("DISPLAY"));
        assertEquals("display:none;", tr.attributesForTemplate().get("style"));
    }

    // HtmlContainerControl: InnerText setter encodes, getter decodes
    @Test
    void innerTextIsEncodedInnerHtml() {
        HtmlGenericControl msg = new HtmlGenericControl("msg");
        assertNull(msg.innerHtmlForTemplate("span"));
        msg.InnerText("a < b & c");
        assertEquals("a &lt; b &amp; c", msg.InnerHtml());
        assertEquals("a < b & c", msg.InnerText());
        assertEquals("a &lt; b &amp; c", msg.innerHtmlForTemplate("span"));
    }

    // [J-U1] TemplateColumn controls are named <Grid>$ctl<NN>$<GroupName>, id <Grid>_ctl<NN>_<ID>; header = ctl01
    @Test
    void dataGridTemplateColumnNaming() {
        DataGrid g = permissionsGrid();
        g.DataSource(List.of(row("pj_name", "project 1", "pj_id", 1), row("pj_name", "", "pj_id", 2)));
        g.DataBind();
        assertEquals(2, g.Items().size());
        RadioButton none = (RadioButton) g.Items().get(0).FindControl("none");
        assertEquals("MyDataGrid$ctl02$none", none.UniqueID());
        assertEquals("MyDataGrid_ctl02_none", none.ClientID());
        assertEquals("MyDataGrid$ctl02$permissions", none.UniqueGroupName());
        assertEquals("0", none.ValueAttribute());
        assertEquals("1", g.Items().get(0).Cells().get(1).Text());
        // BoundColumn: empty value renders &nbsp; (.NET)
        assertEquals("&nbsp;", g.Items().get(1).Cells().get(0).Text());
        String html = g.innerHtmlForTemplate("table");
        assertTrue(html.contains("name=\"MyDataGrid$ctl03$permissions\""), html);
        assertFalse(html.contains(">1</td>"), "invisible column is not rendered: " + html);
    }

    // B4 (c): items persist; on postback they are recreated before posted values load
    @Test
    void dataGridItemsSurviveViewStateRoundTrip() {
        DataGrid g = permissionsGrid();
        g.DataSource(List.of(row("pj_name", "p1", "pj_id", 1), row("pj_name", "p2", "pj_id", 2)));
        g.DataBind();
        Map<String, Object> saved = Control.SaveViewStateTree(List.of(g));

        DataGrid g2 = permissionsGrid();               // a new page instance: initializer only
        Control.LoadViewStateTree(List.of(g2), saved);
        assertEquals(2, g2.Items().size());
        assertEquals("2", g2.Items().get(1).Cells().get(1).Text());
        RadioButton none = (RadioButton) g2.Items().get(0).FindControl("none");
        assertTrue(none.Checked(), "binding state restored");

        NameValueCollection post = new NameValueCollection();
        post.Add("MyDataGrid$ctl02$permissions", "3");
        Control.LoadPostDataTree(List.of(g2), post, saved);
        assertFalse(((RadioButton) g2.Items().get(0).FindControl("none")).Checked());
        assertTrue(((RadioButton) g2.Items().get(0).FindControl("reporter")).Checked());
        // second row: registered radios with nothing posted → unchecked (J-U11)
        assertFalse(((RadioButton) g2.Items().get(1).FindControl("none")).Checked());
    }

    // B4 [J-U11]: a registered checkbox absent from the post is unchecked; an absent hidden field keeps its value
    @Test
    void postDataAppliesOnlyToPostedOrRegisteredControls() {
        CheckBox admin = new CheckBox("admin").Checked(true);
        CheckBox hidden = new CheckBox("use_fckeditor").Checked(true).Visible(false);
        HtmlInputHidden prev = new HtmlInputHidden("prev_project").Value("4");
        List<Control<?>> roots = List.of(admin, hidden, prev);
        Map<String, Object> saved = Control.SaveViewStateTree(roots);

        CheckBox admin2 = new CheckBox("admin");
        CheckBox hidden2 = new CheckBox("use_fckeditor");
        HtmlInputHidden prev2 = new HtmlInputHidden("prev_project");
        List<Control<?>> roots2 = List.of(admin2, hidden2, prev2);
        Control.LoadViewStateTree(roots2, saved);
        Control.LoadPostDataTree(roots2, new NameValueCollection(), saved);
        assertFalse(admin2.Checked());
        assertTrue(hidden2.Checked(), "invisible checkbox keeps its view-state Checked");
        assertEquals("4", prev2.Value());
    }

    // B4: a posted select value not among the restored items fails event validation (→ 500)
    @Test
    void unknownPostedValueFailsEventValidation() {
        DropDownList d = new DropDownList("priority");
        d.Items().Add(new ListItem("low", "1"));
        NameValueCollection post = new NameValueCollection();
        post.Add("priority", "7");
        assertThrows(btnet.runtime.ArgumentException.class, () -> d.ProcessPostData(post, false));
        post.Set("priority", "1");
        d.ProcessPostData(post, false);
        assertEquals("1", d.SelectedValue());
    }

    // B2 step 6: a submit button whose name is posted raises its click; else __EVENTTARGET → ItemCommand
    @Test
    void postBackEventsAreRaised() {
        List<String> log = new ArrayList<>();
        Button b = new Button("reindexLink").Text("Recreate Search Index");
        b.OnClick((s, e) -> log.add("click"));
        NameValueCollection post = new NameValueCollection();
        post.Add("reindexLink", "Recreate Search Index");
        Control<?> reg = Control.LoadPostDataTree(List.of(b), post, null);
        Control.RaisePostBackEventTree(List.of(b), post, reg);
        assertEquals(List.of("click"), log);

        DataGrid g = new DataGrid("MyDataGrid").AutoGenerateColumns(false)
                .Column(new BoundColumn("File", "file"))
                .Column(new HyperLinkColumn("Download", "Download", "url", "_blank"))
                .Column(new ButtonColumn("Delete", "Delete", "dlt"));
        g.OnItemCommand((s, e) -> log.add(e.CommandName() + ":" + e.Item().ItemIndex() + ":"
                + g.Items().get(e.Item().ItemIndex()).Cells().get(0).Text()));
        g.DataSource(List.of(row("file", "a.bak", "url", "x?a"), row("file", "b.bak", "url", "x?b")));
        g.DataBind();
        LinkButton del = (LinkButton) g.Items().get(1).Cells().get(2).Controls().get(0);
        assertEquals("javascript:__doPostBack('MyDataGrid$ctl03$ctl01','')", del.attributesForTemplate().get("href"));
        NameValueCollection post2 = new NameValueCollection();
        post2.Add("__EVENTTARGET", del.UniqueID());
        post2.Add("__EVENTARGUMENT", "");
        Control.RaisePostBackEventTree(List.of(g), post2, null);
        assertEquals("dlt:1:b.bak", log.get(1));
    }

    // An unbound DataGrid renders nothing (no control hierarchy)
    @Test
    void unboundGridIsSuppressed() {
        assertTrue(new DataGrid("MyDataGrid").IsRenderSuppressed());
    }

    // CheckBoxList [J-U10]: item names x$i, ids x_i; checked = name present
    @Test
    void checkBoxListNamesAndPostback() {
        CheckBoxList l = new CheckBoxList("lstAttachments");
        l.Items().Add(new ListItem("a.txt", "10"));
        l.Items().Add(new ListItem("b.txt", "11"));
        String html = l.innerHtmlForTemplate("table");
        assertTrue(html.contains("id=\"lstAttachments_1\" type=\"checkbox\" name=\"lstAttachments$1\""), html);
        NameValueCollection post = new NameValueCollection();
        post.Add("lstAttachments$1", "on");
        l.ProcessPostData(post, true);
        assertFalse(l.Items().get(0).Selected());
        assertTrue(l.Items().get(1).Selected());
    }

    private static DataGrid permissionsGrid() {
        return new DataGrid("MyDataGrid").CssClass("datat").BorderColor("black").CellPadding(3)
                .AutoGenerateColumns(false).HeaderStyle("datah").ItemStyle("datad")
                .Column(new BoundColumn("Project", "pj_name"))
                .Column(new BoundColumn("Project", "pj_id").Visible(false))
                .Column(new TemplateColumn("Permissions", item -> {
                    String[][] defs = {{"none", "none", "0"}, {"readonly", "view only", "1"},
                            {"reporter", "report (add and comment only)", "3"}, {"edit", "all (add and edit)", "2"}};
                    for (String[] d : defs) {
                        RadioButton rb = new RadioButton(d[0]);
                        rb.GroupName("permissions");
                        rb.Text(d[1]);
                        rb.Attributes().set("value", d[2]);
                        item.Add(rb);
                    }
                }, (item, drv) -> ((RadioButton) item.FindControl("none")).Checked(true)));
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
