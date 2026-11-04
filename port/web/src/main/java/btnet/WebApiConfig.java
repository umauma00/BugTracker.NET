// Source: src/BugTracker.Web/App_Start/WebApiConfig.cs
package btnet;

public final class WebApiConfig {
    private WebApiConfig() {}

    // TODO(port): runtime (RULEBOOK §3) specifies no HttpConfiguration type; parameter kept as Object. Routing is expressed by @RequestMapping on each @RestController (RULEBOOK §2A JSON / Web API), so this body is intentionally empty and nothing calls Register.
    public static void Register(Object config) {
        // config.MapHttpAttributeRoutes(); → attribute routes are the Spring @RequestMapping/@GetMapping/@PostMapping annotations on the controllers.

        // config.Routes.MapHttpRoute(name: "DefaultApi", routeTemplate: "api/{controller}/{id}", defaults: new { id = RouteParameter.Optional });
        // → each controller carries @RequestMapping("/api/<Name minus Controller>") (RULEBOOK §2A, WebApiConfig.cs:14-18); {id} is bound by name from the query string.
    }
}

// PORT STATUS: confidence=medium todos=1
