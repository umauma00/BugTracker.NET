// Source: src/BugTracker.Web/App_Start/BundleConfig.cs
package btnet.App_Start;

import btnet.runtime.web.BundleCollection;
import btnet.runtime.web.ScriptBundle;

public class BundleConfig {
    public static void RegisterBundles(BundleCollection bundles) {
        bundles.Add(new ScriptBundle("~/bundles/all").IncludeDirectory("~/Scripts/Site", "*.js", true));
    }
}
// PORT STATUS: confidence=high todos=0
