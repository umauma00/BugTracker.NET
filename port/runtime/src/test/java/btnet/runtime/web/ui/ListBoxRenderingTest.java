package btnet.runtime.web.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import btnet.runtime.NameValueCollection;
import btnet.runtime.web.HttpException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** [S2-20] ListBox Rows/SelectionMode render size/multiple; posted values select per mode. */
class ListBoxRenderingTest {

    private static ListBox box(String id) {
        ListBox l = new ListBox(id);
        l.Items().Add(new ListItem("a", "1"));
        l.Items().Add(new ListItem("b", "2"));
        l.Items().Add(new ListItem("c", "3"));
        return l;
    }

    @Test
    void defaultsAreFourRowsSingle() {
        ListBox l = box("lst");
        assertEquals(4, l.Rows());
        assertEquals(ListSelectionMode.Single, l.SelectionMode());
        Map<String, String> a = l.attributesForTemplate();
        assertEquals("4", a.get("size"));
        assertTrue(a.containsKey("multiple"));
        assertNull(a.get("multiple"));                    // bt:control removes a literal multiple
        assertEquals("lst", a.get("name"));
        assertEquals("lst", a.get("id"));
    }

    @Test
    void rowsAndMultipleRender() {
        ListBox l = box("project").Rows(6).SelectionMode(ListSelectionMode.Multiple);
        Map<String, String> a = l.attributesForTemplate();
        assertEquals("6", a.get("size"));
        assertEquals("multiple", a.get("multiple"));
        String self = l.RenderControlToString();
        assertTrue(self.startsWith("<select"), self);
        assertTrue(self.contains("size=\"6\""), self);
        assertTrue(self.contains("multiple=\"multiple\""), self);
    }

    @Test
    void multiplePostSelectsEveryMatchingItem() {
        ListBox l = box("project").SelectionMode(ListSelectionMode.Multiple);
        NameValueCollection post = new NameValueCollection();
        post.Add("project", "1");
        post.Add("project", "3");
        l.ProcessPostData(post, true);
        assertArrayEquals(new int[] {0, 2}, l.GetSelectedIndices());
        String html = l.innerHtmlForTemplate("select");
        assertTrue(html.contains("<option selected=\"selected\" value=\"1\">a</option>"), html);
        assertTrue(html.contains("<option value=\"2\">b</option>"), html);
        assertTrue(html.contains("<option selected=\"selected\" value=\"3\">c</option>"), html);
    }

    @Test
    void singlePostSelectsTheFirstValueOnly() {
        ListBox l = box("lst");
        NameValueCollection post = new NameValueCollection();
        post.Add("lst", "2");
        post.Add("lst", "3");
        l.ProcessPostData(post, false);
        assertArrayEquals(new int[] {1}, l.GetSelectedIndices());
        assertFalse(l.Items().get(2).Selected());
    }

    @Test
    void singleWithTwoSelectedItemsFailsToRender() {
        ListBox l = box("lst");
        l.Items().get(0).Selected(true);
        l.Items().get(1).Selected(true);
        assertThrows(HttpException.class, () -> l.innerHtmlForTemplate("select"));
        l.SelectionMode(ListSelectionMode.Multiple);
        assertTrue(l.innerHtmlForTemplate("select").contains("value=\"2\""));
    }
}
