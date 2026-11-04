package btnet.runtime.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * The {@code __VIEWSTATE} codec (RULEBOOK §2B B4, B3 {@code ViewStateUserKey} row):
 * {@code Base64( HMAC-SHA256(key, json) ‖ json )} where {@code json} is the JSON of the
 * page's state tree and {@code key} = application secret ‖ UTF-8({@code ViewStateUserKey}).
 *
 * <p>The state is a tree of {@code null}, {@code Boolean}, numbers, {@code String},
 * {@code List} and {@code Map<String,?>}; after a round trip numbers come back as
 * {@code Integer}/{@code Long}/{@code Double}, maps as {@code LinkedHashMap}, lists as
 * {@code ArrayList}. A corrupt value or a MAC mismatch (e.g. a different session id with
 * {@code ViewStateUserKey = Session.SessionID}) → {@link HttpException} → 500, as ASP.NET.
 *
 * <p>The application secret is system property {@code btnet.viewstate.key} / env
 * {@code BTNET_VIEWSTATE_KEY} (any string), else 32 random bytes per JVM — like
 * {@code <machineKey>} AutoGenerate, a restart invalidates outstanding view state.
 */
public final class ViewStateCodec {
    private ViewStateCodec() {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final byte[] SECRET = loadSecret();
    private static final int MAC_LEN = 32;

    static final String MAC_FAILED = "Validation of viewstate MAC failed. If this application is hosted by a Web Farm or cluster, "
        + "ensure that <machineKey> configuration specifies the same validationKey and validation algorithm. "
        + "AutoGenerate cannot be used in a cluster.";
    static final String INVALID = "The state information is invalid for this page and might be corrupted.";

    private static byte[] loadSecret() {
        String s = System.getProperty("btnet.viewstate.key");
        if (s == null || s.isEmpty()) s = System.getenv("BTNET_VIEWSTATE_KEY");
        if (s != null && !s.isEmpty()) return s.getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static byte[] mac(byte[] payload, String userKey) {
        try {
            byte[] uk = userKey == null ? new byte[0] : userKey.getBytes(StandardCharsets.UTF_8);
            byte[] key = Arrays.copyOf(SECRET, SECRET.length + uk.length + 1);
            System.arraycopy(uk, 0, key, SECRET.length + 1, uk.length);
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Encodes {@code state} for the {@code __VIEWSTATE} hidden field. */
    public static String Serialize(Object state, String viewStateUserKey) {
        byte[] json;
        try {
            json = MAPPER.writeValueAsBytes(state);
        } catch (JacksonException e) {
            throw new HttpException("View state is not serializable: " + e.getMessage(), e);
        }
        byte[] mac = mac(json, viewStateUserKey);
        byte[] all = new byte[MAC_LEN + json.length];
        System.arraycopy(mac, 0, all, 0, MAC_LEN);
        System.arraycopy(json, 0, all, MAC_LEN, json.length);
        return Base64.getEncoder().encodeToString(all);
    }

    /** Decodes and verifies a posted {@code __VIEWSTATE}; {@code null}/empty → {@code null}. */
    public static Object Deserialize(String value, String viewStateUserKey) {
        if (value == null || value.isEmpty()) return null;
        byte[] all;
        try {
            all = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new HttpException(INVALID, e);
        }
        if (all.length < MAC_LEN) throw new HttpException(INVALID);
        byte[] mac = Arrays.copyOfRange(all, 0, MAC_LEN);
        byte[] json = Arrays.copyOfRange(all, MAC_LEN, all.length);
        if (!MessageDigest.isEqual(mac, mac(json, viewStateUserKey))) throw new HttpException(MAC_FAILED);
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JacksonException e) {
            throw new HttpException(INVALID, e);
        }
    }
}
