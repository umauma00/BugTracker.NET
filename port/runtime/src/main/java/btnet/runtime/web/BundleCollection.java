package btnet.runtime.web;

import java.util.ArrayList;
import java.util.List;

/** .NET {@code System.Web.Optimization.BundleCollection} ({@code BundleTable.Bundles}). */
public class BundleCollection {
    private final List<ScriptBundle> bundles = new ArrayList<>();

    public synchronized void Add(ScriptBundle bundle) {
        bundles.removeIf(b -> b.Path().equalsIgnoreCase(bundle.Path()));
        bundles.add(bundle);
    }

    /** The bundle registered under {@code virtualPath} (case-insensitive), or {@code null}. */
    public synchronized ScriptBundle GetBundleFor(String virtualPath) {
        for (ScriptBundle b : bundles) {
            if (b.Path().equalsIgnoreCase(virtualPath)) return b;
        }
        return null;
    }

    public synchronized int Count() {
        return bundles.size();
    }

    public synchronized void Clear() {
        bundles.clear();
    }
}
