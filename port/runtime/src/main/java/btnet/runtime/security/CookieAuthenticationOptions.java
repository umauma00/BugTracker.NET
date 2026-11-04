package btnet.runtime.security;

/**
 * Stands for {@code Microsoft.Owin.Security.Cookies.CookieAuthenticationOptions}
 * (Katana 3.0.0) [J-C14]: public fields, set by the translated
 * {@code Startup.ConfigureAuth} in source order (C6). {@link SecurityConfig}
 * takes the API login redirect from {@link #LoginPath} and
 * {@link #ReturnUrlParameter}.
 */
public class CookieAuthenticationOptions {
    /** Katana default {@code "Cookies"}; the source sets {@code "ApplicationCookie"}. */
    public String AuthenticationType = "Cookies";
    /** {@code LoginPath}; empty = no redirect (401 stays 401). */
    public PathString LoginPath = PathString.Empty;
    /** Katana default {@code "ReturnUrl"}. */
    public String ReturnUrlParameter = "ReturnUrl";
}
