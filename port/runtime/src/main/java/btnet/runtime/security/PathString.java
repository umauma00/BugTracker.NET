package btnet.runtime.security;

import btnet.runtime.ArgumentException;

/**
 * Stands for {@code Microsoft.Owin.PathString} [J-C14]. A non-empty value must
 * start with {@code /} (Katana throws {@code ArgumentException} otherwise).
 */
public final class PathString {
    /** {@code PathString.Empty}. */
    public static final PathString Empty = new PathString("");

    private final String value;

    public PathString(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) != '/') {
            throw new ArgumentException("The path in 'value' must start with '/'.", "value");
        }
        this.value = value;
    }

    /** {@code PathString.Value}. */
    public String Value() {
        return value;
    }

    /** {@code PathString.HasValue}. */
    public boolean HasValue() {
        return value != null && !value.isEmpty();
    }

    /** {@code PathString.ToString()} = the value ({@code ""} for null). */
    @Override
    public String toString() {
        return value == null ? "" : value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PathString p && toString().equalsIgnoreCase(p.toString());
    }

    @Override
    public int hashCode() {
        return toString().toLowerCase(java.util.Locale.ROOT).hashCode();
    }
}
