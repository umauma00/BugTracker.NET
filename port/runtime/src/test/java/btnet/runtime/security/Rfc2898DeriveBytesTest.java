package btnet.runtime.security;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentOutOfRangeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Rfc2898DeriveBytesTest {

    /** The us_password literal of src/BugTracker.Web/setup.sql:168 (admin/admin, salt PmafvZSWFYML3). */
    static final String SETUP_SQL_ADMIN_HASH =
            "si�ύ�oJ̚�M����cx����piĦWNls��!�9O��X\u000fL]�s��׊6\u0018\u0010�{�%�$�7���\u000c���ڨFL\u000e�I\u0012S�v.�\u0010�\u0008\u0019��MC��1(K�C�8F[�\u0010fW�0����.a\u0005���c\u0015'iR%�U�hߎ��";

    /** PBKDF2-HMAC-SHA1("admin", "PmafvZSWFYML3PmafvZSWFYML3", 1000, 128). */
    static final String ADMIN_DK_HEX =
            "736980cf8dd46f4acc9aa14d9ea7b9b96378cad2d1e17069c4a6574e6c7385822180394fb6e0580f4c5dd373b080d78a3618108f7b9b25d7248f378cf8f50cc7ceffdaa8464c0eef4912539c762e9510be0819bce74d43f1f331284bf643c938465bf910665784308e9fc6fc2e610581c1c9631527695225b755cc68df8eb8c8";

    /** util.cs:494-499 HashString, with UTF8.GetString = new String(bytes, UTF_8) (B7). */
    static String hashString(String password, String salt) {
        Rfc2898DeriveBytes k2 = new Rfc2898DeriveBytes(password, (salt + salt).getBytes(StandardCharsets.UTF_8));
        return new String(k2.GetBytes(128), StandardCharsets.UTF_8);
    }

    // B7 acceptance vector: HashString("admin", "PmafvZSWFYML3") == setup.sql:168 us_password.
    @Test
    void adminAcceptanceVector() {
        Rfc2898DeriveBytes k = new Rfc2898DeriveBytes("admin", "PmafvZSWFYML3PmafvZSWFYML3".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(HexFormat.of().parseHex(ADMIN_DK_HEX), k.GetBytes(128));
        assertEquals(SETUP_SQL_ADMIN_HASH, hashString("admin", "PmafvZSWFYML3"));
    }

    // Same vector, read from the real setup.sql when the source tree is present.
    @Test
    void adminAcceptanceVectorAgainstSetupSql() throws Exception {
        Path p = Paths.get("../../src/BugTracker.Web/setup.sql");
        if (!Files.exists(p)) {
            return;
        }
        String line = new String(Files.readAllBytes(p), StandardCharsets.UTF_8).split("\n")[167];
        Matcher m = Pattern.compile("N'PmafvZSWFYML3', N'(.*?)', NULL, N'System'").matcher(line);
        if (m.find()) {
            assertEquals(m.group(1).replace("''", "'"), hashString("admin", "PmafvZSWFYML3"));
        }
    }

    // RFC 6070 test vector 5 (PBKDF2-HMAC-SHA1, c=4096, dkLen=25).
    @Test
    void rfc6070Vector() {
        Rfc2898DeriveBytes k = new Rfc2898DeriveBytes("passwordPASSWORDpassword",
                "saltSALTsaltSALTsaltSALTsaltSALTsalt".getBytes(StandardCharsets.US_ASCII), 4096);
        assertEquals("3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038", HexFormat.of().formatHex(k.GetBytes(25)));
    }

    // .NET GetBytes continues the derived-key stream across calls.
    @Test
    void getBytesContinuesTheStream() {
        byte[] salt = "PmafvZSWFYML3PmafvZSWFYML3".getBytes(StandardCharsets.UTF_8);
        byte[] whole = new Rfc2898DeriveBytes("admin", salt).GetBytes(128);
        Rfc2898DeriveBytes k = new Rfc2898DeriveBytes("admin", salt);
        byte[] a = k.GetBytes(7);
        byte[] b = k.GetBytes(30);
        byte[] c = k.GetBytes(91);
        byte[] joined = new byte[128];
        System.arraycopy(a, 0, joined, 0, 7);
        System.arraycopy(b, 0, joined, 7, 30);
        System.arraycopy(c, 0, joined, 37, 91);
        assertArrayEquals(whole, joined);
        k.Reset();
        assertArrayEquals(whole, k.GetBytes(128));
    }

    // .NET: "Salt is not at least eight bytes." (seeded users: isnull(us_salt,0) -> "00" -> salt "0000" -> 500).
    @Test
    void shortSaltIsRejected() {
        ArgumentException e = assertThrows(ArgumentException.class,
                () -> new Rfc2898DeriveBytes("admin", "0000".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Salt is not at least eight bytes.", e.getMessage());
    }

    // .NET: GetBytes(cb <= 0) -> ArgumentOutOfRangeException.
    @Test
    void nonPositiveCountIsRejected() {
        Rfc2898DeriveBytes k = new Rfc2898DeriveBytes("x", new byte[8]);
        assertThrows(ArgumentOutOfRangeException.class, () -> k.GetBytes(0));
    }
}
