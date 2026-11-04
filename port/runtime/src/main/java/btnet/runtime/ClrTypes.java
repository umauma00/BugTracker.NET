package btnet.runtime;

/**
 * Type tokens for .NET types with no JDK class (RULEBOOK §3 S1-7): {@code typeof(byte)} →
 * {@code ClrTypes.Byte.class}, etc. Never instantiated.
 */
public final class ClrTypes {
    private ClrTypes() {
    }

    /** .NET {@code System.Byte} (values are held as {@link java.lang.Short} 0–255). */
    public static final class Byte {
        private Byte() {
        }
    }

    /** .NET {@code System.SByte}. */
    public static final class SByte {
        private SByte() {
        }
    }

    /** .NET {@code System.UInt16}. */
    public static final class UInt16 {
        private UInt16() {
        }
    }

    /** .NET {@code System.UInt32}. */
    public static final class UInt32 {
        private UInt32() {
        }
    }

    /** .NET {@code System.UInt64}. */
    public static final class UInt64 {
        private UInt64() {
        }
    }
}
