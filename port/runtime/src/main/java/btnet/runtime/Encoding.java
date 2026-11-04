package btnet.runtime;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Stands for .NET {@code System.Text.Encoding} (RULEBOOK §2A Strings, §3 J-C2/S1-21). {@link #UTF8}
 * (preamble EF BB BF; lossy decode = {@code new String(bytes, UTF_8)}; an unpaired surrogate encodes as
 * EF BF BD), {@link #Default} = windows-1252 (the five unassigned bytes 0x81 0x8D 0x8F 0x90 0x9D decode to the
 * same code point, as .NET), plus {@link #ASCII}, {@link #Unicode}, {@link #BigEndianUnicode},
 * {@link #GetEncoding(String)}.
 */
public class Encoding {

    /** .NET {@code Encoding.UTF8} (with BOM preamble). */
    public static final Encoding UTF8 = new Encoding(StandardCharsets.UTF_8, "utf-8", 65001,
            new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    /** .NET {@code Encoding.Default}: Windows ANSI code page of en-US = windows-1252, no preamble. */
    public static final Encoding Default = new Windows1252();
    /** .NET {@code Encoding.ASCII}. */
    public static final Encoding ASCII = new Encoding(StandardCharsets.US_ASCII, "us-ascii", 20127, new byte[0]);
    /** .NET {@code Encoding.Unicode} (UTF-16LE, preamble FF FE). */
    public static final Encoding Unicode = new Encoding(StandardCharsets.UTF_16LE, "utf-16", 1200,
            new byte[] {(byte) 0xFF, (byte) 0xFE});
    /** .NET {@code Encoding.BigEndianUnicode} (UTF-16BE, preamble FE FF). */
    public static final Encoding BigEndianUnicode = new Encoding(StandardCharsets.UTF_16BE, "utf-16BE", 1201,
            new byte[] {(byte) 0xFE, (byte) 0xFF});

    /** UTF-8 without BOM: the default of .NET {@code StreamWriter}/{@code File.WriteAllText}. */
    static final Encoding UTF8_NO_BOM = new Encoding(StandardCharsets.UTF_8, "utf-8", 65001, new byte[0]);

    private final Charset charset;
    private final String webName;
    private final int codePage;
    private final byte[] preamble;

    protected Encoding(Charset charset, String webName, int codePage, byte[] preamble) {
        this.charset = charset;
        this.webName = webName;
        this.codePage = codePage;
        this.preamble = preamble;
    }

    /** .NET {@code Encoding.GetEncoding(name)}: utf-8, windows-1252, us-ascii, utf-16, iso-8859-1, … */
    public static Encoding GetEncoding(String name) {
        if (name == null) {
            throw new ArgumentNullException("name");
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        switch (n) {
            case "utf-8":
            case "utf8":
                return UTF8;
            case "windows-1252":
            case "cp1252":
                return Default;
            case "us-ascii":
            case "ascii":
                return ASCII;
            case "utf-16":
            case "utf-16le":
            case "unicode":
                return Unicode;
            case "utf-16be":
                return BigEndianUnicode;
            default:
                try {
                    Charset cs = Charset.forName(name.trim());
                    return new Encoding(cs, n, 0, new byte[0]);
                } catch (RuntimeException e) {
                    throw new ArgumentException("'" + name
                            + "' is not a supported encoding name. For information on defining a custom encoding, see the documentation for the Encoding.RegisterProvider method.",
                            "name");
                }
        }
    }

    /** .NET {@code Encoding.GetEncoding(codepage)}. */
    public static Encoding GetEncoding(int codePage) {
        switch (codePage) {
            case 65001: return UTF8;
            case 1252: return Default;
            case 20127: return ASCII;
            case 1200: return Unicode;
            case 1201: return BigEndianUnicode;
            case 28591: return GetEncoding("iso-8859-1");
            default:
                throw new ArgumentException("No data is available for encoding " + codePage + ".", "codepage");
        }
    }

    /** The JDK charset behind this encoding (for the web/mail facades). */
    public Charset charset() {
        return charset;
    }

    /** .NET {@code WebName}. */
    public String WebName() {
        return webName;
    }

    /** .NET {@code CodePage}. */
    public int CodePage() {
        return codePage;
    }

    /** .NET {@code GetPreamble()} [S1-21]: UTF8 → EF BB BF, Default → empty. */
    public byte[] GetPreamble() {
        return preamble.clone();
    }

    /** .NET {@code GetBytes(string)}; null → ArgumentNullException("s"). */
    public byte[] GetBytes(String s) {
        if (s == null) {
            throw new ArgumentNullException("s");
        }
        CharsetEncoder enc = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        if (charset == StandardCharsets.UTF_8) {
            enc.replaceWith(new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});
        }
        try {
            ByteBuffer bb = enc.encode(CharBuffer.wrap(s));
            return Arrays.copyOfRange(bb.array(), bb.position(), bb.limit());
        } catch (CharacterCodingException e) {
            return s.getBytes(charset);
        }
    }

    /** .NET {@code GetString(byte[])}: lossy decode (U+FFFD for malformed input); null → ArgumentNullException. */
    public String GetString(byte[] bytes) {
        if (bytes == null) {
            throw new ArgumentNullException("bytes");
        }
        return GetString(bytes, 0, bytes.length);
    }

    /** .NET {@code GetString(byte[], index, count)}. */
    public String GetString(byte[] bytes, int index, int count) {
        if (bytes == null) {
            throw new ArgumentNullException("bytes");
        }
        if (index < 0 || count < 0 || index > bytes.length - count) {
            throw new ArgumentOutOfRangeException("index", "Index and count must refer to a location within the buffer.");
        }
        return new String(bytes, index, count, charset);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Encoding e && e.charset.equals(charset) && Arrays.equals(e.preamble, preamble);
    }

    @Override
    public int hashCode() {
        return charset.hashCode();
    }

    @Override
    public String toString() {
        return "System.Text." + (this == UTF8 ? "UTF8Encoding" : "Encoding");
    }

    /** windows-1252 with .NET's mapping of the five unassigned bytes to the same code point. */
    private static final class Windows1252 extends Encoding {
        private static final char[] HIGH = {
            '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡',
            'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
            '\u0090', '‘', '’', '“', '”', '•', '–', '—',
            '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ'};

        Windows1252() {
            super(Charset.forName("windows-1252"), "Windows-1252", 1252, new byte[0]);
        }

        @Override
        public String GetString(byte[] bytes, int index, int count) {
            if (bytes == null) {
                throw new ArgumentNullException("bytes");
            }
            if (index < 0 || count < 0 || index > bytes.length - count) {
                throw new ArgumentOutOfRangeException("index", "Index and count must refer to a location within the buffer.");
            }
            char[] out = new char[count];
            for (int i = 0; i < count; i++) {
                int b = bytes[index + i] & 0xFF;
                out[i] = (b >= 0x80 && b <= 0x9F) ? HIGH[b - 0x80] : (char) b;
            }
            return new String(out);
        }

        /** Unmappable characters → {@code '?'}. TODO(port): .NET best-fit mapping (e.g. 'ā' → 'a') is not reproduced. */
        @Override
        public byte[] GetBytes(String s) {
            if (s == null) {
                throw new ArgumentNullException("s");
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c < 0x80 || (c >= 0xA0 && c <= 0xFF)) {
                    out.write(c);
                    continue;
                }
                int found = -1;
                for (int k = 0; k < HIGH.length; k++) {
                    if (HIGH[k] == c) {
                        found = 0x80 + k;
                        break;
                    }
                }
                if (found >= 0) {
                    out.write(found);
                } else {
                    if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                        i++;
                    }
                    out.write('?');
                }
            }
            return out.toByteArray();
        }
    }
}
