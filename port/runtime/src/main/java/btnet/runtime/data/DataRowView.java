package btnet.runtime.data;

/**
 * .NET {@code System.Data.DataRowView}: one row as seen through a {@link DataView}
 * ({@code drv["col"]} → {@code drv.get("col")}, {@code drv.Row} → {@code drv.Row()}).
 */
public class DataRowView {
    private final DataView view;
    private final DataRow row;

    DataRowView(DataView view, DataRow row) {
        this.view = view;
        this.row = row;
    }

    public DataView DataView() { return view; }

    public DataRow Row() { return row; }

    public Object get(String columnName) { return row.get(columnName); }

    public Object get(int columnIndex) { return row.get(columnIndex); }

    public void set(String columnName, Object value) { row.set(columnName, value); }

    public void set(int columnIndex, Object value) { row.set(columnIndex, value); }

    public void Delete() { row.Delete(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof DataRowView d && d.row == row && d.view == view;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(row);
    }

    @Override
    public String toString() {
        return "System.Data.DataRowView";
    }
}
