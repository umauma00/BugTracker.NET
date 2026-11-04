package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.DBNull;
import btnet.runtime.IndexOutOfRangeException;

import java.util.ArrayList;
import java.util.List;

/**
 * .NET {@code System.Data.DataRow}. Indexers: {@code dr["col"]} → {@link #get(String)},
 * {@code dr[i]} → {@link #get(int)}, {@code dr[column]} → {@link #get(DataColumn)}; writes via
 * the {@code set} overloads, which convert into the column type (see {@link DataColumn}).
 * Values keep their .NET types; SQL NULL is {@code DBNull.Value}, never {@code null}.
 * Column lookup is case-insensitive (exact match first); an unknown name →
 * {@code ArgumentException("Column 'x' does not belong to table T.")}.
 */
public class DataRow {
    private final DataTable table;
    private final List<Object> values = new ArrayList<>();
    boolean attached;

    DataRow(DataTable table) {
        this.table = table;
        for (DataColumn c : table.Columns()) {
            values.add(c.DefaultValue());
        }
    }

    public DataTable Table() {
        return table;
    }

    private DataColumn column(String name) {
        DataColumn c = table.Columns().find(name);
        if (c == null) {
            throw new ArgumentException("Column '" + name + "' does not belong to table " + table.TableName() + ".");
        }
        return c;
    }

    private int ordinal(DataColumn column) {
        int i = column.table == table ? table.Columns().IndexOf(column) : -1;
        if (i < 0) {
            throw new ArgumentException("Column '" + column.ColumnName() + "' does not belong to table "
                    + table.TableName() + ".");
        }
        return i;
    }

    private void checkOrdinal(int i) {
        if (i < 0 || i >= table.Columns().Count()) {
            throw new IndexOutOfRangeException("Cannot find column " + i + ".");
        }
    }

    Object valueAt(int i) {
        if (i < values.size()) {
            return values.get(i);
        }
        return table.Columns().get(i).DefaultValue();
    }

    private void store(int i, Object v) {
        while (values.size() <= i) {
            values.add(table.Columns().get(values.size()).DefaultValue());
        }
        values.set(i, v);
        table.changed();
    }

    void removeValueAt(int i) {
        if (i < values.size()) values.remove(i);
    }

    public Object get(String columnName) {
        DataColumn c = column(columnName);
        return valueAt(table.Columns().IndexOf(c));
    }

    public Object get(int columnIndex) {
        checkOrdinal(columnIndex);
        return valueAt(columnIndex);
    }

    public Object get(DataColumn column) {
        return valueAt(ordinal(column));
    }

    public void set(String columnName, Object value) {
        DataColumn c = column(columnName);
        store(table.Columns().IndexOf(c), DataColumn.coerce(c, value));
    }

    public void set(int columnIndex, Object value) {
        checkOrdinal(columnIndex);
        store(columnIndex, DataColumn.coerce(table.Columns().get(columnIndex), value));
    }

    public void set(DataColumn column, Object value) {
        int i = ordinal(column);
        store(i, DataColumn.coerce(column, value));
    }

    /** Stores a value already of the column's type (used by {@code Fill}). */
    void setRaw(DataColumn column, Object value) {
        store(ordinal(column), value);
    }

    public boolean IsNull(String columnName) {
        return get(columnName) == DBNull.Value;
    }

    public boolean IsNull(int columnIndex) {
        return get(columnIndex) == DBNull.Value;
    }

    public boolean IsNull(DataColumn column) {
        return get(column) == DBNull.Value;
    }

    public Object[] ItemArray() {
        int n = table.Columns().Count();
        Object[] a = new Object[n];
        for (int i = 0; i < n; i++) a[i] = valueAt(i);
        return a;
    }

    public void ItemArray(Object[] items) {
        int n = table.Columns().Count();
        if (items.length > n) {
            throw new ArgumentException("Input array is longer than the number of columns in this table.");
        }
        for (int i = 0; i < items.length; i++) {
            store(i, DataColumn.coerce(table.Columns().get(i), items[i]));
        }
    }

    /** Removes the row from its table (the port keeps no deleted-row versions). */
    public void Delete() {
        if (attached) {
            table.Rows().Remove(this);
        }
    }

    public void AcceptChanges() {
        // no row versions are kept by the port
    }
}
