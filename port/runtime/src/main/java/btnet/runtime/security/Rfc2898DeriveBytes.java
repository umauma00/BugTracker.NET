package btnet.runtime.security;

import btnet.runtime.ArgumentException;
import btnet.runtime.ArgumentNullException;
import btnet.runtime.ArgumentOutOfRangeException;
import btnet.runtime.Exceptions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Stands for .NET {@code System.Security.Cryptography.Rfc2898DeriveBytes}
 * (.NET 4.5; B7): PBKDF2 with HMAC-SHA1 (RFC 2898 §5.2), default 1000
 * iterations, password encoded as UTF-8 without BOM ({@code new UTF8Encoding(false)},
 * so an unpaired surrogate becomes U+FFFD's bytes {@code EF BF BD}).
 *
 * <p>A salt shorter than 8 bytes → {@code ArgumentException("Salt is not at least
 * eight bytes.")} (the seeded-user quirk of §0.1 is preserved through this).
 * {@link #GetBytes(int)} continues the derived-key stream across calls, block by
 * block (20-byte blocks {@code T_1, T_2, …}), exactly as the .NET implementation.
 *
 * <p>Acceptance vector (B7): {@code Util.HashString("admin", "PmafvZSWFYML3")} =
 * {@code UTF8.GetString(new Rfc2898DeriveBytes("admin", UTF8.GetBytes(salt + salt)).GetBytes(128))}
 * equals the {@code us_password} literal of {@code setup.sql:168} (see the test).
 */
public class Rfc2898DeriveBytes implements AutoCloseable {
    private static final int BLOCK_SIZE = 20; // SHA-1 output

    private final byte[] password;
    private byte[] salt;
    private int iterations;

    private Mac hmac;
    private int blockIndex;      // index of the next block to compute (1-based)
    private byte[] buffer;       // the current block
    private int startIndex;      // first unread byte in buffer
    private int endIndex;        // end of valid bytes in buffer

    /** .NET {@code Rfc2898DeriveBytes(string password, byte[] salt)} — 1000 iterations. */
    public Rfc2898DeriveBytes(String password, byte[] salt) {
        this(password, salt, 1000);
    }

    /** .NET {@code Rfc2898DeriveBytes(string password, byte[] salt, int iterations)}. */
    public Rfc2898DeriveBytes(String password, byte[] salt, int iterations) {
        this(utf8(password), salt, iterations);
    }

    /** .NET {@code Rfc2898DeriveBytes(string password, int saltSize)} — random salt, 1000 iterations. */
    public Rfc2898DeriveBytes(String password, int saltSize) {
        this(password, saltSize, 1000);
    }

    /** .NET {@code Rfc2898DeriveBytes(string password, int saltSize, int iterations)}. */
    public Rfc2898DeriveBytes(String password, int saltSize, int iterations) {
        if (saltSize < 0) {
            throw new ArgumentOutOfRangeException("Non-negative number required.\r\nParameter name: saltSize");
        }
        byte[] s = new byte[saltSize];
        new SecureRandom().nextBytes(s);
        this.password = utf8(password);
        Salt(s);
        IterationCount(iterations);
        initialize();
    }

    /** .NET {@code Rfc2898DeriveBytes(byte[] password, byte[] salt, int iterations)}. */
    public Rfc2898DeriveBytes(byte[] password, byte[] salt, int iterations) {
        if (password == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: password");
        }
        this.password = password.clone();
        Salt(salt);
        IterationCount(iterations);
        initialize();
    }

    /** .NET {@code Salt} getter (a copy). */
    public byte[] Salt() {
        return salt.clone();
    }

    /** .NET {@code Salt} setter: null → {@code ArgumentNullException}; under 8 bytes → {@code ArgumentException}. */
    public void Salt(byte[] value) {
        if (value == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: value");
        }
        if (value.length < 8) {
            throw new ArgumentException("Salt is not at least eight bytes.");
        }
        this.salt = value.clone();
        if (hmac != null) {
            Reset();
        }
    }

    /** .NET {@code IterationCount} getter. */
    public int IterationCount() {
        return iterations;
    }

    /** .NET {@code IterationCount} setter: ≤ 0 → {@code ArgumentOutOfRangeException}. */
    public void IterationCount(int value) {
        if (value <= 0) {
            throw new ArgumentOutOfRangeException("Positive number required.\r\nParameter name: value");
        }
        this.iterations = value;
        if (hmac != null) {
            Reset();
        }
    }

    /**
     * .NET {@code GetBytes(int cb)}: the next {@code cb} bytes of the derived-key
     * stream. {@code cb <= 0} → {@code ArgumentOutOfRangeException}.
     */
    public byte[] GetBytes(int cb) {
        if (cb <= 0) {
            throw new ArgumentOutOfRangeException("Positive number required.\r\nParameter name: cb");
        }
        byte[] result = new byte[cb];
        int offset = 0;
        int size = endIndex - startIndex;
        if (size > 0) {
            if (cb >= size) {
                System.arraycopy(buffer, startIndex, result, 0, size);
                startIndex = endIndex = 0;
                offset += size;
            } else {
                System.arraycopy(buffer, startIndex, result, 0, cb);
                startIndex += cb;
                return result;
            }
        }
        while (offset < cb) {
            byte[] t = func();
            int remainder = cb - offset;
            if (remainder > BLOCK_SIZE) {
                System.arraycopy(t, 0, result, offset, BLOCK_SIZE);
                offset += BLOCK_SIZE;
            } else {
                System.arraycopy(t, 0, result, offset, remainder);
                offset += remainder;
                System.arraycopy(t, remainder, buffer, startIndex, BLOCK_SIZE - remainder);
                endIndex += BLOCK_SIZE - remainder;
                return result;
            }
        }
        return result;
    }

    /** .NET {@code Reset()}: restarts the stream. */
    public void Reset() {
        initialize();
    }

    /** .NET {@code Dispose()}. */
    public void Dispose() {
        java.util.Arrays.fill(buffer, (byte) 0);
    }

    @Override
    public void close() {
        Dispose();
    }

    // ------------------------------------------------------------------ private

    private void initialize() {
        try {
            if (hmac == null) {
                hmac = Mac.getInstance("HmacSHA1");
            }
            // an empty password is legal in .NET; SecretKeySpec rejects empty keys
            hmac.init(password.length == 0 ? new EmptyKey() : new SecretKeySpec(password, "HmacSHA1"));
        } catch (GeneralSecurityException e) {
            throw Exceptions.wrap(e);
        }
        buffer = new byte[BLOCK_SIZE];
        blockIndex = 1;
        startIndex = endIndex = 0;
    }

    /** RFC 2898 F(P, S, c, i) for the current block index. */
    private byte[] func() {
        byte[] intBlock = ByteBuffer.allocate(4).putInt(blockIndex).array();
        hmac.update(salt);
        hmac.update(intBlock);
        byte[] u = hmac.doFinal();
        byte[] ret = u.clone();
        for (int i = 2; i <= iterations; i++) {
            u = hmac.doFinal(u);
            for (int j = 0; j < BLOCK_SIZE; j++) {
                ret[j] ^= u[j];
            }
        }
        blockIndex++;
        return ret;
    }

    private static byte[] utf8(String s) {
        if (s == null) {
            throw new ArgumentNullException("String reference not set to an instance of a String.\r\nParameter name: s");
        }
        CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(new byte[]{(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});
        try {
            ByteBuffer bb = enc.encode(CharBuffer.wrap(s));
            byte[] out = new byte[bb.remaining()];
            bb.get(out);
            return out;
        } catch (CharacterCodingException e) {
            throw Exceptions.wrap(e);
        }
    }

    /** A zero-length HMAC key (HMAC pads it with zeros, as .NET does). */
    private static final class EmptyKey implements javax.crypto.SecretKey {
        private static final long serialVersionUID = 1L;

        @Override
        public String getAlgorithm() {
            return "HmacSHA1";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }
}
