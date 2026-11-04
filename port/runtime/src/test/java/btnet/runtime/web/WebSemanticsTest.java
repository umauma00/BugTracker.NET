package btnet.runtime.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** .NET semantics of request validation, IsLocalUrl, cookies, Response buffering/Redirect, view state. */
class WebSemanticsTest {

    @Test
    void requestValidationDangerousStrings() {
        // CrossSiteScriptingValidation.IsDangerousString: '<' + letter/!/ /?, or "&#"
        assertTrue(HttpRequest.isDangerousString("<script>"));
        assertTrue(HttpRequest.isDangerousString("a</b"));
        assertTrue(HttpRequest.isDangerousString("<!--"));
        assertTrue(HttpRequest.isDangerousString("&#60;"));
        assertFalse(HttpRequest.isDangerousString("a < b"));
        assertFalse(HttpRequest.isDangerousString("1<2"));
        assertFalse(HttpRequest.isDangerousString("x<"));
        assertFalse(HttpRequest.isDangerousString("&amp;"));
    }

    @Test
    void isLocalUrlMvc523() {
        UrlHelper u = new UrlHelper(null);
        assertTrue(u.IsLocalUrl("/"));
        assertTrue(u.IsLocalUrl("/bugs.aspx"));
        assertTrue(u.IsLocalUrl("~/bugs.aspx"));
        assertFalse(u.IsLocalUrl("edit_bug.aspx")); // relative: not local (BUG(port) site in util.cs)
        assertFalse(u.IsLocalUrl("//evil.com"));
        assertFalse(u.IsLocalUrl("/\\evil.com"));
        assertFalse(u.IsLocalUrl("http://x/"));
        assertFalse(u.IsLocalUrl(""));
        assertFalse(u.IsLocalUrl(null));
    }

    @Test
    void cookieSubkeys() {
        // default.aspx.cs:80-81: Response.Cookies["user"]["name"] = ""; ["NTLM"] = "0" → "name=&NTLM=0"
        HttpCookieCollection rc = new HttpCookieCollection(true, null);
        rc.get("user").set("name", "");
        rc.get("user").set("NTLM", "0");
        assertEquals(1, rc.Count());
        assertEquals("name=&NTLM=0", rc.get("user").Value());
        assertEquals("user=name=&NTLM=0; path=/", rc.get("user").toSetCookieHeader());
        HttpCookie req = HttpCookie.fromRequestPair("user=name=bob&NTLM=1");
        assertEquals("bob", req.get("name"));
        assertEquals("1", req.get("ntlm"));
        assertTrue(req.HasKeys());
        assertNull(new HttpCookieCollection(false, null).get("absent"));
    }

    @Test
    void responseBuffersAndRedirects() {
        HttpResponse r = new HttpResponse((HttpRequest) null);
        r.Write("a");
        r.Write((Object) null);
        r.Write((Object) Boolean.TRUE);
        assertEquals("aTrue", new String(r.bodyBytes(), StandardCharsets.UTF_8));
        assertEquals("text/html; charset=utf-8", r.effectiveContentType());
        RedirectException e = assertThrows(RedirectException.class, () -> r.Redirect("edit_bug.aspx?id=3"));
        assertEquals("/edit_bug.aspx?id=3", e.Location());
        assertEquals(302, r.StatusCode());
        assertEquals("Thread was being aborted.", e.getMessage());
        // .NET Redirect clears the body and writes the "Object moved" page
        assertEquals("<html><head><title>Object moved</title></head><body>\r\n<h2>Object moved to <a href=\"/edit_bug.aspx?id=3\">here</a>.</h2>\r\n</body></html>\r\n",
            new String(r.bodyBytes(), StandardCharsets.UTF_8));
        assertThrows(btnet.runtime.ArgumentException.class, () -> r.Redirect("a\nb"));
    }

    @Test
    void redirectEncodingAndResolution() {
        assertEquals("/a%20b.aspx?q=a b", HttpResponse.encodeRedirect("/a b.aspx?q=a b"));
        HttpResponse r = new HttpResponse((HttpRequest) null);
        r.Redirect("~/bugs.aspx", false);
        assertEquals("/bugs.aspx", r.RedirectLocation());
        r.Redirect("http://example.com/x", false);
        assertEquals("http://example.com/x", r.RedirectLocation());
    }

    @Test
    void responseClearKeepsHeaders() {
        HttpResponse r = new HttpResponse((HttpRequest) null);
        r.ContentType("text/plain");
        r.StatusCode(404);
        r.Write("x");
        r.Clear();
        assertEquals(0, r.bodyBytes().length);
        assertEquals(404, r.StatusCode());
        assertEquals("text/plain", r.ContentType());
    }

    @Test
    void responseEndIsAnError() {
        HttpResponse r = new HttpResponse((HttpRequest) null);
        ResponseEndException e = assertThrows(ResponseEndException.class, r::End);
        assertTrue(e instanceof Error); // never caught by catch (RuntimeException)
    }

    @Test
    void viewStateRoundTripAndMac() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("x", List.of("a", 1, true));
        String vs = ViewStateCodec.Serialize(state, "session-1");
        assertEquals(state, ViewStateCodec.Deserialize(vs, "session-1"));
        HttpException e = assertThrows(HttpException.class, () -> ViewStateCodec.Deserialize(vs, "session-2"));
        assertTrue(e.getMessage().startsWith("Validation of viewstate MAC failed."));
        assertThrows(HttpException.class, () -> ViewStateCodec.Deserialize("not base64!", null));
        assertNull(ViewStateCodec.Deserialize("", null));
    }

    @Test
    void applicationStateIsCaseInsensitiveAndNullRemoves() {
        HttpApplicationState a = ApplicationState.get();
        a.set("Custom_Logo", "x");
        assertEquals("x", a.get("custom_logo"));
        a.set("CUSTOM_LOGO", null);
        assertNull(a.get("Custom_Logo"));
    }

    @Test
    void cacheAddIfAbsent() {
        Cache c = HttpRuntime.Cache();
        c.Remove("t-key");
        assertNull(c.addIfAbsent("t-key", "v1"));
        assertEquals("v1", c.addIfAbsent("t-key", "v2"));
        c.set("t-key", null);
        assertNull(c.get("t-key"));
    }

    @Test
    void mapPath() {
        String root = HttpServerUtility.WebRoot();
        assertEquals(root, HttpServerUtility.mapPath(null, "/"));
        assertEquals(root + "/App_Data/uploads", HttpServerUtility.mapPath("App_Data\\uploads", "/"));
        assertEquals(root + "/custom/x.html", HttpServerUtility.mapPath("~/custom/x.html", "/"));
        assertThrows(HttpException.class, () -> HttpServerUtility.mapPath("../x", "/"));
    }

    @Test
    void windowsWildcard() {
        assertTrue(ScriptBundle.matches("EditBug.js", "*.js"));
        assertFalse(ScriptBundle.matches("EditBug.js.map", "*.js"));
    }
}
