package btnet.runtime.net;

import btnet.runtime.UriFormatException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriTest {

    // .NET 4.5 Uri.EscapeDataString: RFC 3986 unreserved kept, UTF-8, upper-case hex.
    @Test
    void escapeDataString() {
        assertEquals("a%20b%2Bc%26d%3D%2F~._-", Uri.EscapeDataString("a b+c&d=/~._-"));
        assertEquals("%C3%A9%E2%82%AC", Uri.EscapeDataString("é€"));
        assertEquals("%21%2A%27%28%29", Uri.EscapeDataString("!*'()"));
        assertEquals("", Uri.EscapeDataString(""));
    }

    // .NET c_MaxUriBufferSize: 0xFFF0 chars or more -> "Invalid URI: The Uri string is too long."
    @Test
    void escapeDataStringLengthLimit() {
        String big = "a".repeat(0xFFF0);
        UriFormatException e = assertThrows(UriFormatException.class, () -> Uri.EscapeDataString(big));
        assertEquals("Invalid URI: The Uri string is too long.", e.getMessage());
        assertEquals(0xFFEF, Uri.EscapeDataString(big.substring(1)).length());
    }

    // .NET Uri canonical form: lower-case scheme/host, default port dropped, "/" path, ToString unescapes.
    @Test
    void canonicalForms() {
        Uri u = new Uri("HTTP://LocalHost:80/a%20b/../c?x=%41%20y");
        assertEquals("http://localhost/c?x=A%20y", u.AbsoluteUri());
        assertEquals("http://localhost/c?x=A y", u.toString());
        assertEquals("/c?x=A%20y", u.PathAndQuery());
        assertEquals(80, u.Port());
        assertEquals("http://localhost:8080/", new Uri("http://localhost:8080").AbsoluteUri());
        assertEquals("http://h/a%20b", new Uri("http://h/a b").AbsoluteUri());
        assertEquals("http://h/a/b", new Uri("http://h\\a\\b").AbsoluteUri());
    }

    // .NET new Uri(relative) -> UriFormatException.
    @Test
    void relativeIsRejected() {
        assertThrows(UriFormatException.class, () -> new Uri("default.aspx"));
        assertThrows(UriFormatException.class, () -> new Uri("http://"));
    }

    // .NET 4.5 FormUrlEncodedContent: EscapeDataString with %20 -> '+', null -> "".
    @Test
    void formUrlEncodedContent() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("user", "a b");
        m.put("password", null);
        m.put("message", "x&y=zé");
        assertEquals("user=a+b&password=&message=x%26y%3Dz%C3%A9", FormUrlEncodedContent.encode(m));
    }
}
