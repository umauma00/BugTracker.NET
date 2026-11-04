package btnet.runtime.web;

/**
 * .NET {@code System.Web.Optimization.Scripts} (RULEBOOK §2B B6, {@code LoggedIn.Master:37}).
 * {@link #Render(String...)} in debug mode ({@code compilation debug="true"}): one
 * {@code <script src="<file>"></script>} + CRLF per file of each bundle (ordinal path order);
 * a path that names no registered bundle is rendered as a script URL itself ({@code ~/x} →
 * {@code /x}). Returns an {@link IHtmlString}, so {@code HttpUtility.HtmlEncode(Object)} /
 * {@code <%: %>} leave it unencoded.
 */
public final class Scripts {
    private Scripts() {
    }

    public static IHtmlString Render(String... paths) {
        StringBuilder sb = new StringBuilder();
        for (String p : paths) {
            ScriptBundle b = BundleTable.Bundles.GetBundleFor(p);
            if (b != null) {
                for (String f : b.Files()) tag(sb, f);
            } else {
                tag(sb, Page.resolveUrlStatic(p));
            }
        }
        return new HtmlString(sb.toString());
    }

    private static void tag(StringBuilder sb, String src) {
        sb.append("<script src=\"").append(HttpUtility.HtmlAttributeEncode(src)).append("\"></script>\r\n");
    }

    /** {@code Scripts.Url(path)}: the bundle URL ({@code ~/x} → {@code /x}). */
    public static IHtmlString Url(String path) {
        return new HtmlString(Page.resolveUrlStatic(path));
    }
}
