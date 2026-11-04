package btnet.runtime;

/**
 * Application paths (RULEBOOK §2A, §3 C18/C32/S1-17): the virtual service executable path, {@code MapPath}
 * without a request, and {@code AppDomain.CurrentDomain.SetupInformation.ConfigurationFile}. All paths use
 * {@code /}.
 */
public final class AppPaths {
    private AppPaths() {
    }

    private static volatile String webRoot;
    private static volatile Boolean webMode;

    /**
     * Sets the web root (Spring property {@code btnet.webroot}); called once by {@code BtnetApplication} at boot.
     * Marks the process as the web application.
     */
    public static void SetWebRoot(String path) {
        webRoot = normalize(new java.io.File(path.replace('\\', '/')).getAbsolutePath());
        webMode = Boolean.TRUE;
    }

    /**
     * The web root: {@link #SetWebRoot}, else system property / env {@code btnet.webroot}/{@code BTNET_WEBROOT},
     * else {@code port/web/webroot} under {@code user.dir} (or {@code webroot} when running inside port/web).
     */
    public static String WebRoot() {
        String r = webRoot;
        if (r != null) {
            return r;
        }
        String p = System.getProperty("btnet.webroot");
        if (p == null || p.isEmpty()) {
            p = System.getenv("BTNET_WEBROOT");
        }
        if (p == null || p.isEmpty()) {
            java.io.File local = new java.io.File(System.getProperty("user.dir"), "webroot");
            java.io.File fromRepo = new java.io.File(System.getProperty("user.dir"), "port/web/webroot");
            p = (local.isDirectory() && !fromRepo.isDirectory()) ? local.getPath() : fromRepo.getPath();
        }
        r = normalize(new java.io.File(p.replace('\\', '/')).getAbsolutePath());
        webRoot = r;
        return r;
    }

    private static String normalize(String p) {
        String s = p.replace('\\', '/');
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * .NET {@code Process.GetCurrentProcess().MainModule.FileName} for the service [C18]:
     * {@code btnet.service.home} (default {@code user.dir}) + {@code "/btnet_service.exe"}; never needs to exist.
     */
    public static String executable() {
        return System.getProperty("btnet.service.home", System.getProperty("user.dir")) + "/btnet_service.exe";
    }

    /**
     * {@code Server.MapPath(p)} without a request [C32]: {@code null}, {@code ""}, {@code "~"}, {@code "/"} → web root;
     * {@code "~/x"}, {@code "/x"}, {@code "x"} → web root + {@code "/x"} ({@code \} normalized to {@code /};
     * a trailing slash is kept).
     */
    public static String MapPath(String p) {
        String root = WebRoot();
        if (p == null) {
            return root;
        }
        String s = p.replace('\\', '/');
        if (s.startsWith("~")) {
            s = s.substring(1);
        }
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return p.endsWith("/") || p.endsWith("\\") ? root + "/" : root;
        }
        return root + "/" + s;
    }

    /**
     * {@code AppDomain.CurrentDomain.SetupInformation.ConfigurationFile} [S1-17]: {@code MapPath(null) + "/Web.config"}
     * in port/web, {@code executable() + ".config"} in port/service. The web application is recognised by
     * {@link #SetWebRoot} having run, or by the translated {@code btnet.Util} class being on the class path.
     */
    public static String ConfigurationFile() {
        return isWeb() ? MapPath(null) + "/Web.config" : executable() + ".config";
    }

    private static boolean isWeb() {
        Boolean w = webMode;
        if (w == null) {
            try {
                Class.forName("btnet.Util", false, AppPaths.class.getClassLoader());
                w = Boolean.TRUE;
            } catch (ClassNotFoundException | LinkageError e) {
                w = Boolean.FALSE;
            }
            webMode = w;
        }
        return w;
    }
}
