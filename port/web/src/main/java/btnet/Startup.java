// Source: src/BugTracker.Web/App_Start/Startup.Auth.cs + src/BugTracker.Web/Startup.cs
package btnet;

import btnet.runtime.security.CookieAuthenticationOptions;
import btnet.runtime.security.IAppBuilder;
import btnet.runtime.security.PathString;

// [assembly: OwinStartup] → invoked by SecurityConfig
// inventory: src/BugTracker.Web/Startup.cs L8
public class Startup {

    // Startup.cs
    public void Configuration(IAppBuilder app) {
        ConfigureAuth(app);
    }

    // App_Start/Startup.Auth.cs
    public void ConfigureAuth(IAppBuilder app) {
        // Enable the application to use a cookie to store information for the signed in user
        // inventory: src/BugTracker.Web/App_Start/Startup.Auth.cs L16
        var o = new CookieAuthenticationOptions();
        o.AuthenticationType = "ApplicationCookie";
        o.LoginPath = new PathString("/default.aspx");
        app.UseCookieAuthentication(o);
    }
}

// PORT STATUS: confidence=high todos=0
