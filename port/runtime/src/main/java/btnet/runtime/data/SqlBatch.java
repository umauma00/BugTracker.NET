package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.ClrTypes;
import btnet.runtime.DBNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The one parameter-binding strategy of the port (RULEBOOK §2A "Parameter binding"),
 * standing for SqlClient's {@code sp_executesql}: a command with N parameters is sent as
 * one {@link PreparedStatement} whose text is
 * {@code DECLARE @p1 <type> = ?, …, @pN <type> = ?;\n} + the original SQL (never rewritten),
 * so a parameter used twice binds once, T-SQL may reassign it, and a missing parameter fails
 * with "Must declare the scalar variable". Zero parameters → the text is sent unmodified
 * through {@link Statement#execute(String)} (DDL batches work).
 * <p>
 * An instance is one executing batch: {@link #NextResultSet()} walks the results in order,
 * skipping (and summing) update counts, exactly as SqlClient surfaces them. It also owns the
 * value mapping SQL type → .NET type used by {@code Fill}, readers and {@code ExecuteScalar}.
 */
public final class SqlBatch implements AutoCloseable {
    private final Statement statement;
    private boolean isResultSet;
    private boolean started;
    private boolean done;
    private int rowsAffected = -1;
    private boolean closed;

    private SqlBatch(Statement statement, boolean firstIsResultSet) {
        this.statement = statement;
        this.isResultSet = firstIsResultSet;
    }

    /** Executes {@code sql} with {@code parameters} on {@code conn}; the caller closes the batch. */
    public static SqlBatch execute(Connection conn, String sql, List<SqlParameter> parameters, int timeoutSeconds)
            throws SQLException {
        if (parameters == null || parameters.isEmpty()) {
            Statement st = conn.createStatement();
            try {
                st.setQueryTimeout(Math.max(0, timeoutSeconds));
                boolean rs = st.execute(sql);
                return new SqlBatch(st, rs);
            } catch (SQLException | RuntimeException e) {
                closeQuietly(st);
                throw e;
            }
        }
        String text = commandText(sql, parameters);
        PreparedStatement ps = conn.prepareStatement(text);
        try {
            ps.setQueryTimeout(Math.max(0, timeoutSeconds));
            for (int i = 0; i < parameters.size(); i++) {
                bind(ps, i + 1, parameters.get(i));
            }
            boolean rs = ps.execute();
            return new SqlBatch(ps, rs);
        } catch (SQLException | RuntimeException e) {
            closeQuietly(ps);
            throw e;
        }
    }

    /** The exact text sent for {@code sql} with {@code parameters} (RULEBOOK §2A). */
    public static String commandText(String sql, List<SqlParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return sql;
        }
        StringBuilder sb = new StringBuilder("DECLARE ");
        for (int i = 0; i < parameters.size(); i++) {
            SqlParameter p = parameters.get(i);
            if (i > 0) sb.append(", ");
            sb.append(normalizedName(p.ParameterName())).append(' ').append(declaredType(p)).append(" = ?");
        }
        sb.append(";\n").append(sql);
        return sb.toString();
    }

    /** SqlClient prefixes a missing {@code @}. */
    static String normalizedName(String name) {
        if (name == null || name.isEmpty()) {
            return "@";
        }
        return name.startsWith("@") ? name : "@" + name;
    }

    /** SqlClient type inference from a value (RULEBOOK §2A). */
    static SqlDbType inferType(Object v) {
        if (v == null || v == DBNull.Value || v instanceof String || v instanceof Character) return SqlDbType.NVarChar;
        if (v instanceof Integer) return SqlDbType.Int;
        if (v instanceof Long) return SqlDbType.BigInt;
        if (v instanceof Short) return SqlDbType.SmallInt;
        if (v instanceof Byte) return SqlDbType.TinyInt;
        if (v instanceof Boolean) return SqlDbType.Bit;
        if (v instanceof BigDecimal) return SqlDbType.Decimal;
        if (v instanceof Double) return SqlDbType.Float;
        if (v instanceof Float) return SqlDbType.Real;
        if (v instanceof LocalDateTime) return SqlDbType.DateTime;
        if (v instanceof byte[]) return SqlDbType.VarBinary;
        if (v instanceof UUID) return SqlDbType.UniqueIdentifier;
        if (v instanceof Duration) return SqlDbType.Time;
        if (v instanceof OffsetDateTime) return SqlDbType.DateTimeOffset;
        throw new ArgumentException("No mapping exists from object type " + clrTypeName(v.getClass())
                + " to a known managed provider native type.");
    }

    /** T-SQL type used in the DECLARE for one parameter. */
    static String declaredType(SqlParameter p) {
        Object v = p.Value();
        SqlDbType t = p.hasExplicitType() ? p.SqlDbType() : inferType(v);
        int size = p.Size();
        switch (t) {
            case BigInt: return "bigint";
            case Int: return "int";
            case SmallInt: return "smallint";
            case TinyInt: return "tinyint";
            case Bit: return "bit";
            case Float: return "float";
            case Real: return "real";
            case Money: return "money";
            case SmallMoney: return "smallmoney";
            case DateTime: return "datetime";
            case SmallDateTime: return "smalldatetime";
            case Date: return "date";
            case Time: return "time";
            case DateTime2: return "datetime2";
            case DateTimeOffset: return "datetimeoffset";
            case UniqueIdentifier: return "uniqueidentifier";
            case Variant: return "sql_variant";
            case Xml: return "xml";
            case Timestamp: return "binary(8)";
            case Decimal: {
                if (p.hasExplicitType() && p.Precision() > 0) {
                    return "decimal(" + p.Precision() + "," + p.Scale() + ")";
                }
                int scale = v instanceof BigDecimal bd ? Math.max(0, Math.min(38, bd.scale())) : 0;
                return "decimal(38," + scale + ")";
            }
            case NVarChar: {
                if (p.hasExplicitType() && size > 0 && size <= 4000) return "nvarchar(" + size + ")";
                if (p.hasExplicitType() && size == -1) return "nvarchar(max)";
                return textLength(v) > 4000 ? "nvarchar(max)" : "nvarchar(4000)";
            }
            case NChar:
                return size > 0 && size <= 4000 ? "nchar(" + size + ")" : (textLength(v) > 4000 ? "nvarchar(max)" : "nvarchar(4000)");
            case VarChar:
                if (size > 0 && size <= 8000) return "varchar(" + size + ")";
                return textLength(v) > 8000 || size == -1 ? "varchar(max)" : "varchar(8000)";
            case Char:
                return size > 0 && size <= 8000 ? "char(" + size + ")" : "varchar(8000)";
            // text/ntext/image are invalid for DECLAREd variables; the (max) types convert implicitly
            case Text: return "varchar(max)";
            case NText: return "nvarchar(max)";
            case Image: return "varbinary(max)";
            case VarBinary:
                return size > 0 && size <= 8000 ? "varbinary(" + size + ")" : "varbinary(max)";
            case Binary:
                return size > 0 && size <= 8000 ? "binary(" + size + ")" : "varbinary(max)";
            default:
                throw new ArgumentException("SqlDbType." + t + " is not supported by the port's SqlBatch.");
        }
    }

    private static int textLength(Object v) {
        if (v instanceof String s) return s.length();
        return 0;
    }

    private static int nullJdbcType(String declared) {
        String d = declared.toLowerCase(Locale.ROOT);
        if (d.startsWith("varbinary") || d.startsWith("binary")) return Types.VARBINARY;
        if (d.startsWith("varchar") || d.startsWith("char")) return Types.VARCHAR;
        if (d.equals("int")) return Types.INTEGER;
        if (d.equals("bigint")) return Types.BIGINT;
        if (d.equals("smallint")) return Types.SMALLINT;
        if (d.equals("tinyint")) return Types.TINYINT;
        if (d.equals("bit")) return Types.BIT;
        if (d.startsWith("decimal") || d.equals("money") || d.equals("smallmoney")) return Types.DECIMAL;
        if (d.equals("float")) return Types.DOUBLE;
        if (d.equals("real")) return Types.REAL;
        if (d.contains("date") || d.equals("time")) return Types.TIMESTAMP;
        return Types.NVARCHAR;
    }

    static void bind(PreparedStatement ps, int index, SqlParameter p) throws SQLException {
        Object v = p.Value();
        String declared = declaredType(p);
        if (v == null || v == DBNull.Value) {
            ps.setNull(index, nullJdbcType(declared));
        } else if (v instanceof String s) {
            ps.setString(index, s);
        } else if (v instanceof Character c) {
            ps.setString(index, String.valueOf(c));
        } else if (v instanceof Integer i) {
            ps.setInt(index, i);
        } else if (v instanceof Long l) {
            ps.setLong(index, l);
        } else if (v instanceof Short s) {
            ps.setShort(index, s);
        } else if (v instanceof Byte b) {
            ps.setShort(index, (short) (b & 0xFF));
        } else if (v instanceof Boolean b) {
            ps.setBoolean(index, b);
        } else if (v instanceof BigDecimal bd) {
            ps.setBigDecimal(index, bd.scale() < 0 ? bd.setScale(0) : bd);
        } else if (v instanceof Double d) {
            ps.setDouble(index, d);
        } else if (v instanceof Float f) {
            ps.setFloat(index, f);
        } else if (v instanceof LocalDateTime ldt) {
            ps.setTimestamp(index, Timestamp.valueOf(ldt));
        } else if (v instanceof byte[] bytes) {
            ps.setBytes(index, bytes);
        } else if (v instanceof UUID u) {
            ps.setString(index, u.toString());
        } else if (v instanceof Duration d) {
            ps.setObject(index, LocalTime.ofNanoOfDay(d.toNanos()));
        } else if (v instanceof OffsetDateTime o) {
            ps.setObject(index, o);
        } else {
            throw new ArgumentException("No mapping exists from object type " + clrTypeName(v.getClass())
                    + " to a known managed provider native type.");
        }
    }

    // ------------------------------------------------------------------ results

    /**
     * Advances to the next result set, skipping update counts (which are summed for
     * {@link #RecordsAffected()}); {@code null} when there are no more results. A server error
     * raised by a later statement of the batch surfaces here as a {@link SQLException}.
     */
    public ResultSet NextResultSet() throws SQLException {
        if (done || closed) {
            return null;
        }
        if (started) {
            isResultSet = statement.getMoreResults();
        }
        started = true;
        while (true) {
            if (isResultSet) {
                return statement.getResultSet();
            }
            int uc = statement.getUpdateCount();
            if (uc == -1) {
                done = true;
                return null;
            }
            rowsAffected = (rowsAffected < 0 ? 0 : rowsAffected) + uc;
            isResultSet = statement.getMoreResults();
        }
    }

    /** Consumes every remaining result (surfacing late errors, as SqlClient does). */
    public void drain() throws SQLException {
        while (NextResultSet() != null) {
            // skip
        }
    }

    /** Sum of the update counts seen so far; -1 if none (.NET {@code RecordsAffected}). */
    public int RecordsAffected() {
        return rowsAffected;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        closeQuietly(statement);
    }

    private static void closeQuietly(Statement st) {
        try {
            st.close();
        } catch (SQLException ignored) {
            // nothing to report: SqlClient's Dispose does not throw either
        }
    }

    // ------------------------------------------------------------------ value mapping

    private static boolean isTypeName(ResultSetMetaData md, int col, String name) throws SQLException {
        String tn = md.getColumnTypeName(col);
        return tn != null && tn.toLowerCase(Locale.ROOT).startsWith(name);
    }

    /**
     * The .NET column type of a result column (RULEBOOK §2A "Results"): int→Integer,
     * smallint→Short, tinyint→{@code ClrTypes.Byte}, bigint→Long, bit→Boolean,
     * decimal/numeric/money→BigDecimal, float→Double, real→Float, dates→LocalDateTime,
     * strings→String, binary→byte[], uniqueidentifier→UUID.
     */
    public static Class<?> clrType(ResultSetMetaData md, int col) throws SQLException {
        if (isTypeName(md, col, "uniqueidentifier")) return UUID.class;
        if (isTypeName(md, col, "datetimeoffset")) return OffsetDateTime.class;
        if (isTypeName(md, col, "timestamp") || isTypeName(md, col, "rowversion")) return byte[].class;
        if (isTypeName(md, col, "sql_variant")) return Object.class;
        int t = md.getColumnType(col);
        switch (t) {
            case Types.INTEGER: return Integer.class;
            case Types.SMALLINT: return Short.class;
            case Types.TINYINT: return ClrTypes.Byte.class;
            case Types.BIGINT: return Long.class;
            case Types.BIT:
            case Types.BOOLEAN: return Boolean.class;
            case Types.DECIMAL:
            case Types.NUMERIC: return BigDecimal.class;
            case Types.FLOAT:
            case Types.DOUBLE: return Double.class;
            case Types.REAL: return Float.class;
            case Types.DATE:
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE: return LocalDateTime.class;
            case Types.TIME: return Duration.class;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB: return byte[].class;
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
            case Types.SQLXML: return String.class;
            default:
                if (isTypeName(md, col, "money") || isTypeName(md, col, "smallmoney")) return BigDecimal.class;
                if (isTypeName(md, col, "datetime") || isTypeName(md, col, "smalldatetime")) return LocalDateTime.class;
                return Object.class;
        }
    }

    /** Reads one value with its .NET type; SQL NULL → {@code DBNull.Value} (never Java null). */
    public static Object readValue(ResultSet rs, int col, Class<?> clrType) throws SQLException {
        Object v;
        if (clrType == Integer.class) {
            int i = rs.getInt(col);
            v = rs.wasNull() ? null : i;
        } else if (clrType == Short.class || clrType == ClrTypes.Byte.class) {
            short s = rs.getShort(col);
            v = rs.wasNull() ? null : s;
        } else if (clrType == Long.class) {
            long l = rs.getLong(col);
            v = rs.wasNull() ? null : l;
        } else if (clrType == Boolean.class) {
            boolean b = rs.getBoolean(col);
            v = rs.wasNull() ? null : b;
        } else if (clrType == BigDecimal.class) {
            v = rs.getBigDecimal(col);
        } else if (clrType == Double.class) {
            double d = rs.getDouble(col);
            v = rs.wasNull() ? null : d;
        } else if (clrType == Float.class) {
            float f = rs.getFloat(col);
            v = rs.wasNull() ? null : f;
        } else if (clrType == LocalDateTime.class) {
            Timestamp ts = rs.getTimestamp(col);
            v = ts == null ? null : ts.toLocalDateTime();
        } else if (clrType == Duration.class) {
            LocalTime lt = rs.getObject(col, LocalTime.class);
            v = lt == null ? null : Duration.ofNanos(lt.toNanoOfDay());
        } else if (clrType == OffsetDateTime.class) {
            v = rs.getObject(col, OffsetDateTime.class);
        } else if (clrType == byte[].class) {
            v = rs.getBytes(col);
        } else if (clrType == String.class) {
            v = rs.getString(col);
        } else if (clrType == UUID.class) {
            String s = rs.getString(col);
            v = s == null ? null : UUID.fromString(s);
        } else {
            v = rs.getObject(col);
            if (v instanceof Timestamp ts) v = ts.toLocalDateTime();
            else if (v instanceof java.sql.Date d) v = d.toLocalDate().atStartOfDay();
            else if (v instanceof LocalDate d) v = d.atStartOfDay();
            else if (v instanceof Byte b) v = (short) (b & 0xFF);
        }
        return v == null ? DBNull.Value : v;
    }

    /** The .NET simple type name for messages ({@code Int32}, {@code String}, …). */
    public static String clrTypeName(Class<?> c) {
        if (c == null) return "Object";
        if (c == String.class) return "String";
        if (c == Integer.class || c == int.class) return "Int32";
        if (c == Long.class || c == long.class) return "Int64";
        if (c == Short.class || c == short.class) return "Int16";
        if (c == ClrTypes.Byte.class || c == Byte.class || c == byte.class) return "Byte";
        if (c == ClrTypes.SByte.class) return "SByte";
        if (c == ClrTypes.UInt16.class) return "UInt16";
        if (c == ClrTypes.UInt32.class) return "UInt32";
        if (c == ClrTypes.UInt64.class) return "UInt64";
        if (c == Boolean.class || c == boolean.class) return "Boolean";
        if (c == BigDecimal.class) return "Decimal";
        if (c == Double.class || c == double.class) return "Double";
        if (c == Float.class || c == float.class) return "Single";
        if (c == LocalDateTime.class) return "DateTime";
        if (c == Duration.class) return "TimeSpan";
        if (c == OffsetDateTime.class) return "DateTimeOffset";
        if (c == UUID.class) return "Guid";
        if (c == byte[].class) return "Byte[]";
        if (c == DBNull.class) return "DBNull";
        if (c == Character.class || c == char.class) return "Char";
        if (c == Object.class) return "Object";
        return c.getSimpleName();
    }
}
