package btnet.runtime.config;

import btnet.runtime.NameValueCollection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * .NET {@code System.Configuration.ConfigurationManager} (RULEBOOK §2A "Settings").
 * {@code GetSection("appSettings")} returns one case-insensitive {@link NameValueCollection}
 * loaded once from the classpath resource {@value #RESOURCE} (UTF-8; generated from
 * {@code Web.config} by {@link WebConfigToProperties}, never hand-edited). Overrides win over
 * the file: Spring property {@code btnet.setting.<Key>} (e.g. env
 * {@code BTNET_SETTING_SearchServerURI} or {@code BTNET_SETTING_SEARCHSERVERURI}), and — when no
 * Spring {@link Environment} has been registered — the system property
 * {@code btnet.setting.<Key>} or env {@code BTNET_SETTING_<Key>}. Any other section → {@code null}.
 */
public final class ConfigurationManager {
    private ConfigurationManager() {}

    /** Classpath resource holding the generated appSettings. */
    public static final String RESOURCE = "btnet-settings.properties";
    static final String PREFIX = "btnet.setting.";
    static final String ENV_PREFIX = "BTNET_SETTING_";

    private static final Logger log = LoggerFactory.getLogger(ConfigurationManager.class);
    private static volatile Environment environment;
    private static volatile NameValueCollection appSettings;

    /** Called by {@code BtnetApplication} once the Spring environment exists; reloads the section. */
    public static void UseEnvironment(Environment env) {
        environment = env;
        appSettings = null;
    }

    /** {@code (NameValueCollection) ConfigurationManager.GetSection("appSettings")}. */
    public static Object GetSection(String sectionName) {
        if (sectionName != null && sectionName.equalsIgnoreCase("appSettings")) {
            return AppSettings();
        }
        // TODO(port): other sections (system.net/mailSettings/smtp, email.cs:215) are not emulated
        return null;
    }

    /** .NET {@code ConfigurationManager.AppSettings}. */
    public static NameValueCollection AppSettings() {
        NameValueCollection s = appSettings;
        if (s == null) {
            synchronized (ConfigurationManager.class) {
                s = appSettings;
                if (s == null) {
                    s = load();
                    appSettings = s;
                }
            }
        }
        return s;
    }

    /** Forgets the loaded section (tests). */
    static void reset() {
        appSettings = null;
    }

    private static NameValueCollection load() {
        Map<String, String> file = readResource();
        Map<String, String> merged = new LinkedHashMap<>(file);
        applyOverrides(merged);
        NameValueCollection c = new NameValueCollection();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            c.Add(e.getKey(), e.getValue());
        }
        return c;
    }

    /** Reads the properties resource preserving the file's key order. */
    static Map<String, String> readResource() {
        Map<String, String> ordered = new LinkedHashMap<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = ConfigurationManager.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found on the classpath; appSettings is empty", RESOURCE);
                return ordered;
            }
            parseInto(new InputStreamReader(in, StandardCharsets.UTF_8), ordered);
        } catch (IOException e) {
            log.warn("cannot read {}: {}", RESOURCE, e.toString());
        }
        return ordered;
    }

    /** {@link Properties#load(Reader)} semantics, keeping insertion order (later duplicates win). */
    static void parseInto(Reader reader, Map<String, String> target) throws IOException {
        Properties p = new Properties() {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Object put(Object key, Object value) {
                String k = (String) key;
                String existing = findKey(target, k);
                if (existing != null) target.remove(existing);
                target.put(k, (String) value);
                return super.put(key, value);
            }
        };
        p.load(reader);
    }

    private static String findKey(Map<String, String> m, String key) {
        for (String k : m.keySet()) {
            if (k.equalsIgnoreCase(key)) return k;
        }
        return null;
    }

    private static String normalizeEnv(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static void put(Map<String, String> merged, String key, String value) {
        if (value == null || key.isEmpty()) return;
        String existing = findKey(merged, key);
        if (existing == null) {
            for (String k : merged.keySet()) {
                if (normalizeEnv(k).equals(normalizeEnv(key))) {
                    existing = k;
                    break;
                }
            }
        }
        merged.put(existing != null ? existing : key, value);
    }

    private static void applyOverrides(Map<String, String> merged) {
        Environment env = environment;
        List<String> fileKeys = new ArrayList<>(merged.keySet());
        if (env != null) {
            if (env instanceof ConfigurableEnvironment ce) {
                List<PropertySource<?>> sources = new ArrayList<>();
                ce.getPropertySources().forEach(sources::add);
                // lowest precedence first, so higher-precedence sources overwrite
                for (int i = sources.size() - 1; i >= 0; i--) {
                    if (sources.get(i) instanceof EnumerablePropertySource<?> eps) {
                        for (String name : eps.getPropertyNames()) {
                            Object raw = eps.getProperty(name);
                            String v = raw == null ? null : raw.toString();
                            if (name.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
                                put(merged, name.substring(PREFIX.length()), v);
                            } else if (name.startsWith(ENV_PREFIX)) {
                                put(merged, name.substring(ENV_PREFIX.length()), v);
                            }
                        }
                    }
                }
            }
            for (String k : fileKeys) {
                String v = env.getProperty(PREFIX + k);    // relaxed binding (BTNET_SETTING_<KEY>)
                if (v != null) merged.put(k, v);
            }
        } else {
            for (Map.Entry<String, String> e : System.getenv().entrySet()) {
                if (e.getKey().startsWith(ENV_PREFIX)) put(merged, e.getKey().substring(ENV_PREFIX.length()), e.getValue());
            }
            for (String name : System.getProperties().stringPropertyNames()) {
                if (name.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
                    put(merged, name.substring(PREFIX.length()), System.getProperty(name));
                }
            }
        }
    }
}
