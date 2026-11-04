package btnet.runtime.web;

/** .NET {@code System.Web.HtmlString}: an {@link IHtmlString} over a fixed string. */
public final class HtmlString implements IHtmlString {
    private final String value;

    public HtmlString(String value) {
        this.value = value;
    }

    @Override
    public String ToHtmlString() {
        return value;
    }

    /** .NET {@code HtmlString.ToString()} returns the markup. */
    @Override
    public String toString() {
        return value == null ? "" : value;
    }
}
