package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.IndexOutOfRangeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * .NET {@code System.Data.DataColumnCollection} ({@code dt.Columns()}): ordered, iterable in
 * column order; lookup by name is exact first, then case-insensitive, and returns {@code null}
 * when absent (.NET indexer). Duplicate names (case-insensitive) →
 * {@link DuplicateNameException} (RULEBOOK §3 [S1-19]).
 */
public class DataColumnCollection implements Iterable<DataColumn> {
    private final DataTable table;
    private final List<DataColumn> columns = new ArrayList<>();

    DataColumnCollection(DataTable table) {
        this.table = table;
    }

    public int Count() {
        return columns.size();
    }

    public DataColumn get(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IndexOutOfRangeException("Cannot find column " + index + ".");
        }
        return columns.get(index);
    }

    /** {@code null} when absent (.NET {@code Columns[name]}). */
    public DataColumn get(String name) {
        return find(name);
    }

    DataColumn find(String name) {
        if (name == null) return null;
        for (DataColumn c : columns) {
            if (c.ColumnName().equals(name)) return c;
        }
        for (DataColumn c : columns) {
            if (c.ColumnName().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    public boolean Contains(String name) {
        return find(name) != null;
    }

    public int IndexOf(String name) {
        DataColumn c = find(name);
        return c == null ? -1 : columns.indexOf(c);
    }

    public int IndexOf(DataColumn column) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i) == column) return i;
        }
        return -1;
    }

    public void Add(DataColumn column) {
        if (column.table == table) {
            throw new ArgumentException("Column '" + column.ColumnName() + "' already belongs to this DataTable.");
        }
        if (column.table != null) {
            throw new ArgumentException("Column '" + column.ColumnName() + "' already belongs to another DataTable.");
        }
        if (column.ColumnName().isEmpty()) {
            column.ColumnName(uniqueName(""));
        } else if (find(column.ColumnName()) != null && sameNameIgnoreCase(column.ColumnName())) {
            throw new DuplicateNameException("A column named '" + column.ColumnName() + "' already belongs to this DataTable.");
        }
        column.table = table;
        columns.add(column);
        table.changed();
    }

    private boolean sameNameIgnoreCase(String name) {
        for (DataColumn c : columns) {
            if (c.ColumnName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public DataColumn Add(String columnName, Class<?> type) {
        DataColumn c = new DataColumn(columnName, type);
        Add(c);
        return c;
    }

    public DataColumn Add(String columnName) {
        DataColumn c = new DataColumn(columnName);
        Add(c);
        return c;
    }

    public DataColumn Add() {
        DataColumn c = new DataColumn();
        Add(c);
        return c;
    }

    public void Remove(String name) {
        DataColumn c = find(name);
        if (c == null) {
            throw new ArgumentException("Column '" + name + "' does not belong to table " + table.TableName() + ".");
        }
        Remove(c);
    }

    public void Remove(DataColumn column) {
        int idx = IndexOf(column);
        if (idx < 0) {
            throw new ArgumentException("Cannot remove a column that doesn't belong to this table.");
        }
        columns.remove(idx);
        column.table = null;
        for (DataRow r : table.Rows()) {
            r.removeValueAt(idx);
        }
        table.changed();
    }

    /**
     * The name a newly mapped result column gets (RULEBOOK §2A [J-C12]): empty →
     * {@code Column1}, {@code Column2}, … (first free); a name already present
     * (case-insensitively) → {@code name1}, {@code name2}, … (first free); else the name itself.
     */
    String uniqueName(String base) {
        if (base == null || base.isEmpty()) {
            for (int k = 1; ; k++) {
                if (!sameNameIgnoreCase("Column" + k)) return "Column" + k;
            }
        }
        if (!sameNameIgnoreCase(base)) return base;
        for (int k = 1; ; k++) {
            if (!sameNameIgnoreCase(base + k)) return base + k;
        }
    }

    public Stream<DataColumn> stream() {
        return columns.stream();
    }

    @Override
    public Iterator<DataColumn> iterator() {
        return Collections.unmodifiableList(columns).iterator();
    }
}
