package btnet.runtime.config;

import btnet.runtime.NameValueCollection;

/**
 * {@code ConfigurationManager.AppSettings[k]} → {@code Settings.get(k)} (RULEBOOK §2A [C26]):
 * reads the same collection as {@code GetSection("appSettings")}, so overrides apply;
 * absent → {@code null}, no default logic.
 */
public final class Settings {
    private Settings() {}

    public static String get(String key) {
        return ((NameValueCollection) ConfigurationManager.GetSection("appSettings")).get(key);
    }
}
