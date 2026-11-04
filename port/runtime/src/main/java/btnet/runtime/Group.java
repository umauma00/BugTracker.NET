package btnet.runtime;

/**
 * Stands for .NET {@code System.Text.RegularExpressions.Group} [S2-6]: {@code Value()}, {@code Success()},
 * {@code Index()}, {@code Length()}; {@code toString()} = {@code Value()}. A group that did not participate,
 * or an index/name that does not exist, is an unsuccessful group with {@code Value()} {@code ""} and
 * {@code Index()}/{@code Length()} 0 — never {@code null}. {@link Match} extends it (as .NET).
 */
public class Group {
    private final boolean success;
    private final String value;
    private final int index;

    Group(boolean success, String value, int index) {
        this.success = success;
        this.value = success && value != null ? value : "";
        this.index = success ? index : 0;
    }

    static Group unsuccessful() {
        return new Group(false, "", 0);
    }

    public boolean Success() {
        return success;
    }

    public String Value() {
        return value;
    }

    public int Index() {
        return index;
    }

    public int Length() {
        return value.length();
    }

    @Override
    public String toString() {
        return value;
    }
}
