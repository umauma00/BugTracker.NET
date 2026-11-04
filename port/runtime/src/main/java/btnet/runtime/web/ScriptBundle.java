package btnet.runtime.web;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * .NET {@code System.Web.Optimization.ScriptBundle} ({@code BundleConfig.cs}):
 * {@code new ScriptBundle("~/bundles/all").IncludeDirectory("~/Scripts/Site", "*.js", true)}.
 * With {@code compilation debug="true"} the bundle renders unbundled (RULEBOOK §2B B6), so
 * only the file list matters: {@link #Files()} = every matching file, recursive if asked,
 * in ordinal relative-path order, as {@code <dir>/<relative path>}.
 *
 * <p>Files are looked up under the webroot directory first ({@code Server.MapPath(dir)}),
 * else on the classpath under {@code static/<lower-cased dir>} (B8 copies assets with
 * lower-cased paths; {@link LowercaseResourceResolver} serves either case).
 */
public class ScriptBundle {
    private final String path;
    private final List<String[]> directories = new ArrayList<>(); // {dir, pattern, recursive}
    private final List<String> includes = new ArrayList<>();
    private List<String> files;

    public ScriptBundle(String virtualPath) {
        this.path = virtualPath;
    }

    public String Path() {
        return path;
    }

    /** .NET {@code IncludeDirectory(directoryVirtualPath, searchPattern, searchSubdirectories)}. */
    public ScriptBundle IncludeDirectory(String directoryVirtualPath, String searchPattern, boolean searchSubdirectories) {
        directories.add(new String[] {directoryVirtualPath, searchPattern, String.valueOf(searchSubdirectories)});
        files = null;
        return this;
    }

    public ScriptBundle IncludeDirectory(String directoryVirtualPath, String searchPattern) {
        return IncludeDirectory(directoryVirtualPath, searchPattern, false);
    }

    /** .NET {@code Include(params string[] virtualPaths)}. */
    public ScriptBundle Include(String... virtualPaths) {
        Collections.addAll(includes, virtualPaths);
        files = null;
        return this;
    }

    /** The app-relative file paths, without a leading {@code ~/} (e.g. {@code Scripts/Site/Page.js}). */
    public synchronized List<String> Files() {
        if (files == null) {
            List<String> out = new ArrayList<>();
            for (String inc : includes) out.add(strip(inc));
            for (String[] d : directories) out.addAll(list(strip(d[0]), d[1], Boolean.parseBoolean(d[2])));
            files = Collections.unmodifiableList(out);
        }
        return files;
    }

    private static String strip(String v) {
        String s = v.replace('\\', '/');
        if (s.startsWith("~/")) s = s.substring(2);
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static List<String> list(String dir, String pattern, boolean recursive) {
        List<String> rel = new ArrayList<>();
        File fsDir = new File(HttpServerUtility.mapPath("/" + dir, "/"));
        if (fsDir.isDirectory()) {
            walk(fsDir, "", pattern, recursive, rel);
        } else {
            String base = "static/" + dir.toLowerCase(Locale.ROOT) + "/";
            String glob = (recursive ? "**/" : "") + pattern.toLowerCase(Locale.ROOT);
            try {
                Resource[] rs = new PathMatchingResourcePatternResolver(ScriptBundle.class.getClassLoader())
                    .getResources("classpath*:" + base + glob);
                for (Resource r : rs) {
                    String url = r.getURL().toString();
                    int i = url.lastIndexOf(base);
                    if (i >= 0 && r.isReadable()) rel.add(url.substring(i + base.length()));
                }
            } catch (IOException e) {
                // no such directory: an empty bundle
            }
        }
        Collections.sort(rel); // ordinal
        List<String> out = new ArrayList<>();
        for (String r : rel) {
            if (!out.contains(dir + "/" + r)) out.add(dir + "/" + r);
        }
        return out;
    }

    private static void walk(File d, String prefix, String pattern, boolean recursive, List<String> out) {
        File[] children = d.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                if (recursive) walk(f, prefix + f.getName() + "/", pattern, true, out);
            } else if (matches(f.getName(), pattern)) {
                out.add(prefix + f.getName());
            }
        }
    }

    /** Windows wildcard match ({@code *}, {@code ?}), case-insensitive. */
    static boolean matches(String name, String pattern) {
        String n = name.toLowerCase(Locale.ROOT), p = pattern.toLowerCase(Locale.ROOT);
        return match(n, 0, p, 0);
    }

    private static boolean match(String n, int i, String p, int j) {
        if (j == p.length()) return i == n.length();
        char c = p.charAt(j);
        if (c == '*') {
            for (int k = i; k <= n.length(); k++) {
                if (match(n, k, p, j + 1)) return true;
            }
            return false;
        }
        if (i == n.length()) return false;
        if (c == '?' || c == n.charAt(i)) return match(n, i + 1, p, j + 1);
        return false;
    }
}
