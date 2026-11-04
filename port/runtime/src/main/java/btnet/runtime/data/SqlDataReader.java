package btnet.runtime.data;

import btnet.runtime.DBNull;
import btnet.runtime.DotNetException;
import btnet.runtime.IndexOutOfRangeException;
import btnet.runtime.InvalidCastException;
import btnet.runtime.InvalidOperationException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * .NET {@code System.Data.SqlClient.SqlDataReader}: forward-only over the results of one
 * batch. Values carry .NET types (RULEBOOK §2A "Results"). Typed getters throw
 * {@link SqlNullValueException} on SQL NULL and {@code InvalidCastException("Specified cast is
 * not valid.")} on a type mismatch (no conversion, as SqlClient). {@code Close()}/{@code close()}
 * are idempotent, consume the remaining results, close the JDBC statement and — when opened
 * with {@code CommandBehavior.CloseConnection} — the connection (RULEBOOK §3 [N3]).
 */
public class SqlDataReader implements AutoCloseable {

    /** .NET {@code System.Data.SqlTypes.SqlNullValueException}. */
    public static class SqlNullValueException extends DotNetException {
        private static final long serialVersionUID = 1L;

        public SqlNullValueException() {
            super("Data is Null. This method or property cannot be called on Null values.");
        }
    }

    private final SqlBatch batch;
    private final SqlConnection connection;
    private final int behavior;
    private ResultSet rs;
    private String[] names = new String[0];
    private Class<?>[] types = new Class<?>[0];
    private Object[] current;
    private boolean pending;          // a row has been fetched but not yet returned by Read()
    private boolean anyRow;
    private boolean closed;
    private int rowsRead;

    SqlDataReader(SqlBatch batch, SqlConnection connection, int behavior) {
        this.batch = batch;
        this.connection = connection;
        this.behavior = behavior;
        try {
            openResult(batch.NextResultSet());
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    private void openResult(ResultSet next) throws SQLException {
        rs = next;
        current = null;
        rowsRead = 0;
        anyRow = false;
        pending = false;
        if (rs == null) {
            names = new String[0];
            types = new Class<?>[0];
            return;
        }
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        names = new String[n];
        types = new Class<?>[n];
        for (int i = 0; i < n; i++) {
            names[i] = md.getColumnLabel(i + 1);
            types[i] = SqlBatch.clrType(md, i + 1);
        }
        if (rs.next()) {
            pending = true;
            anyRow = true;
        }
    }

    private void checkOpen(String what) {
        if (closed) {
            throw new InvalidOperationException("Invalid attempt to call " + what + " when reader is closed.");
        }
    }

    public boolean Read() {
        checkOpen("Read");
        if (rs == null) return false;
        try {
            if (!pending) {
                if ((behavior & CommandBehavior.SingleRow) != 0 && rowsRead >= 1) {
                    current = null;
                    return false;
                }
                if (!rs.next()) {
                    current = null;
                    return false;
                }
            }
            pending = false;
            Object[] row = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                row[i] = SqlBatch.readValue(rs, i + 1, types[i]);
            }
            current = row;
            rowsRead++;
            return true;
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    public boolean NextResult() {
        checkOpen("NextResult");
        try {
            if ((behavior & CommandBehavior.SingleResult) != 0) {
                batch.drain();
                openResult(null);
                return false;
            }
            ResultSet next = batch.NextResultSet();
            openResult(next);
            return next != null;
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    public boolean HasRows() {
        checkOpen("HasRows");
        return anyRow;
    }

    public boolean IsClosed() {
        return closed;
    }

    public int FieldCount() {
        return names.length;
    }

    public int RecordsAffected() {
        return batch.RecordsAffected();
    }

    public String GetName(int i) {
        checkIndex(i);
        return names[i];
    }

    public Class<?> GetFieldType(int i) {
        checkIndex(i);
        return types[i];
    }

    /** Exact name first, then case-insensitive; missing → {@code IndexOutOfRangeException(name)}. */
    public int GetOrdinal(String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return i;
        }
        String up = name == null ? null : name.toUpperCase(Locale.ROOT);
        for (int i = 0; i < names.length; i++) {
            if (names[i].toUpperCase(Locale.ROOT).equals(up)) return i;
        }
        throw new IndexOutOfRangeException(name);
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= names.length) {
            throw new IndexOutOfRangeException("Index was outside the bounds of the array.");
        }
    }

    public Object GetValue(int i) {
        checkOpen("GetValue");
        checkIndex(i);
        if (current == null) {
            throw new InvalidOperationException("Invalid attempt to read when no data is present.");
        }
        return current[i];
    }

    /** {@code reader[i]}. */
    public Object get(int i) {
        return GetValue(i);
    }

    /** {@code reader["name"]}. */
    public Object get(String name) {
        return GetValue(GetOrdinal(name));
    }

    public boolean IsDBNull(int i) {
        return GetValue(i) == DBNull.Value;
    }

    private <T> T typed(int i, Class<T> type) {
        Object v = GetValue(i);
        if (v == DBNull.Value) {
            throw new SqlNullValueException();
        }
        if (!type.isInstance(v)) {
            throw new InvalidCastException("Specified cast is not valid.");
        }
        return type.cast(v);
    }

    public int GetInt32(int i) { return typed(i, Integer.class); }

    public short GetInt16(int i) { return typed(i, Short.class); }

    public long GetInt64(int i) { return typed(i, Long.class); }

    public boolean GetBoolean(int i) { return typed(i, Boolean.class); }

    public String GetString(int i) { return typed(i, String.class); }

    public BigDecimal GetDecimal(int i) { return typed(i, BigDecimal.class); }

    public double GetDouble(int i) { return typed(i, Double.class); }

    public float GetFloat(int i) { return typed(i, Float.class); }

    public LocalDateTime GetDateTime(int i) { return typed(i, LocalDateTime.class); }

    public UUID GetGuid(int i) { return typed(i, UUID.class); }

    /** tinyint values are {@code Short} (RULEBOOK §2A); .NET {@code GetByte}. */
    public short GetByte(int i) {
        if (types[i] != btnet.runtime.ClrTypes.Byte.class) {
            Object v = GetValue(i);
            if (v == DBNull.Value) throw new SqlNullValueException();
            throw new InvalidCastException("Specified cast is not valid.");
        }
        return typed(i, Short.class);
    }

    /** Called by the connection when it is closed under an open reader. */
    void connectionClosing() {
        if (!closed) {
            closed = true;
            batch.close();
        }
    }

    public void Close() {
        if (closed) return;
        closed = true;
        try {
            batch.drain();
        } catch (SQLException e) {
            throw new SqlException(e);
        } finally {
            batch.close();
            if (connection != null && connection.activeReader == this) {
                connection.activeReader = null;
            }
            if ((behavior & CommandBehavior.CloseConnection) != 0 && connection != null) {
                connection.Close();
            }
        }
    }

    public void Dispose() {
        Close();
    }

    @Override
    public void close() {
        Close();
    }
}
