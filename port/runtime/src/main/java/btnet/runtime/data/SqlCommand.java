package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.InvalidOperationException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * .NET {@code System.Data.SqlClient.SqlCommand}. Execution goes through {@link SqlBatch}
 * (SQL text never rewritten; parameters as a DECLARE prefix). Same members as .NET:
 * {@code CommandText}, {@code Connection}, {@code CommandTimeout} (default 30 s),
 * {@code Parameters()}, {@code ExecuteNonQuery}, {@code ExecuteScalar}, {@code ExecuteReader}.
 * <p>
 * {@code close()} is idempotent; if a {@link SqlDataReader} produced by this command is still
 * open, the reader keeps (owns) its JDBC statement (RULEBOOK §3 [N3]).
 */
public class SqlCommand implements AutoCloseable {
    private String commandText = "";
    private SqlConnection connection;
    private int commandTimeout = 30;
    private final SqlParameterCollection parameters = new SqlParameterCollection();

    public SqlCommand() {}

    public SqlCommand(String cmdText) {
        CommandText(cmdText);
    }

    public SqlCommand(String cmdText, SqlConnection connection) {
        CommandText(cmdText);
        this.connection = connection;
    }

    public String CommandText() { return commandText; }

    public void CommandText(String value) { this.commandText = value == null ? "" : value; }

    public SqlConnection Connection() { return connection; }

    public void Connection(SqlConnection value) { this.connection = value; }

    public int CommandTimeout() { return commandTimeout; }

    public void CommandTimeout(int value) {
        if (value < 0) {
            throw new ArgumentException("Invalid CommandTimeout value " + value + "; the value must be >= 0.\r\nParameter name: CommandTimeout");
        }
        this.commandTimeout = value;
    }

    public SqlParameterCollection Parameters() { return parameters; }

    private java.sql.Connection validate(String op) {
        if (connection == null) {
            throw new InvalidOperationException(op + ": Connection property has not been initialized.");
        }
        java.sql.Connection c = connection.jdbc(op);
        if (connection.activeReader != null && !connection.activeReader.IsClosed()) {
            throw new InvalidOperationException("There is already an open DataReader associated with this Command which must be closed first.");
        }
        if (commandText.isEmpty()) {
            throw new InvalidOperationException(op + ": CommandText property has not been initialized");
        }
        return c;
    }

    SqlBatch start(String op) {
        java.sql.Connection c = validate(op);
        try {
            return SqlBatch.execute(c, commandText, parameters.list(), commandTimeout);
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    /** Runs the whole batch; returns the rows affected by its statements, -1 if none. */
    public int ExecuteNonQuery() {
        try (SqlBatch b = start("ExecuteNonQuery")) {
            b.drain();
            return b.RecordsAffected();
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    /**
     * First column of the first row of the first result set; no result set or no rows →
     * {@code null}; SQL NULL → {@code DBNull.Value}. The rest of the batch still runs.
     */
    public Object ExecuteScalar() {
        try (SqlBatch b = start("ExecuteScalar")) {
            Object result = null;
            ResultSet rs = b.NextResultSet();
            if (rs != null) {
                ResultSetMetaData md = rs.getMetaData();
                if (md.getColumnCount() > 0 && rs.next()) {
                    result = SqlBatch.readValue(rs, 1, SqlBatch.clrType(md, 1));
                }
            }
            b.drain();
            return result;
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    public SqlDataReader ExecuteReader() {
        return ExecuteReader(CommandBehavior.Default);
    }

    public SqlDataReader ExecuteReader(int behavior) {
        SqlBatch b = start("ExecuteReader");
        try {
            SqlDataReader r = new SqlDataReader(b, connection, behavior);
            connection.activeReader = r;
            return r;
        } catch (RuntimeException e) {
            b.close();
            throw e;
        }
    }

    public void Dispose() {
        close();
    }

    @Override
    public void close() {
        // Nothing to release: non-query batches are closed when they finish, and an open
        // reader owns its statement (RULEBOOK §3 [N3]). Idempotent.
    }
}
