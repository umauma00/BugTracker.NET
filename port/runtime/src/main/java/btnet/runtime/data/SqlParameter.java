package btnet.runtime.data;

import btnet.runtime.ArgumentException;

/**
 * .NET {@code System.Data.SqlClient.SqlParameter}. Properties follow the runtime facade
 * convention (RULEBOOK §2A): read {@code p.Value()}, write {@code p.Value(v)}.
 * <p>
 * The SQL type sent is the explicit {@link SqlDbType} when one was set, else inferred from
 * the value exactly as RULEBOOK §2A "Parameter binding" states (see {@link SqlBatch}).
 * Like SqlClient, a parameter can belong to only one {@link SqlParameterCollection}.
 */
public class SqlParameter {
    private String parameterName = "";
    private Object value;
    private SqlDbType sqlDbType;          // null = inferred from the value
    private int size;
    private byte precision;
    private byte scale;
    SqlParameterCollection parent;

    public SqlParameter() {}

    public SqlParameter(String parameterName, Object value) {
        this.parameterName = parameterName == null ? "" : parameterName;
        this.value = value;
    }

    public SqlParameter(String parameterName, SqlDbType dbType) {
        this.parameterName = parameterName == null ? "" : parameterName;
        this.sqlDbType = dbType;
    }

    public SqlParameter(String parameterName, SqlDbType dbType, int size) {
        this(parameterName, dbType);
        Size(size);
    }

    public String ParameterName() { return parameterName; }

    public void ParameterName(String value) { this.parameterName = value == null ? "" : value; }

    public Object Value() { return value; }

    public void Value(Object value) { this.value = value; }

    /** The explicit type, or the type SqlClient would infer from the current value. */
    public SqlDbType SqlDbType() {
        return sqlDbType != null ? sqlDbType : SqlBatch.inferType(value);
    }

    public void SqlDbType(SqlDbType value) { this.sqlDbType = value; }

    /** {@code true} when {@link #SqlDbType(SqlDbType)} was set explicitly. */
    boolean hasExplicitType() { return sqlDbType != null; }

    public int Size() { return size; }

    public void Size(int value) {
        if (value < -1) {
            throw new ArgumentException("Invalid parameter Size value '" + value
                    + "'. The value must be greater than or equal to 0.");
        }
        this.size = value;
    }

    public byte Precision() { return precision; }

    public void Precision(byte value) { this.precision = value; }

    public byte Scale() { return scale; }

    public void Scale(byte value) { this.scale = value; }

    /** .NET {@code SqlParameter.ToString()} = {@code ParameterName}. */
    @Override
    public String toString() {
        return parameterName;
    }
}
