package btnet.runtime.data;

/**
 * .NET {@code System.Data.CommandBehavior} ([Flags] enum) as int constants with the
 * .NET numeric values (RULEBOOK §2A [J-C5]); every parameter of this type is {@code int},
 * so {@code behavior | CommandBehavior.CloseConnection} translates unchanged.
 */
public final class CommandBehavior {
    private CommandBehavior() {}

    public static final int Default = 0;
    public static final int SingleResult = 1;
    public static final int SchemaOnly = 2;
    public static final int KeyInfo = 4;
    public static final int SingleRow = 8;
    public static final int SequentialAccess = 16;
    public static final int CloseConnection = 32;
}
