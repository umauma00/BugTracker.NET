package btnet.runtime.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private static CookieAuthenticationOptions options() {
        CookieAuthenticationOptions o = new CookieAuthenticationOptions();
        o.AuthenticationType = "ApplicationCookie";
        o.LoginPath = new PathString("/default.aspx");
        return o;
    }

    // Katana 3.0 ApplyRedirect: scheme://host + PathBase + LoginPath + ?ReturnUrl=EscapeDataString(PathBase+Path+Query) (judge S5:41).
    @Test
    void loginRedirectUri() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/BugFromEmail");
        req.addHeader("Host", "localhost:8080");
        assertEquals("http://localhost:8080/default.aspx?ReturnUrl=%2Fapi%2FBugFromEmail",
                SecurityConfig.loginRedirectUri(req, options()));
        req.setQueryString("a=1&b=x y");
        assertEquals("http://localhost:8080/default.aspx?ReturnUrl=%2Fapi%2FBugFromEmail%3Fa%3D1%26b%3Dx%20y",
                SecurityConfig.loginRedirectUri(req, options()));
    }

    // Katana IsAjaxRequest: X-Requested-With in the query string or in the headers.
    @Test
    void ajaxDetection() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/BugQuery");
        assertFalse(SecurityConfig.IsAjaxRequest(req));
        req.addHeader("X-Requested-With", "XMLHttpRequest");
        assertTrue(SecurityConfig.IsAjaxRequest(req));
        MockHttpServletRequest q = new MockHttpServletRequest("GET", "/api/BugQuery");
        q.setQueryString("X-Requested-With=XMLHttpRequest");
        assertTrue(SecurityConfig.IsAjaxRequest(q));
    }

    // Paths are matched case-insensitively below the context path (B8).
    @Test
    void pathOfIsLowerCased() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ctx/API/Login");
        req.setContextPath("/ctx");
        assertEquals("/api/login", SecurityConfig.pathOf(req));
    }

    // SignIn stores the principal in the session without changing the session id; SignOut removes only it.
    @Test
    void signInAndOutKeepTheSession() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/default.aspx");
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.getSession(true).setAttribute("page", "x");
        String sid = req.getSession().getId();
        IOwinContext ctx = IOwinContext.of(req, res);
        ctx.Authentication().SignIn(new ClaimsIdentity(java.util.List.of(new Claim(ClaimTypes.Name, "admin")), "ApplicationCookie"));
        assertEquals(sid, req.getSession().getId());
        org.springframework.security.core.context.SecurityContext sc =
                (org.springframework.security.core.context.SecurityContext) req.getSession().getAttribute(AuthenticationManager.SESSION_KEY);
        assertTrue(sc.getAuthentication().isAuthenticated());
        assertEquals("admin", ((ClaimsPrincipal) sc.getAuthentication().getPrincipal()).Identity().Name());
        ctx.Authentication().SignOut();
        assertEquals(null, req.getSession().getAttribute(AuthenticationManager.SESSION_KEY));
        assertEquals("x", req.getSession().getAttribute("page"));
    }
}
