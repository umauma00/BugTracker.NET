package btnet.runtime.mail;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentNullException;
import btnet.runtime.Encoding;
import btnet.runtime.FormatException;

/**
 * Stands for .NET {@code System.Net.Mail.MailAddress} (.NET 4.5).
 *
 * <p>Accepted forms: {@code addr}, {@code Display <addr>}, {@code "Display" <addr>},
 * {@code <addr>}; surrounding white space is ignored. {@code null} →
 * {@code ArgumentNullException}; {@code ""} → {@code ArgumentException("The
 * parameter 'address' cannot be an empty string.", "address")}; anything else
 * that is not {@code local@domain} (dot-atom or quoted local part, dot-atom or
 * {@code [literal]} domain) → {@code FormatException("The specified string is not in
 * the form required for an e-mail address.")}. TODO(port): .NET 4.5's
 * {@code MailAddressParser} also accepts comments and obsolete forms; only the
 * forms above are pinned.
 */
public class MailAddress {
    static final String BAD_FORMAT = "The specified string is not in the form required for an e-mail address.";

    private final String user;
    private final String host;
    private final String displayName;
    private final Encoding displayNameEncoding;

    /** .NET {@code MailAddress(string address)}. */
    public MailAddress(String address) {
        this(address, null, null);
    }

    /** .NET {@code MailAddress(string address, string displayName)}. */
    public MailAddress(String address, String displayName) {
        this(address, displayName, null);
    }

    /**
     * .NET {@code MailAddress(address, displayName, displayNameEncoding)}: a
     * non-null {@code displayName} replaces the one parsed from {@code address}
     * (outer quotes removed, trimmed).
     */
    public MailAddress(String address, String displayName, Encoding displayNameEncoding) {
        if (address == null) {
            throw new ArgumentNullException("address");
        }
        if (address.isEmpty()) {
            throw new ArgumentException("The parameter 'address' cannot be an empty string.", "address");
        }
        String s = address.trim();
        String parsedDisplay = "";
        String addr;
        int lt = s.indexOf('<');
        if (lt >= 0) {
            int gt = s.indexOf('>', lt);
            if (gt < 0 || !s.substring(gt + 1).trim().isEmpty()) {
                throw new FormatException(BAD_FORMAT);
            }
            parsedDisplay = unquote(s.substring(0, lt).trim());
            addr = s.substring(lt + 1, gt).trim();
        } else {
            addr = s;
        }
        int at = atIndex(addr);
        if (at <= 0 || at == addr.length() - 1) {
            throw new FormatException(BAD_FORMAT);
        }
        String local = addr.substring(0, at);
        String domain = addr.substring(at + 1);
        if (!validLocal(local) || !validDomain(domain)) {
            throw new FormatException(BAD_FORMAT);
        }
        this.user = local;
        this.host = domain;
        String dn = displayName != null ? unquote(displayName.trim()) : parsedDisplay;
        this.displayName = dn == null ? "" : dn;
        this.displayNameEncoding = displayNameEncoding;
    }

    /** .NET {@code Address} ({@code user@host}). */
    public String Address() {
        return user + "@" + host;
    }

    /** .NET {@code DisplayName} ({@code ""} if none). */
    public String DisplayName() {
        return displayName;
    }

    /** .NET {@code User}. */
    public String User() {
        return user;
    }

    /** .NET {@code Host}. */
    public String Host() {
        return host;
    }

    /** The display-name encoding given at construction (may be null). */
    public Encoding DisplayNameEncoding() {
        return displayNameEncoding;
    }

    /** .NET {@code ToString()}: {@code Address}, or {@code "Display" <Address>}. */
    @Override
    public String toString() {
        if (displayName.isEmpty()) {
            return Address();
        }
        return "\"" + displayName + "\" <" + Address() + ">";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MailAddress m && toString().equalsIgnoreCase(m.toString());
    }

    @Override
    public int hashCode() {
        return toString().toLowerCase(java.util.Locale.ROOT).hashCode();
    }

    // ------------------------------------------------------------------ parsing

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Index of the '@' separating local part and domain (outside a quoted local part), -1 if invalid. */
    private static int atIndex(String a) {
        boolean quoted = false;
        int found = -1;
        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);
            if (c == '\\' && quoted) {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == '@' && !quoted) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return quoted ? -1 : found;
    }

    private static boolean atext(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || "!#$%&'*+-/=?^_`{|}~".indexOf(c) >= 0 || c > 0x7F;
    }

    private static boolean dotAtom(String s) {
        if (s.isEmpty() || s.startsWith(".") || s.endsWith(".") || s.contains("..")) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '.' && !atext(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validLocal(String l) {
        if (l.length() >= 2 && l.startsWith("\"") && l.endsWith("\"")) {
            for (int i = 1; i < l.length() - 1; i++) {
                char c = l.charAt(i);
                if (c == '\r' || c == '\n') {
                    return false;
                }
            }
            return true;
        }
        return dotAtom(l);
    }

    private static boolean validDomain(String d) {
        if (d.startsWith("[") && d.endsWith("]")) {
            return d.length() > 2 && d.indexOf('[', 1) < 0 && d.indexOf(']') == d.length() - 1;
        }
        return dotAtom(d);
    }
}
