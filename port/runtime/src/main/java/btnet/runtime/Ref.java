package btnet.runtime;

/** Stands for a C# {@code ref}/{@code out} parameter slot (RULEBOOK §2A). The callee writes {@link #value}. */
public final class Ref<T> {
    public T value;

    public Ref() {
    }

    public Ref(T value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
