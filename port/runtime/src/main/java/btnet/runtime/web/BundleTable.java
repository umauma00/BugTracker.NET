package btnet.runtime.web;

/**
 * .NET {@code System.Web.Optimization.BundleTable}: {@code BundleTable.Bundles} is the one
 * {@link BundleCollection} ({@code Global.asax.cs RegisterBundles} →
 * {@code BundleConfig.RegisterBundles(BundleTable.Bundles)}).
 */
public final class BundleTable {
    private BundleTable() {
    }

    public static final BundleCollection Bundles = new BundleCollection();
}
