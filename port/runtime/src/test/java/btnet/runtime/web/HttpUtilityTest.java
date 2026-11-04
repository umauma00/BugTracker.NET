package btnet.runtime.web;

import btnet.runtime.NameValueCollection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exact .NET 4.5 HttpUtility output (System.Web.Util.HttpEncoder / WebUtility reference source). */
class HttpUtilityTest {

    @Test
    void htmlEncodeEncodesFiveCharsAndLatin1() {
        // .NET 4.x: < > " & ' → &lt; &gt; &quot; &amp; &#39;; U+00A0–U+00FF → &#NNN;
        assertEquals("&lt;a href=&quot;x&quot;&gt;O&#39;Neil &amp; co&lt;/a&gt;",
            HttpUtility.HtmlEncode("<a href=\"x\">O'Neil & co</a>"));
        assertEquals("caf&#233; &#160;&#255;", HttpUtility.HtmlEncode("café  ÿ"));
        // U+0100 and above (BMP) are not encoded
        assertEquals("Ā€", HttpUtility.HtmlEncode("Ā€"));
    }

    @Test
    void htmlEncodeNullAndSurrogates() {
        assertNull(HttpUtility.HtmlEncode((String) null));
        assertEquals("", HttpUtility.HtmlEncode(""));
        // 4.5 Strict conformance: a surrogate pair → &#scalar;, a lone surrogate → U+FFFD
        assertEquals("&#128512;", HttpUtility.HtmlEncode("😀"));
        assertEquals("a�b", HttpUtility.HtmlEncode("a\ud83db"));
    }

    @Test
    void htmlEncodeObjectOverload() {
        // HttpUtility.HtmlEncode(object): null → null, IHtmlString unencoded, else Convert.ToString
        assertNull(HttpUtility.HtmlEncode((Object) null));
        assertEquals("<b>", HttpUtility.HtmlEncode((Object) new HtmlString("<b>")));
        assertEquals("True", HttpUtility.HtmlEncode((Object) Boolean.TRUE));
    }

    @Test
    void htmlDecode() {
        // WebUtility.HtmlDecode: named (HTML 4 + apos), decimal, hex; unknown kept; ';' required
        assertEquals("<a> & \"'é€♥", HttpUtility.HtmlDecode("&lt;a&gt; &amp; &quot;&apos;&eacute;&euro;&hearts;"));
        assertEquals("AB😀", HttpUtility.HtmlDecode("&#65;&#x42;&#x1F600;"));
        assertEquals("&bogus; &amp &#xD800;", HttpUtility.HtmlDecode("&bogus; &amp &#xD800;"));
        assertEquals("a&b", HttpUtility.HtmlDecode("a&b"));
        assertNull(HttpUtility.HtmlDecode(null));
    }

    @Test
    void entityTableHas253Entries() {
        assertEquals(253, HttpUtility.ENTITIES.size());
        assertEquals('ÿ', HttpUtility.ENTITIES.get("yuml").charValue());
        assertEquals('Ÿ', HttpUtility.ENTITIES.get("Yuml").charValue());
    }

    @Test
    void urlEncodeLowerHexPlusForSpace() {
        // HttpUtility.UrlEncode: UTF-8, ' ' → '+', safe = A-Za-z0-9 - _ . ! * ( ), lower-case hex
        assertEquals("a+b%2fc%3fd%3de%26f", HttpUtility.UrlEncode("a b/c?d=e&f"));
        assertEquals("-_.!*()%27%7e", HttpUtility.UrlEncode("-_.!*()'~"));
        assertEquals("caf%c3%a9", HttpUtility.UrlEncode("café"));
        assertEquals("%2fedit_bug.aspx%3fid%3d3", HttpUtility.UrlEncode("/edit_bug.aspx?id=3"));
        assertNull(HttpUtility.UrlEncode(null));
    }

    @Test
    void urlDecode() {
        // '+' → space, %XX as UTF-8 bytes, %uXXXX, invalid escapes kept
        assertEquals("a b/c", HttpUtility.UrlDecode("a+b%2Fc"));
        assertEquals("café", HttpUtility.UrlDecode("caf%C3%A9"));
        assertEquals("€", HttpUtility.UrlDecode("%u20AC"));
        assertEquals("100%", HttpUtility.UrlDecode("100%"));
        assertEquals("%zz%4", HttpUtility.UrlDecode("%zz%4"));
    }

    @Test
    void urlPathEncode() {
        // UrlPathEncode: spaces → %20 and non-ASCII in the path; query untouched
        assertEquals("/a%20b/caf%c3%a9.aspx?q=a b", HttpUtility.UrlPathEncode("/a b/café.aspx?q=a b"));
    }

    @Test
    void parseQueryStringLikeHttpValueCollection() {
        // FillFromString: "a" without '=' goes under the null key; trailing '&' adds (null, "")
        NameValueCollection c = HttpUtility.ParseQueryString("x=1&x=2&a&y=%41+b&");
        assertEquals("1,2", c.get("x"));
        assertEquals("A b", c.get("y"));
        assertArrayEquals(new String[] {"a", ""}, c.GetValues(null));
    }
}
