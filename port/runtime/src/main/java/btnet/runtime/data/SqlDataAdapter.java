package btnet.runtime.data;

import btnet.runtime.InvalidOperationException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * .NET {@code System.Data.SqlClient.SqlDataAdapter} (fill only — the source never updates
 * through an adapter). {@link #Fill(DataSet)} adds one {@link DataTable} per result set
 * ({@code Table}, {@code Table1}, …), skipping update counts; columns carry the .NET types of
 * RULEBOOK §2A "Results"; an unnamed column is {@code Column1}, … and a repeated name
 * (case-insensitive) gets the suffix {@code 1}, {@code 2}, … [J-C12]. A closed connection is
 * opened for the fill and closed again afterwards, as .NET does.
 */
public class SqlDataAdapter implements AutoCloseable {
    private SqlCommand selectCommand;

    public SqlDataAdapter() {}

    public SqlDataAdapter(SqlCommand selectCommand) {
        this.selectCommand = selectCommand;
    }

    public SqlDataAdapter(String selectCommandText, SqlConnection selectConnection) {
        this.selectCommand = new SqlCommand(selectCommandText, selectConnection);
    }

    public SqlDataAdapter(String selectCommandText, String selectConnectionString) {
        this.selectCommand = new SqlCommand(selectCommandText, new SqlConnection(selectConnectionString));
    }

    public SqlCommand SelectCommand() {
        return selectCommand;
    }

    public void SelectCommand(SqlCommand value) {
        this.selectCommand = value;
    }

    /** Returns the number of rows added. */
    public int Fill(DataSet dataSet) {
        return fill(dataSet, null);
    }

    /** Fills the first result set into {@code dataTable} (adding missing columns). */
    public int Fill(DataTable dataTable) {
        return fill(null, dataTable);
    }

    private int fill(DataSet ds, DataTable single) {
        if (selectCommand == null) {
            throw new InvalidOperationException("The SelectCommand property has not been initialized before calling 'Fill'.");
        }
        SqlConnection conn = selectCommand.Connection();
        if (conn == null) {
            throw new InvalidOperationException("Fill: SelectCommand.Connection property has not been initialized.");
        }
        boolean openedHere = false;
        if (!conn.isOpen()) {
            conn.Open();
            openedHere = true;
        }
        int total = 0;
        try (SqlBatch b = selectCommand.start("Fill")) {
            ResultSet rs;
            boolean first = true;
            while ((rs = b.NextResultSet()) != null) {
                DataTable target;
                if (single != null) {
                    if (!first) {
                        break;
                    }
                    target = single;
                } else {
                    // TODO(port): .NET Fill merges into an existing "Table"; the port always adds a new table
                    target = new DataTable(ds.Tables().nextName());
                    ds.Tables().Add(target);
                }
                first = false;
                total += load(rs, target, single != null);
            }
            b.drain();
        } catch (SQLException e) {
            throw new SqlException(e);
        } finally {
            if (openedHere) {
                conn.Close();
            }
        }
        return total;
    }

    private static int load(ResultSet rs, DataTable target, boolean mapExisting) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        DataColumn[] cols = new DataColumn[n];
        Class<?>[] types = new Class<?>[n];
        java.util.Set<DataColumn> used = new java.util.HashSet<>();
        DataColumnCollection cc = target.Columns();
        for (int i = 0; i < n; i++) {
            types[i] = SqlBatch.clrType(md, i + 1);
            String label = md.getColumnLabel(i + 1);
            if (label == null) label = "";
            DataColumn existing = mapExisting && !label.isEmpty() ? cc.find(label) : null;
            if (existing != null && used.add(existing)) {
                cols[i] = existing;
            } else {
                DataColumn c = new DataColumn(cc.uniqueName(label), types[i]);
                cc.Add(c);
                used.add(c);
                cols[i] = c;
            }
        }
        int count = 0;
        while (rs.next()) {
            DataRow row = target.NewRow();
            for (int i = 0; i < n; i++) {
                Object v = SqlBatch.readValue(rs, i + 1, types[i]);
                if (cols[i].DataType() == types[i] || v == btnet.runtime.DBNull.Value) {
                    row.setRaw(cols[i], v);
                } else {
                    row.set(cols[i], v);
                }
            }
            target.Rows().Add(row);
            count++;
        }
        return count;
    }

    public void Dispose() {
        close();
    }

    @Override
    public void close() {
        // the adapter owns no resources; the command/connection belong to the caller
    }
}
