package btnet.runtime;

/** Stands for .NET {@code System.DBNull}; {@link #Value} is the singleton, {@code toString()} is {@code ""}. */
public final class DBNull {
    /** .NET {@code DBNull.Value}. */
    public static final DBNull Value = new DBNull();

    private DBNull() {
    }

    @Override
    public String toString() {
        return "";
    }
}
