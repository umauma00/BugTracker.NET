package btnet.runtime.web.ui;

/**
 * Internal rendering helpers of {@code btnet.runtime.web.ui} (no .NET counterpart; stands for the
 * encoding half of {@code System.Web.UI.HtmlTextWriter}).
 */
final class Html {
    private Html() {}

    /**
     * .NET 4.5 {@code HttpUtility.HtmlEncode(string)}: {@code < > " & '} become entities and every
     * char in U+00A0..U+00FF becomes {@code &#NNN;}; {@code null} → {@code ""}.
     */
    static String encode(String s) {
        if (s == null) return "";
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String rep = switch (c) {
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '&' -> "&amp;";
                case '\'' -> "&#39;";
                default -> (c >= 160 && c < 256) ? "&#" + (int) c + ";" : null;
            };
            if (rep != null) {
                if (sb == null) { sb = new StringBuilder(s.length() + 16); sb.append(s, 0, i); }
                sb.append(rep);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }

    /**
     * .NET 4.5 {@code HttpUtility.HtmlAttributeEncode} (what {@code HtmlTextWriter.WriteAttribute(k, v, true)}
     * applies): {@code " & < '} only; {@code null} → {@code ""}.
     */
    static String attr(String s) {
        if (s == null) return "";
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String rep = switch (c) {
                case '"' -> "&quot;";
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '\'' -> "&#39;";
                default -> null;
            };
            if (rep != null) {
                if (sb == null) { sb = new StringBuilder(s.length() + 16); sb.append(s, 0, i); }
                sb.append(rep);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }

    /** HTML void elements (no end tag, no body). */
    static boolean isVoid(String tag) {
        if (tag == null) return false;
        return switch (tag.toLowerCase(java.util.Locale.ROOT)) {
            case "input", "img", "br", "hr", "meta", "link", "area", "base", "col", "embed", "param", "source", "track", "wbr" -> true;
            default -> false;
        };
    }

    /** Writes {@code <tag a="v" …>} (attribute values encoded; {@code null} values skipped). */
    static void openTag(StringBuilder sb, String tag, java.util.Map<String, String> attrs, boolean selfClose) {
        sb.append('<').append(tag);
        for (var e : attrs.entrySet()) {
            if (e.getValue() == null) continue;
            sb.append(' ').append(e.getKey()).append("=\"").append(attr(e.getValue())).append('"');
        }
        sb.append(selfClose ? " />" : ">");
    }

    /** ASP.NET auto-ID / item naming: {@code ctl00} … {@code ctl09}, {@code ctl10}, {@code ctl100}. */
    static String ctl(int n) {
        return n < 10 ? "ctl0" + n : "ctl" + n;
    }

    /** JS for {@code ClientScriptManager.GetPostBackEventReference} as RULEBOOK B5 fixes it: {@code __doPostBack('uid','arg')}. */
    static String postBack(String uniqueId, String argument) {
        return "__doPostBack('" + uniqueId + "','" + (argument == null ? "" : argument) + "')";
    }

    /** .NET {@code Util.MergeScript}: first script, a {@code ;} if it does not end with one, then the second. */
    static String mergeScript(String first, String second) {
        if (first == null || first.isEmpty()) return second;
        return first.endsWith(";") ? first + second : first + ";" + second;
    }

    /**
     * {@code Control.ResolveClientUrl}: an app-relative {@code ~/x} becomes {@code /x} (context root
     * {@code /}, B8); everything else is returned unchanged.
     */
    static String resolveUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("~/")) return url.substring(1);
        if (url.equals("~")) return "/";
        return url;
    }
}
