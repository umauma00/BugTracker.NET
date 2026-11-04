package btnet.runtime.mail.pop;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands for OpenPop {@code OpenPop.Mime.Header.RfcMailAddress}: {@code Address()}
 * ({@code ""} when the text holds no parsable address), {@code DisplayName()}
 * ({@code ""} if none; the decoded raw text when unparsable), {@code Raw()}.
 */
public final class RfcMailAddress {
    private final String address;
    private final String displayName;
    private final String raw;

    RfcMailAddress(String address, String displayName, String raw) {
        this.address = address == null ? "" : address;
        this.displayName = displayName == null ? "" : displayName;
        this.raw = raw == null ? "" : raw;
    }

    public String Address() { return address; }

    public String DisplayName() { return displayName; }

    public String Raw() { return raw; }

    /** OpenPop {@code HasValidMailAddress}. */
    public boolean HasValidMailAddress() { return !address.isEmpty(); }

    /** OpenPop {@code ToString()}: {@code "Display" <addr>}, the address, or the raw text. */
    @Override
    public String toString() {
        if (!address.isEmpty()) {
            return displayName.isEmpty() ? address : "\"" + displayName + "\" <" + address + ">";
        }
        return raw;
    }

    /** One address from a header value (the first one), as OpenPop's {@code ParseMailAddress}. */
    static RfcMailAddress parse(String headerValue) {
        List<RfcMailAddress> all = parseList(headerValue);
        return all.isEmpty() ? new RfcMailAddress("", decode(headerValue == null ? "" : headerValue.trim()), headerValue) : all.get(0);
    }

    /** Every address of a header value (OpenPop {@code ParseMailAddresses}). */
    static List<RfcMailAddress> parseList(String headerValue) {
        List<RfcMailAddress> r = new ArrayList<>();
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return r;
        }
        try {
            for (InternetAddress ia : InternetAddress.parseHeader(headerValue, false)) {
                String a = ia.getAddress();
                String p = ia.getPersonal();
                if (a == null || a.isEmpty() || a.indexOf('@') < 0) {
                    r.add(new RfcMailAddress("", decode(ia.toString()), ia.toString()));
                } else {
                    r.add(new RfcMailAddress(a, p, ia.toString()));
                }
            }
        } catch (AddressException e) {
            r.add(new RfcMailAddress("", decode(headerValue.trim()), headerValue));
        }
        return r;
    }

    private static String decode(String s) {
        try {
            return MimeUtility.decodeText(s);
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
