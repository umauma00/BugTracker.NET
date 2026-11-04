package btnet.runtime;

import java.util.function.Supplier;

/** Stands for the C# null-coalescing operator {@code a ?? b} (RULEBOOK §2A J-C7). */
public final class Nulls {
    private Nulls() {
    }

    /** {@code a ?? b} where {@code b} is a literal, local or field. */
    public static <T> T coalesce(T a, T b) {
        return a != null ? a : b;
    }

    /** {@code a ?? b} where {@code b} must only be evaluated when {@code a} is null. */
    public static <T> T coalesce(T a, Supplier<? extends T> b) {
        return a != null ? a : b.get();
    }
}
