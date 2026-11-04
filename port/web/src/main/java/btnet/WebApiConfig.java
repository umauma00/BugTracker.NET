// Source: src/BugTracker.Web/App_Start/WebApiConfig.cs
package btnet;

import btnet.runtime.web.HttpConfiguration;
import btnet.runtime.web.RouteParameter;
import java.util.LinkedHashMap;

public final class WebApiConfig {
    private WebApiConfig() {}

    public static void Register(HttpConfiguration config) {
        config.MapHttpAttributeRoutes();

        LinkedHashMap<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("id", RouteParameter.Optional);
        config.Routes().MapHttpRoute("DefaultApi", "api/{controller}/{id}", defaults);
    }
}

// PORT STATUS: confidence=high todos=0
