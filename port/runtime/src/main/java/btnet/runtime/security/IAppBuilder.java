package btnet.runtime.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stands for OWIN {@code Owin.IAppBuilder} [J-C14]. Only the member the source
 * uses: {@link #UseCookieAuthentication(CookieAuthenticationOptions)}, which
 * records the options. {@link SecurityConfig} creates one, runs
 * {@code new btnet.Startup().Configuration(builder)} at boot, and reads the
 * recorded options.
 */
public class IAppBuilder {
    private final List<CookieAuthenticationOptions> cookieOptions = new ArrayList<>();

    public IAppBuilder() {
    }

    /** {@code app.UseCookieAuthentication(options)}: records {@code options}; returns this. */
    public IAppBuilder UseCookieAuthentication(CookieAuthenticationOptions options) {
        if (options == null) {
            throw new btnet.runtime.ArgumentNullException("options");
        }
        cookieOptions.add(options);
        return this;
    }

    /** The recorded cookie options, in registration order. */
    public List<CookieAuthenticationOptions> CookieAuthenticationOptions() {
        return Collections.unmodifiableList(cookieOptions);
    }
}
