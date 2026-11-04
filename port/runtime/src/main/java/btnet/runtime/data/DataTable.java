package btnet.runtime.data;

/**
 * .NET {@code System.Data.DataTable}: {@code Columns()}, {@code Rows()}, {@code NewRow()},
 * {@code DefaultView()} (one cached {@link DataView}), {@code TableName}. Serialized to JSON by
 * {@link DataTableSerializer} (array of row objects).
 */
public class DataTable {
    private String tableName;
    DataSet dataSet;
    private final DataColumnCollection columns = new DataColumnCollection(this);
    private final DataRowCollection rows = new DataRowCollection(this);
    private DataView defaultView;
    private int version;

    public DataTable() {
        this("");
    }

    public DataTable(String tableName) {
        this.tableName = tableName == null ? "" : tableName;
    }

    public String TableName() {
        return tableName;
    }

    public void TableName(String value) {
        String v = value == null ? "" : value;
        if (dataSet != null && !v.equalsIgnoreCase(tableName) && dataSet.Tables().get(v) != null) {
            throw new DuplicateNameException("A DataTable named '" + v + "' already belongs to this DataSet.");
        }
        this.tableName = v;
    }

    public DataColumnCollection Columns() {
        return columns;
    }

    public DataRowCollection Rows() {
        return rows;
    }

    public DataSet DataSet() {
        return dataSet;
    }

    public DataRow NewRow() {
        return new DataRow(this);
    }

    public DataView DefaultView() {
        if (defaultView == null) {
            defaultView = new DataView(this);
        }
        return defaultView;
    }

    public void Clear() {
        rows.Clear();
    }

    public void AcceptChanges() {
        // no row versions are kept by the port
    }

    /** Copies a row (values only) into this table, as .NET {@code ImportRow}. */
    public void ImportRow(DataRow row) {
        DataRow r = NewRow();
        for (DataColumn c : columns) {
            DataColumn src = row.Table().Columns().find(c.ColumnName());
            if (src != null) {
                r.set(c, row.get(src));
            }
        }
        rows.Add(r);
    }

    /** Monotonic change counter read by views. */
    int version() {
        return version;
    }

    void changed() {
        version++;
    }

    /** .NET {@code DataTable.ToString()} = {@code TableName}. */
    @Override
    public String toString() {
        return tableName;
    }
}
