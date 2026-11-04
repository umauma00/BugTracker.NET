package btnet.runtime.security;

import btnet.runtime.net.Uri;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps the OWIN pipeline of {@code Startup.cs} + {@code App_Start/Startup.Auth.cs}
 * (Katana 3.0.0 cookie authentication) onto Spring Security (B7, J-C14).
 *
 * <ul>
 *   <li>At boot, runs {@code new btnet.Startup().Configuration(builder)} (looked up
 *       reflectively: {@code btnet.Startup} lives in {@code port/web}, which the
 *       runtime does not depend on) and keeps the recorded
 *       {@link CookieAuthenticationOptions}.</li>
 *   <li>CSRF disabled (the view-state MAC is the original's protection); Spring's
 *       security headers, request cache, form login and {@code /logout} are off
 *       (the original sends none of them); session fixation protection off
 *       (the original keeps {@code Session} across login).</li>
 *   <li>{@code /api/**} requires an authenticated identity except {@code /api/login};
 *       every other path is {@code permitAll} — page authorization stays in the
 *       translated {@code BasePage.OnPreInit}. Paths compare case-insensitively (B8).</li>
 *   <li>Unauthenticated API request (Web API {@code [Authorize]} 401, rewritten by
 *       Katana {@code CookieAuthenticationProvider.ApplyRedirect}): an AJAX request
 *       ({@code X-Requested-With: XMLHttpRequest} in the query string or a header)
 *       stays 401 with the Web API body; otherwise 302 to the absolute
 *       {@code scheme://host + PathBase + LoginPath + "?ReturnUrl=" +
 *       Uri.EscapeDataString(PathBase + Path + QueryString)} (judge S5:41).</li>
 *   <li>The identity lives in the {@code HttpSession} security context
 *       ({@link AuthenticationManager}). TODO(port): persistent auth cookie (OWIN
 *       14-day sliding cookie becomes session-bound, 120 min); not judged.</li>
 * </ul>
 *
 * <p>Picked up by component scanning of the {@code btnet} base package.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Web API 5.2.3 {@code AuthorizeAttribute} 401 body. */
    static final String WEB_API_401_BODY = "{\"Message\":\"Authorization has been denied for this request.\"}";

    private static volatile CookieAuthenticationOptions cookieOptions;

    /** The cookie options recorded by {@code Startup.Configuration} (null if no Startup was found). */
    public static CookieAuthenticationOptions CookieOptions() {
        CookieAuthenticationOptions o = cookieOptions;
        if (o == null) {
            synchronized (SecurityConfig.class) {
                o = cookieOptions;
                if (o == null) {
                    o = runStartup();
                    cookieOptions = o;
                }
            }
        }
        return o;
    }

    /** Runs {@code new btnet.Startup().Configuration(builder)} and returns the first recorded cookie options. */
    static CookieAuthenticationOptions runStartup() {
        IAppBuilder builder = new IAppBuilder();
        try {
            Class<?> startup = Class.forName("btnet.Startup");
            Object instance = startup.getDeclaredConstructor().newInstance();
            startup.getMethod("Configuration", IAppBuilder.class).invoke(instance, builder);
        } catch (ClassNotFoundException e) {
            log.warn("btnet.Startup not found; no OWIN cookie options (API 401s are not redirected)");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw btnet.runtime.Exceptions.wrap(e);
        } catch (ReflectiveOperationException e) {
            throw btnet.runtime.Exceptions.wrap(e);
        }
        List<CookieAuthenticationOptions> recorded = builder.CookieAuthenticationOptions();
        if (recorded.isEmpty()) {
            CookieAuthenticationOptions none = new CookieAuthenticationOptions();
            return none;
        }
        return recorded.get(0);
    }

    @Bean
    public SecurityFilterChain btnetSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieOptions(); // run Startup at boot, as OWIN does
        RequestMatcher apiLogin = r -> pathOf(r).equals("/api/login") || pathOf(r).startsWith("/api/login/");
        RequestMatcher api = r -> pathOf(r).equals("/api") || pathOf(r).startsWith("/api/");
        http
                .csrf(c -> c.disable())
                .headers(h -> h.disable())
                .requestCache(rc -> rc.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionFixation(f -> f.none()))
                .securityContext(s -> s.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(apiLogin).permitAll()
                        .requestMatchers(api).authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(katanaEntryPoint())
                        .accessDeniedHandler((req, res, ex) -> katanaEntryPoint().commence(req, res, null)));
        return http.build();
    }

    /** The Katana 3.0.0 {@code ApplyRedirect} behaviour for an unauthenticated API call. */
    static AuthenticationEntryPoint katanaEntryPoint() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException ex) -> {
            CookieAuthenticationOptions o = CookieOptions();
            if (o == null || o.LoginPath == null || !o.LoginPath.HasValue() || IsAjaxRequest(req)) {
                // TODO(port): Katana 3.0 may add an X-Responded-JSON header to the AJAX 401
                write401(res);
                return;
            }
            res.setStatus(302);
            res.setHeader("Location", loginRedirectUri(req, o));
        };
    }

    /** {@code scheme://host + PathBase + LoginPath + ?ReturnUrl=EscapeDataString(PathBase + Path + Query)}. */
    static String loginRedirectUri(HttpServletRequest req, CookieAuthenticationOptions o) {
        String pathBase = req.getContextPath() == null ? "" : req.getContextPath();
        String path = req.getRequestURI() == null ? "" : req.getRequestURI().substring(Math.min(pathBase.length(), req.getRequestURI().length()));
        String qs = req.getQueryString();
        String current = pathBase + path + (qs == null || qs.isEmpty() ? "" : "?" + qs);
        String host = req.getHeader("Host");
        if (host == null || host.isEmpty()) {
            int port = req.getServerPort();
            boolean dflt = ("http".equals(req.getScheme()) && port == 80) || ("https".equals(req.getScheme()) && port == 443);
            host = req.getServerName() + (dflt ? "" : ":" + port);
        }
        String param = o.ReturnUrlParameter == null ? "ReturnUrl" : o.ReturnUrlParameter;
        return req.getScheme() + "://" + host + pathBase + o.LoginPath.Value()
                + "?" + Uri.EscapeDataString(param) + "=" + Uri.EscapeDataString(current);
    }

    /** Katana {@code IsAjaxRequest}: {@code X-Requested-With=XMLHttpRequest} in the query or the headers. */
    static boolean IsAjaxRequest(HttpServletRequest req) {
        return "XMLHttpRequest".equals(queryValue(req.getQueryString(), "X-Requested-With"))
                || "XMLHttpRequest".equals(req.getHeader("X-Requested-With"));
    }

    /** First value of {@code name} in the raw query string (never touches the form body). */
    static String queryValue(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = java.net.URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            if (k.equalsIgnoreCase(name)) {
                return eq < 0 ? "" : java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void write401(HttpServletResponse res) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json; charset=utf-8");
        byte[] b = WEB_API_401_BODY.getBytes(StandardCharsets.UTF_8);
        res.setContentLength(b.length);
        res.getOutputStream().write(b);
    }

    /** Request path below the context path, lower-cased (case-insensitive matching, B8). */
    static String pathOf(HttpServletRequest r) {
        String uri = r.getRequestURI() == null ? "" : r.getRequestURI();
        String ctx = r.getContextPath() == null ? "" : r.getContextPath();
        if (!ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        return uri.toLowerCase(Locale.ROOT);
    }

    /**
     * The Spring Security {@code Authentication} that carries a signed-in
     * {@link ClaimsPrincipal}; authorities = the role claims. Authenticated iff
     * the principal's identity is.
     */
    public static final class ClaimsAuthenticationToken extends AbstractAuthenticationToken {
        private static final long serialVersionUID = 1L;

        private final ClaimsPrincipal principal;

        public ClaimsAuthenticationToken(ClaimsPrincipal principal) {
            super(authoritiesOf(principal));
            this.principal = principal;
            IIdentity id = principal.Identity();
            setAuthenticated(id != null && id.IsAuthenticated());
        }

        private static List<GrantedAuthority> authoritiesOf(ClaimsPrincipal p) {
            List<GrantedAuthority> r = new ArrayList<>();
            for (ClaimsIdentity i : p.Identities()) {
                for (Claim c : i.Claims()) {
                    if (c.Type().equalsIgnoreCase(i.RoleClaimType())) {
                        r.add(new SimpleGrantedAuthority(c.Value()));
                    }
                }
            }
            return r;
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public String getName() {
            IIdentity id = principal.Identity();
            String n = id == null ? null : id.Name();
            return n == null ? "" : n;
        }
    }
}
