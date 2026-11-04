package btnet.runtime.web;

import java.util.function.Consumer;

/**
 * Web API {@code System.Web.Http.GlobalConfiguration} ({@code Global.asax.cs:27}
 * {@code GlobalConfiguration.Configure(WebApiConfig.Register)} →
 * {@code GlobalConfiguration.Configure(WebApiConfig::Register)}).
 */
public final class GlobalConfiguration {
    private GlobalConfiguration() {
    }

    public static final HttpConfiguration Configuration = new HttpConfiguration();

    public static void Configure(Consumer<HttpConfiguration> configurationCallback) {
        configurationCallback.accept(Configuration);
    }
}
