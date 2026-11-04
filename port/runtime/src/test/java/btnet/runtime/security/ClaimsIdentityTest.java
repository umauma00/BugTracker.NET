package btnet.runtime.security;

import btnet.runtime.ArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimsIdentityTest {

    // J-C3: duplicates kept; FindFirst returns the FIRST in insertion order (Security.cs:49 shadows :73).
    @Test
    void findFirstReturnsFirstDuplicate() {
        ClaimsIdentity id = new ClaimsIdentity(List.of(
                new Claim("og_can_only_see_own_reported", "False"),
                new Claim(ClaimTypes.Name, "admin"),
                new Claim("og_can_only_see_own_reported", "True")),
                "ApplicationCookie", ClaimTypes.Name, ClaimTypes.Role);
        assertEquals("False", id.FindFirst("og_can_only_see_own_reported").Value());
        assertEquals(2, id.FindAll("og_can_only_see_own_reported").size());
        assertEquals(3, id.Claims().size());
    }

    // .NET ClaimsIdentity.FindFirst/HasClaim compare the type OrdinalIgnoreCase, the value Ordinal.
    @Test
    void claimTypeIgnoresCaseValueDoesNot() {
        ClaimsIdentity id = new ClaimsIdentity(List.of(new Claim(ClaimTypes.Role, "admin")), "ApplicationCookie");
        assertTrue(id.HasClaim(ClaimTypes.Role.toUpperCase(), "admin"));
        assertFalse(id.HasClaim(ClaimTypes.Role, "Admin"));
        assertNull(id.FindFirst("us_id"));
    }

    // IsInRole matches ANY role claim (Security.cs:111-120 adds several).
    @Test
    void isInRoleMatchesAnyRoleClaim() {
        ClaimsIdentity id = new ClaimsIdentity(List.of(
                new Claim(ClaimTypes.Role, "admin"), new Claim(ClaimTypes.Role, "user")), "ApplicationCookie");
        ClaimsPrincipal p = new ClaimsPrincipal(id);
        assertTrue(p.IsInRole("admin"));
        assertTrue(p.IsInRole("user"));
        assertFalse(p.IsInRole("guest"));
        assertTrue(p.Identity().IsAuthenticated());
        assertEquals("ApplicationCookie", p.Identity().AuthenticationType());
    }

    // B7: anonymous -> IsAuthenticated false, Name "" (ASP.NET GenericIdentity("")).
    @Test
    void anonymousPrincipal() {
        ClaimsPrincipal a = ClaimsPrincipal.Anonymous();
        assertFalse(a.Identity().IsAuthenticated());
        assertEquals("", a.Identity().Name());
        assertFalse(a.IsInRole("user"));
    }

    // Name = first claim of NameClaimType; none -> null (.NET).
    @Test
    void nameClaim() {
        assertNull(new ClaimsIdentity(List.of(), "x").Name());
        assertEquals("bob", new ClaimsIdentity(List.of(new Claim(ClaimTypes.Name, "bob"), new Claim(ClaimTypes.Name, "eve"))).Name());
    }

    // Katana PathString: a non-empty value must start with '/'.
    @Test
    void pathString() {
        assertEquals("/default.aspx", new PathString("/default.aspx").toString());
        assertThrows(ArgumentException.class, () -> new PathString("default.aspx"));
    }

    // Startup.ConfigureAuth records its options on the builder (J-C14).
    @Test
    void appBuilderRecordsCookieOptions() {
        IAppBuilder app = new IAppBuilder();
        CookieAuthenticationOptions o = new CookieAuthenticationOptions();
        o.AuthenticationType = "ApplicationCookie";
        o.LoginPath = new PathString("/default.aspx");
        app.UseCookieAuthentication(o);
        assertEquals("/default.aspx", app.CookieAuthenticationOptions().get(0).LoginPath.Value());
        assertEquals("ReturnUrl", app.CookieAuthenticationOptions().get(0).ReturnUrlParameter);
    }
}
