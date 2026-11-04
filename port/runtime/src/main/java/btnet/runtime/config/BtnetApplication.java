package btnet.runtime.config;

import btnet.runtime.AppPaths;
import btnet.runtime.data.SqlConnection;
import btnet.runtime.web.LowercaseResourceResolver;
import btnet.runtime.web.ui.BtnetDialect;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot main of {@code port/web} and its MVC configuration — stands for the IIS/ASP.NET
 * host ({@code Web.config} {@code <system.web>}/{@code <system.webServer>}) of the original
 * (RULEBOOK §1, §2B B2/B8, §3):
 * <ul>
 * <li>component scan of {@code btnet} (pages, API controllers, runtime configs);</li>
 * <li>registers the Boot-managed DataSource with {@link SqlConnection#UseDataSource} and the
 *   Spring {@link Environment} with {@link ConfigurationManager#UseEnvironment} (overrides
 *   {@code btnet.setting.<Key>}); sets {@code AppPaths.SetWebRoot} from {@code btnet.webroot};</li>
 * <li>case-insensitive request paths ({@code PathPatternParser.setCaseSensitive(false)}, B8);</li>
 * <li>static assets from {@code classpath:/static/} through {@link LowercaseResourceResolver}
 *   (lower-cased paths, B8) and {@code /custom/**} from {@code <btnet.webroot>/custom/};</li>
 * <li>the {@link BtnetDialect} ({@code bt:control}), the runtime request filter
 *   ({@code btnet.runtime.web.RequestContextFilter}, binds {@code HttpContext.Current()}), and
 *   the one Jackson mapper ({@link JsonConfig#mapper()}) as the {@code JsonMapper} bean;</li>
 * <li>defaults (overridable in {@code application.properties}): 50 MB uploads
 *   ({@code maxRequestLength=51200}, Web.config:46), session timeout 120 min (Web.config:30).</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "btnet")
public class BtnetApplication implements WebMvcConfigurer, EnvironmentAware {

    private Environment environment;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BtnetApplication.class);
        app.setDefaultProperties(defaultProperties());
        app.run(args);
    }

    /** Lowest-precedence defaults standing for the original Web.config. */
    public static Map<String, Object> defaultProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("spring.servlet.multipart.max-file-size", "50MB");
        p.put("spring.servlet.multipart.max-request-size", "50MB");
        p.put("server.servlet.session.timeout", "120m");
        p.put("spring.web.resources.add-mappings", "false");   // replaced by addResourceHandlers below
        return p;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        ConfigurationManager.UseEnvironment(environment);
        String webroot = environment.getProperty("btnet.webroot");
        AppPaths.SetWebRoot(webroot != null && !webroot.isEmpty() ? webroot : AppPaths.WebRoot());   // marks web mode
    }

    @Autowired(required = false)
    public void registerDataSource(DataSource dataSource) {
        SqlConnection.UseDataSource(dataSource);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PathPatternParser parser = new PathPatternParser();
        parser.setCaseSensitive(false);
        configurer.setPatternParser(parser);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String custom = Path.of(AppPaths.WebRoot(), "custom").toAbsolutePath().toUri().toString();
        if (!custom.endsWith("/")) custom = custom + "/";
        registry.addResourceHandler("/custom/**")
                .addResourceLocations(custom);
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false)
                .addResolver(new LowercaseResourceResolver())
                .addResolver(new PathResourceResolver());
    }

    @Bean
    public BtnetDialect btnetDialect() {
        return new BtnetDialect();
    }

    @Bean
    public Filter btnetRequestContextFilter() {
        return new btnet.runtime.web.RequestContextFilter();
    }

    @Bean
    public JsonMapper jsonMapper() {
        return JsonConfig.mapper();
    }
}
