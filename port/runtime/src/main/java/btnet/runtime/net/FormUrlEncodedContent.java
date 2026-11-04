package btnet.runtime.net;

import java.util.Map;

/**
 * Stands for .NET 4.5 {@code System.Net.Http.FormUrlEncodedContent} [J-C2].
 * {@code new FormUrlEncodedContent(dict)} → the body string
 * {@code FormUrlEncodedContent.encode(dict)}, sent with
 * {@link #CONTENT_TYPE}.
 */
public final class FormUrlEncodedContent {
    /** The content type .NET sends (no charset parameter). */
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private FormUrlEncodedContent() {
    }

    /**
     * .NET 4.5: for each entry in map order, {@code Encode(key) + "=" + Encode(value)},
     * joined with {@code &}; {@code Encode(s)} = {@code ""} for null/empty, else
     * {@code Uri.EscapeDataString(s).Replace("%20", "+")}. A value of 65520 chars
     * or more throws {@code UriFormatException} as .NET does (see {@link Uri#EscapeDataString}).
     */
    public static String encode(Map<String, String> nameValueCollection) {
        if (nameValueCollection == null) {
            throw new btnet.runtime.ArgumentNullException("Value cannot be null.\r\nParameter name: nameValueCollection");
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : nameValueCollection.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encodeOne(e.getKey())).append('=').append(encodeOne(e.getValue()));
        }
        return sb.toString();
    }

    private static String encodeOne(String data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        return Uri.EscapeDataString(data).replace("%20", "+");
    }
}
