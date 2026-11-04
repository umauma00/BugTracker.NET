package btnet.runtime.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Web API {@code System.Web.Http.HttpConfiguration} as {@code App_Start/WebApiConfig.cs}
 * uses it. Routing itself is done by Spring ({@code @RequestMapping("/api/<Name>")}, RULEBOOK
 * §2A); this class only records what {@code Register} declares:
 * {@code config.MapHttpAttributeRoutes(); config.Routes().MapHttpRoute("DefaultApi", "api/{controller}/{id}", defaults);}.
 */
public class HttpConfiguration {
    private boolean attributeRoutes;
    private final HttpRouteCollection routes = new HttpRouteCollection();

    public void MapHttpAttributeRoutes() {
        attributeRoutes = true;
    }

    public boolean attributeRoutesMapped() {
        return attributeRoutes;
    }

    public HttpRouteCollection Routes() {
        return routes;
    }

    /** {@code config.Routes}: records {@code MapHttpRoute} calls. */
    public static final class HttpRouteCollection {
        private final List<String[]> routes = new ArrayList<>();
        private final List<Object> defaults = new ArrayList<>();

        HttpRouteCollection() {
        }

        /** {@code MapHttpRoute(name:, routeTemplate:, defaults:)}; {@code defaults} is any object (e.g. a map). */
        public void MapHttpRoute(String name, String routeTemplate, Object defaults) {
            routes.add(new String[] {name, routeTemplate});
            this.defaults.add(defaults);
        }

        public void MapHttpRoute(String name, String routeTemplate) {
            MapHttpRoute(name, routeTemplate, null);
        }

        /** Recorded {name, routeTemplate} pairs. */
        public List<String[]> recorded() {
            return Collections.unmodifiableList(routes);
        }

        public int Count() {
            return routes.size();
        }
    }
}
